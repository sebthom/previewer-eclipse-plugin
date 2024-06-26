/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.ITextEditor;

import de.sebthom.eclipse.commons.ui.Editors;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;
import de.sebthom.eclipse.previewer.command.ToggleLinkToEditor;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.renderer.PreviewRendererExtension;
import de.sebthom.eclipse.previewer.util.ContentSources;
import net.sf.jstuff.core.event.ThrottlingEventDispatcher;
import net.sf.jstuff.core.exception.Exceptions;

/**
 * @author Sebastian Thomschke
 */
public final class PreviewComposite extends Composite {

   static final class EditorContext {

      static @Nullable EditorContext get(final ITextEditor editor) {
         final Path file = Editors.getFilePath(editor);
         if (file == null)
            return null;
         final var doc = Editors.getDocument(editor);
         if (doc == null)
            return null;
         return new EditorContext(editor, file, doc);
      }

      static @Nullable EditorContext getActive() {
         final var editor = Editors.getActiveTextEditor();
         if (editor == null)
            return null;
         return get(editor);
      }

      final IDocument document;
      final ITextEditor editor;
      final Path file;

      EditorContext(final ITextEditor editor, final Path file, final IDocument doc) {
         this.editor = editor;
         this.file = file;
         document = doc;
      }

      @Override
      public boolean equals(final @Nullable Object obj) {
         if (this == obj)
            return true;
         if (obj instanceof final EditorContext other)
            return document == other.document && editor == other.editor && file.equals(other.file);
         return false;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + System.identityHashCode(document);
         result = prime * result + System.identityHashCode(editor);
         result = prime * result + System.identityHashCode(file);
         return result;
      }
   }

   private static final String WELCOME_TEXT = "<html><body style='font-family: Tahoma,sans-serif'>"
         + "Open a supported file in a text editor to see a rendered preview here.</body></html>";

   private final ThrottlingEventDispatcher<EditorContext> editorTextModifiedEventDispatcher = ThrottlingEventDispatcher //
      .builder(EditorContext.class, Duration.ofMillis(1_000)).build();

   private IPropertyListener editorDirtyStateListener = (source, propId) -> {
      if (propId == ISaveablePart.PROP_DIRTY //
            && source instanceof final ITextEditor editor //
            && !editor.isDirty()) {
         final var editorCtx = EditorContext.get(editor);
         if (editorCtx != null) {
            onDocumentSaved(editorCtx);
         }
      }
   };

   private IPartListener2 editorOpenCloseListener = new IPartListener2() {

      @Override
      public void partActivated(final IWorkbenchPartReference partRef) {

         /*
          * Order is:
          * partOpened:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partVisible:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partBroughtToTop:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partActivated:org.eclipse.ui.internal.EditorReference@7c5a7f
          */
         if (partRef instanceof final IEditorReference editorRef //
               && editorRef.getEditor(false) instanceof final ITextEditor editor) {

            final var editorCtx = EditorContext.get(editor);
            if (editorCtx == null)
               return;

            final var trackedEditorContext = PreviewComposite.this.trackedEditorContext;
            if (trackedEditorContext == null || ToggleLinkToEditor.isLinkToEditorEnabled()) {
               if (trackedEditorContext != null) {
                  trackedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
                  trackedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
               }

               editorCtx.editor.addPropertyListener(editorDirtyStateListener);
               editorCtx.document.addDocumentListener(editorTextModifiedListener);
               PreviewComposite.this.trackedEditorContext = editorCtx;

               render(editorCtx, false);
            }
         }
      }

      @Override
      public void partClosed(final IWorkbenchPartReference partRef) {
         if (Editors.getActiveTextEditor() == null) {
            final var trackedEditorContext = PreviewComposite.this.trackedEditorContext;
            if (trackedEditorContext != null) {
               trackedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
               trackedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
            }
            PreviewComposite.this.trackedEditorContext = null;

            infoPanel.setText(WELCOME_TEXT);
            showStackElement(infoPanel);
         }
      }
   };

   private IDocumentListener editorTextModifiedListener = new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(final DocumentEvent event) {
      }

      @Override
      public void documentChanged(final DocumentEvent event) {
         final var editor = Editors.getActiveTextEditor();
         if (editor == null)
            return;
         final var editorCtx = EditorContext.get(editor);
         if (editorCtx == null)
            return;
         if (editorCtx.document == event.getDocument()) {
            editorTextModifiedEventDispatcher.fire(editorCtx);
         }
      }
   };

   private final IPartService partService;
   private final Map<PreviewRendererExtension<PreviewRenderer>, Composite> renderers = new LinkedHashMap<>();
   private final StackLayout stack = new StackLayout();
   private Browser infoPanel;
   private @Nullable EditorContext trackedEditorContext;

   public PreviewComposite(final Composite parent, final PreviewView viewPart) {
      super(parent, SWT.NONE);

      partService = viewPart.getSite().getWorkbenchWindow().getPartService();
      partService.addPartListener(editorOpenCloseListener);

      setLayout(stack);
      infoPanel = new Browser(this, SWT.None);
      infoPanel.setText(WELCOME_TEXT);
      showStackElement(infoPanel);

      editorTextModifiedEventDispatcher.subscribe(this::onDocumentEdited);

      loadRenderersFromExtensionPoints();
   }

   @Override
   public void dispose() {
      partService.removePartListener(editorOpenCloseListener);
      editorTextModifiedEventDispatcher.close();

      final var trackedEditorContext = this.trackedEditorContext;
      if (trackedEditorContext != null) {
         trackedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
         trackedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
      }
      this.trackedEditorContext = null;

      renderers.keySet().forEach(ext -> ext.renderer.dispose());
      renderers.clear();

      super.dispose();
   }

   void forceRefresh() {
      final var trackedEditorContext = this.trackedEditorContext;
      if (trackedEditorContext != null) {
         render(trackedEditorContext, true);
      }
   }

   private void loadRenderersFromExtensionPoints() {
      for (final IConfigurationElement ce : Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) {
         final String extensionName = ce.getName();
         if ("previewRenderer".equals(extensionName)) {
            try {
               final var rendererExt = new PreviewRendererExtension<PreviewRenderer>(ce);
               final var rendererParent = new Composite(this, SWT.NONE);
               rendererParent.setLayout(new FillLayout());
               rendererExt.renderer.init(rendererParent);
               renderers.put(rendererExt, rendererParent);
            } catch (final LinkageError | CoreException ex) {
               Plugin.log().error(ex);
            }
         }
      }
   }

   private void onDocumentEdited(final EditorContext editorCtx) {
      if (editorCtx.equals(trackedEditorContext) && ToggleLivePreview.isLivePreviewEnabled()) {
         render(editorCtx, false);
      }
   }

   private void onDocumentSaved(final EditorContext editorCtx) {
      if (editorCtx.equals(trackedEditorContext)) {
         render(editorCtx, false);
      }
   }

   void openEditor() {
      final var trackedEditorContext = this.trackedEditorContext;
      final var page = UI.getActiveWorkbenchPage();
      if (trackedEditorContext != null && page != null) {
         page.activate(trackedEditorContext.editor);
      }
   }

   private void render(final EditorContext editorCtx, final boolean forceCacheUpdate) {
      // if on UI thread offload rendering to background thread
      if (UI.isUIThread()) {
         CompletableFuture.runAsync(() -> render(editorCtx, forceCacheUpdate));
         return;
      }

      final var source = ToggleLivePreview.isLivePreviewEnabled() && editorCtx.editor.isDirty() //
            ? ContentSources.of(editorCtx.editor)
            : ContentSources.of(editorCtx.file);

      for (final var entry : renderers.entrySet()) {
         final var rendererExt = entry.getKey();
         try {
            if (rendererExt.supports(source) && rendererExt.renderer.render(source, forceCacheUpdate)) {
               UI.run(() -> showStackElement(entry.getValue()));
               return;
            }
         } catch (final LinkageError | StackOverflowError | Exception ex) {
            Plugin.log().error(ex);
            UI.run(() -> {
               if (!isDisposed()) {
                  infoPanel.setText("<!DOCTYPE html>\n<html><body>" //
                        + "Failed to render: <b>" + source.path() + "</b><br>\n" //
                        + "Renderer: <b>" + rendererExt.renderer.getClass().getName() + "</b>\n<br>\n" //
                        + "Time: <b>" + LocalTime.now() + "</b><br>\n" //
                        + "Reason:<pre>" + Exceptions.getStackTrace(ex).replace("\t", "  ") + "</pre>\n" //
                        + "</body></html>");
                  showStackElement(infoPanel);
               }
            });
         }
      }
   }

   private void showStackElement(final Control control) {
      if (isDisposed())
         return;
      stack.topControl = control;
      layout();
   }
}
