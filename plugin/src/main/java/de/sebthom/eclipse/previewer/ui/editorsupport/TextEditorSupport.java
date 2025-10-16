/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui.editorsupport;

import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.texteditor.ITextEditor;

import de.sebthom.eclipse.commons.ui.Editors;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.util.ContentSources;

/**
 * @author Sebastian Thomschke
 */
public final class TextEditorSupport implements EditorSupport {

   public static final class TrackedTextEditorContext extends TrackedEditorContext {

      TrackedTextEditorContext(final ITextEditor editor, final Path file, final IDocument doc) {
         super(editor, file, doc);
      }

      @Override
      public ITextEditor getEditor() {
         return (ITextEditor) editor;
      }

      @Override
      public ContentSource getSource() {
         return ToggleLivePreview.isLivePreviewEnabled() && editor.isDirty() //
               ? ContentSources.of(getEditor())
               : ContentSources.of(file);
      }
   }

   @Override
   public @Nullable TrackedTextEditorContext createFrom(final IEditorReference editorRef) {
      if (editorRef.getEditor(false) instanceof final ITextEditor editor) {
         final Path file = Editors.getFilePath(editor);
         if (file == null)
            return null;
         final var doc = Editors.getDocument(editor);
         if (doc == null)
            return null;
         return new TrackedTextEditorContext(editor, file, doc);
      }
      return null;
   }
}
