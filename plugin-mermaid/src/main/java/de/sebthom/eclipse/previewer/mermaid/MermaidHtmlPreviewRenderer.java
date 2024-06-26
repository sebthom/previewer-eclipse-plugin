/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.mermaid;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * @author Sebastian Thomschke
 */
public class MermaidHtmlPreviewRenderer implements HtmlPreviewRenderer {

   private File mermaidJS;

   public MermaidHtmlPreviewRenderer() throws IOException {
      mermaidJS = Plugin.resources().extract(Constants.MERMAID_JS);
   }

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));
      final var now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

      out.append("<!DOCTYPE html>");
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("<script src='" + mermaidJS.toURI() + "'></script>");
      out.append("<script>mermaid.initialize({ startOnLoad: true });</script>");
      out.append("</head>");
      out.append("<body>\n\n");
      out.append("""
         <pre class="mermaid" style="width:100%">
         """ + source.contentAsString() + """
         </pre>
         """);
      out.append(StringUtils.htmlInfoBox(shortPath + " " + now));
      out.append("</body></html>");
   }
}
