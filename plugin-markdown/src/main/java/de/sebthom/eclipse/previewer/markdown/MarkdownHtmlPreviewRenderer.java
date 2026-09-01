/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.EnumSet;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.markdown.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.markdown.preprocessor.MarkdownDiagramPreprocessor;
import de.sebthom.eclipse.previewer.markdown.preprocessor.MarkdownDiagramPreprocessor.DiagramType;
import de.sebthom.eclipse.previewer.markdown.renderer.CommonMarkRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.GitHubMarkdownRenderer;
import de.sebthom.eclipse.previewer.pikchr.PikchrRendering;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * Renders Markdown through the configured engine and adds browser-side support for embedded diagrams, math, and CommonMark code
 * highlighting.
 *
 * @author Sebastian Thomschke
 */
public class MarkdownHtmlPreviewRenderer implements HtmlPreviewRenderer {

   private File cssDark;
   private File cssLight;
   private File highlightJSCSS;
   private File highlightJS;
   private @Nullable File mathJaxJS;
   private File mermaidJS;

   private static final String HIGHLIGHT_JS_INIT_SCRIPT = """
      <script>
      (function() {
        if (typeof hljs === 'undefined' || typeof hljs.getLanguage !== 'function'
            || typeof hljs.highlightElement !== 'function') return;

        var codeBlocks = document.querySelectorAll('pre > code[class*="language-"]');
        for (var blockIndex = 0; blockIndex < codeBlocks.length; blockIndex++) {
          var codeBlock = codeBlocks[blockIndex];
          var languageMatch = /(?:^|\\s)language-([^\\s]+)/.exec(codeBlock.className);
          // Explicit fences are the contract. highlightAll() would also auto-detect and rewrite unlabelled blocks.
          if (!languageMatch || !hljs.getLanguage(languageMatch[1])) continue;

          try {
            hljs.highlightElement(codeBlock);
          } catch (error) {
            // One malformed block must not prevent later blocks from being highlighted or make the preview unreadable.
            if (typeof console !== 'undefined' && console.error) console.error(error);
          }
        }
      })();
      </script>
      """;

   private static final String MERMAID_INIT_SCRIPT = """
      <script>
      (function(){
        try {
          // Collect potential mermaid blocks from both CommonMark and GitHub API output
          const targets = new Set();
          document.querySelectorAll('pre > code.language-mermaid, pre.language-mermaid > code, code.language-mermaid, pre.language-mermaid')
            .forEach(n => targets.add(n.closest('pre') || n));
          document.querySelectorAll("[class*='source-mermaid']")
            .forEach(n => targets.add(n.closest('div.highlight, figure.highlight, pre') || n));

          targets.forEach(function(container){
            const pre = container.matches('pre')
                          ? container
                          : (container.querySelector && container.querySelector('pre')) || container.closest('pre') || container;
            const txt = (pre && pre.textContent ? pre.textContent : container.textContent || '').toString();
            const div = document.createElement('div');
            div.className = 'mermaid';
            div.textContent = txt.trim();
            container.parentNode.insertBefore(div, container);
            container.parentNode.removeChild(container);
          });

          if (document.querySelector('.mermaid')) {
              mermaid.initialize({ startOnLoad: false, theme: '$$THEME$$' });
              mermaid.run({ querySelector: '.mermaid' }).catch(function(err) {
                 if (typeof console !== 'undefined' && console.error) console.error(err);
              });
             }
           } catch (e) {
             if (typeof console !== 'undefined' && console.error) console.error(e);
           }
         })();
         </script>
         """;

   public MarkdownHtmlPreviewRenderer() throws IOException {
      cssDark = Plugin.resources().extract(Constants.MARKDOWN_CSS_DARK);
      cssLight = Plugin.resources().extract(Constants.MARKDOWN_CSS_LIGHT);
      highlightJSCSS = Plugin.resources().extract(Constants.HIGHLIGHT_JS_CSS);
      highlightJS = Plugin.resources().extract(Constants.HIGHLIGHT_JS);
      mermaidJS = de.sebthom.eclipse.previewer.mermaid.Plugin.resources().extract(
         de.sebthom.eclipse.previewer.mermaid.Constants.MERMAID_JS);
   }

   @Override
   public void dispose() {
   }

   private static EnumSet<DiagramType> getEnabledDiagramTypes() {
      final var enabledTypes = EnumSet.noneOf(DiagramType.class);
      if (PluginPreferences.isRenderPlantUmlAndGraphvizDiagrams()) {
         enabledTypes.add(DiagramType.GRAPHVIZ);
         enabledTypes.add(DiagramType.PLANTUML);
      }
      if (PluginPreferences.isRenderPikchrDiagrams()) {
         enabledTypes.add(DiagramType.PIKCHR);
      }
      return enabledTypes;
   }

   private int getPreferredTabSize() {
      return InstanceScope.INSTANCE.getNode("org.eclipse.ui.editors") //
         .getInt(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH, 4);
   }

   private synchronized File getMathJaxJS() throws IOException {
      var mathJaxJS = this.mathJaxJS;
      if (mathJaxJS == null) {
         // Delay extraction of the large component until a preview actually contains an explicit math marker.
         Plugin.resources().extract(Constants.MATHJAX_COLOR_JS);
         Plugin.resources().extract(Constants.MATHJAX_SAFE_JS);
         // The combined SVG component embeds MathJax's compact TeX font and therefore needs no extracted font tree.
         this.mathJaxJS = mathJaxJS = Plugin.resources().extract(Constants.MATHJAX_JS);
      }
      return mathJaxJS;
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      var renderer = PluginPreferences.getMarkdownRenderer(source.path());
      // Snapshot selection once so preprocessing and required page runtimes cannot diverge if preferences change mid-render.
      final var enabledDiagramTypes = getEnabledDiagramTypes();
      // Resolve the theme once so embedded Pikchr SVG and the surrounding Markdown page always use the same mode.
      final var useDarkTheme = MiscUtils.isDarkEclipseTheme();
      final var preprocessedMarkdown = MarkdownDiagramPreprocessor.preprocess(source, enabledDiagramTypes, useDarkTheme);

      final var htmlBody = new StringBuilder();

      boolean isCommonMarkFallback = false;
      try {
         renderer.markdownToHTML(preprocessedMarkdown.source(), htmlBody);
      } catch (final ConnectException ex) {
         if (renderer instanceof GitHubMarkdownRenderer && PluginPreferences.isGithubApiFallbackToCommonMark()) {
            Plugin.log().debug(ex);
            htmlBody.setLength(0);
            renderer = CommonMarkRenderer.INSTANCE;
            renderer.markdownToHTML(preprocessedMarkdown.source(), htmlBody);
            isCommonMarkFallback = true;
         } else
            throw ex;
      }
      preprocessedMarkdown.applyHtmlReplacements(htmlBody);
      final boolean containsMath = MathJaxSupport.containsMath(htmlBody);
      // Check after rendering so GitHub API fallback receives the same local support as explicitly selected CommonMark.
      final boolean isCommonMark = renderer instanceof CommonMarkRenderer;

      final var rendererName = isCommonMarkFallback //
            ? "CommonMark, GitHub Markdown API unavailable"
            : isCommonMark //
                  ? "CommonMark"
                  : renderer instanceof GitHubMarkdownRenderer //
                        ? "GitHub Markdown API"
                        : renderer.getClass().getSimpleName();

      out.append("<!DOCTYPE html>"); // https://github.com/sindresorhus/github-markdown-css#troubleshooting
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("<link rel='stylesheet' href='" + (useDarkTheme ? cssDark : cssLight).toURI() + "'>");
      // GitHub API output already contains token markup; applying another highlighter would destroy that structure.
      if (isCommonMark) {
         out.append("<link rel='stylesheet' href='" + highlightJSCSS.toURI() + "'>");
         out.append("<script src='" + highlightJS.toURI() + "'></script>");
      }
      out.append("<style>* { tab-size: " + getPreferredTabSize() + " !important}</style>");
      if (PluginPreferences.isRenderMermaidDiagrams()) {
         out.append("<script src='" + mermaidJS.toURI() + "'></script>");
      }
      if (enabledDiagramTypes.contains(DiagramType.PIKCHR)) {
         PikchrRendering.appendPageSupport(out);
      }
      out.append("</head>");
      out.append("<body class='markdown-body" + (useDarkTheme ? " previewer-dark" : "") + "' style='padding:5px'>\n\n");
      out.append(htmlBody);
      if (PluginPreferences.isRenderMermaidDiagrams()) {
         out.append(MERMAID_INIT_SCRIPT.replace("$$THEME$$", useDarkTheme ? "dark" : "default"));
      }
      if (isCommonMark) {
         // Mermaid source is converted first so a diagram fence cannot also be rewritten as highlighted code.
         out.append(HIGHLIGHT_JS_INIT_SCRIPT);
      }
      if (containsMath) {
         out.append(MathJaxSupport.createInitializationScript(getMathJaxJS()));
      }
      out.append(StringUtils.htmlInfoBox(source.shortDisplayPath() + " (" + rendererName + ") " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }

}
