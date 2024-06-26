/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.runtime.content.IContentType;

import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public class RenderSourceSupport {

   private final Set<String> contentTypes = new HashSet<>();
   private final Set<String> fileExtensions = new HashSet<>();
   private final Set<String> fileNames = new HashSet<>();
   private final Set<PathMatcher> filePatterns = new HashSet<>();

   protected void addContentType(final String id) {
      contentTypes.add(id);
   }

   protected void addContentTypes(final Stream<String> contentTypeIds) {
      contentTypes.addAll(contentTypeIds.toList());
   }

   protected void addContentTypes(final String... contentTypeIds) {
      addContentTypes(Arrays.stream(contentTypeIds));
   }

   protected void addFileExtensions(final Stream<String> values) {
      fileExtensions.addAll(values //
         .map(ext -> Strings.removeStart(ext.trim(), ".").toLowerCase()) //
         .filter(name -> !name.isBlank()) //
         .toList());
   }

   protected void addFileExtensions(final String... values) {
      addFileExtensions(Arrays.stream(values));
   }

   protected void addFileNames(final Stream<String> values) {
      fileNames.addAll(values //
         .map(String::trim) //
         .filter(name -> !name.isBlank()) //
         .toList());
   }

   protected void addFileNames(final String... values) {
      addFileNames(Arrays.stream(values));
   }

   protected void addFilePatterns(final Stream<String> values) {
      @SuppressWarnings("resource")
      final var fs = Path.of(".").getFileSystem();
      filePatterns.addAll(values //
         .map(String::trim) //
         .filter(name -> !name.isEmpty()) //
         .map(pattern -> fs.getPathMatcher("glob:" + pattern)) //
         .toList());
   }

   protected void addFilePatterns(final String... values) {
      addFilePatterns(Arrays.stream(values));
   }

   public boolean supports(final ContentSource source) {
      final var path = source.path();
      if (!fileNames.isEmpty() && fileNames.contains(path.getFileName().toString()) //
            || !fileExtensions.isEmpty() && fileExtensions.contains(FilenameUtils.getExtension(path.getFileName().toString())
               .toLowerCase()))
         return true;

      if (!contentTypes.isEmpty()) {
         for (final IContentType contentType : source.contentTypes()) {
            if (contentTypes.contains(contentType.getId()))
               return true;
         }
      }

      for (final var filePattern : filePatterns) {
         if (filePattern.matches(path))
            return true;
      }

      return false;
   }
}
