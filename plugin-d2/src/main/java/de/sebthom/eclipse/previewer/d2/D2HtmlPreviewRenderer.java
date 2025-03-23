/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.d2;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.d2.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * @author Sebastian Thomschke
 */
public class D2HtmlPreviewRenderer implements HtmlPreviewRenderer {

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

      final var renderer = PluginPreferences.getD2Renderer();

      if (SystemUtils.IS_OS_WINDOWS) {
         out.append("""
            <script>
              if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
                 document.body.style.overflowX = 'hidden';
              }
            </script>
            """);
      }

      renderer.d2ToHTML(source, out);
      out.append(StringUtils.htmlInfoBox(shortPath + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
