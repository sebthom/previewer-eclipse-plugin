/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: CHOOSLA
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Checks external web link routing and preservation of existing preview navigation.
 *
 * @author CHOOSLA
 */
class LinkNavigationTest {

   @Test
   void opensWebLinksExternallyAndCancelsEmbeddedNavigation() {
      final var opened = new ArrayList<URI>();
      for (final String scheme : List.of("http", "https", "HTTP", "HTTPS")) {
         final URI target = URI.create(scheme + "://example.org/problem?id=42#description");
         assertTrue(LinkNavigation.handle(target, true, opened::add, uri -> {
            fail("Web links must not reach the local file handler");
            return false;
         }));
         assertEquals(target, opened.get(opened.size() - 1));
      }
      assertEquals(4, opened.size());
   }

   @Test
   void retainsEmbeddedWebNavigationWhenThePreferenceIsDisabled() {
      for (final String scheme : List.of("http", "https")) {
         final URI target = URI.create(scheme + "://example.org/problem");
         assertFalse(LinkNavigation.handle(target, false, uri -> fail("External browser must not open"), uri -> false));
      }
   }

   @Test
   void preservesLocalFileAndAnchorHandlingWithEitherPreferenceValue() {
      for (final boolean enabled : List.of(false, true)) {
         for (final String link : List.of("file:///workspace/README.md", "file:///workspace/README.md#section", "#section", "../README.md",
            "about:blank", "mailto:user@example.org", "data:text/html,preview", "javascript:void(0)")) {
            final URI target = URI.create(link);
            for (final boolean localHandled : List.of(false, true)) {
               final var localTargets = new ArrayList<URI>();
               assertEquals(localHandled, LinkNavigation.handle(target, enabled, uri -> fail("Not a web link"), uri -> {
                  localTargets.add(uri);
                  return localHandled;
               }));
               assertEquals(List.of(target), localTargets);
            }
         }
      }
   }
}
