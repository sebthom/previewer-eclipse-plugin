/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.net.URI;
import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.services.IDisposable;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Plugin;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public class BrowserWrapper implements IDisposable {

   private final Browser browser;
   private int browserLastScrollPos = -1;

   public BrowserWrapper(final Composite parent) {
      // for SWT.EDGE, see https://github.com/eclipse-platform/eclipse.platform.swt/blob/master/bundles/org.eclipse.swt/Readme.WebView2.md#limitation-and-caveats
      browser = new Browser(parent, SystemUtils.IS_OS_WINDOWS ? SWT.EDGE : SWT.NONE);
      browser.addProgressListener(new ProgressAdapter() {
         @Override
         public void completed(final ProgressEvent event) {
            restoreBrowserScrollPos();
         }
      });
   }

   public boolean setContent(final String content) {
      return UI.run(() -> {
         if (browser.isDisposed())
            return false;

         return browser.setText(content);
      });
   }

   public boolean navigateTo(final Path target) {
      return navigateTo(target.toUri());
   }

   public boolean navigateTo(final URI target) {
      return navigateTo(target.toString());
   }

   public boolean navigateTo(final String url) {
      return UI.run(() -> {
         if (browser.isDisposed())
            return false;

         final var currentUrl = browser.getUrl();
         if (Strings.replaceEach(url, "/render_cache_editors/", "", "/render_cache_files/", "") //
            .equals(Strings.replaceEach(currentUrl, "/render_cache_editors/", "", "/render_cache_files/", ""))) {
            saveBrowserScrollPos();
         } else {
            browserLastScrollPos = -1;
         }

         return browser.setUrl(url);
      });
   }

   public String getUrl() {
      return UI.run(() -> browser.isDisposed() ? "" : browser.getUrl());
   }

   private void restoreBrowserScrollPos() {
      if (browserLastScrollPos > -1) {
         browser.execute(String.format("window.scrollTo(0, %d);", browserLastScrollPos));
      }
   }

   private void saveBrowserScrollPos() {
      try {
         if (browser.evaluate("""
               return window.pageYOffset || (document.documentElement
                 && document.documentElement.scrollTop) || (document.body && document.body.scrollTop) || 0;
            """) instanceof final Number pos) {
            browserLastScrollPos = pos.intValue();
            return;
         }
      } catch (

      final SWTException ex) {
         Plugin.log().warn(ex, "Cannot determine scroll position.");
      }
      browserLastScrollPos = -1;
   }

   @Override
   public void dispose() {
      browser.dispose();
   }
}
