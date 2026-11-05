/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: CHOOSLA
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Routes opted-in web links to an external browser while retaining local preview navigation.
 *
 * @author CHOOSLA
 */
final class LinkNavigation {

   /** @return true if the embedded browser navigation should be cancelled */
   static boolean handle(final URI target, final boolean openWebLinksExternally, final Consumer<URI> externalWebLinkHandler,
         final Predicate<URI> localFileLinkHandler) {
      final String scheme = target.getScheme();
      if (openWebLinksExternally && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
         externalWebLinkHandler.accept(target);
         return true;
      }
      return localFileLinkHandler.test(target);
   }

   private LinkNavigation() {
   }
}
