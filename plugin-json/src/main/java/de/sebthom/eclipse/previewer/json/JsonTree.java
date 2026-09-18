/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.json;

import static java.util.Objects.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.JsonStringEncoder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;

/**
 * Parses JSON, JSONC and best-effort JSON5 into ordered preview nodes, retaining number text, source positions and JSON Pointers.
 * The model is independent of SWT and is only mutated while parsing.
 *
 * @author Sebastian Thomschke
 */
final class JsonTree {

   // Use the same permissive grammar for every extension, including commented .json files and unsaved source relocation.
   // The factory is never reconfigured; each parse gets its own parser because rendering and relocation may run concurrently.
   private static final JsonFactory JSON = JsonFactory.builder()
      // Filtering and subtree copying also recurse, so reject excessive nesting before publishing a UI model.
      .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(128).build())
      // Duplicate names would make JSON Pointers and restored selections ambiguous.
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION) //
      .enable( //
         JsonReadFeature.ALLOW_JAVA_COMMENTS, //
         JsonReadFeature.ALLOW_TRAILING_COMMA, //
         JsonReadFeature.ALLOW_SINGLE_QUOTES, //
         JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, //
         JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS, //
         JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS, //
         JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS, //
         JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, //
         JsonReadFeature.ALLOW_HEXADECIMAL_NUMBERS)
      // NOTE: Accept CRLF continuations for best-effort JSON5: Jackson treats CR as escaped but LF as unescaped.
      // This also accepts raw controls in strings and retains line endings instead of joining the continued lines.
      // Hex escapes remain unsupported. Revisit these options if full JSON5 decoding becomes a requirement.
      .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();

   private static final ObjectWriteContext COPY_CONTEXT = new ObjectWriteContext.Base() {
      @Override
      public DefaultPrettyPrinter getPrettyPrinter() {
         // Each generator needs its own stateful printer so nesting cannot leak between copies.
         // Preserve the existing layout, including compact empty containers, independently of the host OS.
         final var separators = Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER)
            .withObjectEmptySeparator("").withArrayEmptySeparator("");
         final var indenter = new DefaultIndenter("  ", "\n");
         return new DefaultPrettyPrinter(separators).withObjectIndenter(indenter).withArrayIndenter(indenter);
      }
   };

   /**
    * A document position. Identity equality keeps equal values in different branches distinct in JFace's element map.
    */
   static final class Node {
      final @Nullable Node parent;
      final String name;
      final String pointer;
      final JsonToken type;
      final int sourceOffset;
      final int sourceLength;
      final int valueOffset;
      int valueLength;
      final List<Node> children = new ArrayList<>();
      private String value = "";

      private Node(final @Nullable Node parent, final String name, final String pointer, final JsonToken type, final int sourceOffset,
            final int sourceLength, final int valueOffset) {
         this.parent = parent;
         this.name = name;
         this.pointer = pointer;
         this.type = type;
         this.sourceOffset = sourceOffset;
         this.sourceLength = sourceLength;
         this.valueOffset = valueOffset;
      }

      String label() {
         final Node owner = parent;
         if (owner == null)
            return "$";
         if (owner.type == JsonToken.START_ARRAY)
            return "[" + name + "]";

         // Ordinary keys need no quotes. Keep JSON notation when it reveals empty keys or significant edge whitespace.
         final String quoted = quote(name);
         if (name.isEmpty())
            return quoted;
         final int first = name.codePointAt(0);
         final int last = name.codePointBefore(name.length());
         // Escapes add length beyond the two delimiter quotes, keeping quotes, backslashes, and controls unambiguous.
         // isSpaceChar also covers non-breaking spaces, which isWhitespace deliberately excludes.
         return quoted.length() != name.length() + 2 || Character.isWhitespace(first) || Character.isSpaceChar(first) || Character
            .isWhitespace(last) || Character.isSpaceChar(last) ? quoted : name;
      }

      boolean hasContainerSummary() {
         // Only containers have children; empty containers display literal JSON and keep the normal value styling.
         return !children.isEmpty();
      }

      String displayValue() {
         return switch (type) {
            case START_OBJECT -> hasContainerSummary() //
                  ? "Object (" + children.size() + (children.size() == 1 ? " property)" : " properties)")
                  : "{}";
            case START_ARRAY -> hasContainerSummary() //
                  ? "Array (" + children.size() + (children.size() == 1 ? " item)" : " items)")
                  : "[]";
            case VALUE_STRING -> quote(value);
            default -> value;
         };
      }

      String scalarValue() {
         return value;
      }

      String copyValue() {
         return type == JsonToken.START_OBJECT || type == JsonToken.START_ARRAY ? serialize() : value;
      }

      String serialize() {
         final var out = new StringWriter();
         try (var writer = JSON.createGenerator(COPY_CONTEXT, out)) {
            write(writer);
         }
         return out.toString();
      }

      private void write(final JsonGenerator writer) {
         switch (type) {
            case START_OBJECT -> {
               writer.writeStartObject();
               for (final var child : children) {
                  writer.writeName(child.name);
                  child.write(writer);
               }
               writer.writeEndObject();
            }
            case START_ARRAY -> {
               writer.writeStartArray();
               for (final var child : children) {
                  child.write(writer);
               }
               writer.writeEndArray();
            }
            case VALUE_STRING -> writer.writeString(value);
            // Only parser-validated literals reach this branch. Raw output preserves precision, hex, + signs and non-finite numbers
            // without turning them into quoted strings; copied subtrees can therefore contain extended JSON syntax.
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> writer.writeRawValue(value);
            default -> throw new IllegalStateException("Unexpected JSON node type: " + type);
         }
      }
   }

   final Node root;
   private final Map<String, Node> nodes;

   private JsonTree(final Node root, final Map<String, Node> nodes) {
      this.root = root;
      this.nodes = nodes;
   }

   private static String quote(final String value) {
      final var quoted = new StringBuilder(value.length() + 2).append('"');
      JsonStringEncoder.getInstance().quoteAsString(value, quoted);
      return quoted.append('"').toString();
   }

   /**
    * Finds the end of a parser-validated key or scalar in the original text; Jackson remains responsible for syntax validation.
    */
   private static int tokenEnd(final String content, final int start) {
      int position = start;
      final char quote = content.charAt(position);
      if (quote == '"' || quote == '\'') {
         position++;
         while (position < content.length()) {
            final char character = content.charAt(position++);
            if (character == '\\') {
               // An escaped delimiter belongs to the token, rather than ending its original quoted span.
               position++;
            } else if (character == quote) {
               break;
            }
         }
      } else {
         // A comment may directly follow a number or unquoted key; it must never become part of the selected/copied token.
         while (position < content.length() && " \t\r\n,:{}[]/".indexOf(content.charAt(position)) < 0) {
            position++;
         }
      }
      return position;
   }

   @Nullable
   Node find(final String pointer) {
      return nodes.get(pointer);
   }

   static JsonTree parse(final String content) throws IOException {
      // Character input keeps Jackson offsets in UTF-16, matching the editor. Its string parser does not consume a BOM;
      // skip only the initial BOM and add it back to every source offset.
      final int sourceStart = content.startsWith("\uFEFF") ? 1 : 0;
      try (var reader = JSON.createParser(ObjectReadContext.empty(), content.substring(sourceStart))) {
         final var nodes = new HashMap<String, Node>();
         reader.nextToken();
         // An explicit root row makes scalar documents selectable too; the document's JSON Pointer is the empty string.
         final Node root = read(reader, content, null, "", "", nodes, -1);
         // A valid first value must not hide a second value or trailing garbage in the editor.
         if (reader.nextToken() != null)
            throw new StreamReadException(reader, "Expected end of document");
         return new JsonTree(root, nodes);
      } catch (final JacksonException ex) {
         final var location = ex.getLocation();
         // NOTE: Jackson 3's redacted location text can omit line/column despite retaining both values.
         // Format them explicitly until its diagnostics preserve text positions without including source content.
         final String position = location == null || location.getLineNr() < 1 ? ""
               : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
         // Jackson 3 reports unchecked failures; the shared tree renderer expects IOException for invalid-document messages.
         throw new IOException(ex.getOriginalMessage() + position, ex);
      }
   }

   private static int sourceOffset(final JsonParser reader, final String content) {
      return Math.toIntExact(reader.currentTokenLocation().getCharOffset()) + (content.startsWith("\uFEFF") ? 1 : 0);
   }

   private static Node read(final JsonParser reader, final String content, final @Nullable Node parent, final String name,
         final String pointer, final Map<String, Node> nodes, final int keyOffset) {
      final var type = reader.currentToken();
      if (type == null)
         throw new StreamReadException(reader, "Expected a JSON value");
      final int valueOffset = sourceOffset(reader, content);
      // Jackson may prefetch the value while reporting a field name, so its current position cannot delimit the raw key.
      final int keyLength = keyOffset < 0 ? 0 : tokenEnd(content, keyOffset) - keyOffset;
      // The key/index column selects the original key; array indices and the root have no key token, so they place the caret.
      final var node = new Node(parent, name, pointer, type, keyOffset < 0 ? valueOffset : keyOffset, keyLength, valueOffset);
      nodes.put(pointer, node);
      switch (type) {
         case START_OBJECT -> {
            while (reader.nextToken() != JsonToken.END_OBJECT) {
               final String childName = requireNonNull(reader.currentName());
               final int childOffset = sourceOffset(reader, content);
               reader.nextToken();
               // RFC 6901 requires escaping '~' first, including when a property name already contains '~1'.
               final String childPointer = pointer + "/" + childName.replace("~", "~0").replace("/", "~1");
               node.children.add(read(reader, content, node, childName, childPointer, nodes, childOffset));
            }
         }
         case START_ARRAY -> {
            while (reader.nextToken() != JsonToken.END_ARRAY) {
               final String index = Integer.toString(node.children.size());
               node.children.add(read(reader, content, node, index, pointer + "/" + index, nodes, -1));
            }
         }
         // Jackson's numeric text can omit a leading '+'. Preserve the validated source spelling rather than normalizing it.
         case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> node.value = content.substring(valueOffset, tokenEnd(content, valueOffset));
         case VALUE_STRING, VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> node.value = requireNonNull(reader.getString());
         default -> throw new StreamReadException(reader, "Expected a JSON value");
      }
      // Use the matching closing token for containers rather than rescanning each subtree. Scalar spans retain original
      // quotes and escapes: decoded strings and normalized numbers do not have the same length as their source text.
      final int valueEnd = type == JsonToken.START_OBJECT || type == JsonToken.START_ARRAY ? sourceOffset(reader, content) + 1
            : tokenEnd(content, valueOffset);
      node.valueLength = valueEnd - valueOffset;
      return node;
   }
}
