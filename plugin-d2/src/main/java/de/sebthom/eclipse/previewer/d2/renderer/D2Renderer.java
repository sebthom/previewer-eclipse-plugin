/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.d2.renderer;

import java.io.IOException;

import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * @author Sebastian Thomschke
 */
public interface D2Renderer {
   void d2ToHTML(ContentSource source, Appendable out) throws IOException;
}
