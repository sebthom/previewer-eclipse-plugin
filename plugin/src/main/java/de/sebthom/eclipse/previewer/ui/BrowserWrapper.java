/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.services.IDisposable;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.prefs.PluginPreferences;
import net.sf.jstuff.core.collection.tuple.Tuple2;

/**
 * @author Sebastian Thomschke
 */
public final class BrowserWrapper implements IDisposable {

   private final Browser browser;
   private final Clipboard clipboard;

   public BrowserWrapper(final Composite parent) {
      // for SWT.EDGE, see https://github.com/eclipse-platform/eclipse.platform.swt/blob/master/bundles/org.eclipse.swt/Readme.WebView2.md#limitation-and-caveats
      Browser browser;
      if (SystemUtils.IS_OS_WINDOWS && "edge".equals(PluginPreferences.getWebView())) {
         try {
            browser = new Browser(parent, SWT.EDGE);
         } catch (final SWTException ex) {
            Plugin.log().error(ex);
            browser = new Browser(parent, SWT.NONE);
         }
      } else {
         browser = new Browser(parent, SWT.NONE);
      }
      this.browser = browser;
      clipboard = new Clipboard(parent.getDisplay());

      // Workaround for Eclipse keybinding handling: Ctrl+C often triggers the workbench Copy command,
      // which does not propagate the embedded web selection to the system clipboard.
      // Copy the current web selection ourselves when the Browser has focus.
      browser.addListener(SWT.KeyDown, event -> {
         if (this.browser.isDisposed())
            return;

         final boolean mod1 = (event.stateMask & SWT.MOD1) != 0;
         if (!mod1)
            return;

         // SWT uses ASCII key codes for letters in KeyDown events.
         if (event.keyCode != 'c' && event.keyCode != 'C')
            return;

         final String selection = getSelectedText();
         if (selection.isEmpty())
            return;

         clipboard.setContents(new Object[] {selection}, new Transfer[] {TextTransfer.getInstance()});
         event.doit = false;
      });
   }

   private String getSelectedText() {
      if (browser.isDisposed())
         return "";

      try {
         final Object result = browser.evaluate("""
            try {
              return (window.getSelection && window.getSelection().toString()) || "";
            } catch (e) {
              return "";
            }
            """);
         return result instanceof final String s ? s : "";
      } catch (final SWTException ex) {
         Plugin.log().warn(ex, "Cannot read browser selection.");
         return "";
      }
   }

   public boolean setContent(final String content) {
      return UI.supply(() -> {
         if (browser.isDisposed())
            return false;

         return browser.setText(content);
      });
   }

   public CompletionStage<@Nullable Void> navigateTo(Path target) {
      if (SystemUtils.IS_OS_WINDOWS && target.toString().contains("~")) {
         try {
            target = target.toRealPath(); // resolve 8.3 short paths, this is required to make save/restore BrowserScrollPos work reliably
         } catch (final IOException ex) {
            Plugin.log().error(ex);
         }
      }
      return navigateTo(target.toUri());
   }

   public CompletionStage<@Nullable Void> navigateTo(final URI target) {
      return navigateTo(target.toString());
   }

   private ProgressListener onPageLoaded = new ProgressAdapter() {};

   public CompletionStage<@Nullable Void> navigateTo(final String url) {
      return UI.supply(() -> {
         if (browser.isDisposed())
            return CompletableFuture.failedStage(new IllegalStateException("Browser is already disposed"));

         browser.removeProgressListener(onPageLoaded);

         final var future = new CompletableFuture<@Nullable Void>();
         onPageLoaded = new ProgressAdapter() {
            @Override
            public void completed(final ProgressEvent event) {
               browser.removeProgressListener(this);
               future.complete(null);
            }
         };
         browser.addProgressListener(onPageLoaded);

         if (browser.setUrl(url))
            return future;

         browser.removeProgressListener(onPageLoaded);
         return CompletableFuture.failedStage(new IllegalStateException("Failed to navigate to " + url + " for an unknown reason."));
      });
   }

   public String getUrl() {
      return UI.supply(() -> browser.isDisposed() ? "" : browser.getUrl());
   }

   public Tuple2<Integer, Integer> getScrollPos() {
      return UI.supply(() -> {
         try {
            if (browser.evaluate("""
                  return window.pageXOffset || (document.documentElement
                    && document.documentElement.scrollLeft) || (document.body && document.body.scrollLeft) || 0;
               """) instanceof final Number posX && browser.evaluate("""
                  return window.pageYOffset || (document.documentElement
                    && document.documentElement.scrollTop) || (document.body && document.body.scrollTop) || 0;
               """) instanceof final Number posY)
               return Tuple2.create(posX.intValue(), posY.intValue());
         } catch (final SWTException ex) {
            Plugin.log().warn(ex, "Cannot determine scroll position.");
         }
         return Tuple2.create(0, 0);
      });
   }

   public void setScrollPos(final Tuple2<Integer, Integer> pos) {
      UI.run(() -> browser.execute(String.format("window.scrollTo(%d, %d);", pos.get1(), pos.get2())));
   }

   public float getZoom() {
      return UI.supply(() -> {
         if (browser.evaluate("""
            const transform = document.body.style.transform;
            if (transform) return 1;

            const scaleMatch = transform.match(/scale\\(([^)]+)\\)/);
            return scaleMatch ? parseFloat(scaleMatch[1]) : 1;
            """) instanceof final Number zoom)
            return zoom.floatValue();
         return 1.0f;
      });
   }

   public void setZoom(final float zoom) {
      UI.run(() -> browser.execute("document.body.style.transform = 'scale(" + zoom + ")';document.body.style.transformOrigin = '0 0';"));
   }

   @Override
   public void dispose() {
      clipboard.dispose();
      browser.dispose();
   }
}
