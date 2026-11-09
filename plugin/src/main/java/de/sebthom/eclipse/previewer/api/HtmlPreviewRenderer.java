/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import java.io.IOException;

import org.eclipse.ui.services.IDisposable;

/**
 * Specialized renderer that renders the given {@link ContentSource} to an HTML representation.
 *
 * @author Sebastian Thomschke
 */
public interface HtmlPreviewRenderer extends IDisposable {
   void renderToHtml(ContentSource source, Appendable out) throws IOException;

   /**
    * Releases the renderer's resources when the preview hosting it closes.
    * The hosting browser renderer calls this once on the SWT thread; HTML renderers own no controls.
    * A {@link #renderToHtml(ContentSource, Appendable)} call that is already running on a background thread may finish
    * concurrently; do not block waiting for it.
    */
   @Override
   void dispose();
}
