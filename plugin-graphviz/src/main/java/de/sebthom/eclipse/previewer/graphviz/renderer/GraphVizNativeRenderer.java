/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.renderer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;
import net.sf.jstuff.core.io.Processes;

/**
 * @author Sebastian Thomschke
 */
public class GraphVizNativeRenderer implements GraphvizRenderer {

   public static final GraphVizNativeRenderer INSTANCE = new GraphVizNativeRenderer();

   @Override
   public void dotToHTML(final ContentSource source, final Appendable out) throws IOException {
      final var exe = PluginPreferences.getGraphvizNativeExe();

      final var sb = new StringBuilder();
      final var proc = Processes.builder(exe).withArg("-Tsvg") //
         .withRedirectOutput(sb) //
         .withRedirectErrorToOutput() //
         .start();

      try (var raw = source.contentAsInputStream();
           var dotStdIn = proc.getStdIn()) {
         IOUtils.copy(raw, dotStdIn);
      }

      try {
         proc.waitForExit(5, TimeUnit.SECONDS);
      } catch (final InterruptedException ex) {
         Thread.currentThread().interrupt();
         throw new IOException(ex);
      } finally {
         proc.terminate();
      }

      // out.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 width height'>\n");
      //out.append(sb, sb.indexOf("<svg"), sb.length());
      out.append("<svg width='100%'");
      out.append(sb, sb.indexOf(" viewBox"), sb.length());
   }
}
