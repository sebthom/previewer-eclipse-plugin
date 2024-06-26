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

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.functional.ThrowingFunction;
import net.sf.jstuff.core.functional.ThrowingSupplier;
import net.sf.jstuff.core.security.Hash;

/**
 * @author Sebastian Thomschke
 */
public final class RenderCacheUsingSourceContentHash extends AbstractRenderCache {

   private static final Path SOURCE_CONTENT_HASH_FILE = Path.of("source_content.sha1");

   private final Path renderedContentFileName;

   public RenderCacheUsingSourceContentHash(final String cacheFolderName, final String renderedContentFileExtension) {
      super(cacheFolderName, renderedContentFileExtension);
      renderedContentFileName = Path.of("rendered_content" + this.renderedContentFileExtension);
   }

   @Override
   public @Nullable Path computeIfAbsent(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer) throws IOException {
      try (var sourceContent = source.contentAsInputStream()) {
         final var sourceContentHash = Hash.SHA1.hash(sourceContent);
         final var renderedContentPath = get(source, () -> sourceContentHash);
         if (renderedContentPath != null)
            return renderedContentPath;

         final var renderedContent = renderer.apply(source);
         return renderedContent == null //
               ? null
               : put(source, renderedContent, () -> sourceContentHash);
      }
   }

   @Override
   public @Nullable Path get(final ContentSource source) {
      return get(source, () -> {
         try (var sourceContent = source.contentAsInputStream()) {
            return Hash.SHA1.hash(sourceContent);
         }
      });
   }

   private @Nullable Path get(final ContentSource source, final ThrowingSupplier<String, IOException> sourceContentHashProvider) {
      final var cacheDir = getCacheEntryDir(source.path());
      if (!Files.exists(cacheDir))
         return null;

      try {
         final var renderedContentPath = cacheDir.resolve(renderedContentFileName);
         if (!Files.isRegularFile(renderedContentPath))
            return null;

         // invalidate the cache if hash mismatch
         final var sourceContentHashPath = cacheDir.resolve(SOURCE_CONTENT_HASH_FILE);
         if (!sourceContentHashProvider.get().equals(Files.readString(sourceContentHashPath))) {
            Files.delete(sourceContentHashPath);
            Files.delete(renderedContentPath);
            return null;
         }

         return renderedContentPath;

      } catch (final Exception ex) {
         Plugin.log().error(ex);
      }
      return null;
   }

   @Override
   public Path put(final ContentSource source, final CharSequence renderedContent) throws IOException {
      return put(source, renderedContent, () -> {
         try (var sourceContent = source.contentAsInputStream()) {
            return Hash.SHA1.hash(sourceContent);
         }
      });
   }

   private Path put(final ContentSource source, final CharSequence renderedContent,
         final ThrowingSupplier<String, IOException> sourceContentHashProvider) throws IOException {
      final var cacheDir = getCacheEntryDir(source.path());
      try {
         Files.createDirectories(cacheDir);
      } catch (final IOException ex) {
         FileUtils.deleteDirectory(cacheDir.toFile());
         throw ex;
      }

      final var renderedContentPath = cacheDir.resolve(renderedContentFileName);
      Files.writeString(cacheDir.resolve(SOURCE_PATH_FILE), source.path().toString());
      Files.writeString(renderedContentPath, renderedContent);
      Files.writeString(cacheDir.resolve(SOURCE_CONTENT_HASH_FILE), sourceContentHashProvider.get());
      return renderedContentPath;
   }

   @Override
   public @Nullable Path replace(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer) throws IOException {
      try (var sourceContent = source.contentAsInputStream()) {
         final var sourceContentHash = Hash.SHA1.hash(sourceContent);
         final var renderedContent = renderer.apply(source);
         if (renderedContent == null) {
            final Path cacheDir = getCacheEntryDir(source.path());
            if (Files.exists(cacheDir)) {
               FileUtils.deleteDirectory(cacheDir.toFile());
            }
            return null;
         }
         return put(source, renderedContent, () -> sourceContentHash);
      }
   }

}
