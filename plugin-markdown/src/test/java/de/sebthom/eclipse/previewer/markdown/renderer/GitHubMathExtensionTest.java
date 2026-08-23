/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.renderer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.jupiter.api.Test;

/**
 * @author Sebastian Thomschke
 */
@SuppressWarnings("null")
class GitHubMathExtensionTest {

   private static final Parser PARSER = Parser.builder().extensions(List.of(GitHubMathExtension.create())).build();
   private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(List.of(GitHubMathExtension.create())).build();

   private static String render(final String markdown) {
      return RENDERER.render(PARSER.parse(markdown));
   }

   @Test
   void rendersDocumentedInlineForms() {
      assertEquals("<p>Euler: <math-renderer class=\"js-inline-math\">$e^{i\\pi}+1=0$</math-renderer>.</p>\n", render(
         "Euler: $e^{i\\pi}+1=0$."));
      assertEquals("<p><math-renderer class=\"js-inline-math\">$\\sqrt{x}$</math-renderer></p>\n", render("$`\\sqrt{x}`$"));
   }

   @Test
   void rendersDisplayMathInHeadingsAndFences() {
      assertEquals(
         "<h1><math-renderer class=\"js-display-math\" display=\"block\">$$\\color{#7cd0fd}\\textrm{Title}$$</math-renderer></h1>\n",
         render("# $$\\color{#7cd0fd}\\textrm{Title}$$"));
      assertEquals("<math-renderer class=\"js-display-math\" display=\"block\">$$a^2+b^2=c^2$$</math-renderer>\n", render(
         "```math\na^2+b^2=c^2\n```"));
   }

   @Test
   void leavesAmbiguousDollarsAndCodeUntouched() {
      final String html = render("Prices are $5 and $10. Code: `$x$`. Escaped: \\$x$.");
      assertFalse(html.contains("<math-renderer"));
      assertTrue(html.contains("Prices are $5 and $10."));
      assertTrue(html.contains("<code>$x$</code>"));
      assertTrue(html.contains("Escaped: $x$."));
   }

   @Test
   void leavesMalformedMathAndOtherFencesUntouched() {
      assertFalse(render("Unclosed $x + 1").contains("<math-renderer"));
      assertEquals("<pre><code class=\"language-java\">$x$\n</code></pre>\n", render("```java\n$x$\n```"));
   }

   @Test
   void preservesGithubLiteralDollarSpanBesideMath() {
      assertEquals(
         "<p>To split <span>$</span>100 in half, we calculate <math-renderer class=\"js-inline-math\">$100/2$</math-renderer></p>\n",
         render("To split <span>$</span>100 in half, we calculate $100/2$"));
   }

   @Test
   void escapesMathSourceInGeneratedHtml() {
      assertEquals("<p><math-renderer class=\"js-inline-math\">$x &lt; y$</math-renderer></p>\n", render("$x < y$"));
   }
}
