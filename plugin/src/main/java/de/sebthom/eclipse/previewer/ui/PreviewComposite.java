/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;
import de.sebthom.eclipse.previewer.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.renderer.PreviewRendererExtension;
import de.sebthom.eclipse.previewer.renderer.html.ExtensibleHtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.ContentSources;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import net.sf.jstuff.core.exception.Exceptions;

/**
 * Base rendering composite reused by {@link PreviewEditor} and {@link PreviewView}.
 *
 * @author Sebastian Thomschke
 */
final class PreviewComposite extends Composite {

   static final String MARKDOWN_WEBVIEW_CRASHED = """
      If you see this message, it means the **Microsoft Edge WebView2** view has crashed. You can try the following solutions:

      1. Restart Eclipse.
      2. Restart your computer.
      3. Switch to using the **Internet Explorer WebView** via **Window > Preferences > Previewer > Web View Implementation**.
      4. Download/install a newer WebView2 version from: https://developer.microsoft.com/microsoft-edge/webview2
      """;

   private final Map<PreviewRendererExtension<PreviewRenderer>, Composite> renderers = new LinkedHashMap<>();
   private final StackLayout stack = new StackLayout();
   private final boolean openPreviewableLinksInPreviewEditor;
   private StyledText infoPanel;

   static PreviewComposite forPreviewEditor(final Composite parent, final int style) {
      return new PreviewComposite(parent, style, true);
   }

   static PreviewComposite forPreviewView(final Composite parent, final int style) {
      return new PreviewComposite(parent, style, false);
   }

   private PreviewComposite(final Composite parent, final int style, final boolean openPreviewableLinksInPreviewEditor) {
      super(parent, style);
      this.openPreviewableLinksInPreviewEditor = openPreviewableLinksInPreviewEditor;
      setLayout(stack);

      infoPanel = new StyledText(this, SWT.NONE);
      infoPanel.setLeftMargin(10);
      infoPanel.setRightMargin(10);
      infoPanel.setTopMargin(10);
      infoPanel.setBottomMargin(5);
      infoPanel.setWordWrap(true);
      infoPanel.setCaret(null);

      loadRenderersFromExtensionPoints();
   }

   @Override
   public void dispose() {
      renderers.keySet().forEach(ext -> ext.renderer.dispose());
      renderers.clear();
      super.dispose();
   }

   @SuppressWarnings("all")
   float getZoom() {
      final var zoom = new MutableFloat(1);
      renderers.entrySet().stream() //
         .filter(e -> e.getValue() == stack.topControl) //
         .findFirst().ifPresent(e -> zoom.setValue(e.getKey().renderer.getZoom()));
      return zoom.floatValue();
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
               if (rendererExt.renderer instanceof final ExtensibleHtmlPreviewRenderer htmlRenderer) {
                  htmlRenderer.setLocalFileLinkHandler(this::openLocalFileLink);
               }
               renderers.put(rendererExt, rendererParent);
            } catch (final LinkageError | CoreException ex) {
               Plugin.log().error(ex);
            }
         }
      }
   }

   private boolean canPreview(final ContentSource source) {
      for (final var rendererExt : renderers.keySet()) {
         final var renderer = rendererExt.renderer;
         if (renderer instanceof final ExtensibleHtmlPreviewRenderer htmlRenderer) {
            if (htmlRenderer.supports(source))
               return true;
         } else if (rendererExt.supports(source))
            return true;
      }
      return false;
   }

   private static @Nullable IFile findWorkspaceFile(final URI uri) {
      for (final IFile file : ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(uri)) {
         if (file.exists())
            return file;
      }
      return null;
   }

   private boolean openExternalFile(final IWorkbenchPage page, final URI target, final boolean previewable) throws CoreException {
      final var fileStore = EFS.getStore(target);
      if (shouldOpenInPreviewEditor(previewable)) {
         page.openEditor(new FileStoreEditorInput(fileStore), PreviewEditor.ID, true);
      } else {
         IDE.openEditorOnFileStore(page, fileStore);
      }
      return true;
   }

   private void openWorkspaceFile(final IWorkbenchPage page, final IFile workspaceFile, final boolean previewable) throws CoreException {
      if (shouldOpenInPreviewEditor(previewable)) {
         IDE.openEditor(page, workspaceFile, PreviewEditor.ID, true);
      } else {
         IDE.openEditor(page, workspaceFile, true);
      }
   }

   private boolean shouldOpenInPreviewEditor(final boolean previewable) {
      // Preview View opens normal editors so activation drives its follow-active-editor behavior; Preview Editor opens
      // preview links as standalone preview editors.
      return previewable && openPreviewableLinksInPreviewEditor;
   }

   private boolean openLocalFileLink(final Path path, final URI target) {
      final var page = UI.getActiveWorkbenchPage();
      if (page == null)
         return false;

      final boolean previewable = canPreview(ContentSources.of(path));
      try {
         final IFile workspaceFile = findWorkspaceFile(target);
         if (workspaceFile != null) {
            openWorkspaceFile(page, workspaceFile, previewable);
            return true;
         }

         return openExternalFile(page, target, previewable);
      } catch (final CoreException ex) {
         Plugin.log().warn(ex, "Cannot open linked file [" + target + "].");
         return false;
      }
   }

   void render(final ContentSource source, final boolean forceCacheUpdate) {
      if (UI.isUIThread()) {
         CompletableFuture.runAsync(() -> render(source, forceCacheUpdate));
         return;
      }

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

      showMessage("No renderer found for: **" + source.path() + "**");
   }

   void setZoom(final float zoom) {
      renderers.entrySet().stream() //
         .filter(e -> e.getValue() == stack.topControl) //
         .findFirst().ifPresent(e -> e.getKey().renderer.setZoom(zoom));
   }

   void showMessage(final String markdown) {
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
}
