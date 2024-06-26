/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphVizNativeRenderer;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizEmbeddedRenderer;
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
      final var renderer = PluginPreferences.getGraphvizRenderer();

      final var htmlBody = new StringBuilder();

      renderer.dotToHTML(source, htmlBody);

      final var rendererName = renderer instanceof GraphVizNativeRenderer //
            ? "dot"
            : renderer instanceof GraphvizEmbeddedRenderer //
                  ? "viz.js"
                  : renderer.getClass().getSimpleName();

      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));
      final var now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

      out.append("<!DOCTYPE html>");
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("</head>");
      out.append("<body>\n\n");
      out.append(htmlBody);
      out.append(StringUtils.htmlInfoBox(shortPath + " (" + rendererName + ") " + now));
      out.append("</body></html>");
   }
}
