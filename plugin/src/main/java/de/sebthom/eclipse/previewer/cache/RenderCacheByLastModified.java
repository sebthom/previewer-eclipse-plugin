/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.functional.ThrowingFunction;

/**
 * @author Sebastian Thomschke
 */
public final class RenderCacheByLastModified extends AbstractRenderCache {

   public RenderCacheByLastModified(final String cacheFolderName) {
      super(cacheFolderName);
   }

   @Override
   public @Nullable Path computeIfAbsent(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer, final String fileExtension)
         throws IOException {
      final Path renderedContentPath = get(source, fileExtension);
      if (renderedContentPath != null)
         return renderedContentPath;
      final CharSequence renderedContent = renderer.apply(source);
      return renderedContent == null //
            ? null
            : put(source, renderedContent, fileExtension);
   }

   @Override
   public @Nullable Path get(final ContentSource source, final String fileExtension) {
      final Path cacheDir = getCacheEntryDir(source.path());
      if (!Files.exists(cacheDir))
         return null;

      try {
         final Path renderedContentPath = getRenderedContentFilePath(cacheDir, fileExtension);
         if (renderedContentPath.toFile().lastModified() == source.lastModified())
            return renderedContentPath;
      } catch (final IOException ex) {
         Plugin.log().error(ex);
      }
      return null;
   }

   private Path getRenderedContentFilePath(final Path cacheDir, final String fileExtension) {
      return cacheDir.resolve("rendered_content." + fileExtension);
   }

   @Override
   public Path put(final ContentSource source, final CharSequence renderedContent, final String fileExtension) throws IOException {
      final var cacheDir = getCacheEntryDir(source.path());
      try {
         Files.createDirectories(cacheDir);

         final Path renderedContentPath = getRenderedContentFilePath(cacheDir, fileExtension);
         Files.writeString(cacheDir.resolve(SOURCE_PATH_FILE), source.path().toString());
         Files.writeString(renderedContentPath, renderedContent);
         Files.setLastModifiedTime(renderedContentPath, FileTime.fromMillis(source.lastModified()));
         return renderedContentPath;
      } catch (final IOException ex) {
         FileUtils.deleteDirectory(cacheDir.toFile());
         throw ex;
      }
   }

   @Override
   public @Nullable Path replace(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer, final String fileExtension)
         throws IOException {
      final CharSequence renderedContent = renderer.apply(source);
      if (renderedContent == null) {
         final Path cacheDir = getCacheEntryDir(source.path());
         if (Files.exists(cacheDir)) {
            FileUtils.deleteDirectory(cacheDir.toFile());
         }
         return null;
      }
      return put(source, renderedContent, fileExtension);
   }
}
