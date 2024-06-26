/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util.cache;

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

   private final Path renderedContentFileName;

   public RenderCacheByLastModified(final String cacheFolderName, final String renderedContentFileExtension) {
      super(cacheFolderName, renderedContentFileExtension);
      renderedContentFileName = Path.of("rendered_content" + this.renderedContentFileExtension);
   }

   @Override
   public @Nullable Path computeIfAbsent(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer) throws IOException {
      final Path renderedContentPath = get(source);
      if (renderedContentPath != null)
         return renderedContentPath;
      final CharSequence renderedContent = renderer.apply(source);
      return renderedContent == null //
            ? null
            : put(source, renderedContent);
   }

   @Override
   public @Nullable Path get(final ContentSource source) {
      final Path cacheDir = getCacheEntryDir(source.path());
      if (!Files.exists(cacheDir))
         return null;

      try {
         final Path renderedContentPath = cacheDir.resolve(renderedContentFileName);
         if (renderedContentPath.toFile().lastModified() == source.lastModified())
            return renderedContentPath;
      } catch (final IOException ex) {
         Plugin.log().error(ex);
      }
      return null;
   }

   @Override
   public Path put(final ContentSource source, final CharSequence renderedContent) throws IOException {
      final var cacheDir = getCacheEntryDir(source.path());
      try {
         Files.createDirectories(cacheDir);

         final Path renderedContentPath = cacheDir.resolve(renderedContentFileName);
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
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer) throws IOException {
      final CharSequence renderedContent = renderer.apply(source);
      if (renderedContent == null) {
         final Path cacheDir = getCacheEntryDir(source.path());
         if (Files.exists(cacheDir)) {
            FileUtils.deleteDirectory(cacheDir.toFile());
         }
         return null;
      }
      return put(source, renderedContent);
   }
}
