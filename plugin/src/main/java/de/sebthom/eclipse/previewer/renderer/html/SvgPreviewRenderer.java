/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public class SvgPreviewRenderer implements HtmlPreviewRenderer {

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
         <body class='markdown-body' style='padding:5px'>
         """);

      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));

      out.append(StringUtils.htmlSvgWithHoverDownloadButton("<svg viewBox='0 0 width height' " + Strings.substringBetween(source
         .contentAsString(), "<svg ", "</svg>") + "</svg>"));
      out.append(StringUtils.htmlInfoBox(shortPath + " " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
