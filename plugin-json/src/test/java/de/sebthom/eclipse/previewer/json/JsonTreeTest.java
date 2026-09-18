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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.exc.StreamReadException;

/**
 * Verifies JSON fidelity, labels, pointer identity, source positions, and diagnostics independently of native preview controls.
 *
 * @author Sebastian Thomschke
 */
class JsonTreeTest {

   @Test
   void preservesPropertyOrderAndArrayPositions() throws IOException {
      final var model = JsonTree.parse("{\"z\":1,\"a\":[false,null,{},[]]}");
      assertEquals(List.of("z", "a"), model.root.children.stream().map(node -> node.name).toList());
      final var array = requireNonNull(model.find("/a"));
      assertEquals(List.of("[0]", "[1]", "[2]", "[3]"), array.children.stream().map(JsonTree.Node::label).toList());
      assertEquals(List.of("false", "null", "{}", "[]"), array.children.stream().map(JsonTree.Node::displayValue).toList());
      assertSame(array, requireNonNull(model.find("/a/0")).parent);
      assertSame(model.root, model.find(""));
   }

   @Test
   void showsOrdinaryKeysWithoutQuotesWhileKeepingJsonCopyQuoted() throws IOException {
      final var model = JsonTree.parse("{\"name\":1,\"first name\":2,\"a/b~c\":3,\"[0]\":4,\"$\":5,\"café\":6}");
      assertEquals(List.of("name", "first name", "a/b~c", "[0]", "$", "café"), //
         model.root.children.stream().map(JsonTree.Node::label).toList());
      assertEquals("$", model.root.label());
      assertTrue(model.root.serialize().contains("\"name\": 1"));
      assertEquals("/a~1b~0c", requireNonNull(model.find("/a~1b~0c")).pointer);
   }

   @ParameterizedTest
   @ValueSource(strings = {"\"\"", "\" \"", "\" leading\"", "\"trailing \"", "\"\u00a0key\"", "\"key\u00a0\"", "\"line\\nbreak\"",
      "\"tab\\tkey\"", "\"\\\\n\"", "\"\\\"quoted\\\"\""})
   void keepsAmbiguousKeysInEscapedJsonNotation(final String encodedName) throws IOException {
      final var model = JsonTree.parse("{" + encodedName + ":0}");
      assertEquals(encodedName, model.root.children.get(0).label());
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "true", "false", "17", "\"hello\"", "{}", "[]"})
   void supportsEveryRootValue(final String source) throws IOException {
      assertEquals(source, JsonTree.parse(source).root.serialize());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {"{\"one\":1}|Object (1 property)", "{\"one\":1,\"two\":2}|Object (2 properties)",
      "[1]|Array (1 item)", "[1,2]|Array (2 items)"})
   void identifiesGeneratedContainerSummaries(final String source, final String summary) throws IOException {
      final var node = JsonTree.parse(source).root;
      assertTrue(node.hasContainerSummary());
      assertEquals(summary, node.displayValue());
   }

   @ParameterizedTest
   @ValueSource(strings = {"{}", "[]", "null", "true", "1", "\"text\"", "\"Object (1 property)\"", "\"Array (2 items)\""})
   void keepsLiteralValuesDistinctFromGeneratedSummaries(final String source) throws IOException {
      final var node = JsonTree.parse(source).root;
      // A literal can resemble a summary; styling follows the node's structure, never its displayed text.
      assertFalse(node.hasContainerSummary());
      assertEquals(source, node.displayValue());
   }

   @ParameterizedTest
   @ValueSource(strings = {"-0", "9007199254740993", "123456789012345678901234567890", "1.2300", "1e+1000", "-2.50e-4"})
   void preservesNumberTextWithoutFloatingPointConversions(final String source) throws IOException {
      final var node = JsonTree.parse(source).root;
      assertEquals(source, node.displayValue());
      assertEquals(source, node.copyValue());
      assertEquals(source, node.serialize());
   }

   @Test
   void distinguishesRawStringCopyFromJsonCopy() throws IOException {
      final var node = JsonTree.parse("\"line\\n\\\"quoted\\\"\\tvalue\"").root;
      assertEquals("line\n\"quoted\"\tvalue", node.copyValue());
      assertEquals("\"line\\n\\\"quoted\\\"\\tvalue\"", node.serialize());
      assertEquals(node.serialize(), node.displayValue());
      assertEquals("", JsonTree.parse("\"\"").root.copyValue());
      assertEquals("\"null\"", JsonTree.parse("\"null\"").root.displayValue());
   }

   @Test
   void escapesPointersAndRetainsEqualValuesAsDifferentNodes() throws IOException {
      final var model = JsonTree.parse("{\"\":{\"a/b~1\":[1,1]},\"/~\":2}");
      assertEquals("\"\"", requireNonNull(model.find("/")).label());
      final var first = requireNonNull(model.find("//a~1b~01/0"));
      final var second = requireNonNull(model.find("//a~1b~01/1"));
      assertNotEquals(first, second);
      assertEquals(first.copyValue(), second.copyValue());
      assertEquals("2", requireNonNull(model.find("/~1~0")).copyValue());
      assertNull(model.find("/missing"));
   }

   @Test
   void rejectsDuplicateKeysWithoutSilentlyDroppingValues() {
      final var error = assertThrows(IOException.class, () -> JsonTree.parse("{\n\"key\":1,\n\"key\":2}"));
      final String message = requireNonNull(error.getMessage());
      assertTrue(message.contains("Duplicate Object property \"key\""));
      // Keep the renderer's checked exception contract while retaining Jackson's diagnostic and original source location.
      final var cause = assertInstanceOf(StreamReadException.class, error.getCause());
      final var location = requireNonNull(cause.getLocation());
      assertEquals(3, location.getLineNr());
      assertTrue(location.getColumnNr() > 0);
   }

   @ParameterizedTest
   // Extended syntax and unescaped string controls are accepted intentionally; unterminated strings must still fail.
   @ValueSource(strings = {"", " ", "{", "01", "1 2", "true trailing", "\"unterminated", "\"line\nbreak"})
   void rejectsMalformedInputWithLocation(final String source) {
      final var error = assertThrows(IOException.class, () -> JsonTree.parse(source));
      final String message = requireNonNull(error.getMessage());
      assertTrue(message.contains("line"), message);
      assertTrue(message.contains("column"), message);
   }

   @Test
   void boundsDepthBeforeRecursiveViewerOperations() throws IOException {
      final String valid = "[".repeat(128) + "0" + "]".repeat(128);
      assertNotNull(JsonTree.parse(valid));
      assertThrows(IOException.class, () -> JsonTree.parse("[" + valid + "]"));
   }

   @Test
   void preservesSubtreeCopyLayout() throws IOException {
      final var node = JsonTree.parse("{\"items\":[{},[],{\"value\":1}]}").root;
      final String expected = "{\n  \"items\": [\n    {},\n    [],\n    {\n      \"value\": 1\n    }\n  ]\n}";
      assertEquals(expected, node.serialize());
      // Pretty printers carry nesting state; repeated copying must not retain it.
      assertEquals(expected, node.serialize());
   }

   @Test
   void copiedSubtreeContainsOnlyTheSelectedValue() throws IOException {
      final var model = JsonTree.parse("{\"outside\":0,\"selected\":{\"string\":\"a\\nb\",\"number\":1.2300,\"nothing\":null}}");
      final var selected = requireNonNull(model.find("/selected"));
      final var copied = JsonTree.parse(selected.serialize());
      assertEquals(List.of("string", "number", "nothing"), copied.root.children.stream().map(node -> node.name).toList());
      assertEquals("1.2300", requireNonNull(copied.find("/number")).copyValue());
      assertEquals("a\nb", requireNonNull(copied.find("/string")).copyValue());
   }

   @ParameterizedTest
   @ValueSource(strings = {"\n", "\r\n"})
   void tracksPropertiesAndArrayItemsInFormattedJson(final String newline) throws IOException {
      final String source = newline + "{" + newline + "  \"items\" : [" + newline + "    \"first\"," + newline + "    {\"key\": false}"
            + newline + "  ]," + newline + "  \"empty\": {}" + newline + "}";
      final var model = JsonTree.parse(source);
      assertLocation(model, "", source.indexOf('{'), 0);
      assertLocation(model, "/items", source.indexOf("\"items\""), 7);
      assertLocation(model, "/items/0", source.indexOf("\"first\""), 0);
      assertLocation(model, "/items/1", source.indexOf("{\"key\""), 0);
      assertLocation(model, "/items/1/key", source.indexOf("\"key\""), 5);
      assertLocation(model, "/empty", source.indexOf("\"empty\""), 7);
   }

   @Test
   void keepsOffsetsInRawUtf16TextWithEscapesAndBom() throws IOException {
      final String key = "\"a\\\"/\\\\~\\u0062\"";
      final String source = "\uFEFF {\"emoji\":\"😀\", " + key + ": {\"\":[]}, \"after\":1}";
      final var model = JsonTree.parse(source);
      assertLocation(model, "", 2, 0);
      final var escaped = model.root.children.get(1);
      assertEquals("a\"/\\~b", escaped.name);
      // Select the original token, including quotes and escapes; decoded names have a different length.
      assertEquals(key, source.substring(escaped.sourceOffset, escaped.sourceOffset + escaped.sourceLength));
      assertLocation(model, escaped.pointer + "/", source.indexOf("\"\""), 2);
      assertLocation(model, "/after", source.indexOf("\"after\""), 7);
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "true", "false", "-1.20e-3", "\"escaped\\\"[,]\\\\\"", "{}", "[]", "{\"key\":[1]}", "[[[],{}]]"})
   void locatesEveryValueTypeAfterAdjacentTokens(final String value) throws IOException {
      assertLocation(JsonTree.parse(" \t" + value + "\r\n"), "", 2, 0);
      final var array = JsonTree.parse("[" + value + "," + value + "]");
      assertLocation(array, "/0", 1, 0);
      assertLocation(array, "/1", value.length() + 2, 0);
   }

   @Test
   void tracksPositionsAcrossParserBufferBoundaries() throws IOException {
      final String source = "{\"padding\":\"" + "x".repeat(4096) + "\",\"target\":[{},true]}";
      final var model = JsonTree.parse(source);
      assertLocation(model, "/target", source.indexOf("\"target\""), 8);
      assertLocation(model, "/target/0", source.indexOf("{}"), 0);
      assertLocation(model, "/target/1", source.indexOf("true"), 0);
   }

   private static void assertLocation(final JsonTree model, final String pointer, final int offset, final int length) {
      final var node = requireNonNull(model.find(pointer));
      assertEquals(offset, node.sourceOffset, pointer);
      assertEquals(length, node.sourceLength, pointer);
   }
}
