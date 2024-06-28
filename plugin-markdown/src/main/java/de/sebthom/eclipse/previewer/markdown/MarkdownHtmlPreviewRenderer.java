/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.markdown.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.markdown.renderer.CommonMarkRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.GitHubMarkdownRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.graphic.RGB;

/**
 * @author Sebastian Thomschke
 */
public class MarkdownHtmlPreviewRenderer implements HtmlPreviewRenderer {

   private File cssDark;
   private File cssLight;

   public MarkdownHtmlPreviewRenderer() throws IOException {
      cssDark = Plugin.resources().extract(Constants.MARKDOWN_CSS_DARK);
      cssLight = Plugin.resources().extract(Constants.MARKDOWN_CSS_LIGHT);
   }

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      var renderer = PluginPreferences.getMarkdownRenderer();

      final var htmlBody = new StringBuilder();

      boolean isCommonMarkFallback = false;
      try {
         renderer.markdownToHTML(source, htmlBody);
      } catch (final ConnectException ex) {
         if (renderer instanceof GitHubMarkdownRenderer && PluginPreferences.isGithubApiFallbackToCommonMark()) {
            Plugin.log().debug(ex);
            renderer = CommonMarkRenderer.INSTANCE;
            renderer.markdownToHTML(source, htmlBody);
            isCommonMarkFallback = true;
         } else
            throw ex;
      }

      final var rendererName = isCommonMarkFallback //
            ? "CommonMark, GitHub Markdown API unavailable"
            : renderer instanceof CommonMarkRenderer //
                  ? "CommonMark"
                  : renderer instanceof GitHubMarkdownRenderer //
                        ? "GitHub Markdown API"
                        : renderer.getClass().getSimpleName();

      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));

      out.append("<!DOCTYPE html>");
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("<link rel='stylesheet' href='" + (isDarkEclipseTheme() ? cssDark : cssLight).toURI() + "'>");
      out.append("</head>");
      out.append("<body class='markdown-body' style='padding:5px'>\n\n");
      out.append(htmlBody);
      out.append(StringUtils.htmlInfoBox(shortPath + " (" + rendererName + ") " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }

   private static boolean isDarkEclipseTheme() {
      final var bgColor = UI.run(() -> UI.getShell().getBackground());
      return new RGB(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()).getBrightnessFast() < 128;
   }
}
