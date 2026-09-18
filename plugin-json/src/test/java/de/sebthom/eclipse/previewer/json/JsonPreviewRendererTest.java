/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.eclipse.jface.text.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies JSON source navigation through the shared renderer without initializing SWT or the workbench.
 * Navigation IDs prefix the JSON Pointer with column 0 for keys/indices or column 1 for values.
 *
 * @author Sebastian Thomschke
 */
class JsonPreviewRendererTest {

   @ParameterizedTest
   @ValueSource(strings = {"null", "true", "false", "-1.20e-3", "+0x007f", "+.5", "NaN", "Infinity", "\"\"", "\"escaped\\\"[,]\\\\\"",
      "'single\\'quoted'", "\"line\\\r\ncontinued\"", "{}", "[]", "{/* inside */ key:[1,],}", "[{},true,]"})
   void selectsOriginalValueTextInEveryPosition(final String value) throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         final String prefix = "\uFEFF/* before */ ";
         final String suffix = " /* after */ ";
         assertEquals(new Region(prefix.length(), value.length()), renderer.resolveSourceLocation(prefix + value + suffix, "1:"));
         final String propertyPrefix = prefix + "{emoji:'😀', key: /* value */ ";
         assertEquals(new Region(propertyPrefix.length(), value.length()), renderer.resolveSourceLocation(propertyPrefix + value + suffix
               + "}", "1:/key"));
         final String arrayPrefix = prefix + "[" + value + ", ";
         // Identical values must still select the clicked occurrence, excluding surrounding comments and separators.
         assertEquals(new Region(arrayPrefix.length(), value.length()), renderer.resolveSourceLocation(arrayPrefix + value + suffix + "]",
            "1:/1"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void relocatesValueAfterUnsavedChangesToItsPositionAndType() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         assertEquals(new Region(7, 1), renderer.resolveSourceLocation("{\"key\":1}", "1:/key"));
         final String value = "{ /* nested */ child: ['new', +0xFF,], }";
         final String edited = "// header\r\n{padding:'" + "x".repeat(4096) + "', key: " + value + ", after:true}";
         assertEquals(new Region(edited.indexOf(value), value.length()), renderer.resolveSourceLocation(edited, "1:/key"));
         assertNull(renderer.resolveSourceLocation("{}", "1:/key"));
         assertThrows(IOException.class, () -> renderer.resolveSourceLocation("{", "1:/key"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void keepsCellIdentitySeparateFromEscapedPropertyNames() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         final String source = "{'1:a/b~':{'':42}}";
         assertEquals(new Region(source.indexOf("42"), 2), renderer.resolveSourceLocation(source, "1:/1:a~1b~0/"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void relocatesPropertyInCurrentUnsavedText() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         assertEquals(new Region(1, 5), renderer.resolveSourceLocation("{\"key\":1}", "0:/key"));
         final String edited = "{\n\"prefix\":true,\n\"key\":2}";
         assertEquals(new Region(edited.indexOf("\"key\""), 5), renderer.resolveSourceLocation(edited, "0:/key"));
         assertNull(renderer.resolveSourceLocation("{}", "0:/key"));
         assertThrows(IOException.class, () -> renderer.resolveSourceLocation("{", "0:/key"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void relocatesPropertyAfterChangingCommentsAndQuoting() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         assertEquals(new Region(1, 5), renderer.resolveSourceLocation("{\"key\":1}", "0:/key"));
         final String edited = "\uFEFF// unsaved header\n{emoji:'😀', /* before */ key /* after */ :+.5,}";
         assertEquals(new Region(edited.indexOf("key"), 3), renderer.resolveSourceLocation(edited, "0:/key"));
         final String quoted = "{/* comment */ 'key': [1,],}";
         assertEquals(new Region(quoted.indexOf("'key'"), 5), renderer.resolveSourceLocation(quoted, "0:/key"));
         assertNull(renderer.resolveSourceLocation("{/* removed */}", "0:/key"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void relocatesEntriesAfterEditingHexadecimalValues() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         assertEquals(new Region(1, 5), renderer.resolveSourceLocation("{\"key\":1}", "0:/key"));
         final String edited = "\uFEFF// unsaved header\r\n{emoji:'😀', prefix:0xFF, key:[+0x007f,],}";
         assertEquals(new Region(edited.indexOf("key"), 3), renderer.resolveSourceLocation(edited, "0:/key"));
         assertEquals(new Region(edited.indexOf("+0x007f"), 0), renderer.resolveSourceLocation(edited, "0:/key/0"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void relocatesEntriesAfterAddingAContinuedString() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         assertEquals(new Region(1, 5), renderer.resolveSourceLocation("{\"key\":1}", "0:/key"));
         final String edited = "\uFEFF{emoji:'😀', lineBreak:\"This is a line \\\r\n  break\", key:[0xFF]}";
         assertEquals(new Region(edited.indexOf("lineBreak"), 9), renderer.resolveSourceLocation(edited, "0:/lineBreak"));
         assertEquals(new Region(edited.indexOf("key"), 3), renderer.resolveSourceLocation(edited, "0:/key"));
         assertEquals(new Region(edited.indexOf("0xFF"), 0), renderer.resolveSourceLocation(edited, "0:/key/0"));
      } finally {
         renderer.dispose();
      }
   }

   @Test
   void preservesRootAndArrayPositionSemantics() throws IOException {
      final var renderer = new JsonPreviewRenderer();
      try {
         final String edited = "  [false, {\"key\":2}]";
         assertEquals(new Region(2, 0), renderer.resolveSourceLocation(edited, "0:"));
         // Array IDs intentionally follow the current index, rather than tracking the object moved by an insertion.
         assertEquals(new Region(edited.indexOf("false"), 0), renderer.resolveSourceLocation(edited, "0:/0"));
         assertEquals(new Region(edited.indexOf('{'), 0), renderer.resolveSourceLocation(edited, "0:/1"));
      } finally {
         renderer.dispose();
      }
   }
}
