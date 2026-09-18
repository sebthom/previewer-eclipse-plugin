/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.dialogs.PatternFilter;
import org.junit.jupiter.api.Test;

/**
 * Exercises the shared model and relocation contracts with multiple roots, repeated labels and IDs unrelated to JSON Pointers.
 * The fixture uses simple name=value lines so these checks require neither another production parser nor SWT controls.
 *
 * @author Sebastian Thomschke
 */
class TreePreviewRendererTest {

   /** Each line supplies a separate root and value entry, even when their displayed text is equal. */
   static final class Entry {
      final String id;
      final String label;
      final @Nullable Entry parent;
      final List<Entry> children = new ArrayList<>();
      final IRegion region;

      Entry(final String id, final String label, final @Nullable Entry parent, final IRegion region) {
         this.id = id;
         this.label = label;
         this.parent = parent;
         this.region = region;
      }
   }

   record Document(List<Entry> roots, Map<String, Entry> entries) {
   }

   /** Provides only format-specific hooks; all current-text relocation comes from the shared base. */
   static final class Renderer extends AbstractTreePreviewRenderer<Document> {
      int releases;

      Renderer() {
         super("properties", "Filter values", new PatternFilter() {
            @Override
            protected boolean isLeafMatch(final Viewer viewer, final @Nullable Object element) {
               return element instanceof final Entry entry && wordMatches(entry.label);
            }
         });
      }

      @Override
      protected Document parse(final String content) throws IOException {
         final List<Entry> roots = new ArrayList<>();
         final Map<String, Entry> entries = new LinkedHashMap<>();
         int offset = 0;
         for (final String line : content.split("\n", -1)) {
            if (!line.isEmpty()) {
               final int separator = line.indexOf('=');
               if (separator <= 0)
                  throw new IOException("Expected name=value");
               final String name = line.substring(0, separator);
               // Stable IDs identify occurrences across reparses; labels are deliberately unsuitable as identifiers.
               final var root = new Entry(name + ":section", "section", null, new Region(offset, separator));
               final var value = new Entry(name + ":value", line.substring(separator + 1), root, new Region(offset + separator + 1, line
                  .length() - separator - 1));
               root.children.add(value);
               roots.add(root);
               entries.put(root.id, root);
               entries.put(value.id, value);
            }
            offset += line.length() + 1;
         }
         return new Document(roots, entries);
      }

      @Override
      @SuppressWarnings("null") // The fixture only adds non-null entries to its root and child lists.
      protected ITreeContentProvider createContentProvider() {
         return new ITreeContentProvider() {
            @Override
            public Object[] getElements(final @Nullable Object input) {
               return input instanceof final Document document ? document.roots.toArray() : new Object[0];
            }

            @Override
            public Object[] getChildren(final Object parent) {
               return ((Entry) parent).children.toArray();
            }

            @Override
            public @Nullable Object getParent(final Object element) {
               return ((Entry) element).parent;
            }

            @Override
            public boolean hasChildren(final Object element) {
               return !((Entry) element).children.isEmpty();
            }
         };
      }

      @Override
      protected void configureViewer(final TreeViewer viewer) {
         viewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(final @Nullable Object element) {
               return element instanceof final Entry entry ? entry.label : "";
            }
         });
      }

      @Override
      protected String getElementId(final Object element) {
         return ((Entry) element).id;
      }

      @Override
      protected @Nullable Entry findElement(final Document model, final String elementId) {
         return model.entries.get(elementId);
      }

      @Override
      protected @NonNull IRegion getSourceRegion(final Object element, final int columnIndex) {
         return ((Entry) element).region;
      }

      @Override
      public float getZoom() {
         return 1;
      }

      @Override
      public void setZoom(final float level) {
         // The fixture has no format-specific font resources.
      }

      @Override
      protected void disposeViewerResources() {
         releases++;
      }
   }

   @Test
   void supportsMultipleRootsAndRepeatedLabels() throws IOException {
      final var renderer = new Renderer();
      final var document = renderer.parse("first=same\nsecond=same");
      final var provider = renderer.createContentProvider();
      final Object[] roots = provider.getElements(document);
      assertEquals(2, roots.length);
      final var first = (Entry) provider.getChildren(roots[0])[0];
      final var second = (Entry) provider.getChildren(roots[1])[0];
      assertEquals(first.label, second.label);
      assertNotEquals(first, second);
      assertNotEquals(renderer.getElementId(first), renderer.getElementId(second));
      assertSame(roots[1], provider.getParent(second));
      assertSame(second, renderer.findElement(document, "second:value"));
      assertEquals(first.region, renderer.resolveSourceLocation("first=same\nsecond=same", "0:" + renderer.getElementId(first)));
      assertEquals(second.region, renderer.resolveSourceLocation("first=same\nsecond=same", "0:" + renderer.getElementId(second)));
   }

   @Test
   void relocatesAgainstCurrentTextWithoutInitializingControls() throws IOException {
      final var renderer = new Renderer();
      assertEquals(new Region(18, 4), renderer.resolveSourceLocation("first=same\nsecond=same", "0:second:value"));
      // New lines move offsets while the old entry ID still identifies the second occurrence.
      final String current = "prefix=added\nfirst=same\nsecond=changed";
      assertEquals(new Region(current.indexOf("changed"), 7), renderer.resolveSourceLocation(current, "0:second:value"));
   }

   @Test
   void findsNewInstancesByIdAfterReparsing() throws IOException {
      final var renderer = new Renderer();
      final var before = renderer.parse("first=same\nsecond=same");
      final var after = renderer.parse("second=changed\nfirst=same");
      final var original = before.entries.get("second:value");
      assert original != null;
      final var restored = renderer.findElement(after, renderer.getElementId(original));
      assertNotSame(original, restored);
      assertSame(after.entries.get("second:value"), restored);
      assertEquals(new Region(7, 7), renderer.resolveSourceLocation("second=changed\nfirst=same", "0:" + renderer.getElementId(original)));
   }

   @Test
   void doesNotReuseLocationsForDeletedEntriesOrInvalidText() throws IOException {
      final var renderer = new Renderer();
      assertNotNull(renderer.resolveSourceLocation("first=same\nsecond=same", "0:second:value"));
      assertNull(renderer.resolveSourceLocation("first=same", "0:second:value"));
      assertThrows(IOException.class, () -> renderer.resolveSourceLocation("incomplete", "0:second:value"));
   }

   @Test
   void releasesSubclassResourcesOnlyOnceAfterPartialInitialization() {
      final var renderer = new Renderer();
      renderer.dispose();
      renderer.dispose();
      assertEquals(1, renderer.releases);
   }
}
