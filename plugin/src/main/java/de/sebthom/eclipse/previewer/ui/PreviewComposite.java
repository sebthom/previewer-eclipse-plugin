/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
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
 * Shared renderer host for {@link PreviewEditor} and {@link PreviewView}, including per-file renderer selection, zoom,
 * messages, and source navigation.
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
   // Choices belong to this preview host, not to a format globally or to another open preview of the same file.
   private final Map<Path, PreviewRendererExtension<PreviewRenderer>> selectedRenderers = new HashMap<>();
   private final AtomicLong renderRequests = new AtomicLong();
   private CompletableFuture<@Nullable Void> renderTask = CompletableFuture.completedFuture(null);
   private @Nullable ContentSource currentSource;
   private List<PreviewRendererExtension<PreviewRenderer>> rendererChoices = List.of();
   private boolean hasRendererChoices;
   // The visible stack control determines whether the chooser belongs in a renderer's toolbar or the host's header.
   private final Map<Control, Combo> rendererSelectors = new LinkedHashMap<>();
   private final Composite selector;
   private final Combo rendererCombo;
   private final Composite rendererArea;
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
      // Disposing a parent bypasses this class's dispose() override, but must still invalidate queued renders.
      addDisposeListener(event -> renderRequests.incrementAndGet());
      final var layout = new GridLayout(1, false);
      layout.marginWidth = 0;
      layout.marginHeight = 0;
      layout.verticalSpacing = 0;
      setLayout(layout);

      selector = new Composite(this, SWT.NONE);
      selector.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
      rendererCombo = createRendererCombo(selector);

      rendererArea = new Composite(this, SWT.NONE);
      rendererArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      rendererArea.setLayout(stack);

      infoPanel = new StyledText(rendererArea, SWT.NONE);
      infoPanel.setLeftMargin(10);
      infoPanel.setRightMargin(10);
      infoPanel.setTopMargin(10);
      infoPanel.setBottomMargin(5);
      infoPanel.setWordWrap(true);
      infoPanel.setCaret(null);
      rendererSelectors.put(infoPanel, rendererCombo);

      loadRenderersFromExtensionPoints();
      setRendererChoices(List.of(), null);
   }

   private Combo createRendererCombo(final Composite parent) {
      final var layout = new GridLayout(2, false);
      layout.marginWidth = 0;
      layout.marginHeight = 0;
      parent.setLayout(layout);
      new Label(parent, SWT.NONE).setText("Preview as:");
      final var combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
      combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
      combo.addListener(SWT.Selection, event -> selectRenderer(combo));
      return combo;
   }

   @Override
   public void dispose() {
      renderRequests.incrementAndGet();
      currentSource = null;
      selectedRenderers.clear();
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
      final var configurations = new ArrayList<IConfigurationElement>();
      Collections.addAll(configurations, Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS));
      // General format previews must yield to specialized contributions, such as a TextMate grammar stored as JSON.
      // Stable sorting preserves the existing registry order for all contributions in the same group.
      configurations.sort(Comparator.comparing(ce -> Boolean.parseBoolean(ce.getAttribute("fallback"))));
      for (final IConfigurationElement ce : configurations) {
         final String extensionName = ce.getName();
         if ("previewRenderer".equals(extensionName)) {
            try {
               final var rendererExt = new PreviewRendererExtension<PreviewRenderer>(ce);
               final var rendererParent = new Composite(rendererArea, SWT.NONE);
               rendererParent.setLayout(new FillLayout());
               rendererExt.renderer.init(rendererParent);
               final var selectorContainer = rendererExt.renderer.getPreviewSelectorContainer();
               if (selectorContainer != null) {
                  // Each renderer keeps its own controls; switching previews never reparents native SWT widgets.
                  rendererSelectors.put(rendererParent, createRendererCombo(selectorContainer));
               }
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
         if (supports(rendererExt, source))
            return true;
      }
      return false;
   }

   private static boolean supports(final PreviewRendererExtension<PreviewRenderer> rendererExt, final ContentSource source) {
      // The HTML host registers **/* to delegate matching to its contributions; that wildcard is not a usable preview choice.
      return rendererExt.renderer instanceof final ExtensibleHtmlPreviewRenderer htmlRenderer //
            ? htmlRenderer.supports(source)
            : rendererExt.supports(source);
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
      UI.run(() -> {
         if (isDisposed())
            return;
         final long request = renderRequests.incrementAndGet();
         final var previousSource = currentSource;
         if (previousSource == null || !previousSource.path().equals(source.path())) {
            // Do not let a file switch apply the previous file's still-visible choices to the new source.
            setRendererChoices(List.of(), null);
         }
         currentSource = source;
         final var selected = selectedRenderers.get(source.path());
         final var available = List.copyOf(renderers.keySet());
         // Serialize renderer calls without blocking SWT. Some renderers own mutable caches, and an older call must
         // finish before a newer call can update the same controls. Superseded queued requests are skipped below.
         renderTask = renderTask.thenRunAsync(() -> render(source, forceCacheUpdate, selected, available, request)).exceptionally(ex -> {
            Plugin.log().error(ex);
            updatePreview(request, () -> showInfo("Failed to prepare preview: **" + source.path() + "**\n\n" + ex.getMessage()));
            return null;
         });
      });
   }

   private void render(final ContentSource source, final boolean forceCacheUpdate,
         final @Nullable PreviewRendererExtension<PreviewRenderer> selected,
         final List<PreviewRendererExtension<PreviewRenderer>> available, final long request) {
      if (renderRequests.get() != request)
         return;
      final var matching = available.stream().filter(renderer -> supports(renderer, source)).toList();
      updatePreview(request, () -> setRendererChoices(matching, selected));

      final var candidates = selected == null ? matching : List.of(selected);
      String failure = "No renderer found for: **" + source.path() + "**";
      for (final var rendererExt : candidates) {
         if (renderRequests.get() != request)
            return;
         try {
            if (matching.contains(rendererExt) && rendererExt.renderer.render(source, forceCacheUpdate)) {
               updatePreview(request, () -> {
                  // Only browser renderers need the crash message behind their native control.
                  if (SystemUtils.IS_OS_WINDOWS && rendererExt.renderer instanceof ExtensibleHtmlPreviewRenderer && "edge".equals(
                     PluginPreferences.getWebView())) {
                     showInfo(MARKDOWN_WEBVIEW_CRASHED);
                  }
                  if (selected == null) {
                     final String automaticLabel = "Automatic (" + rendererExt.name + ")";
                     for (final var combo : rendererSelectors.values()) {
                        if (!automaticLabel.equals(combo.getItem(0))) {
                           combo.setItem(0, automaticLabel);
                           combo.select(0);
                        }
                     }
                  }
                  showStackElement(asNonNull(renderers.get(rendererExt)));
               });
               return;
            }
            if (selected != null) {
               failure = "**" + selected.name + "** cannot preview: **" + source.path() + "**";
            }
         } catch (final LinkageError | StackOverflowError | Exception ex) {
            Plugin.log().error(ex);
            failure = "Failed to render: **" + source.path() + "**\n" //
                  + "Renderer: **" + rendererExt.renderer.getClass().getName() + "**\n" //
                  + "Time: **" + MiscUtils.getCurrentTime() + "**\n" //
                  + "Reason:\n```" + Exceptions.getStackTrace(ex).replace("\t", "  ") + "```\n";
         }
      }

      // Explicit selection has exactly one candidate, so its error cannot be hidden by an unrelated fallback preview.
      final String message = failure;
      updatePreview(request, () -> showInfo(message));
   }

   private void selectRenderer(final Combo combo) {
      final var source = currentSource;
      final int index = combo.getSelectionIndex();
      if (source == null || index < 0)
         return;
      if (index == 0) {
         selectedRenderers.remove(source.path());
      } else {
         selectedRenderers.put(source.path(), rendererChoices.get(index - 1));
      }
      // Retry even unchanged content: a renderer may have cached its input before its previous attempt failed.
      render(source, true);
   }

   private void setRendererChoices(final List<PreviewRendererExtension<PreviewRenderer>> matching,
         final @Nullable PreviewRendererExtension<PreviewRenderer> selected) {
      final var choices = new ArrayList<>(matching);
      // Content-type matching can change after an edit. Keep an explicit choice visible so the user can reset it.
      if (selected != null && !choices.contains(selected)) {
         choices.add(selected);
      }
      final int selectedIndex = selected == null ? 0 : choices.indexOf(selected) + 1;
      final var labels = new ArrayList<String>();
      labels.add(matching.isEmpty() ? "Automatic" : "Automatic (" + matching.get(0).name + ")");
      choices.forEach(renderer -> labels.add(renderer.name));
      final var items = labels.toArray(String[]::new);
      final boolean visible = selected != null || choices.size() > 1;
      // Live updates normally keep the same choices. Rebuilding the combo would close an open dropdown while typing.
      if (hasRendererChoices == visible && rendererChoices.equals(choices) && rendererSelectors.values().stream().allMatch(combo -> Arrays
         .equals(items, combo.getItems()) && combo.getSelectionIndex() == selectedIndex))
         return;
      rendererChoices = List.copyOf(choices);
      hasRendererChoices = visible;
      // Hidden copies need the same selection before their renderer becomes visible.
      for (final var combo : rendererSelectors.values()) {
         combo.setItems(items);
         combo.select(selectedIndex);
      }
      updateSelectorVisibility();
   }

   private void updateSelectorVisibility() {
      // The standalone selector remains available for renderers without a toolbar and for host-level rendering errors.
      final var activeCombo = rendererSelectors.getOrDefault(stack.topControl, rendererCombo);
      for (final var combo : rendererSelectors.values()) {
         final var parent = asNonNull(combo.getParent());
         final boolean visible = hasRendererChoices && combo == activeCombo;
         parent.setVisible(visible);
         ((GridData) asNonNull(parent.getLayoutData())).exclude = !visible;
      }
      layout(true, true);
   }

   private void updatePreview(final long request, final Runnable update) {
      UI.run(() -> {
         // A slow renderer must not switch the visible preview back after a file or renderer selection has changed.
         if (!isDisposed() && renderRequests.get() == request) {
            update.run();
         }
      });
   }

   void setSourceNavigator(final SourceNavigation navigation) {
      renderers.keySet().forEach(extension -> extension.renderer.setSourceNavigator(navigation.forRenderer(extension.renderer)));
   }

   void setZoom(final float zoom) {
      renderers.entrySet().stream() //
         .filter(e -> e.getValue() == stack.topControl) //
         .findFirst().ifPresent(e -> e.getKey().renderer.setZoom(zoom));
   }

   void showMessage(final String markdown) {
      UI.run(() -> {
         if (!isDisposed()) {
            renderRequests.incrementAndGet();
            currentSource = null;
            setRendererChoices(List.of(), null);
            showInfo(markdown);
         }
      });
   }

   private void showInfo(final String markdown) {
      MiscUtils.setMarkdown(infoPanel, markdown);
      showStackElement(infoPanel);
   }

   private void showStackElement(final Control control) {
      UI.run(() -> {
         if (!isDisposed()) {
            stack.topControl = control;
            updateSelectorVisibility();
         }
      });
   }
}
