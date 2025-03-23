/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.renderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.SystemUtils;
import net.sf.jstuff.core.io.Processes;

/**
 * @author Sebastian Thomschke
 */
public enum GraphVizNativeRenderer implements GraphvizRenderer {

   INSTANCE;

   public boolean isAvailable() {
      var exe = Path.of(PluginPreferences.getGraphvizNativeExe());
      if (exe.getParent() == null) { // check if exe is given without path
         final var foundPath = SystemUtils.findExecutable(exe.toString(), false);
         if (foundPath != null) {
            exe = foundPath;
         }
      }
      return Files.isExecutable(exe);
   }

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

      out.append(StringUtils.htmlSvgWithHoverDownloadButton("<svg width='100%'" + sb.substring(sb.indexOf(" viewBox"), sb.length())));
   }
}
