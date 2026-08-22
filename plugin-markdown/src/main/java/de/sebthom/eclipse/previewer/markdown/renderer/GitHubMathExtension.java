/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.renderer;

import java.util.LinkedHashMap;
import java.util.Set;

import org.commonmark.Extension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.parser.beta.InlineContentParser;
import org.commonmark.parser.beta.InlineContentParserFactory;
import org.commonmark.parser.beta.InlineParserState;
import org.commonmark.parser.beta.ParsedInline;
import org.commonmark.parser.beta.Position;
import org.commonmark.parser.beta.Scanner;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Implements GitHub's documented math forms for the offline CommonMark renderer.
 *
 * <p>
 * GitHub's complete parsing grammar is not public, so ambiguous dollar text is deliberately left unchanged.
 *
 * @author Sebastian Thomschke
 */
@NonNullByDefault({})
final class GitHubMathExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

   private static final class BlockMath extends CustomBlock implements MathLiteral {
      private final String literal;

      BlockMath(final String literal) {
         this.literal = literal;
      }

      @Override
      public boolean display() {
         return true;
      }

      @Override
      public String literal() {
         return literal;
      }
   }

   private static final class InlineMath extends CustomNode implements MathLiteral {
      private final boolean display;
      private final String literal;

      InlineMath(final String literal, final boolean display) {
         this.literal = literal;
         this.display = display;
      }

      @Override
      public boolean display() {
         return display;
      }

      @Override
      public String literal() {
         return literal;
      }
   }

   private static final class MathHtmlNodeRenderer implements NodeRenderer {
      private final HtmlNodeRendererContext context;

      MathHtmlNodeRenderer(final HtmlNodeRendererContext context) {
         this.context = context;
      }

      @Override
      public Set<Class<? extends Node>> getNodeTypes() {
         return Set.of(InlineMath.class, BlockMath.class);
      }

      @Override
      public void render(final Node node) {
         final MathLiteral math = (MathLiteral) node;
         final var attributes = new LinkedHashMap<String, String>();
         attributes.put("class", math.display() ? "js-display-math" : "js-inline-math");
         if (math.display()) {
            attributes.put("display", "block");
         }

         final var html = context.getWriter();
         if (node instanceof BlockMath) {
            html.line();
         }
         html.tag("math-renderer", attributes);
         // Keep delimiters in the marker to match GitHub API output; the browser bridge removes them before conversion.
         html.text(math.display() ? "$$" + math.literal() + "$$" : "$" + math.literal() + "$");
         html.tag("/math-renderer");
         if (node instanceof BlockMath) {
            html.line();
         }
      }
   }

   private interface MathLiteral {
      boolean display();

      String literal();
   }

   private static final class MathInlineParser implements InlineContentParser {

      private static final class Factory implements InlineContentParserFactory {
         @Override
         public InlineContentParser create() {
            return new MathInlineParser();
         }

         @Override
         public Set<Character> getTriggerCharacters() {
            return Set.of('$');
         }
      }

      private static ParsedInline none(final Scanner scanner, final Position originalPosition) {
         // Custom parsers share the scanner, so failed speculative reads must not consume ordinary Markdown.
         scanner.setPosition(originalPosition);
         return ParsedInline.none();
      }

      private static ParsedInline parseBacktickDelimited(final Scanner scanner, final Position originalPosition) {
         scanner.next(); // opening backtick
         final Position contentStart = scanner.position();
         while (scanner.hasNext()) {
            if (scanner.peek() == '`') {
               final Position contentEnd = scanner.position();
               scanner.next();
               if (!scanner.next('$')) {
                  continue;
               }

               final String content = scanner.getSource(contentStart, contentEnd).getContent().strip();
               return content.isEmpty() //
                     ? none(scanner, originalPosition)
                     : ParsedInline.of(new InlineMath(content, false), scanner.position());
            }
            scanner.next();
         }
         return none(scanner, originalPosition);
      }

      private static ParsedInline parseDisplay(final Scanner scanner, final Position originalPosition) {
         if (scanner.peek() == '$')
            return none(scanner, originalPosition); // Runs of three or more dollars are outside the documented syntax.

         final Position contentStart = scanner.position();
         int precedingBackslashes = 0;
         while (scanner.hasNext()) {
            final char current = scanner.peek();
            if (current == '$' && precedingBackslashes % 2 == 0) {
               final Position contentEnd = scanner.position();
               scanner.next();
               if (!scanner.next('$')) {
                  precedingBackslashes = 0;
                  continue;
               }

               final String content = scanner.getSource(contentStart, contentEnd).getContent().strip();
               return content.isEmpty() ? none(scanner, originalPosition)
                     : ParsedInline.of(new InlineMath(content, true), scanner.position());
            }
            scanner.next();
            precedingBackslashes = current == '\\' ? precedingBackslashes + 1 : 0;
         }
         return none(scanner, originalPosition);
      }

      private static ParsedInline parseInline(final Scanner scanner, final Position originalPosition) {
         final char first = scanner.peek();
         if (first == Scanner.END || Character.isWhitespace(first))
            return none(scanner, originalPosition);

         final Position contentStart = scanner.position();
         if (first == '<') {
            scanner.next();
            // GitHub uses <span>$</span> for a literal dollar beside math. Custom parsers run before CommonMark's
            // HTML parser, so reject a delimiter that would otherwise swallow the closing tag and any later math.
            if (scanner.peek() == '/')
               return none(scanner, originalPosition);
            scanner.setPosition(contentStart);
         }
         int precedingBackslashes = 0;
         while (scanner.hasNext()) {
            final char current = scanner.peek();
            if (current == '\n')
               return none(scanner, originalPosition); // Single-dollar math is inline and must not absorb another Markdown line.
            if (current == '`')
               // A speculative dollar opener can precede a code span; stop here so it cannot consume dollars owned by CommonMark's code parser.
               return none(scanner, originalPosition);

            if (current == '$' && precedingBackslashes % 2 == 0 && !Character.isWhitespace(scanner.peekPreviousCodePoint())) {
               final Position contentEnd = scanner.position();
               scanner.next();
               // A dollar immediately before a digit is normally currency, e.g. "$5 and $10".
               if (Character.isDigit(scanner.peekCodePoint())) {
                  precedingBackslashes = 0;
                  continue;
               }

               final String content = scanner.getSource(contentStart, contentEnd).getContent();
               return ParsedInline.of(new InlineMath(content, false), scanner.position());
            }
            scanner.next();
            precedingBackslashes = current == '\\' ? precedingBackslashes + 1 : 0;
         }
         return none(scanner, originalPosition);
      }

      @Override
      public ParsedInline tryParse(final InlineParserState inlineParserState) {
         final Scanner scanner = inlineParserState.scanner();
         final Position originalPosition = scanner.position();
         if (scanner.peekPreviousCodePoint() == '\\')
            return ParsedInline.none();

         scanner.next(); // first dollar
         if (scanner.next('$'))
            return parseDisplay(scanner, originalPosition);
         if (scanner.peek() == '`')
            return parseBacktickDelimited(scanner, originalPosition);
         return parseInline(scanner, originalPosition);
      }
   }

   static Extension create() {
      return new GitHubMathExtension();
   }

   private GitHubMathExtension() {
   }

   @Override
   public void extend(final HtmlRenderer.Builder rendererBuilder) {
      rendererBuilder.nodeRendererFactory(MathHtmlNodeRenderer::new);
   }

   @Override
   public void extend(final Parser.Builder parserBuilder) {
      parserBuilder.customInlineContentParserFactory(new MathInlineParser.Factory());
      parserBuilder.postProcessor(document -> {
         document.accept(new AbstractVisitor() {
            @Override
            public void visit(final FencedCodeBlock block) {
               if (!"math".equals(block.getInfo() == null ? null : block.getInfo().strip()))
                  return;

               // Replacing the parsed fence preserves ordinary code-block parsing and confines the override to `math` fences.
               final String literal = block.getLiteral() == null ? "" : block.getLiteral().strip();
               block.insertBefore(new BlockMath(literal));
               block.unlink();
            }
         });
         return document;
      });
   }
}
