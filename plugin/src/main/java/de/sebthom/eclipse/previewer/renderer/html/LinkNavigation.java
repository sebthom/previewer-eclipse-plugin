/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: CHOOSLA
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import java.net.URI;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Applies the external-browser preference and same-document exception to HTTP(S) link activations.
 *
 * @author CHOOSLA
 */
final class LinkNavigation {

   /** @return true if the embedded browser navigation should be cancelled */
   static boolean handle(final URI target, final @Nullable URI currentDocument, final boolean openWebLinksExternally,
         final Consumer<URI> externalWebLinkHandler) {
      final String scheme = target.getScheme();
      if (openWebLinksExternally && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
         // Browser events contain absolute URLs for anchors. Replace only the fragment to retain escaped paths and queries.
         if (currentDocument != null && !target.isOpaque() && target.resolve("#").equals(currentDocument.resolve("#")))
            return false;
         externalWebLinkHandler.accept(target);
         return true;
      }
      return false;
   }

   private LinkNavigation() {
   }
}
