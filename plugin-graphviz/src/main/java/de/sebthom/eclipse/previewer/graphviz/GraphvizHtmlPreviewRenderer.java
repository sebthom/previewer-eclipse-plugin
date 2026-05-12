/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz;

import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizEmbeddedRenderer;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizNativeRenderer;
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

      final var renderer = PluginPreferences.getGraphvizRenderer();

      final var rendererName = renderer instanceof GraphvizNativeRenderer //
            ? "dot"
            : renderer instanceof GraphvizEmbeddedRenderer //
                  ? "viz.js"
                  : renderer.getClass().getSimpleName();

      if (SystemUtils.IS_OS_WINDOWS && !(renderer instanceof GraphvizEmbeddedRenderer)) {
         out.append("""
            <script>
              if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
                 document.body.style.overflowX = 'hidden';
              }
            </script>
            """);
      }

      renderer.dotToHTML(source, out);
      out.append(StringUtils.htmlInfoBox(source.shortDisplayPath() + " (" + rendererName + ") " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
