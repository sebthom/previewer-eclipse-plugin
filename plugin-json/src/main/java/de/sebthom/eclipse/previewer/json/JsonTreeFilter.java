/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.dialogs.PatternFilter;

/**
 * Matches JSON keys and full scalar values while retaining the ancestors of matching nodes.
 *
 * @author Sebastian Thomschke
 */
final class JsonTreeFilter extends PatternFilter {

   JsonTreeFilter() {
      setIncludeLeadingWildcard(true);
   }

   // Inherit parent matching so JFace caches descendant visibility, including children of collapsed rows.
   @Override
   protected boolean isLeafMatch(final Viewer viewer, final @Nullable Object element) {
      if (!(element instanceof final JsonTree.Node node))
         return false;
      // Search the unabridged model, independently of column labels, string quoting, and display truncation.
      return node.parent != null && wordMatches(node.name) || wordMatches(node.scalarValue());
   }
}
