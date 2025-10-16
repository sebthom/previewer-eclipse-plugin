/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;

import de.sebthom.eclipse.commons.ui.Editors;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.command.TogglePinPreview;
import de.sebthom.eclipse.previewer.ui.editorsupport.CompareEditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.EditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.TextEditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.TrackedEditorContext;
import net.sf.jstuff.core.event.ThrottlingEventDispatcher;

/**
 * Preview view that embeds the shared PreviewComposite and handles linking to editors.
 *
 * @author Sebastian Thomschke
 */
public final class PreviewView extends ViewPart {

   public static final String ID = PreviewView.class.getName();

   private PreviewComposite renderPane = lateNonNull();
   private final List<EditorSupport> editorSupports = new ArrayList<>();
   private @Nullable TrackedEditorContext linkedEditorContext;
   private IPartService partService = lateNonNull();
   private final ThrottlingEventDispatcher<TrackedEditorContext> editorTextModifiedEventDispatcher = ThrottlingEventDispatcher //
      .builder(TrackedEditorContext.class, Duration.ofMillis(1_000)).build();

   private final IPropertyListener editorDirtyStateListener = (source, propId) -> {
      if (propId == ISaveablePart.PROP_DIRTY && source instanceof final ISaveablePart saveable && !saveable.isDirty()) {
         final var linkedEditorContext = this.linkedEditorContext;
         if (linkedEditorContext != null && source == linkedEditorContext.getEditor()) {
            onDocumentSaved(linkedEditorContext);
         }
      }
   };

   private final IPartListener2 partListener = new IPartListener2() {
      @Override
      public void partActivated(final IWorkbenchPartReference partRef) {
         if (partRef instanceof final IEditorReference editorRef) {
            for (final var support : editorSupports) {
               @SuppressWarnings("resource")
               final TrackedEditorContext newEditorContext = support.createFrom(editorRef);
               if (newEditorContext != null) {
                  final var linkedEditorContext = PreviewView.this.linkedEditorContext;
                  if (linkedEditorContext == null || !TogglePinPreview.isPinned()) {
                     linkToEditorContext(newEditorContext);
                  }
                  break;
               }
            }
         }
      }

      @Override
      public void partClosed(final IWorkbenchPartReference partRef) {
         if (partRef instanceof final IEditorReference editorRef) {
            final var linkedEditorContext = PreviewView.this.linkedEditorContext;
            if (linkedEditorContext != null && editorRef.getEditor(false) == linkedEditorContext.editor) {
               linkedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
               linkedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
               linkedEditorContext.close();
               PreviewView.this.linkedEditorContext = null;
               renderPane.showMessage("Open a **supported** file in a text or compare editor to see a rendered preview here.");

               if (TogglePinPreview.isPinned()) {
                  try {
                     final IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
                     if (handlerService != null) {
                        handlerService.executeCommand(TogglePinPreview.COMMAND_ID, null);
                     }
                  } catch (final Exception ignore) {
                     // ignore
                  }
               }
            }
         }
      }
   };

   private final IDocumentListener editorTextModifiedListener = new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(final DocumentEvent event) {
      }

      @Override
      public void documentChanged(final DocumentEvent event) {
         final var linkedEditorContext = PreviewView.this.linkedEditorContext;
         if (linkedEditorContext != null && linkedEditorContext.document == event.getDocument()) {
            editorTextModifiedEventDispatcher.fire(linkedEditorContext);
         }
      }
   };

   @Override
   public void createPartControl(final Composite parent) {
      partService = getSite().getWorkbenchWindow().getPartService();
      partService.addPartListener(partListener);

      parent.setLayout(new FillLayout());
      renderPane = new PreviewComposite(parent, SWT.NONE);
      renderPane.showMessage("Open a **supported** file in a text or compare editor to see a rendered preview here.");

      editorTextModifiedEventDispatcher.subscribe(this::onDocumentEdited);

      // register editor supports in precedence order
      editorSupports.add(new TextEditorSupport());
      editorSupports.add(new CompareEditorSupport());

      // try to link to the currently active editor (if not pinned)
      linkToActiveEditor();
   }

   @Override
   public void dispose() {
      partService.removePartListener(partListener);
      editorTextModifiedEventDispatcher.close();

      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext != null) {
         linkedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
         linkedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
         linkedEditorContext.close();
      }
      this.linkedEditorContext = null;
      super.dispose();
   }

   public void forceRefresh() {
      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext == null)
         return;
      final ContentSource source = linkedEditorContext.getSource();
      renderPane.render(source, true);
   }

   public float getZoom() {
      return renderPane.getZoom();
   }

   public void linkToActiveEditor() {
      linkToActiveEditorInternal();
   }

   @SuppressWarnings("resource")
   private void linkToActiveEditorInternal() {
      if (TogglePinPreview.isPinned())
         return;
      final var activeEditor = Editors.getActiveEditor();
      if (activeEditor == null)
         return;
      for (final IEditorReference editorRef : activeEditor.getSite().getPage().getEditorReferences()) {
         if (editorRef.getEditor(false) == activeEditor) {
            for (final var support : editorSupports) {
               final TrackedEditorContext newEditorContext = support.createFrom(editorRef);
               if (newEditorContext != null) {
                  linkToEditorContext(newEditorContext);
                  return;
               }
            }
            return;
         }
      }
   }

   private void linkToEditorContext(final TrackedEditorContext newEditorContext) {
      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext != null) {
         linkedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
         linkedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
         linkedEditorContext.close();
      }
      newEditorContext.editor.addPropertyListener(editorDirtyStateListener);
      newEditorContext.document.addDocumentListener(editorTextModifiedListener);
      this.linkedEditorContext = newEditorContext;
      renderPane.render(newEditorContext.getSource(), false);
   }

   private void onDocumentEdited(final TrackedEditorContext editorCtx) {
      if (editorCtx == linkedEditorContext && ToggleLivePreview.isLivePreviewEnabled()) {
         renderPane.render(editorCtx.getSource(), false);
      }
   }

   private void onDocumentSaved(final TrackedEditorContext editorCtx) {
      if (editorCtx == linkedEditorContext) {
         renderPane.render(editorCtx.getSource(), false);
      }
   }

   public void openEditor() {
      final var page = UI.getActiveWorkbenchPage();
      if (page == null)
         return;
      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext != null) {
         linkedEditorContext.activateEditor();
      }
   }

   @Override
   public void setFocus() {
      renderPane.setFocus();
   }

   public void setZoom(final float zoom) {
      renderPane.setZoom(zoom);
   }
}
