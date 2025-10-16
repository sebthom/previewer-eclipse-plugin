/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.IWorkbenchPartReference;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;
import de.sebthom.eclipse.previewer.command.ToggleLinkToEditor;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.renderer.PreviewRendererExtension;
import de.sebthom.eclipse.previewer.ui.editorsupport.CompareEditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.EditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.TextEditorSupport;
import de.sebthom.eclipse.previewer.ui.editorsupport.TrackedEditorContext;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import net.sf.jstuff.core.event.ThrottlingEventDispatcher;
import net.sf.jstuff.core.exception.Exceptions;

/**
 * @author Sebastian Thomschke
 */
public final class PreviewComposite extends Composite {

   private static final String MARKDOWN_WELCOME = "Open a **supported** file in a text or compare editor to see a rendered preview here.";
   private static final String MARKDOWN_WEBVIEW_CRASHED = """
      If you see this message, it means the **Microsoft Edge WebView2** view has crashed. You can try the following solutions:

      1. Restart Eclipse.
      2. Restart your computer.
      3. Switch to using the **Internet Explorer WebView** via **Window > Preferences > Previewer > Web View Implementation**.
      4. Download/install a newer WebView2 version from: https://developer.microsoft.com/microsoft-edge/webview2
      """;

   private final ThrottlingEventDispatcher<TrackedEditorContext> editorTextModifiedEventDispatcher = ThrottlingEventDispatcher //
      .builder(TrackedEditorContext.class, Duration.ofMillis(1_000)).build();

   private IPropertyListener editorDirtyStateListener = (source, propId) -> {
      if (propId == ISaveablePart.PROP_DIRTY && source instanceof final ISaveablePart saveable && !saveable.isDirty()) {
         final var linkedEditorContext = this.linkedEditorContext;
         if (linkedEditorContext != null && source == linkedEditorContext.getEditor()) {
            onDocumentSaved(linkedEditorContext);
         }
      }
   };

   private IPartListener2 partListener = new IPartListener2() {

      @Override
      public void partActivated(final IWorkbenchPartReference partRef) {

         /*
          * Order is:
          * partOpened:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partVisible:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partBroughtToTop:org.eclipse.ui.internal.EditorReference@7c5a7f
          * partActivated:org.eclipse.ui.internal.EditorReference@7c5a7f
          */
         if (partRef instanceof final IEditorReference editorRef) {
            for (final var support : editorSupports) {
               final TrackedEditorContext newEditorContext = support.createFrom(editorRef);
               if (newEditorContext != null) {
                  final var linkedEditorContext = PreviewComposite.this.linkedEditorContext;
                  if (linkedEditorContext == null || ToggleLinkToEditor.isLinkToEditorEnabled()) {
                     if (linkedEditorContext != null) {
                        linkedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
                        linkedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
                        linkedEditorContext.close();
                     }
                     newEditorContext.editor.addPropertyListener(editorDirtyStateListener);
                     newEditorContext.document.addDocumentListener(editorTextModifiedListener);
                     PreviewComposite.this.linkedEditorContext = newEditorContext;
                     render(newEditorContext, false);
                  }
                  break;
               }
            }
         }
      }

      @Override
      public void partClosed(final IWorkbenchPartReference partRef) {
         if (partRef instanceof final IEditorReference editorRef) {
            final var linkedEditorContext = PreviewComposite.this.linkedEditorContext;
            if (linkedEditorContext != null && editorRef.getEditor(false) == linkedEditorContext.editor) {
               linkedEditorContext.document.removeDocumentListener(editorTextModifiedListener);
               linkedEditorContext.editor.removePropertyListener(editorDirtyStateListener);
               linkedEditorContext.close();
               PreviewComposite.this.linkedEditorContext = null;
               showMessage(MARKDOWN_WELCOME);
            }
         }
      }
   };

   private IDocumentListener editorTextModifiedListener = new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(final DocumentEvent event) {
      }

      @Override
      public void documentChanged(final DocumentEvent event) {
         final var linkedEditorContext = PreviewComposite.this.linkedEditorContext;
         if (linkedEditorContext != null && linkedEditorContext.document == event.getDocument()) {
            editorTextModifiedEventDispatcher.fire(linkedEditorContext);
         }
      }
   };

   private final IPartService partService;
   private final Map<PreviewRendererExtension<PreviewRenderer>, Composite> renderers = new LinkedHashMap<>();
   private final StackLayout stack = new StackLayout();
   private StyledText infoPanel;
   private @Nullable TrackedEditorContext linkedEditorContext;
   private final List<EditorSupport> editorSupports = new ArrayList<>();

   public PreviewComposite(final Composite parent, final PreviewView viewPart) {
      super(parent, SWT.NONE);

      partService = viewPart.getSite().getWorkbenchWindow().getPartService();
      partService.addPartListener(partListener);

      setLayout(stack);

      infoPanel = new StyledText(this, SWT.NONE);
      infoPanel.setLeftMargin(10);
      infoPanel.setRightMargin(10);
      infoPanel.setTopMargin(10);
      infoPanel.setBottomMargin(5);
      infoPanel.setWordWrap(true);
      infoPanel.setCaret(null);

      showMessage(MARKDOWN_WELCOME);

      editorTextModifiedEventDispatcher.subscribe(this::onDocumentEdited);

      loadRenderersFromExtensionPoints();

      // register editor supports in precedence order
      editorSupports.add(new TextEditorSupport());
      editorSupports.add(new CompareEditorSupport());
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

      renderers.keySet().forEach(ext -> ext.renderer.dispose());
      renderers.clear();

      editorSupports.clear();

      super.dispose();
   }

   void forceRefresh() {
      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext != null) {
         render(linkedEditorContext, true);
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

   private void onDocumentEdited(final TrackedEditorContext editorCtx) {
      if (editorCtx == linkedEditorContext && ToggleLivePreview.isLivePreviewEnabled()) {
         render(editorCtx, false);
      }
   }

   private void onDocumentSaved(final TrackedEditorContext editorCtx) {
      if (editorCtx == linkedEditorContext) {
         render(editorCtx, false);
      }
   }

   void openEditor() {
      final var page = UI.getActiveWorkbenchPage();
      if (page == null)
         return;

      final var linkedEditorContext = this.linkedEditorContext;
      if (linkedEditorContext != null) {
         linkedEditorContext.activateEditor();
      }
   }

   private void render(final TrackedEditorContext editorCtx, final boolean forceCacheUpdate) {
      // if on UI thread offload rendering to background thread
      if (UI.isUIThread()) {
         CompletableFuture.runAsync(() -> render(editorCtx, forceCacheUpdate));
         return;
      }

      final var source = editorCtx.getSource();

      for (final var entry : renderers.entrySet()) {
         final var rendererExt = entry.getKey();
         try {
            if (rendererExt.supports(source) && rendererExt.renderer.render(source, forceCacheUpdate)) {
               if (SystemUtils.IS_OS_WINDOWS && "edge".equals(PluginPreferences.getWebView())) {
                  showMessage(MARKDOWN_WEBVIEW_CRASHED);
               }
               showStackElement(entry.getValue());
               return;
            }
         } catch (final LinkageError | StackOverflowError | Exception ex) {
            Plugin.log().error(ex);
            showMessage("Failed to render: **" + source.path() + "**\n" //
                  + "Renderer: **" + rendererExt.renderer.getClass().getName() + "**\n" //
                  + "Time: **" + MiscUtils.getCurrentTime() + "**\n" //
                  + "Reason:\n```" + Exceptions.getStackTrace(ex).replace("\t", "  ") + "```\n");
         }
      }
   }

   private void showMessage(final String markdown) {
      UI.run(() -> {
         if (!isDisposed()) {
            MiscUtils.setMarkdown(infoPanel, markdown);
            showStackElement(infoPanel);
         }
      });
   }

   private void showStackElement(final Control control) {
      UI.run(() -> {
         if (!isDisposed()) {
            stack.topControl = control;
            layout();
         }
      });
   }

   @SuppressWarnings("all")
   float getZoom() {
      final var zoom = new MutableFloat(1);
      renderers.entrySet().stream() //
         .filter(e -> e.getValue() == stack.topControl) //
         .findFirst().ifPresent(e -> zoom.setValue(e.getKey().renderer.getZoom()));
      return zoom.floatValue();
   }

   void setZoom(final float zoom) {
      renderers.entrySet().stream() //
         .filter(e -> e.getValue() == stack.topControl) //
         .findFirst().ifPresent(e -> e.getKey().renderer.setZoom(zoom));
   }
}
