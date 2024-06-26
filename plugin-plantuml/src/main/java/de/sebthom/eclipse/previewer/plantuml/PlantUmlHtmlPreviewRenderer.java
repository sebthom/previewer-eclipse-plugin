/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.plantuml.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.TitledDiagram;
import net.sourceforge.plantuml.core.Diagram;

/**
 * @author Sebastian Thomschke
 */
public class PlantUmlHtmlPreviewRenderer implements HtmlPreviewRenderer {

   @SuppressWarnings("null")
   private static final FileFormatOption SVG_FORMAT = new FileFormatOption(FileFormat.SVG).withUseRedForError();

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));
      final var now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

      final var reader = new SourceStringReader(source.contentAsString());
      final var baos = new ByteArrayOutputStream();

      out.append("<!DOCTYPE html>");
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("</head>");
      out.append("<body>\n\n");

      TitledDiagram.FORCE_SMETANA = "smetana".equals(PluginPreferences.getPlantUmlLayoutEngine());

      final var blocks = reader.getBlocks();
      if (blocks.isEmpty()) {
         reader.noValidStartFound(baos, SVG_FORMAT);
         out.append(new String(baos.toByteArray(), StandardCharsets.UTF_8));
      } else {
         for (final BlockUml block : blocks) {
            final Diagram system = block.getDiagram();
            final int imageCount = system.getNbImages();
            for (int j = 0; j < imageCount; j++) {
               baos.reset();
               system.exportDiagram(baos, j, SVG_FORMAT);
               out.append(new String(baos.toByteArray(), StandardCharsets.UTF_8));
            }
         }
      }

      out.append(StringUtils.htmlInfoBox(shortPath + " " + now));
      out.append("</body></html>");
   }
}
