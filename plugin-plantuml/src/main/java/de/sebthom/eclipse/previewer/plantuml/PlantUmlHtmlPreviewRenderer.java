/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml;

import java.io.IOException;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * @author Sebastian Thomschke
 */
public class PlantUmlHtmlPreviewRenderer implements HtmlPreviewRenderer {

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

      PlantUmlRendering.renderToHtmlFragment(source.contentAsString(), out);
      out.append(StringUtils.htmlInfoBox(source.shortDisplayPath() + " " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
