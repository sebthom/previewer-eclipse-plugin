/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.pikchr;

import java.io.IOException;
import java.util.List;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * Renders standalone {@code .pikchr} files in the embedded browser.
 *
 * @author Sebastian Thomschke
 */
public final class PikchrHtmlPreviewRenderer implements HtmlPreviewRenderer {

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      final boolean useDarkTheme = MiscUtils.isDarkEclipseTheme();
      out.append("""
         <!DOCTYPE html>
         <html>
         <head>
           <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
         """);
      PikchrRendering.appendPageSupport(out);
      if (useDarkTheme) {
         out.append("<style>html, body { background: #585858; color: #fff; }</style>");
      }
      out.append("</head><body>");
      out.append(PikchrRendering.renderToHtmlFragment(source.contentAsString(), List.of(), useDarkTheme));
      out.append(StringUtils.htmlInfoBox(source.shortDisplayPath() + " " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
