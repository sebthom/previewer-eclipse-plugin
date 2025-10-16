/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui.editorsupport;

import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.IEditorReference;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.util.CompareEditorUtils;
import de.sebthom.eclipse.previewer.util.CompareEditorUtils.CompareEditorRef;
import de.sebthom.eclipse.previewer.util.CompareEditorUtils.CompareEditorSide;
import de.sebthom.eclipse.previewer.util.CompareEditorUtils.SharedDocContext;
import de.sebthom.eclipse.previewer.util.ContentSources;

/**
 * @author Sebastian Thomschke
 */
public final class CompareEditorSupport implements EditorSupport {

   public static final class TrackedCompareEditorContext extends TrackedEditorContext {

      private final CompareEditorRef compareEditor;
      private final SharedDocContext sharedDocCtx;
      private final CompareEditorSide side;

      TrackedCompareEditorContext(final CompareEditorRef compareEditor, final Path path, final SharedDocContext sharedDocCtx,
            final CompareEditorSide side) {
         super(compareEditor.editor(), path, sharedDocCtx.doc());
         this.compareEditor = compareEditor;
         this.sharedDocCtx = sharedDocCtx;
         this.side = side;
      }

      @Override
      public void close() {
         try {
            sharedDocCtx.close();
         } catch (final Exception ignore) {
            //
         }
      }

      @Override
      public ContentSource getSource() {
         if (ToggleLivePreview.isLivePreviewEnabled())
            return ContentSources.of(sharedDocCtx.virtualPathName(), sharedDocCtx.doc().get());
         final var cs = CompareEditorUtils.resolve(compareEditor.input(), side);
         return cs != null ? cs : ContentSources.of(sharedDocCtx.virtualPathName(), sharedDocCtx.doc().get());
      }
   }

   @SuppressWarnings("resource")
   @Override
   public @Nullable TrackedCompareEditorContext createFrom(final IEditorReference editorRef) {
      final CompareEditorRef ref = CompareEditorUtils.getCompareEditor(editorRef);
      if (ref == null)
         return null;

      // Prefer LEFT side, with fallback handled internally by util
      final CompareEditorSide side = CompareEditorSide.LEFT;
      final SharedDocContext shared = CompareEditorUtils.connectSharedDocument(ref.input(), side);
      if (shared == null)
         return null;

      final ContentSource resolved = CompareEditorUtils.resolve(ref.input(), side);
      final Path path = resolved != null ? resolved.path() : Path.of(shared.virtualPathName());
      return new TrackedCompareEditorContext(ref, path, shared, side);
   }
}
