/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.util.MiscUtils;

/**
 * The native Save SVG action is granted to one generated preview document and must not survive navigation away from it.
 * Enable explicitly with previewer.tests.browser because ordinary builds may have no graphical session or browser runtime.
 *
 * @author Sebastian Thomschke
 */
@EnabledIfSystemProperty(named = "previewer.tests.browser", matches = "default|ie|edge")
class BrowserWrapperSvgBridgeTest {

   private static final String HAS_BRIDGE = "return typeof window." + Constants.JAVASCRIPT_SAVE_SVG_FUNCTION + " === 'function';";

   @TempDir
   Path directory = lateNonNull();

   private final List<String> navigations = new CopyOnWriteArrayList<>();
   private @Nullable Display display;
   private @Nullable BrowserWrapper wrapper;
   private Browser browser = lateNonNull();

   @BeforeEach
   void createBrowser() throws Exception {
      final var display = this.display = new Display();
      final var browser = this.browser = BrowserTestSupport.createBrowser(new Shell(display));
      browser.addLocationListener(new LocationAdapter() {
         @Override
         public void changing(final LocationEvent event) {
            final var loc = event.location;
            if (loc != null) {
               navigations.add(loc);
            }
         }
      });
      final var wrapper = this.wrapper = new BrowserWrapper(browser);

      // A local destination: Java's HttpServer itself rejects a request line that java.net.URI cannot parse.
      Files.writeString(directory.resolve("destination.html"), "<html><body><p id='destination'>Destination</p></body></html>");
      final var preview = directory.resolve("preview.html");
      Files.writeString(preview, "<html><body><a id='fragment' href='#section'>Fragment</a>"
            + "<a id='local' href='other.html'>Local</a><p id='section'>Section</p></body></html>");
      final var loaded = wrapper.navigateTo(preview, true).toCompletableFuture();
      await(loaded::isDone);
      await(() -> Boolean.TRUE.equals(evaluate(HAS_BRIDGE)));
   }

   @AfterEach
   void disposeBrowser() {
      try {
         if (wrapper != null) {
            wrapper.dispose();
         }
      } finally {
         if (display != null) {
            display.dispose();
         }
      }
   }

   @ParameterizedTest
   // The literal '|' is kept by browsers in a query but rejected by java.net.URI.
   @ValueSource(strings = {"destination.html", "destination.html?a|b"})
   void revokesTheBridgeWhenThePreviewNavigatesAway(final String target) throws Exception {
      navigations.clear();
      assertTrue(browser.execute("location.href = '" + target + "';"));
      await(() -> Boolean.TRUE.equals(evaluate("return document.getElementById('destination') !== null;")));

      if (target.contains("|")) {
         final var reported = navigations.stream().filter(location -> location.contains("/destination.html")).findFirst().orElse("");
         // A backend that escapes the character reports a parsable URI and cannot reach the unparsable-location path.
         assumeTrue(MiscUtils.toURI(reported) == null, () -> "The browser reported a parsable location: " + reported);
      }
      assertEquals(Boolean.FALSE, evaluate(HAS_BRIDGE));
   }

   @Test
   void keepsTheBridgeForFragmentsAndCancelledNavigation() throws Exception {
      final var wrapper = asNonNull(this.wrapper);
      final var url = wrapper.getUrl();
      wrapper.setShouldOverrideNavigation(target -> target.toString().endsWith("/other.html"));

      click("local");
      await(() -> navigations.stream().anyMatch(location -> location.endsWith("/other.html")));
      assertEquals(url, wrapper.getUrl());
      assertEquals(Boolean.TRUE, evaluate(HAS_BRIDGE));

      click("fragment");
      await(() -> wrapper.getUrl().endsWith("#section"));
      assertEquals(Boolean.TRUE, evaluate(HAS_BRIDGE));
   }

   private void click(final String id) {
      assertTrue(browser.execute("document.getElementById('" + id + "').click();"));
   }

   private @Nullable Object evaluate(final String script) {
      try {
         return browser.evaluate(script);
      } catch (final RuntimeException ex) {
         // Readiness probes can run before the native document is available.
         return null;
      }
   }

   private void await(final BooleanSupplier condition) throws InterruptedException {
      BrowserTestSupport.await(asNonNull(display), condition);
   }
}
