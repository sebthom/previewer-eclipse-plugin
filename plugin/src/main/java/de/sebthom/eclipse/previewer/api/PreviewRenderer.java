/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import java.io.IOException;

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
    * @return true if rendering of the given {@link ContentSource} is supported and rendering was performed.
    */
   boolean render(ContentSource source, boolean forceCacheUpdate) throws IOException;

   float getZoom();

   /**
    * @param level 1.0f means no zoom
    */
   void setZoom(float level);
}
