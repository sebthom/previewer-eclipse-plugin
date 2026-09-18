/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies extended JSON syntax without losing numeric spelling, subtree values or original editor positions.
 *
 * @author Sebastian Thomschke
 */
class JsonTreeExtensionsTest {

   @Test
   void acceptsCommentsAndTrailingCommasWithoutChangingStrings() throws IOException {
      final String source = "// header\n{\"url\":\"https://example.org/*literal*/\",/* between */"
            + "\"items\":[1,2,/* last */],\"text\":\"}, // literal\",// last property\n}\n// footer";
      final var model = JsonTree.parse(source);
      assertEquals("https://example.org/*literal*/", requireNonNull(model.find("/url")).copyValue());
      assertEquals("}, // literal", requireNonNull(model.find("/text")).copyValue());
      assertEquals(2, requireNonNull(model.find("/items")).children.size());
      assertEquals(3, model.root.children.size());
   }

   @Test
   void acceptsSingleQuotedStringsAndUnquotedNames() throws IOException {
      final var model = JsonTree.parse("{plain:'value', $key:'it\\'s', café:'line\\nnext', 'a/b~c':true,}");
      assertEquals(List.of("plain", "$key", "café", "a/b~c"), model.root.children.stream().map(node -> node.name).toList());
      assertEquals("it's", requireNonNull(model.find("/$key")).copyValue());
      assertEquals("line\nnext", requireNonNull(model.find("/café")).copyValue());
      assertEquals("true", requireNonNull(model.find("/a~1b~0c")).copyValue());
   }

   @ParameterizedTest
   @ValueSource(strings = {"\n", "\r\n"})
   void acceptsContinuedStringsWithBestEffortValues(final String newline) throws IOException {
      for (final char quote : new char[] {'\'', '"'}) {
         final var node = JsonTree.parse(quote + "This is a line \\" + newline + "  break" + quote).root;
         // Jackson retains the line ending and indentation instead of applying JSON5's continuation semantics.
         final String expected = "This is a line " + newline + "  break";
         assertEquals(expected, node.copyValue());
         final String escaped = newline.replace("\r", "\\r").replace("\n", "\\n");
         assertEquals("\"This is a line " + escaped + "  break\"", node.displayValue());
         assertEquals(node.displayValue(), node.serialize());
         assertEquals(expected, JsonTree.parse(node.serialize()).root.copyValue());
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {"\n", "\r\n", "\t"})
   void acceptsUnescapedStringControlsAsPartOfBestEffortParsing(final String control) throws IOException {
      // The option needed for CRLF continuations also deliberately permits controls without a preceding backslash.
      final String value = "line" + control + "break";
      for (final char quote : new char[] {'\'', '"'}) {
         final var node = JsonTree.parse(quote + value + quote).root;
         assertEquals(value, node.copyValue());
         assertEquals(value, JsonTree.parse(node.serialize()).root.copyValue());
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {"+1", "+0", ".5", "-.5", "+.5", "1.", "1.e+2", "+1.2300e-4", "NaN", "Infinity", "-Infinity", "+Infinity", "0x0",
      "0xdecaf", "0XDECAF", "+0xFF", "-0XfF", "0x007f", "0x1234567890abcdef1234567890abcdef"})
   void preservesExtendedNumberSpellingWhenDisplayingAndCopying(final String number) throws IOException {
      final var root = JsonTree.parse(" /* before */ " + number + " \n// after").root;
      assertEquals(number, root.displayValue());
      assertEquals(number, root.copyValue());
      assertEquals(number, root.serialize());

      final var model = JsonTree.parse("{number:" + number + "/* after */, items:[" + number + ",],}");
      assertEquals(number, requireNonNull(model.find("/number")).copyValue());
      assertEquals(number, requireNonNull(model.find("/items/0")).copyValue());
      final var copied = JsonTree.parse(model.root.serialize());
      assertEquals(number, requireNonNull(copied.find("/number")).copyValue());
      assertEquals(number, requireNonNull(copied.find("/items/0")).copyValue());
   }

   @Test
   void distinguishesHexadecimalNumbersFromStringsKeysAndComments() throws IOException {
      final String source = "{// 0xG is harmless in a comment\n '0xFF':'0xFF', text:\"\\\"0x20\\\" // 0x30\","
            + " hex:0xdecaf/*0xinvalid*/, items:[-0XfF,//+0xG\n+0x007f,],}";
      final var model = JsonTree.parse(source);
      assertEquals("0xFF", requireNonNull(model.find("/0xFF")).copyValue());
      assertEquals("\"0x20\" // 0x30", requireNonNull(model.find("/text")).copyValue());
      assertEquals("0xdecaf", requireNonNull(model.find("/hex")).copyValue());
      assertEquals("-0XfF", requireNonNull(model.find("/items/0")).copyValue());
      assertEquals("+0x007f", requireNonNull(model.find("/items/1")).copyValue());
      assertTrue(model.root.serialize().contains("\"hex\": 0xdecaf"));
   }

   @ParameterizedTest
   @ValueSource(strings = {"\n", "\r\n"})
   void locatesOriginalKeysAndValuesAcrossComments(final String newline) throws IOException {
      final String key = "'a\\'/~\\u0062'";
      final String source = "\uFEFF// header" + newline + "{emoji:'😀', " + key + "/* : fake */ : [],"
            + " plain /* : fake */ : [/* value */ +.5, {nested:false,}, -0xFF/* after */], after:0x1,}";
      final var model = JsonTree.parse(source);
      final var escaped = requireNonNull(model.find("/a'~1~0b"));
      assertEquals(key, source.substring(escaped.sourceOffset, escaped.sourceOffset + escaped.sourceLength));
      final var plain = requireNonNull(model.find("/plain"));
      assertEquals("plain", source.substring(plain.sourceOffset, plain.sourceOffset + plain.sourceLength));
      assertEquals(source.indexOf("+.5"), requireNonNull(model.find("/plain/0")).sourceOffset);
      assertEquals(0, requireNonNull(model.find("/plain/0")).sourceLength);
      assertEquals(source.indexOf("nested"), requireNonNull(model.find("/plain/1/nested")).sourceOffset);
      assertEquals(source.indexOf("-0xFF"), requireNonNull(model.find("/plain/2")).sourceOffset);
      assertEquals(source.indexOf("after:0x1"), requireNonNull(model.find("/after")).sourceOffset);
      assertEquals(source.indexOf('{'), model.root.sourceOffset);
   }

   @Test
   void locatesKeysAcrossParserBufferBoundaries() throws IOException {
      final String key = "'" + "x".repeat(8192) + "\\'end'";
      final String source = "{/*" + "padding".repeat(2048) + "*/" + key + ":+1, after:2}";
      final var model = JsonTree.parse(source);
      final var first = model.root.children.get(0);
      assertEquals(key, source.substring(first.sourceOffset, first.sourceOffset + first.sourceLength));
      assertEquals("+1", first.copyValue());
      assertEquals(source.indexOf("after"), requireNonNull(model.find("/after")).sourceOffset);
   }

   @ParameterizedTest
   @ValueSource(strings = {"{a:1,'a':2}", "{\"a\":1,'\\u0061':2}"})
   void rejectsDuplicateDecodedNamesAcrossQuotingStyles(final String source) {
      final var error = assertThrows(IOException.class, () -> JsonTree.parse(source));
      assertTrue(requireNonNull(error.getMessage()).contains("Duplicate"));
   }

   @ParameterizedTest
   @ValueSource(strings = {"[1,,2]", "[,1]", "# comment\n1", "01", "'line\nbreak", "1 /* comment */ 2", "{/* unterminated", "0x", "0xG",
      "0xFFoops", "0x1.2"})
   void stillRejectsMalformedOrUnsupportedInput(final String source) {
      assertThrows(IOException.class, () -> JsonTree.parse(source));
   }
}
