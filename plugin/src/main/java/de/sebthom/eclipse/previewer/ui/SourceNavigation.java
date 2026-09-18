/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import de.sebthom.eclipse.commons.ui.Editors;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;
import de.sebthom.eclipse.previewer.api.SourceNavigator;

/**
 * Resolves preview locations off the SWT thread and reveals them only in the same, unchanged source document.
 * Editor lookup follows the preview's linked editor, including when the preview is pinned.
 *
 * @author Sebastian Thomschke
 */
final class SourceNavigation {

   private record Target(IEditorPart part, ITextEditor editor) {
   }

   private final Control owner;
   private final Supplier<@Nullable IEditorPart> linkedEditor;
   private final IStatusLineManager statusLine;
   private long requests;

   SourceNavigation(final Control owner, final Supplier<@Nullable IEditorPart> linkedEditor, final IStatusLineManager statusLine) {
      this.owner = owner;
      this.linkedEditor = linkedEditor;
      this.statusLine = statusLine;
   }

   SourceNavigator forRenderer(final PreviewRenderer renderer) {
      // Entry IDs belong to the issuing renderer. Never resolve them through whichever renderer is active later.
      return (source, elementId) -> navigateToSource(renderer, source, elementId);
   }

   private void navigateToSource(final PreviewRenderer renderer, final Path source, final String elementId) {
      final long request = ++requests;
      if (owner.isDisposed())
         return;
      final var origin = linkedEditor.get();
      if (origin == null)
         return;
      statusLine.setErrorMessage(null);
      final var originPath = Editors.getFilePath(origin);
      if (originPath == null) {
         // Compare revisions can have virtual identities. Do not redirect them to a different file on disk.
         statusLine.setErrorMessage("Source navigation is not available for this editor.");
         return;
      }
      if (!source.equals(originPath)) {
         // A renderer may still show the previous input while the next background render is pending.
         statusLine.setErrorMessage("The preview source has changed. Wait for the preview to refresh.");
         return;
      }

      final var page = origin.getSite().getPage();
      final Target target;
      try {
         target = findTarget(origin, page, source);
      } catch (final PartInitException ex) {
         Plugin.log().error(ex);
         statusLine.setErrorMessage("Cannot open the source editor: " + ex.getMessage());
         return;
      }
      if (target == null) {
         statusLine.setErrorMessage("Source navigation is not available for this editor.");
         return;
      }
      final var document = Editors.getDocument(target.editor);
      if (document == null) {
         statusLine.setErrorMessage("The source document is no longer available.");
         return;
      }

      final String content = document.get();
      CompletableFuture.runAsync(() -> {
         IRegion region = null;
         String error = null;
         try {
            region = renderer.resolveSourceLocation(content, elementId);
            if (region == null) {
               error = "This entry no longer exists in the current document.";
            }
         } catch (final IOException ex) {
            // Incomplete source text while typing is an expected navigation failure, not an error in the preview plugin.
            error = "Cannot locate this entry in the current document: " + ex.getMessage();
         } catch (final RuntimeException ex) {
            Plugin.log().error(ex);
            error = "Cannot locate this entry in the current document.";
         }
         final IRegion resolved = region;
         final String failure = error;
         UI.run(() -> {
            // A newer click, a changed preview link, or a closed editor must invalidate delayed navigation.
            if (requests != request || owner.isDisposed() || linkedEditor.get() != origin || page.getReference(target.part) == null)
               return;
            if (!source.equals(Editors.getFilePath(target.part)) || Editors.getDocument(target.editor) != document || !content.equals(
               document.get())) {
               statusLine.setErrorMessage("The source document changed. Double-click the entry again.");
               return;
            }
            if (resolved == null) {
               statusLine.setErrorMessage(failure);
               return;
            }
            final int offset = resolved.getOffset();
            final int length = resolved.getLength();
            if (offset < 0 || length < 0 || offset > content.length() || length > content.length() - offset) {
               statusLine.setErrorMessage("The preview entry has an invalid source location.");
               return;
            }
            // Activate the owning part first; multi-page editors may expose a nested ITextEditor through an adapter.
            page.activate(target.part);
            target.editor.selectAndReveal(offset, length);
            target.editor.setFocus();
         });
      });
   }

   private static @Nullable Target asTextTarget(final @Nullable IEditorPart part) {
      if (part == null)
         return null;
      final var textEditor = Editors.getTextEditor(part);
      return textEditor == null ? null : new Target(part, textEditor);
   }

   private static @Nullable Target findTarget(final IEditorPart origin, final IWorkbenchPage page, final Path source)
         throws PartInitException {
      if (!(origin instanceof PreviewEditor))
         return asTextTarget(origin);

      // Reuse an open text editor so navigation honors unsaved content when starting from the standalone Preview Editor.
      for (final var reference : page.getEditorReferences()) {
         final var candidate = reference.getEditor(false);
         if (candidate != null && source.equals(Editors.getFilePath(candidate))) {
            final var target = asTextTarget(candidate);
            if (target != null)
               return target;
         }
      }
      // Explicitly open a text editor: the user's default association might otherwise reopen the Preview Editor itself.
      return asTextTarget(IDE.openEditor(page, source.toUri(), EditorsUI.DEFAULT_TEXT_EDITOR_ID, false));
   }
}
