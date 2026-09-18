/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerRow;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.dialogs.PatternFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises JSON matching and JFace's descendant cache without requiring SWT widgets or an Eclipse workbench.
 *
 * @author Sebastian Thomschke
 */
class JsonTreeFilterTest {

   private final ModelViewer viewer = new ModelViewer();
   private final JsonTreeFilter filter = new JsonTreeFilter();

   @BeforeEach
   void enableViewerCache() throws ReflectiveOperationException {
      // FilteredTree enables this package-private option for its notifying viewer. Reproduce that setup without native controls.
      final var enableCache = PatternFilter.class.getDeclaredMethod("setUseCache", boolean.class);
      enableCache.setAccessible(true);
      enableCache.invoke(filter, true);
   }

   @Test
   void retainsAncestorsAndFiltersUnrelatedBranches() throws IOException {
      final var model = JsonTree.parse("{\"users\":[{\"name\":\"Alice\"}],\"unrelated\":\"Bob\"}");
      filter.setPattern("lic");
      for (final String pointer : new String[] {"", "/users", "/users/0", "/users/0/name"}) {
         assertTrue(filter.isElementVisible(viewer, requireNonNull(model.find(pointer))), pointer);
      }
      assertFalse(filter.isElementVisible(viewer, requireNonNull(model.find("/unrelated"))));
   }

   @Test
   void matchesKeysAndCompleteDecodedScalarValues() throws IOException {
      final var model = JsonTree.parse("{\"hiddenKey\":\"" + "x".repeat(3000) + "TARGET\",\"other\":false}");
      filter.setPattern("DENkey");
      assertTrue(filter.isElementVisible(viewer, requireNonNull(model.find("/hiddenKey"))));
      filter.setPattern("target");
      assertTrue(filter.isElementVisible(viewer, model.root));
      filter.setPattern("false");
      assertTrue(filter.isElementVisible(viewer, requireNonNull(model.find("/other"))));
      assertFalse(filter.isElementVisible(viewer, requireNonNull(model.find("/hiddenKey"))));
   }

   @Test
   void changingAndClearingFilterDoesNotChangeTheCopiedDocument() throws IOException {
      final var model = JsonTree.parse("{\"a\":1,\"b\":2}");
      final String original = model.root.serialize();
      filter.setPattern("missing");
      assertFalse(filter.isElementVisible(viewer, model.root));
      filter.setPattern("");
      assertTrue(filter.isElementVisible(viewer, model.root));
      assertTrue(filter.isElementVisible(viewer, requireNonNull(model.find("/b"))));
      assertEquals(original, model.root.serialize());
   }

   @Test
   void findsTheLastValueInALargeFlatArray() throws IOException {
      final String source = IntStream.range(0, 10_000).mapToObj(Integer::toString).collect(Collectors.joining(",", "[", "]"));
      final var model = JsonTree.parse(source);
      filter.setPattern("9999");
      assertTrue(filter.isElementVisible(viewer, model.root));
      assertTrue(filter.isElementVisible(viewer, requireNonNull(model.find("/9999"))));
      assertFalse(filter.isElementVisible(viewer, requireNonNull(model.find("/0"))));
   }

   @Test
   void reusesDescendantMatchesWhenCheckingAncestors() throws IOException {
      final int depth = 64;
      final int items = 1000;
      final var model = JsonTree.parse("{\"nested\":".repeat(depth) + "[" + "\"other\",".repeat(items - 1) + "\"needle\"]" + "}".repeat(
         depth));
      filter.setPattern("needle");
      assertTrue(filter.isElementVisible(viewer, model.root));
      final int firstPassQueries = viewer.childQueries;
      assertTrue(firstPassQueries >= items, "The first search must reach the match at the end through the content provider");

      for (var ancestor = model.root; !ancestor.children.isEmpty(); ancestor = ancestor.children.get(0)) {
         assertTrue(filter.isElementVisible(viewer, ancestor));
      }
      // Count model reads instead of elapsed time: visiting ancestors again must not rescan the large array at each level.
      assertTrue(viewer.childQueries - firstPassQueries <= depth + 1, "Cached ancestor checks rescanned their descendants");
   }

   /** Supplies the tree contract required by PatternFilter while making accidental widget access fail in headless tests. */
   // Inherit JFace's null contracts for the widget stubs instead of strengthening them with the package defaults.
   @NonNullByDefault({})
   private static final class ModelViewer extends AbstractTreeViewer {
      private int childQueries;
      private final ITreeContentProvider content = new ITreeContentProvider() {
         @Override
         public Object[] getElements(final @Nullable Object input) {
            return input instanceof final JsonTree model ? new Object[] {model.root} : new Object[0];
         }

         @Override
         public Object[] getChildren(final Object parent) {
            childQueries++;
            return ((JsonTree.Node) parent).children.toArray();
         }

         @Override
         public @Nullable Object getParent(final Object element) {
            return ((JsonTree.Node) element).parent;
         }

         @Override
         public boolean hasChildren(final Object element) {
            return !((JsonTree.Node) element).children.isEmpty();
         }
      };

      @Override
      public ITreeContentProvider getContentProvider() {
         return content;
      }

      // Filtering needs only the content provider. Reject all native operations instead of silently faking widget state.
      @Override
      public Control getControl() {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void addTreeListener(final Control control, final TreeListener listener) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item[] getChildren(final Widget widget) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected boolean getExpanded(final Item item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected int getItemCount(final Control control) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected int getItemCount(final Item item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item[] getItems(final Item item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item getParentItem(final Item item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item[] getSelection(final Control control) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item newItem(final Widget parent, final int style, final int index) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void removeAll(final Control control) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void setExpanded(final Item item, final boolean expand) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void setSelection(final List<Item> items) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void showItem(final Item item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected ColumnViewerEditor createViewerEditor() {
         throw new UnsupportedOperationException();
      }

      @Override
      protected ViewerRow getViewerRowFromItem(final Widget item) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Widget getColumnViewerOwner(final int columnIndex) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected Item getItemAt(final Point point) {
         throw new UnsupportedOperationException();
      }

      @Override
      protected int doGetColumnCount() {
         throw new UnsupportedOperationException();
      }

      @Override
      protected void doUpdateItem(final Widget item, final Object element, final boolean fullMap) {
         throw new UnsupportedOperationException();
      }
   }
}
