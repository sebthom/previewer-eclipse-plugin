/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;

import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.EditorPart;

import de.sebthom.eclipse.commons.resources.Resources;
import de.sebthom.eclipse.commons.ui.Buttons;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.command.ToggleLivePreview;
import de.sebthom.eclipse.previewer.util.ContentSources;

/**
 * Preview EditorPart that renders a file without requiring a text editor.
 *
 * @author Sebastian Thomschke
 */
public final class PreviewEditor extends EditorPart {

   public static final String ID = PreviewEditor.class.getName();

   private @Nullable IFile workspaceFile;
   private @Nullable Path filePath;

   private PreviewComposite renderPane = lateNonNull();

   private final IResourceChangeListener resourceListener = event -> {
      if (workspaceFile == null || event.getType() != IResourceChangeEvent.POST_CHANGE)
         return;
      final IResourceDelta delta = event.getDelta();
      if (delta == null)
         return;
      try {
         delta.accept((IResourceDeltaVisitor) d -> {
            if (d.getResource() instanceof final IFile f && f.equals(workspaceFile)) {
               // re-render on save/change when Live Preview is enabled
               if (ToggleLivePreview.isLivePreviewEnabled()) {
                  renderCurrent(false);
               }
               return false;
            }
            return true;
         });
      } catch (final CoreException ignore) {
         // ignore
      }
   };

   @Override
   public void createPartControl(final Composite parent) {
      final var root = new Composite(parent, SWT.NONE);
      final var layout = new GridLayout(1, false);
      layout.marginWidth = 0;
      layout.marginHeight = 0;
      layout.horizontalSpacing = 0;
      layout.verticalSpacing = 0;
      root.setLayout(layout);

      final var toolbar = new ToolBar(root, SWT.FLAT | SWT.RIGHT);
      toolbar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

      final boolean hiDPI = UI.getDisplay().getDPI().x > 120;
      final var zoomIn = new ToolItem(toolbar, SWT.PUSH);
      zoomIn.setImage(Plugin.get().getImageRegistry().get(hiDPI ? Constants.IMAGE_ZOOM_IN_2X : Constants.IMAGE_ZOOM_IN));
      zoomIn.setToolTipText("Zoom In");
      Buttons.onSelected(zoomIn, () -> renderPane.setZoom(renderPane.getZoom() + 0.1f));

      final var zoomReset = new ToolItem(toolbar, SWT.PUSH);
      zoomReset.setImage(Plugin.get().getImageRegistry().get(hiDPI ? Constants.IMAGE_ZOOM_RESET_2X : Constants.IMAGE_ZOOM_RESET));
      zoomReset.setToolTipText("Reset Zoom");
      Buttons.onSelected(zoomReset, () -> renderPane.setZoom(1.0f));

      final var zoomOut = new ToolItem(toolbar, SWT.PUSH);
      zoomOut.setImage(Plugin.get().getImageRegistry().get(hiDPI ? Constants.IMAGE_ZOOM_OUT_2X : Constants.IMAGE_ZOOM_OUT));
      zoomOut.setToolTipText("Zoom Out");
      Buttons.onSelected(zoomOut, () -> renderPane.setZoom(renderPane.getZoom() - 0.1f));

      @SuppressWarnings("unused")
      final var separator = new ToolItem(toolbar, SWT.SEPARATOR);

      final var refresh = new ToolItem(toolbar, SWT.PUSH);
      refresh.setImage(Plugin.get().getImageRegistry().get(hiDPI ? Constants.IMAGE_REFRESH_2X : Constants.IMAGE_REFRESH));
      refresh.setToolTipText("Refresh");
      Buttons.onSelected(refresh, () -> renderCurrent(true));

      final var settings = new ToolItem(toolbar, SWT.PUSH);
      settings.setImage(Plugin.get().getImageRegistry().get(hiDPI ? Constants.IMAGE_SETTINGS_2X : Constants.IMAGE_SETTINGS));
      settings.setToolTipText("Open Preferences");
      Buttons.onSelected(settings, () -> PreferencesUtil.createPreferenceDialogOn(getSite().getShell(),
         de.sebthom.eclipse.previewer.prefs.PluginPreferencePage.class.getName(), null, null).open());

      renderPane = new PreviewComposite(root, SWT.NONE);
      renderPane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      renderPane.showMessage("Rendering preview...");

      renderCurrent(false);
   }

   @Override
   public void dispose() {
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
      super.dispose();
   }

   @Override
   public void doSave(final @Nullable IProgressMonitor monitor) {
      // read-only editor
   }

   @Override
   public void doSaveAs() {
      // not supported
   }

   @Override
   public void init(final @NonNullByDefault({}) IEditorSite site, final @NonNullByDefault({}) IEditorInput input) throws PartInitException {
      setSite(site);
      setInput(input);

      if (input instanceof final IFileEditorInput fei) {
         final var workspaceFile = this.workspaceFile = fei.getFile();
         filePath = Resources.toAbsolutePath(workspaceFile);
         setPartName(workspaceFile.getName());
      } else if (input instanceof final IURIEditorInput uei) {
         final var uri = uei.getURI();
         if ("file".equalsIgnoreCase(uri.getScheme())) {
            filePath = Path.of(uri);
            setPartName(filePath.getFileName().toString());
         } else
            throw new PartInitException("Unsupported editor input URI: " + uri);
      } else
         throw new PartInitException("Unsupported editor input: " + input.getClass().getName());

      // listen for resource changes to refresh on saves
      if (workspaceFile != null) {
         ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener, IResourceChangeEvent.POST_CHANGE);
      }
   }

   @Override
   public boolean isDirty() {
      return false;
   }

   @Override
   public boolean isSaveAsAllowed() {
      return false;
   }

   private void renderCurrent(final boolean forceCacheUpdate) {
      final var path = filePath;
      if (path == null) {
         renderPane.showMessage("No file selected.");
         return;
      }
      renderPane.render(ContentSources.of(path), forceCacheUpdate);
   }

   @Override
   public void setFocus() {
      renderPane.setFocus();
   }
}
