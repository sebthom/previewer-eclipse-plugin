/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.renderer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.Constants;
import de.sebthom.eclipse.previewer.graphviz.Plugin;

/**
 * @author Sebastian Thomschke
 */
public final class GraphvizEmbeddedRenderer implements GraphvizRenderer {

   public static final GraphvizEmbeddedRenderer INSTANCE = new GraphvizEmbeddedRenderer();

   private final File vizJS;

   public GraphvizEmbeddedRenderer() {
      try {
         vizJS = Plugin.resources().extract(Constants.VIZ_JS);
      } catch (final IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   @Override
   public void dotToHTML(final ContentSource source, final Appendable out) throws IOException {
      out.append("<script src='" + vizJS.toURI() + "'></script>");
      out.append("""
         <div id="graph"></div>
         <script>
         Viz.instance().then(function(viz) {
             var svg = viz.renderSVGElement(`
             """ + source.contentAsString() + """
             `);
             document.getElementById("graph").appendChild(svg);
         });
         </script>
         """);
   }
}
