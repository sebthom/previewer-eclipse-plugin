/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.renderer;

import java.io.IOException;

import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * @author Sebastian Thomschke
 */
public interface MarkdownRenderer {
   void markdownToHTML(ContentSource source, Appendable out) throws IOException;
}
