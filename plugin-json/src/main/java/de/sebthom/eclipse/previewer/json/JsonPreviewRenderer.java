/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.ui.PlatformUI;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.api.AbstractTreePreviewRenderer;
import tools.jackson.core.JsonToken;

/**
 * Supplies JSON, JSONC and best-effort JSON5 parsing, labels, copy actions and workbench appearance for the shared native tree preview.
 * JSON Pointers identify entries for state restoration and source relocation.
 *
 * @author Sebastian Thomschke
 */
public final class JsonPreviewRenderer extends AbstractTreePreviewRenderer<JsonTree> {

   private static final int MAX_LABEL_LENGTH = 2000;

   private final MenuManager menu = new MenuManager();
   private @Nullable JsonPreviewAppearance appearance;

   public JsonPreviewRenderer() {
      super("JSON", "Filter keys and values", new JsonTreeFilter());
   }

   private static String abbreviate(final String text) {
      if (text.length() <= MAX_LABEL_LENGTH)
         return text;
      // SWT rows stay bounded, but filtering and copying must continue to use the complete model value.
      final int end = Character.isHighSurrogate(text.charAt(MAX_LABEL_LENGTH - 1)) ? MAX_LABEL_LENGTH - 1 : MAX_LABEL_LENGTH;
      return text.substring(0, end) + "...";
   }

   @Override
   protected JsonTree parse(final String content) throws IOException {
      return JsonTree.parse(content);
   }

   @Override
   protected String getElementId(final Object element) {
      return ((JsonTree.Node) element).pointer;
   }

   @Override
   protected JsonTree.@Nullable Node findElement(final JsonTree model, final String elementId) {
      // JSON Pointers name document positions; array insertion/reordering remains best effort for restoration and navigation.
      return model.find(elementId);
   }

   @Override
   protected @NonNull IRegion getSourceRegion(final Object element, final int columnIndex) {
      final var node = (JsonTree.Node) element;
      // A container summary in the Value column represents the whole object or array in the source.
      return columnIndex == 1 ? new Region(node.valueOffset, node.valueLength) : new Region(node.sourceOffset, node.sourceLength);
   }

   @Override
   protected ITreeContentProvider createContentProvider() {
      return new ITreeContentProvider() {
         @Override
         public Object[] getElements(final @Nullable Object inputElement) {
            return inputElement instanceof final JsonTree model ? new Object[] {model.root} : new Object[0];
         }

         @Override
         public Object[] getChildren(final Object parentElement) {
            return ((JsonTree.Node) parentElement).children.toArray();
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
   }

   @Override
   protected void configureViewer(final TreeViewer viewer) {
      final var treeAppearance = new JsonPreviewAppearance(viewer, PlatformUI.getWorkbench().getThemeManager());
      appearance = treeAppearance;

      final var tree = viewer.getTree();
      tree.setHeaderVisible(true);
      final var columns = new TreeColumnLayout();
      tree.getParent().setLayout(columns);
      final var keyColumn = new TreeViewerColumn(viewer, SWT.NONE);
      keyColumn.getColumn().setText("Key / Index");
      columns.setColumnData(keyColumn.getColumn(), new ColumnWeightData(35, 100));
      keyColumn.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public String getText(final @Nullable Object element) {
            return element instanceof final JsonTree.Node node ? abbreviate(node.label()) : "";
         }
      });
      final var valueColumn = new TreeViewerColumn(viewer, SWT.NONE);
      valueColumn.getColumn().setText("Value");
      columns.setColumnData(valueColumn.getColumn(), new ColumnWeightData(65, 140));
      valueColumn.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public String getText(final @Nullable Object element) {
            if (!(element instanceof final JsonTree.Node node))
               return "";
            // Uniform padding separates literals, including empty containers, from summaries without repeating tree depth.
            // Keep spacing here so model values stay free of presentation-only whitespace.
            return (node.hasContainerSummary() ? "" : "    ") + abbreviate(node.displayValue());
         }

         @Override
         public @Nullable Color getForeground(final @Nullable Object element) {
            // Native cell colors leave selection highlighting to SWT; keys keep the normal tree color.
            return element instanceof final JsonTree.Node node ? treeAppearance.getForeground(node) : null;
         }

         @Override
         public @Nullable Font getFont(final @Nullable Object element) {
            return element instanceof final JsonTree.Node node ? treeAppearance.getFont(node) : null;
         }
      });

      menu.setRemoveAllWhenShown(true);
      menu.addMenuListener(manager -> {
         final var selection = viewer.getStructuredSelection().getFirstElement();
         if (selection instanceof final JsonTree.Node node) {
            final var copyValue = new Action("Copy Value") {
               @Override
               public void run() {
                  copy(node.copyValue());
               }
            };
            // SWT cannot transfer empty text, and clearing a clipboard we do not own is not portable.
            // Disable empty results instead of reporting success while leaving unrelated clipboard contents in place.
            copyValue.setEnabled(node.type != JsonToken.VALUE_STRING || !node.scalarValue().isEmpty());
            manager.add(copyValue);
            // Preserving extended numeric literals means the result is not necessarily strict JSON.
            manager.add(new Action("Copy Subtree") {
               @Override
               public void run() {
                  copy(node.serialize());
               }
            });
            final var copyPointer = new Action("Copy JSON Pointer") {
               @Override
               public void run() {
                  copy(node.pointer);
               }
            };
            copyPointer.setEnabled(!node.pointer.isEmpty());
            manager.add(copyPointer);
         }
      });
      tree.setMenu(menu.createContextMenu(tree));
      tree.addListener(SWT.KeyDown, event -> {
         if (event.stateMask == SWT.MOD1 && (event.keyCode == 'c' || event.keyCode == 'C') && viewer.getStructuredSelection()
            .getFirstElement() instanceof final JsonTree.Node node) {
            copy(node.copyValue());
            event.doit = false;
         }
      });
   }

   private void copy(final String text) {
      // Keyboard copy follows the same empty-value restriction as the context menu.
      if (text.isEmpty())
         return;
      final var clipboard = new Clipboard(getViewer().getTree().getDisplay());
      try {
         clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
      } finally {
         clipboard.dispose();
      }
   }

   @Override
   public float getZoom() {
      final var currentAppearance = appearance;
      return currentAppearance == null ? 1 : currentAppearance.getZoom();
   }

   @Override
   public void setZoom(final float level) {
      UI.run(() -> {
         final var currentAppearance = appearance;
         if (currentAppearance != null && !getViewer().getTree().isDisposed()) {
            currentAppearance.setZoom(level);
         }
      });
   }

   @Override
   protected void disposeViewerResources() {
      menu.dispose();
      final var currentAppearance = appearance;
      appearance = null;
      if (currentAppearance != null) {
         currentAppearance.dispose();
      }
   }
}
