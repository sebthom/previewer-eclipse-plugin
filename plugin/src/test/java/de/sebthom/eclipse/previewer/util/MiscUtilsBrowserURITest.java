/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link MiscUtils#toBrowserURI} must accept locations as browsers serialize them without changing what they address.
 *
 * @author Sebastian Thomschke
 */
class MiscUtilsBrowserURITest {

   @ParameterizedTest
   @ValueSource(strings = { //
      "https://example.org/a%2Fb?q=%7C&r=[x]#[y]", //
      "file:///C:/workspace/README.md", //
      "about:blank" //
   })
   void keepsLocationsThatParseStrictly(final String location) {
      assertEquals(URI.create(location), MiscUtils.toBrowserURI(location));
   }

   @ParameterizedTest
   @CsvSource(delimiter = ' ', value = { //
      "https://example.org/search?q=a|b&path=%2F https://example.org/search?q=a%7Cb&path=%2F", //
      "https://example.org/a|b/%2F?q=[x]|#[y] https://example.org/a%7Cb/%2F?q=[x]%7C#[y]", //
      "https://example.org/p[x]?q=| https://example.org/p%5Bx%5D?q=%7C", //
      "https://example.org/100%?q=50%&r=%41 https://example.org/100%25?q=50%25&r=%41", //
      "https://example.org/a#x#y|z https://example.org/a#x%23y%7Cz", //
      "https://example.org/a?q={^}`\\ https://example.org/a?q=%7B%5E%7D%60%5C", //
      "http://user@[fe80::1%25eth0]:8080/?q=a|b http://user@[fe80::1%25eth0]:8080/?q=a%7Cb" //
   })
   void encodesOnlyCharactersThatJavaRejects(final String location, final String expected) {
      assertEquals(URI.create(expected), MiscUtils.toBrowserURI(location));
   }

   @Test
   void encodesNonAsciiCharactersAsUtf8OnceRepairIsNeeded() {
      // Strict parsing accepts non-ASCII text. The repair encodes it as well, including characters outside the BMP.
      final String text = Character.toString(0xE4) + Character.toString(0x1_F600);
      assertEquals(URI.create("https://example.org/a?q=%C3%A4%F0%9F%98%80%7C"), MiscUtils.toBrowserURI("https://example.org/a?q=" + text
            + "|"));
   }

   @Test
   void keepsTheScheme() {
      // Scheme checks run on the result, so the repair must not turn a non-web URL into a web URL.
      final URI mailto = asNonNull(MiscUtils.toBrowserURI("mailto:a|b@example.org"));
      assertEquals("mailto", mailto.getScheme());
   }

   @ParameterizedTest
   @ValueSource(strings = {"", " ", "a|b", ":a|b", "https://exa|mple.org/"})
   void rejectsLocationsWithoutSchemeOrWithInvalidAuthority(final String location) {
      assertNull(MiscUtils.toBrowserURI(location));
   }

   @Test
   void rejectsNull() {
      assertNull(MiscUtils.toBrowserURI(null));
   }
}
