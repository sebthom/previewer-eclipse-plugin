/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.stream.Streams;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.functional.ThrowingFunction;
import net.sf.jstuff.core.functional.ThrowingSupplier;
import net.sf.jstuff.core.security.Hash;

/**
 * @author Sebastian Thomschke
 */
public final class RenderCacheUsingSourceContentHashWithVersions extends AbstractRenderCache {

   private final int versionsToKeep;

   public RenderCacheUsingSourceContentHashWithVersions(final String cacheFolderName, final int versionsToKeep) {
      super(cacheFolderName);
      this.versionsToKeep = versionsToKeep < 0 ? 0 : versionsToKeep;
   }

   private Path getRenderedContentFilePath(final Path cacheDir, final ThrowingSupplier<String, IOException> sourceContentHashProvider,
         final String fileExtension) {
      return cacheDir.resolve("rendered_content_" + sourceContentHashProvider.get() + "." + fileExtension);
   }

   @Override
   public @Nullable Path computeIfAbsent(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer, final String fileExtension)
         throws IOException {
      try (var sourceContent = source.contentAsInputStream()) {
         final var sourceContentHash = Hash.SHA1.hash(sourceContent);
         final var renderedContentPath = get(source, () -> sourceContentHash, fileExtension);
         if (renderedContentPath != null)
            return renderedContentPath;

         final var renderedContent = renderer.apply(source);
         return renderedContent == null //
               ? null
               : put(source, renderedContent, () -> sourceContentHash, fileExtension);
      }
   }

   @Override
   public @Nullable Path get(final ContentSource source, final String fileExtension) {
      return get(source, () -> {
         try (var sourceContent = source.contentAsInputStream()) {
            return Hash.SHA1.hash(sourceContent);
         }
      }, fileExtension);
   }

   private @Nullable Path get(final ContentSource source, final ThrowingSupplier<String, IOException> sourceContentHashProvider,
         final String fileExtension) {
      final var cacheDir = getCacheEntryDir(source.path());
      if (!Files.exists(cacheDir))
         return null;

      try {
         final Path renderedContentPath = getRenderedContentFilePath(cacheDir, sourceContentHashProvider, fileExtension);
         if (Files.isRegularFile(renderedContentPath))
            return renderedContentPath;
      } catch (final Exception ex) {
         Plugin.log().error(ex);
      }
      return null;
   }

   @Override
   public Path put(final ContentSource source, final CharSequence renderedContent, final String fileExtension) throws IOException {
      return put(source, renderedContent, () -> {
         try (var sourceContent = source.contentAsInputStream()) {
            return Hash.SHA1.hash(sourceContent);
         }
      }, fileExtension);
   }

   private Path put(final ContentSource source, final CharSequence renderedContent,
         final ThrowingSupplier<String, IOException> sourceContentHashProvider, final String fileExtension) throws IOException {
      final var cacheDir = getCacheEntryDir(source.path());
      try {
         Files.createDirectories(cacheDir);
      } catch (final IOException ex) {
         FileUtils.deleteDirectory(cacheDir.toFile());
         throw ex;
      }
      if (versionsToKeep == 0) {
         try (var fileStream = Files.newDirectoryStream(cacheDir, "rendered_content_*")) {
            fileStream.forEach(file -> {
               try {
                  Files.delete(file);
               } catch (final IOException ex) {
                  Plugin.log().error(ex);
               }
            });
         }
      } else {
         try (var fileStream = Files.newDirectoryStream(cacheDir, "rendered_content_*")) {
            final File[] files = Streams.of(fileStream).map(Path::toFile).toArray(File[]::new);
            if (files.length > versionsToKeep) {
               Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
               for (int i = versionsToKeep; i < files.length; i++) {
                  Files.delete(files[i].toPath());
               }
            }
         }
      }

      final Path renderedContentPath = getRenderedContentFilePath(cacheDir, sourceContentHashProvider, fileExtension);
      Files.writeString(cacheDir.resolve(SOURCE_PATH_FILE), source.path().toString());
      Files.writeString(renderedContentPath, renderedContent);
      return renderedContentPath;
   }

   @Override
   public @Nullable Path replace(final ContentSource source,
         final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer, final String fileExtension)
         throws IOException {
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
         return put(source, renderedContent, () -> sourceContentHash, fileExtension);
      }
   }
}
