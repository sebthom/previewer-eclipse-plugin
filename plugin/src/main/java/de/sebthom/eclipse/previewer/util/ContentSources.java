/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.input.CharSequenceInputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.texteditor.ITextEditor;

import de.sebthom.eclipse.commons.resources.Resources;
import de.sebthom.eclipse.commons.text.ContentTypes;
import de.sebthom.eclipse.commons.ui.Editors;
import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public final class ContentSources {
   public static final class ContentSourceSnapshot implements ContentSource {
      private final Path path;
      private final String content;
      private final long lastModified;
      private final List<IContentType> contentTypes;

      public ContentSourceSnapshot(final Path path, final String content, final long lastModified, final List<IContentType> contentTypes) {
         this.path = path;
         this.content = content;
         this.lastModified = lastModified;
         this.contentTypes = List.copyOf(contentTypes);
      }

      @Override
      public InputStream contentAsInputStream() throws IOException {
         return CharSequenceInputStream.builder().setCharSequence(content).get();
      }

      @Override
      public Reader contentAsReader() throws IOException {
         return new StringReader(content);
      }

      @Override
      public String contentAsString() throws IOException {
         return content;
      }

      @Override
      public List<IContentType> contentTypes() {
         return contentTypes;
      }

      @Override
      public boolean isSnapshot() {
         return true;
      }

      @Override
      public boolean isSynced() {
         final var file = path.toFile();
         return file.lastModified() == lastModified && file.length() == Strings.lengthUTF8(content);
      }

      @Override
      public long lastModified() throws IOException {
         return lastModified;
      }

      @Override
      public Path path() {
         return path;
      }
   }

   public static class FileContentSource implements ContentSource {
      public final IFile file;

      FileContentSource(final IFile file) {
         this.file = file;
      }

      @Override
      public InputStream contentAsInputStream() throws IOException {
         try {
            return new BufferedInputStream(file.getContents());
         } catch (final CoreException ex) {
            throw new IOException(ex);
         }
      }

      @Override
      public Reader contentAsReader() throws IOException {
         try {
            return Resources.newBufferedReader(file);
         } catch (final CoreException ex) {
            throw new IOException(ex);
         }
      }

      @Override
      public String contentAsString() throws IOException {
         try {
            return Resources.readString(file);
         } catch (final CoreException ex) {
            throw new IOException(ex);
         }
      }

      @Override
      public List<IContentType> contentTypes() {
         return ContentTypes.of(file);
      }

      @Override
      public boolean isSnapshot() {
         return false;
      }

      @Override
      public boolean isSynced() {
         return true;
      }

      @Override
      public long lastModified() {
         return file.getModificationStamp();
      }

      @Override
      public Path path() {
         return Resources.toAbsolutePath(file);
      }
   }

   public static class PathContentSource implements ContentSource {
      private final Path path;

      PathContentSource(final Path path) {
         this.path = path;
      }

      @Override
      public InputStream contentAsInputStream() throws IOException {
         return new BufferedInputStream(Files.newInputStream(path));
      }

      @Override
      public Reader contentAsReader() throws IOException {
         return Files.newBufferedReader(path);
      }

      @Override
      public String contentAsString() throws IOException {
         return Files.readString(path);
      }

      @Override
      public List<IContentType> contentTypes() {
         return ContentTypes.of(path);
      }

      @Override
      public boolean isSnapshot() {
         return false;
      }

      @Override
      public boolean isSynced() {
         return true;
      }

      @Override
      public long lastModified() throws IOException {
         return Files.getLastModifiedTime(path).toMillis();
      }

      @Override
      public Path path() {
         return path;
      }
   }

   public static class TextEditorContentSource implements ContentSource {
      public final ITextEditor editor;

      public TextEditorContentSource(final ITextEditor editor) {
         this.editor = editor;
      }

      @Override
      public InputStream contentAsInputStream() {
         return CharSequenceInputStream.builder().setCharSequence(contentAsString()).get();
      }

      @Override
      public Reader contentAsReader() {
         return new StringReader(contentAsString());
      }

      @Override
      public String contentAsString() {
         return Editors.getText(editor);
      }

      @Override
      public List<IContentType> contentTypes() {
         return ContentTypes.of(path());
      }

      @Override
      public boolean isSnapshot() {
         return false;
      }

      @Override
      public boolean isSynced() {
         return !editor.isDirty();
      }

      @Override
      public long lastModified() {
         return editor.isDirty() ? System.currentTimeMillis() : Resources.lastModified(Editors.getFile(editor));
      }

      @Override
      public Path path() {
         return asNonNull(Editors.getFilePath(editor));
      }
   }

   public static ContentSource of(final IFile file) {
      return new FileContentSource(file);
   }

   public static ContentSource of(final ITextEditor editor) {
      return new TextEditorContentSource(editor);
   }

   public static ContentSource of(final Path path) {
      return new PathContentSource(path);
   }

   /**
    * Creates a ContentSource from a string content with a virtual file name.
    * Used for e.g. CompareEditor content where we have the document content but not a physical file.
    */
   public static ContentSource of(final String filePath, final String content) {
      // Use the filename directly as a virtual path - only the filename/extension matters for content type detection
      final Path virtualPath = Path.of(filePath);
      final long now = System.currentTimeMillis();
      return new ContentSourceSnapshot(virtualPath, content, now, ContentTypes.of(virtualPath));
   }

   private ContentSources() {
   }
}
