/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: CHOOSLA
 * SPDX-FileContributor: Sebastian Thomschke
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
 * Checks HTTP(S) link routing and which targets remain with native browser navigation.
 *
 * @author CHOOSLA
 * @author Sebastian Thomschke
 */
class LinkNavigationTest {

   @Test
   void opensWebLinksExternallyAndCancelsEmbeddedNavigation() {
      final var opened = new ArrayList<URI>();
      for (final String scheme : List.of("http", "https", "HTTP", "HTTPS")) {
         final URI target = URI.create(scheme + "://example.org/problem?id=42#description");
         assertTrue(LinkNavigation.handle(target, null, true, opened::add));
         assertEquals(target, opened.get(opened.size() - 1));
      }
      assertEquals(4, opened.size());
   }

   @Test
   void retainsEmbeddedWebNavigationWhenThePreferenceIsDisabled() {
      for (final String scheme : List.of("http", "https")) {
         final URI target = URI.create(scheme + "://example.org/problem");
         assertFalse(LinkNavigation.handle(target, null, false, uri -> fail("External browser must not open")));
      }
   }

   @Test
   void leavesNonWebTargetsToNativeNavigationWithEitherPreferenceValue() {
      for (final boolean enabled : List.of(false, true)) {
         for (final String link : List.of( //
            "file:///workspace/README.md", //
            "file:///workspace/README.md#section", //
            "#section", //
            "../README.md", "about:blank", //
            "mailto:user@example.org", //
            "data:text/html,preview", //
            "javascript:void(0)" //
         )) {
            final URI target = URI.create(link);
            // Local-file opening belongs to the native navigation listener.
            assertFalse(LinkNavigation.handle(target, null, enabled, uri -> fail("Not a web link")));
         }
      }
   }

   @Test
   void retainsWebAnchorsAfterEnablingExternalLinks() {
      final URI currentDocument = URI.create("https://example.org/a%2Fb?query=%2F#previous");
      for (final boolean enabled : List.of(false, true)) {
         for (final String fragment : List.of("#section", "#", "")) {
            final URI target = URI.create("https://example.org/a%2Fb?query=%2F" + fragment);
            final var opened = new ArrayList<URI>();
            // SWT reports an absolute HTTP(S) URL even for a fragment-only link in an already open web page.
            assertFalse(LinkNavigation.handle(target, currentDocument, enabled, opened::add));
            assertTrue(opened.isEmpty());
         }
      }
   }

   @Test
   void distinguishesWebDocumentsByQueryAndEscapedPath() {
      final URI currentDocument = URI.create("https://example.org/a%2Fb?query=%2F");
      for (final String link : List.of( //
         "https://example.org/a/b?query=%2F#section", //
         "https://example.org/a%2Fb?query=/#section", //
         "https://example.org/a%2Fb?query=other#section" //
      )) {
         final URI target = URI.create(link);
         final var opened = new ArrayList<URI>();
         assertTrue(LinkNavigation.handle(target, currentDocument, true, opened::add));
         assertEquals(List.of(target), opened);
      }
   }
}
