/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util.cache;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.api.ContentSource;
import net.sf.jstuff.core.functional.ThrowingFunction;

/**
 * @author Sebastian Thomschke
 */
public interface RenderCache {

   @Nullable
   Path computeIfAbsent(ContentSource source, ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer)
         throws IOException;

   @Nullable
   Path replace(ContentSource source, ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> renderer) throws IOException;

   @Nullable
   Path get(ContentSource source);

   Path put(ContentSource source, CharSequence renderedContent) throws IOException;
}
