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
import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.Plugin;
import net.sf.jstuff.core.security.Base64;

/**
 * @author Sebastian Thomschke
 */
abstract class AbstractRenderCache implements RenderCache {

   static final Path SOURCE_PATH_FILE = Path.of("source_path");

   final Path cacheRoot;
   final String renderedContentFileExtension;

   AbstractRenderCache(final String cacheFolderName, final String renderedContentFileExtension) {
      cacheRoot = SystemUtils.getJavaIoTmpDir().toPath().resolve(Plugin.PLUGIN_ID).resolve(cacheFolderName);
      Plugin.log().info("Initializing render cache folder [{0}]...", cacheRoot);
      try {
         if (Files.isRegularFile(cacheRoot)) {
            Files.delete(cacheRoot);
         }

         Files.createDirectories(cacheRoot);
      } catch (final IOException ex) {
         Plugin.log().error(ex);
      }

      this.renderedContentFileExtension = (renderedContentFileExtension.startsWith(".") ? "" : ".") + renderedContentFileExtension;
      removeStaleCacheEntries();
   }

   Path getCacheEntryDir(final Path sourcePath) {
      return cacheRoot.resolve(Base64.encode(sourcePath.toString()));
   }

   private void removeStaleCacheEntries() {
      try (var cacheEntryDirs = Files.newDirectoryStream(cacheRoot)) {
         for (final Path cacheEntryDir : cacheEntryDirs) {
            try {
               final var sourcePathFile = cacheEntryDir.resolve(SOURCE_PATH_FILE);
               if (Files.exists(sourcePathFile)) {
                  final var sourcePath = Path.of(Files.readString(sourcePathFile));
                  if (Files.exists(sourcePath)) {
                     continue;
                  }
               }
               Plugin.log().info("Deleting stale cache dir [{0}]...", cacheEntryDir);
               FileUtils.deleteDirectory(cacheEntryDir.toFile());
            } catch (final IOException ex) {
               Plugin.log().error(ex);
            }
         }
      } catch (final IOException ex) {
         Plugin.log().error(ex);
      }
   }
}
