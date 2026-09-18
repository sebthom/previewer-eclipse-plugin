/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import java.io.IOException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.services.IDisposable;

/**
 * Renderer that renders the given {@link ContentSource} into a {@link Control} managed by the renderer
 *
 * @author Sebastian Thomschke
 */
public interface PreviewRenderer extends IDisposable {

   /**
    * @param parent the parent under which the renderer places the rendering UI components
    */
   void init(Composite parent);

   /**
    * Returns the optional container for the host's "Preview as" selector.
    * Create and position the empty container during {@link #init(Composite)}, using {@link org.eclipse.swt.layout.GridData}.
    * Called on the SWT thread after initialization; return the same container without creating or rearranging controls.
    * The host owns the container's contents and internal layout,
    * and hides it with {@code GridData.exclude} when the selector is not needed.
    *
    * @return the container created during initialization, or null to use the host's separate selector
    */
   default @Nullable Composite getPreviewSelectorContainer() {
      return null;
   }

   /**
    * @return true if rendering of the given {@link ContentSource} is supported and rendering was performed.
    */
   boolean render(ContentSource source, boolean forceCacheUpdate) throws IOException;

   /**
    * Supplies source navigation after initialization, on the SWT thread.
    * Renderers using this navigator must implement {@link #resolveSourceLocation(String, String)}.
    * Renderers without source navigation may keep the default implementation.
    */
   default void setSourceNavigator(@SuppressWarnings("unused") final SourceNavigator navigator) {
      // Optional capability keeps existing renderer contributions compatible.
   }

   /**
    * Finds an entry's location in the current source text. The host passes the entry ID from
    * {@link SourceNavigator#navigateToSource(java.nio.file.Path, String)} back to the renderer that issued the request.
    * This runs on a background thread and may overlap rendering; implementations must not access SWT controls.
    *
    * @param currentSourceText a snapshot of the current editor contents, which may differ from the last rendered text
    * @param elementId the renderer-defined entry ID, such as a JSON Pointer
    * @return a UTF-16 source range, or null if the entry no longer exists; a zero length places the caret
    * @throws IOException if the current text cannot be parsed to locate the entry
    */
   default @Nullable IRegion resolveSourceLocation(@SuppressWarnings("unused") final String currentSourceText,
         @SuppressWarnings("unused") final String elementId) throws IOException {
      // Navigation is optional; existing renderers do not need to supply source locations.
      return null;
   }

   float getZoom();

   /**
    * @param level 1.0f means no zoom
    */
   void setZoom(float level);
}
