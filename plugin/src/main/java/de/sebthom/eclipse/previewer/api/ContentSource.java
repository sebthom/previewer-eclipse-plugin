/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.content.IContentType;

import de.sebthom.eclipse.previewer.util.ContentSources.ContentSourceSnapshot;

/**
 * @author Sebastian Thomschke
 */
public interface ContentSource {

   InputStream contentAsInputStream() throws IOException;

   Reader contentAsReader() throws IOException;

   String contentAsString() throws IOException;

   List<IContentType> contentTypes();

   boolean isSnapshot();

   boolean isSynced();

   long lastModified() throws IOException;

   /**
    * @return the source identity path. File-backed sources return an absolute filesystem path; virtual sources may return a synthetic
    *         path that is only meant for renderer matching, cache keys, and display labels.
   */
   Path path();

   /**
    * @return a compact path label for preview UI messages, using the filename and its direct parent when available.
    */
   default String shortDisplayPath() {
      final Path path = path();
      final Path fileName = path.getFileName();
      if (fileName == null)
         return path.toString();

      final Path parent = path.getParent();
      if (parent == null)
         return fileName.toString();

      final Path parentFileName = parent.getFileName();
      // This is UI text, not a filesystem-relative path: root-level files and virtual sources may not have two name elements.
      return parentFileName == null
            ? fileName.toString()
            : parentFileName.resolve(fileName).toString();
   }

   /**
    * @return an immutable source snapshot, or this source itself when it is already a snapshot.
    */
   default ContentSource snapshot() throws IOException {
      return isSnapshot() //
            ? this
            : new ContentSourceSnapshot(path(), contentAsString(), lastModified(), contentTypes());
   }
}
