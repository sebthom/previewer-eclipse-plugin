/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;

import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public class PreviewRendererExtension<T> extends RenderSourceSupport {

   public final T renderer;

   @SuppressWarnings("unchecked")
   public PreviewRendererExtension(final IConfigurationElement config) throws CoreException {
      renderer = (T) config.createExecutableExtension("class");

      for (final var contentType : config.getChildren("content-type")) {
         final var id = contentType.getAttribute("id");
         if (id != null && !id.isBlank()) {
            addContentType(id);
         }
      }

      final var fileExtsArg = config.getAttribute("file-extensions");
      if (fileExtsArg != null && !fileExtsArg.isBlank()) {
         addFileExtensions(Strings.splitAsStream(fileExtsArg, ','));
      }

      final var fileNamesArg = config.getAttribute("file-names");
      if (fileNamesArg != null && !fileNamesArg.isBlank()) {
         addFileNames(Strings.splitAsStream(fileNamesArg, ','));
      }

      final var filePatternsArg = config.getAttribute("file-patterns");
      if (filePatternsArg != null && !filePatternsArg.isBlank()) {
         addFilePatterns(Strings.splitAsStream(filePatternsArg, ','));
      }
   }
}
