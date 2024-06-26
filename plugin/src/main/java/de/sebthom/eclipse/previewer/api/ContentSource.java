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
    * @return an absolute path
    */
   Path path();

   default ContentSource snapshot() throws IOException {
      return isSnapshot() //
            ? this
            : new ContentSourceSnapshot(path(), contentAsString(), lastModified(), contentTypes());
   }
}
