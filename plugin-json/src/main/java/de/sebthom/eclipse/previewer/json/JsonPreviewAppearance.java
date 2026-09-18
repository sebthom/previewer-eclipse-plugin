/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;

import de.sebthom.eclipse.commons.ui.UI;

/**
 * Applies workbench colors and fonts to one JSON tree on the SWT thread. Owns its listeners and scaled fonts;
 * the workbench retains ownership of registry colors and base fonts.
 *
 * @author Sebastian Thomschke
 */
final class JsonPreviewAppearance {

   private static final String PREFIX = "de.sebthom.eclipse.previewer.json.";
   private static final String FONT = PREFIX + "font";

   private final TreeViewer viewer;
   private final IThemeManager themeManager;
   private ITheme theme;
   private @Nullable LocalResourceManager fonts;
   private @Nullable Font summaryFont;
   private float zoom = 1;
   private boolean disposed;

   private final IPropertyChangeListener registryListener = event -> {
      if (event.getProperty().startsWith(PREFIX)) {
         UI.run(this::refresh);
      }
   };
   private final IPropertyChangeListener themeListener = event -> {
      if (IThemeManager.CHANGE_CURRENT_THEME.equals(event.getProperty())) {
         UI.run(this::refresh);
      }
   };
   private final Listener settingsListener = event -> refresh();

   JsonPreviewAppearance(final TreeViewer viewer, final IThemeManager themeManager) {
      this.viewer = viewer;
      this.themeManager = themeManager;
      theme = asNonNull(themeManager.getCurrentTheme());
      theme.getColorRegistry().addListener(registryListener);
      theme.getFontRegistry().addListener(registryListener);
      themeManager.addPropertyChangeListener(themeListener);
      viewer.getTree().getDisplay().addListener(SWT.Settings, settingsListener);
      // Parent disposal can bypass PreviewRenderer.dispose(); global registries must never retain a closed preview.
      viewer.getTree().addDisposeListener(event -> dispose());
      applyZoom();
   }

   @Nullable
   Color getForeground(final JsonTree.Node node) {
      // OS high-contrast colors take precedence over syntax colors, including user overrides.
      if (viewer.getTree().getDisplay().getHighContrast())
         return null;
      if (node.hasContainerSummary())
         return theme.getColorRegistry().get(PREFIX + "summary");
      return switch (node.type) {
         case VALUE_STRING -> theme.getColorRegistry().get(PREFIX + "string");
         case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> theme.getColorRegistry().get(PREFIX + "number");
         case VALUE_TRUE, VALUE_FALSE -> theme.getColorRegistry().get(PREFIX + "boolean");
         case VALUE_NULL -> theme.getColorRegistry().get(PREFIX + "null");
         default -> null;
      };
   }

   @Nullable
   Font getFont(final JsonTree.Node node) {
      return node.hasContainerSummary() ? summaryFont : null;
   }

   float getZoom() {
      return zoom;
   }

   void setZoom(final float level) {
      if (!disposed) {
         zoom = level > 0 && Float.isFinite(level) ? level : 1;
         applyZoom();
      }
   }

   private void refresh() {
      // Registry notifications may have been queued before the preview was closed.
      if (disposed || viewer.getTree().isDisposed())
         return;

      final @NonNull ITheme current = themeManager.getCurrentTheme();
      if (theme != current) {
         // A workbench theme switch replaces the registries; listening only to the original ones misses future edits.
         theme.getColorRegistry().removeListener(registryListener);
         theme.getFontRegistry().removeListener(registryListener);
         theme = current;
         theme.getColorRegistry().addListener(registryListener);
         theme.getFontRegistry().addListener(registryListener);
      }
      applyZoom();
   }

   private void applyZoom() {
      final var tree = viewer.getTree();
      final Font base = theme.getFontRegistry().get(FONT);
      // Always scale from the configured base font, so changing preferences preserves the current zoom without compounding it.
      final var descriptor = FontDescriptor.createFrom(base.getFontData()).setHeight(Math.max(1, Math.round(base.getFontData()[0]
         .getHeight() * zoom)));
      final var next = new LocalResourceManager(JFaceResources.getResources(tree.getDisplay()));
      final var previous = fonts;
      fonts = next;
      // Add italics to the configured style, preserving choices such as bold and the current zoom.
      summaryFont = next.create(descriptor.withStyle(SWT.ITALIC));
      tree.setFont(next.create(descriptor));
      // Cells hold explicit font references. Replace those references before releasing either old font.
      viewer.refresh();
      if (previous != null) {
         previous.dispose();
      }
   }

   void dispose() {
      if (disposed)
         return;
      disposed = true;
      themeManager.removePropertyChangeListener(themeListener);
      theme.getColorRegistry().removeListener(registryListener);
      theme.getFontRegistry().removeListener(registryListener);
      final var tree = viewer.getTree();
      tree.getDisplay().removeListener(SWT.Settings, settingsListener);
      summaryFont = null;
      // Explicit renderer disposal may precede control disposal. Detach tree and cell fonts before releasing them.
      if (!tree.isDisposed()) {
         tree.setFont(null);
         viewer.refresh();
      }
      final var previous = fonts;
      fonts = null;
      if (previous != null) {
         previous.dispose();
      }
   }
}
