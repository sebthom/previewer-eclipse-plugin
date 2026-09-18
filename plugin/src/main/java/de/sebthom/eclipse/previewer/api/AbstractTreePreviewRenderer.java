/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceLocator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.progress.WorkbenchJob;

import de.sebthom.eclipse.commons.ui.Buttons;
import de.sebthom.eclipse.commons.ui.UI;

/**
 * Renders document models in a filterable native tree, preserving expansion and selection across reparses.
 * Subclasses supply parsing, JFace providers, entry IDs and source ranges; they own columns, copy actions, styling and zoom.
 * The content provider must expose a finite tree of distinct display entries, possibly with multiple roots.
 * Entries at different positions must compare unequal in JFace's element map, even when their displayed values match.
 * Formats with shared references or cycles decide how to represent those as finite display entries.
 *
 * @author Sebastian Thomschke
 * @param <Model> the format's document model, read-only after parsing and independent of SWT
 */
public abstract class AbstractTreePreviewRenderer<Model> implements PreviewRenderer {

   private record Result<Model>(@NonNull Path path, String content, @Nullable Model model, @Nullable String error) {
   }

   private final String formatName;
   private final String filterHint;
   private final PatternFilter filter;
   private final AtomicLong requests = new AtomicLong();
   private final GridData errorLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
   private Composite root = lateNonNull();
   private Text errorText = lateNonNull();
   private FilteredTree filteredTree = lateNonNull();
   private @Nullable WorkbenchJob filterRefreshJob;
   private TreeViewer viewer = lateNonNull();
   private ITreeContentProvider contentProvider = lateNonNull();
   private Composite previewSelectorContainer = lateNonNull();
   private ToolBar treeActions = lateNonNull();
   private volatile @Nullable Result<Model> lastResult;
   // Null means no valid model has supplied the initial roots yet; an empty list means the user collapsed everything.
   private @Nullable List<String> expandedIds;
   private @Nullable String selectedId;
   private @Nullable SourceNavigator sourceNavigator;
   private int navigationColumn;
   private boolean disposed;

   /**
    * @param formatName the format name used in inline parse diagnostics, for example "JSON"
    * @param filterHint the search field's placeholder
    * @param filter a new filter for this renderer, matching full model values independently of display labels
    */
   protected AbstractTreePreviewRenderer(final String formatName, final String filterHint, final PatternFilter filter) {
      this.formatName = formatName;
      this.filterHint = filterHint;
      this.filter = filter;
   }

   /**
    * Parses a read-only model on a background thread. Navigation may call this concurrently with rendering;
    * keep parser state local and do not access SWT or editor state.
    */
   protected abstract Model parse(String content) throws IOException;

   /** Creates the hierarchy provider on the SWT thread. JFace owns its disposal with the viewer. */
   protected abstract ITreeContentProvider createContentProvider();

   /**
    * Configures labels or columns and optional actions on the SWT thread, after the hierarchy provider is installed.
    * Keep the installed content provider, filters and input under base-class control so refresh and restoration stay consistent.
    */
   protected abstract void configureViewer(TreeViewer treeViewer);

   /**
    * Identifies a displayed occurrence, not its label or value. IDs must be unique within the model and reusable after reparsing.
    * Called on the SWT thread. Positional IDs restore document positions; they need not track an entity through rearrangements.
    */
   protected abstract String getElementId(Object element);

   /**
    * Finds the occurrence identified by an earlier model, or null if it no longer exists.
    * Called both on the SWT thread for state restoration and on a background thread for source relocation.
    */
   protected abstract @Nullable Object findElement(Model model, String elementId);

   /**
    * Returns the cell's UTF-16 range in the text used to parse its model, or null for cells without source navigation.
    * The column index is the creation index, independent of visual column order; keyboard activation uses column zero.
    * Called on SWT and background threads; do not consult controls, editor state or a different model.
    */
   protected @Nullable IRegion getSourceRegion(@SuppressWarnings("unused") final Object element,
         @SuppressWarnings("unused") final int columnIndex) {
      return null;
   }

   /** Returns the initialized viewer for subclass actions and appearance updates on the SWT thread. */
   protected final TreeViewer getViewer() {
      return viewer;
   }

   /**
    * Releases subclass resources on the SWT thread, once, including after partial initialization.
    * The base invokes this for explicit renderer disposal and parent-control disposal.
    */
   protected void disposeViewerResources() {
      // Most renderers need only the providers and controls already owned by JFace and SWT.
   }

   @Override
   @SuppressWarnings("null") // JFace's legacy widget and resource APIs do not consistently declare their null contracts.
   public final void init(final Composite parent) {
      root = new Composite(parent, SWT.NONE);
      root.setLayout(GridLayoutFactory.fillDefaults().create());
      root.addDisposeListener(event -> dispose());

      errorText = new Text(root, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
      errorLayout.exclude = true;
      errorText.setLayoutData(errorLayout);
      errorText.setVisible(false);

      // Ordinary items support JFace viewer filters; virtual/lazy trees would need a separate filtering model.
      filteredTree = new FilteredTree(root, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL, filter, true, true) {
         @Override
         protected WorkbenchJob doCreateRefreshJob() {
            // Keep the framework's job and its disposal lifecycle; document switches only need to cancel pending refreshes.
            final var job = super.doCreateRefreshJob();
            filterRefreshJob = job;
            return job;
         }

         @Override
         protected Composite createFilterControls(final Composite parent) {
            // Insert the host's slot before FilteredTree creates its search box, keeping the selector on the left.
            previewSelectorContainer = new Composite(parent, SWT.NONE);
            previewSelectorContainer.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
            return super.createFilterControls(parent);
         }

         @Override
         protected void createControl(final Composite parent, final int treeStyle) {
            super.createControl(parent, treeStyle);
            Composite header = filterComposite;
            if (header == null) {
               // FilteredTree skips createFilterControls when filtering is hidden; the selector and actions still need a row.
               header = new Composite(this, SWT.NONE);
               header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
               header.moveAbove(treeComposite);
               previewSelectorContainer = new Composite(header, SWT.NONE);
               previewSelectorContainer.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
            }
            GridLayoutFactory.fillDefaults().numColumns(filterComposite == null ? 2 : 3).applyTo(header);
            // Invalid or empty input disables tree actions, but must still allow switching previews.
            createTreeActions(header, getViewer());
            treeActions.setLayoutData(new GridData(SWT.END, SWT.CENTER, filterComposite == null, false));
         }
      };
      filteredTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      // A real SWT placeholder keeps getText() free of hint text when reapplying the filter after a source update.
      filteredTree.setInitialText("");
      final Text search = filteredTree.getFilterControl();
      // The workbench can hide filter controls globally; a missing search box must still leave a usable preview.
      if (search != null) {
         search.setMessage(filterHint);
      }
      viewer = filteredTree.getViewer();
      contentProvider = createContentProvider();
      viewer.setContentProvider(contentProvider);
      configureViewer(viewer);
      // JFace's double-click event retains the row but drops the mouse coordinates. Capture the cell before activation.
      viewer.getTree().addListener(SWT.MouseDown, event -> {
         final var cell = viewer.getCell(new Point(event.x, event.y));
         navigationColumn = cell == null ? -1 : cell.getColumnIndex();
      });
      // Enter activates the row's primary location, regardless of where the mouse was last used.
      viewer.getTree().addListener(SWT.KeyDown, event -> navigationColumn = 0);
      // Row activation navigates to source; native expansion arrows keep their normal tree behavior.
      viewer.addDoubleClickListener(event -> navigateToSelection());
   }

   @Override
   public final @NonNull Composite getPreviewSelectorContainer() {
      return previewSelectorContainer;
   }

   @Override
   public final void setSourceNavigator(final SourceNavigator navigator) {
      sourceNavigator = navigator;
   }

   @Override
   public final @Nullable IRegion resolveSourceLocation(final String currentSourceText, final String elementId) throws IOException {
      // Navigation IDs include the clicked column so asynchronous relocation never reads later SWT selection state.
      // Split only the prefix: the format's own entry IDs may contain colons, or be empty for a document root.
      final int separator = elementId.indexOf(':');
      final int columnIndex = Integer.parseInt(elementId.substring(0, separator));
      // Read the cache once: rendering and location lookup can run concurrently.
      final var result = lastResult;
      // Live preview can lag or be disabled. Cached offsets are valid only for exactly the same source text.
      final Model model = result != null && result.model != null && currentSourceText.equals(result.content) ? asNonNull(result.model)
            : parse(currentSourceText);
      final var element = findElement(model, elementId.substring(separator + 1));
      return element == null ? null : getSourceRegion(element, columnIndex);
   }

   private void navigateToSelection() {
      final var navigator = sourceNavigator;
      final var result = lastResult;
      final var selected = viewer.getStructuredSelection().getFirstElement();
      final int columnIndex = navigationColumn;
      if (navigator == null || result == null || result.model == null || selected == null || columnIndex < 0 || getSourceRegion(selected,
         columnIndex) == null)
         return;
      navigator.navigateToSource(result.path, columnIndex + ":" + getElementId(selected));
   }

   @SuppressWarnings("null") // Images returned by the resource manager are non-null.
   private void createTreeActions(final Composite parent, final TreeViewer targetViewer) {
      treeActions = new ToolBar(parent, SWT.FLAT | SWT.RIGHT);
      treeActions.setEnabled(false);
      // Bind the native images to the toolbar so closing the preview releases them with its controls.
      final var images = new LocalResourceManager(JFaceResources.getResources(treeActions.getDisplay()), treeActions);
      final var expandAll = new ToolItem(treeActions, SWT.PUSH);
      expandAll.setImage(images.create(treeActionIcon("expandall")));
      expandAll.setToolTipText("Expand All");
      // Traverse filtered items so expanding does not reveal nodes excluded by the current search.
      Buttons.onSelected(expandAll, () -> targetViewer.expandAll());

      final var collapseAll = new ToolItem(treeActions, SWT.PUSH);
      collapseAll.setImage(images.create(treeActionIcon("collapseall")));
      collapseAll.setToolTipText("Collapse All");
      Buttons.onSelected(collapseAll, targetViewer::collapseAll);
   }

   @SuppressWarnings("null") // The fallback descriptor is always present.
   private static ImageDescriptor treeActionIcon(final String name) {
      final String path = "icons/full/elcl16/" + name;
      // Newer Eclipse versions provide SVG; the supported 4.29 target supplies PNG and @2x variants.
      return ResourceLocator.imageDescriptorFromBundle("org.eclipse.ui.editors", path + ".svg").or(() -> ResourceLocator
         .imageDescriptorFromBundle("org.eclipse.ui.editors", path + ".png")).orElseGet(ImageDescriptor::getMissingImageDescriptor);
   }

   @Override
   public final boolean render(final ContentSource source, final boolean forceCacheUpdate) throws IOException {
      final long request = requests.incrementAndGet();
      // A source can contain unsaved or virtual editor content; its path is an identity, not necessarily a readable file.
      final String content = source.contentAsString();
      final Path path = source.path();
      final var previous = lastResult;
      // Replacing unchanged input would rebuild native items and disturb the user's navigation.
      if (!forceCacheUpdate && previous != null && previous.path.equals(path) && previous.content.equals(content))
         return true;

      Result<Model> result;
      try {
         result = new Result<>(path, content, parse(content), null);
      } catch (final IOException ex) {
         // Incomplete input is normal while typing. Keep the renderer and show its diagnostic instead of triggering fallback.
         result = new Result<>(path, content, null, "Invalid " + formatName + ": " + ex.getMessage());
      }
      final Result<Model> completed = result;
      UI.run(() -> {
         // A slower parse must not replace a newer result, and queued work may outlive the preview controls.
         if (!disposed && !root.isDisposed() && requests.get() == request) {
            show(completed);
         }
      });
      return true;
   }

   private void show(final Result<Model> result) {
      final var previous = lastResult;
      final Text search = filteredTree.getFilterControl();
      if (previous != null && previous.path.equals(result.path)) {
         if (previous.model != null) {
            // Missing viewer entries have no ID; an empty string can identify a real root in a format such as JSON.
            expandedIds = Arrays.stream(viewer.getExpandedElements()).filter(Objects::nonNull).map(element -> getElementId(asNonNull(
               element))).toList();
            final var selected = viewer.getStructuredSelection().getFirstElement();
            selectedId = selected == null ? null : getElementId(selected);
         }
         // Keep the last valid navigation state across temporary syntax errors, when the viewer itself is empty.
      } else {
         expandedIds = null;
         selectedId = null;
         if (search != null) {
            search.setText("");
         }
         // setText schedules a refresh that would later collapse the new roots. Cancel after clearing the text;
         // this SWT-thread update applies the filter and expansion while preserving JFace's text-change bookkeeping.
         final var job = filterRefreshJob;
         if (job != null) {
            job.cancel();
         }
      }

      final String error = result.error;
      errorText.setText(error == null ? "" : error);
      errorText.setVisible(error != null);
      errorLayout.exclude = error == null;
      final var model = result.model;
      final var tree = viewer.getTree();
      tree.setRedraw(false);
      try {
         // PatternFilter caches visibility by node. Reset those caches before replacing the document model.
         filter.setPattern(search == null ? "" : search.getText());
         viewer.setInput(model);
         // Providers can prepare model state in inputChanged(); query roots only after JFace has notified them.
         final Object[] roots = model == null ? new Object[0] : contentProvider.getElements(model);
         treeActions.setEnabled(Arrays.stream(roots).anyMatch(contentProvider::hasChildren));
         if (model != null) {
            final var savedExpansion = expandedIds;
            // Expand actual roots on the first valid model; neither a synthetic root nor any particular ID is required.
            viewer.setExpandedElements(savedExpansion == null ? roots
                  : savedExpansion.stream().map(id -> findElement(model, id)).filter(Objects::nonNull).toArray());
            final String id = selectedId;
            final Object selected = id == null ? null : findElement(model, id);
            if (selected != null) {
               viewer.setSelection(new StructuredSelection(selected), true);
            }
         }
      } finally {
         tree.setRedraw(true);
      }
      lastResult = result;
      root.layout(true, true);
   }

   @Override
   public final void dispose() {
      if (disposed)
         return;
      disposed = true;
      requests.incrementAndGet();
      lastResult = null;
      sourceNavigator = null;
      // Explicit disposal can precede control disposal; do not leave the framework refresh job pending in that interval.
      final var job = filterRefreshJob;
      if (job != null) {
         job.cancel();
      }
      disposeViewerResources();
   }
}
