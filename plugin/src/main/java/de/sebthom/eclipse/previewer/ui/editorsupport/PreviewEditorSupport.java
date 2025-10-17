/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui.editorsupport;

import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.Document;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;

import de.sebthom.eclipse.commons.resources.Resources;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.ui.PreviewEditor;
import de.sebthom.eclipse.previewer.util.ContentSources;

/**
 * EditorSupport for the {@link PreviewEditor}, so the Preview view can link to it.
 *
 * @author Sebastian Thomschke
 */
public final class PreviewEditorSupport implements EditorSupport {

   public static final class TrackedPreviewEditorContext extends TrackedEditorContext {

      TrackedPreviewEditorContext(final PreviewEditor editor, final Path file) {
         super(editor, file, new Document());
      }

      @Override
      public ContentSource getSource() {
         return ContentSources.of(file);
      }
   }

   @Override
   public @Nullable TrackedPreviewEditorContext createFrom(final IEditorReference editorRef) {
      if (!(editorRef.getEditor(false) instanceof final PreviewEditor editor))
         return null;
      try {
         final var input = editorRef.getEditorInput();
         Path file = null;
         if (input instanceof final IFileEditorInput fei) {
            file = Resources.toAbsolutePath(fei.getFile());
         } else if (input instanceof final IURIEditorInput uei) {
            final var uri = uei.getURI();
            if ("file".equalsIgnoreCase(uri.getScheme())) {
               file = Path.of(uri);
            }
         }
         return file != null ? new TrackedPreviewEditorContext(editor, file) : null;
      } catch (final PartInitException ignore) {
         return null;
      }
   }
}
