/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui.editorsupport;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.IEditorReference;

/**
 * SPI for wiring editor types to PreviewComposite via contexts.
 *
 * Implementations should be internal to this plugin and registered
 * manually in PreviewComposite's constructor, in precedence order.
 *
 * @author Sebastian Thomschke
 */
public interface EditorSupport {

   @Nullable
   TrackedEditorContext createFrom(IEditorReference editorRef);
}
