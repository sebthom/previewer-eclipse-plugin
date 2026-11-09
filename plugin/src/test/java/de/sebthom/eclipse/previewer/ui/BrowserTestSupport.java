/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * Shared setup for the opt-in native browser tests, which select the engine with the previewer.tests.browser property.
 *
 * @author Sebastian Thomschke
 */
final class BrowserTestSupport {

   static Browser createBrowser(final Composite parent) {
      final var engine = System.getProperty("previewer.tests.browser");
      int style = "edge".equals(engine) ? SWT.EDGE : SWT.NONE;
      if ("ie".equals(engine)) {
         try {
            style = SWT.class.getField("IE").getInt(null);
         } catch (final NoSuchFieldException | IllegalAccessException ex) {
            // SWT releases before the explicit IE style select it with SWT.NONE.
         }
      }
      final var browser = new Browser(parent, style);
      if (!"default".equals(engine)) {
         assertEquals(engine, browser.getBrowserType());
      }
      return browser;
   }

   static void await(final Display display, final BooleanSupplier condition) throws InterruptedException {
      final long deadline = System.nanoTime() + 12_000_000_000L;
      while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
         if (!display.readAndDispatch()) {
            Thread.sleep(10);
         }
      }
      assertTrue(condition.getAsBoolean(), "Timed out waiting for native browser behavior");
   }

   private BrowserTestSupport() {
   }
}
