/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.api;

import java.nio.file.Path;

/**
 * Navigates from a preview entry to its current location in the source editor.
 * Each navigator is bound to the renderer receiving it through {@link PreviewRenderer#setSourceNavigator(SourceNavigator)}.
 *
 * @author Sebastian Thomschke
 */
@FunctionalInterface
public interface SourceNavigator {

   /**
    * Requests navigation on the SWT thread. For example, a renderer can call
    * {@code navigator.navigateToSource(source.path(), "section-2")} from its double-click handler.
    * The host calls the renderer's {@link PreviewRenderer#resolveSourceLocation(String, String)} in the background with current,
    * possibly unsaved text, then reveals the result if the document is still unchanged.
    *
    * @param source the rendered source identity, as returned by {@link ContentSource#path()}
    * @param elementId a renderer-defined entry ID, passed unchanged to that renderer's location resolver
    */
   void navigateToSource(Path source, String elementId);
}
