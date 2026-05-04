/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.sebthom.eclipse.previewer.plantuml.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.io.stream.FastByteArrayOutputStream;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.TitledDiagram;
import net.sourceforge.plantuml.core.Diagram;

/**
 * Renders PlantUML source into embeddable HTML fragments.
 *
 * @author Sebastian Thomschke
 */
public final class PlantUmlRendering {

   @SuppressWarnings("null")
   private static final FileFormatOption SVG_FORMAT = new FileFormatOption(FileFormat.SVG).withUseRedForError();

   public static String renderToHtmlFragment(final String source) throws IOException {
      final var html = new StringBuilder();
      renderToHtmlFragment(source, html);
      return html.toString();
   }

   public static void renderToHtmlFragment(final String source, final Appendable out) throws IOException {
      final var reader = new SourceStringReader(source);
      TitledDiagram.FORCE_SMETANA = "smetana".equals(PluginPreferences.getPlantUmlLayoutEngine());
      try (var baos = new FastByteArrayOutputStream()) {
         final var blocks = reader.getBlocks();
         if (blocks.isEmpty()) {
            reader.noValidStartFound(baos, SVG_FORMAT);
            out.append(StringUtils.htmlSvgWithHoverDownloadButton(new String(baos.toByteArray(), StandardCharsets.UTF_8)));
         } else {
            for (final BlockUml block : blocks) {
               final Diagram system = block.getDiagram();
               final int imageCount = system.getNbImages();
               for (int j = 0; j < imageCount; j++) {
                  baos.reset();
                  system.exportDiagram(baos, j, SVG_FORMAT);
                  out.append(StringUtils.htmlSvgWithHoverDownloadButton(new String(baos.toByteArray(), StandardCharsets.UTF_8)));
               }
            }
         }
      }
   }

   private PlantUmlRendering() {
   }
}
