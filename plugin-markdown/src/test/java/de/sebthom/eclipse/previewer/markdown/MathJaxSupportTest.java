/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * @author Sebastian Thomschke
 */
class MathJaxSupportTest {

   @Test
   void detectsOnlyExplicitMathMarkers() {
      assertFalse(MathJaxSupport.containsMath("<p>Price: $5</p>"));
      assertTrue(MathJaxSupport.containsMath("<math-renderer>$x$</math-renderer>"));
   }

   @Test
   void buildsAnOfflineExplicitTypesettingBridge() {
      final String script = MathJaxSupport.createInitializationScript(new File("assets/mathjax/tex-svg.js"));
      assertTrue(script.contains("tex2svgPromise"));
      assertTrue(script.contains("document.querySelectorAll('math-renderer')"));
      assertTrue(script.contains("'[-]': ['autoload', 'require']"));
      assertTrue(script.contains("enableEnrichment: false"));
      assertTrue(script.contains("enableSpeech: false"));
      assertTrue(script.contains("enrich: false"));
      assertTrue(script.contains("speech: false"));
      assertTrue(script.contains("document.documentMode"));
      assertFalse(script.contains("cdn.jsdelivr.net"));
   }

   @Test
   void detectsDoubleDelimitersFromMarkerContent() {
      final String script = MathJaxSupport.createInitializationScript(new File("assets/mathjax/tex-svg.js"));
      assertTrue(script.contains("source.substring(0, 2) === '$$'"));
      assertTrue(script.contains("source.substring(source.length - 2) === '$$'"));
      assertFalse(script.contains("var delimiter = display"));
   }

   @Test
   void keepsApostrophesInsideQuotedResourceUris() {
      final File component = new File("assets/O'Connor/mathjax/tex-svg.js");
      final String componentUri = component.toURI().toASCIIString();
      final String rootUri = component.toURI().resolve(".").toASCIIString();
      final String script = MathJaxSupport.createInitializationScript(component);

      assertTrue(componentUri.contains("'"));
      assertTrue(script.contains("paths: { mathjax: \"" + rootUri + "\" }"));
      assertTrue(script.contains("script.src = \"" + componentUri + "\";"));
   }
}
