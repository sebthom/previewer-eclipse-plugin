/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.pikchr;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * Verifies Pikchr's generated HTML and executes the vendored browser API to validate its SVG contract.
 *
 * @author Sebastian Thomschke
 */
class PikchrRenderingTest {

   private static final String RUNTIME_TEST_SCRIPT = """
      const fs = require("node:fs");
      const path = require("node:path");
      const vm = require("node:vm");
      const runtimePath = path.resolve(process.argv[process.argv.length - 1]);
      const context = {
        console, process, require, WebAssembly, TextDecoder, TextEncoder, URL,
        __filename: runtimePath,
        __dirname: path.dirname(runtimePath)
      };
      // The CommonJS branch exports the raw Emscripten module. Omit `module` here to exercise the browser-global wrapper
      // and loadPikchr().render() contract used by SWT's embedded browser.
      vm.createContext(context);
      vm.runInContext(fs.readFileSync(runtimePath, "utf8"), context, { filename: runtimePath });
      context.loadPikchr().then(function (pikchr) {
        if (!pikchr || typeof pikchr.render !== "function") {
          throw new Error("loadPikchr() did not expose render().");
        }
        const result = pikchr.render('box "A\\u00a0B"', "previewer-pikchr-svg", 1);
        if (!result || typeof result.svg !== "string" || result.width <= 0 || result.height <= 0) {
          throw new Error("Pikchr returned an invalid render result.");
        }
        process.stdout.write(Buffer.from(result.svg, "utf8").toString("base64"));
      }).catch(function (error) {
        console.error(error);
        process.exitCode = 1;
      });
      """;

   private static boolean isNodeAvailable() {
      try {
         final Process process = new ProcessBuilder("node", "--version").start();
         return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
      } catch (final IOException | InterruptedException ex) {
         if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
         }
         return false;
      }
   }

   @Test
   void encodesSourceAndHandlesErrorsWithoutInjectingHtml() {
      final String source = "box \"</script><script>alert('unsafe')</script> ä\"";
      final String html = PikchrRendering.renderToHtmlFragment(source, List.of(), false);

      assertFalse(html.contains(source));
      assertTrue(html.contains(Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8))));
      assertTrue(html.contains("pikchr.render(source, 'previewer-pikchr-svg', 1)"));
      assertTrue(html.contains("output.textContent"));
      assertTrue(html.contains("result.width < 0"));
      assertTrue(html.contains("new DOMParser()"));
      assertTrue(html.contains("window.previewerPikchrRuntimePromise"));
      assertTrue(html.contains("var output = document.getElementById"));
      assertFalse(html.contains(".closest("));
   }

   @Test
   void appliesTheFirstRecognizedLayoutModifierAndDarkMode() {
      final String html = PikchrRendering.renderToHtmlFragment("box", List.of("toggle", "CENTER", "float-right"), true);

      assertTrue(html.contains("previewer-pikchr-center"));
      assertFalse(html.contains("previewer-pikchr-float-right"));
      assertTrue(html.contains("pikchr.render(source, 'previewer-pikchr-svg', 3)"));
   }

   @Test
   void downloadsTheSvgUsingXmlSerialization() {
      final String html = PikchrRendering.renderToHtmlFragment("box \"Hello world\"", List.of(), false);

      assertTrue(html.contains(".querySelector(\"svg\")"));
      assertTrue(html.contains("new XMLSerializer().serializeToString(svg)"));
      assertTrue(html.contains("window.previewerSaveSvg(svgString)"));
      assertFalse(html.contains("-inner\").innerHTML"));
   }

   @Test
   void escapesInfoBoxText() {
      final String untrustedText = "<img src=x onerror=alert(1)>&\"'";
      final String html = StringUtils.htmlInfoBox(untrustedText);

      assertFalse(html.contains(untrustedText));
      assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;&amp;&quot;&#39;"));
   }

   @Test
   void executesVendoredBrowserApiAndReturnsXmlSvg() throws Exception {
      Assumptions.assumeTrue(isNodeAvailable(), "Node.js is unavailable; skipping the executable Pikchr runtime contract test.");
      final Path runtime = Path.of("src/main/resources/pikchr/pikchr.js").toAbsolutePath();
      final Process process = new ProcessBuilder("node", "-", runtime.toString()).redirectErrorStream(true).start();
      // NOTE: Windows command-line parsing strips nested quotes from long node -e arguments. Standard input preserves the
      // harness verbatim and avoids platform command-length limits.
      try (var processInput = process.getOutputStream()) {
         processInput.write(RUNTIME_TEST_SCRIPT.getBytes(StandardCharsets.UTF_8));
      }
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
         process.destroyForcibly();
         fail("The vendored Pikchr runtime did not finish within 30 seconds.");
      }

      final String output;
      try (var processOutput = process.getInputStream()) {
         output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
      }
      assertEquals(0, process.exitValue(), output);
      final String svg = new String(Base64.getDecoder().decode(output), StandardCharsets.UTF_8);
      assertFalse(svg.contains("&nbsp;"));

      final var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      final org.w3c.dom.Element root;
      try (var svgInput = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))) {
         root = factory.newDocumentBuilder().parse(svgInput).getDocumentElement();
      }
      assertEquals("svg", root.getLocalName());
      assertEquals("http://www.w3.org/2000/svg", root.getNamespaceURI());
   }
}
