/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphVizNativeRenderer;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizEmbeddedRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * @author Sebastian Thomschke
 */
public class GraphvizHtmlPreviewRenderer implements HtmlPreviewRenderer {

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {

      out.append("""
         <!DOCTYPE html>
         <html>
         <head>
           <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
         </head>
         <body>
         """);

      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));

      final var renderer = PluginPreferences.getGraphvizRenderer();

      final var rendererName = renderer instanceof GraphVizNativeRenderer //
            ? "dot"
            : renderer instanceof GraphvizEmbeddedRenderer //
                  ? "viz.js"
                  : renderer.getClass().getSimpleName();

      if (SystemUtils.IS_OS_WINDOWS) {
         if (renderer instanceof GraphvizEmbeddedRenderer) {
            out.append(
               """
                  <div id="ieNotSupported" style="display: none; padding: 20px; background-color: #f44336; color: white; text-align: center; font-size: 18px;">
                    Previewing GraphViz diagrams using the embedded viz.js library is not supported using the Internet Explorer WebView.<br/>
                    <br/>
                    Please switch to Edge WebView2 under <b>Window &gt; Preferences &gt; Previewer &gt; Web View Implementation</b> or
                    switch to the GraphViz DOT renderer under <b>Window &gt; Preferences &gt; Previewer &gt; GraphViz &gt; GraphViz renderer</b> .
                  </div>

                  <script>
                    if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
                      document.getElementById('ieNotSupported').style.display = 'block';
                    }
                  </script>
                  """);
         } else {
            out.append("""
               <script>
                 if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
                    document.body.style.overflowX = 'hidden';
                 }
               </script>
               """);
         }
      }

      renderer.dotToHTML(source, out);
      out.append(StringUtils.htmlInfoBox(shortPath + " (" + rendererName + ") " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
