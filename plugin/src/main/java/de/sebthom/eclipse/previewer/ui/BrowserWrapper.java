/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardCopyOption.*;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.services.IDisposable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import net.sf.jstuff.core.collection.tuple.Tuple2;

/**
 * Wraps SWT's browser with the navigation, clipboard, download, and view-state behavior required by previews.
 *
 * @author Sebastian Thomschke
 */
public final class BrowserWrapper implements IDisposable {

   // Bound data crossing the JavaScript bridge before parsing or copying it on the UI thread.
   private static final int MAX_SVG_DOWNLOAD_LENGTH = 16 * 1024 * 1024;
   private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

   private static Browser createBrowser(final Composite parent) {
      if (!SystemUtils.IS_OS_WINDOWS)
         return new Browser(parent, SWT.NONE);

      final int style = switch (PluginPreferences.getWebView()) {
         case "edge" -> SWT.EDGE;
         // The stored value is "default" for historical reasons. On old SWT releases, SWT.NONE was IE on Windows;
         // on newer releases, request SWT.IE reflectively to keep that preference stable.
         case "default" -> getInternetExplorerBrowserStyle();
         default -> SWT.NONE;
      };

      try {
         return new Browser(parent, style);
      } catch (final SWTException ex) {
         Plugin.log().error(ex);
         return new Browser(parent, SWT.NONE);
      }
   }

   private static int getInternetExplorerBrowserStyle() {
      try {
         return SWT.class.getField("IE").getInt(null);
      } catch (final ReflectiveOperationException | RuntimeException ex) {
         return SWT.NONE;
      }
   }

   private final Browser browser;
   private final BrowserLinkListener webLinkListener;
   private final Clipboard clipboard;
   private @Nullable URI svgSavingDocument;
   private @Nullable BrowserFunction saveSvgFunction;
   private @Nullable Predicate<URI> shouldOverrideNavigation;
   private @Nullable BiPredicate<URI, String> webLinkHandler;

   public BrowserWrapper(final Composite parent) {
      this(createBrowser(parent));
   }

   /**
    * Wraps a browser created by the caller, so tests can choose the engine without the plugin's preference store.
    */
   BrowserWrapper(final Browser browser) {
      this.browser = browser;
      clipboard = new Clipboard(browser.getDisplay());
      webLinkListener = new BrowserLinkListener(browser, (target, location) -> {
         final var handler = webLinkHandler;
         return handler != null && handler.test(target, location);
      }, ex -> Plugin.log().warn(ex, "Cannot handle browser web links."));

      // Workaround for Eclipse keybinding handling: Ctrl+C often triggers the workbench Copy command,
      // which does not propagate the embedded web selection to the system clipboard.
      // Copy the current web selection ourselves when the Browser has focus.
      browser.addListener(SWT.KeyDown, event -> {
         if (this.browser.isDisposed())
            return;

         final boolean mod1 = (event.stateMask & SWT.MOD1) != 0;
         // SWT uses ASCII key codes for letters in KeyDown events.
         if (!mod1 || event.keyCode != 'c' && event.keyCode != 'C')
            return;

         final String selection = getSelectedText();
         if (selection.isEmpty())
            return;

         clipboard.setContents(new Object[] {selection}, new Transfer[] {TextTransfer.getInstance()});
         event.doit = false;
      });

      browser.addLocationListener(new LocationAdapter() {
         @Override
         public void changing(final LocationEvent event) {
            final var targetURI = MiscUtils.toURI(event.location);
            if (targetURI != null) {
               final var shouldOverrideNavigation = BrowserWrapper.this.shouldOverrideNavigation;
               try {
                  if (shouldOverrideNavigation != null && shouldOverrideNavigation.test(targetURI)) {
                     // A cancelled navigation keeps the current document, so its bridge stays valid.
                     event.doit = false;
                     return;
                  }
               } catch (final RuntimeException ex) {
                  Plugin.log().warn(ex, "Cannot handle browser navigation to [" + targetURI + "].");
               }
            }

            final var svgSavingDocument = BrowserWrapper.this.svgSavingDocument;
            // Browsers accept locations that java.net.URI rejects, such as a literal '|' in a query. Such a location is never
            // the trusted preview, which always comes from Path.toUri(), so it must revoke the bridge as well.
            if (svgSavingDocument != null && (targetURI == null || !isSameDocument(svgSavingDocument, targetURI))) {
               // BrowserFunction registrations survive page loads. Revoke the native bridge before a trusted preview can
               // navigate to any other document, including navigation initiated by the preview's own JavaScript.
               BrowserWrapper.this.svgSavingDocument = null;
               setSvgSavingEnabled(false);
            }
         }
      });
   }

   private boolean saveSvg(final Object[] arguments) {
      // Navigation events can be missed or arrive out of order, for example when a load that started before a navigation
      // completes after it. Authorize each call against the document that is shown now instead of relying on revocation.
      if (!isShowing(svgSavingDocument)) {
         Plugin.log().warn("Ignoring an SVG download request from a document that is not the generated preview.");
         return false;
      }
      if (arguments.length != 1 || !(arguments[0] instanceof final String svgContent)) {
         Plugin.log().warn("Ignoring an invalid SVG download request from the preview browser.");
         return false;
      }
      if (!isSvgDocument(svgContent)) {
         Plugin.log().warn("Ignoring an invalid SVG download request from the preview browser.");
         return false;
      }

      final var dialog = new FileDialog(browser.getShell(), SWT.SAVE);
      dialog.setText("Save SVG");
      dialog.setFileName("graphic.svg");
      dialog.setFilterNames(new String[] {"SVG files (*.svg)", "All files (*.*)"});
      dialog.setFilterExtensions(new String[] {"*.svg", "*.*"});
      dialog.setOverwrite(true);

      final String selectedPath = dialog.open();
      if (selectedPath == null)
         return false;

      try {
         writeAtomically(Path.of(selectedPath), svgContent);
         return true;
      } catch (final IOException | InvalidPathException ex) {
         Plugin.log().error(ex);
         MessageDialog.openError(browser.getShell(), "Cannot Save SVG", "Cannot write the SVG file:\n" + selectedPath);
         return false;
      }
   }

   private static boolean isSvgDocument(final String svgContent) {
      if (svgContent.isEmpty() || svgContent.length() > MAX_SVG_DOWNLOAD_LENGTH)
         return false;

      try {
         // Browser content crosses a native trust boundary here. Parse with external resources and document types disabled
         // so the save action accepts one self-contained SVG document without resolving attacker-controlled entities.
         final var factory = DocumentBuilderFactory.newInstance();
         factory.setNamespaceAware(true);
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
         factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
         factory.setXIncludeAware(false);
         factory.setExpandEntityReferences(false);

         final org.w3c.dom.Document document;
         try (var svgReader = new StringReader(svgContent)) {
            document = factory.newDocumentBuilder().parse(new InputSource(svgReader));
         }
         final var root = document.getDocumentElement();
         return "svg".equals(root.getLocalName()) && SVG_NAMESPACE.equals(root.getNamespaceURI());
      } catch (final IOException | ParserConfigurationException | SAXException | RuntimeException ex) {
         Plugin.log().debug(ex);
         return false;
      }
   }

   private void setSvgSavingEnabled(final boolean enabled) {
      if (enabled) {
         if (saveSvgFunction == null) {
            // Browser JavaScript cannot choose a filesystem destination; keep that access behind Eclipse's native dialog.
            saveSvgFunction = new BrowserFunction(browser, Constants.JAVASCRIPT_SAVE_SVG_FUNCTION) {
               @Override
               @SuppressWarnings("null")
               public @Nullable Object function(final @NonNullByDefault({}) Object[] arguments) {
                  return saveSvg(arguments);
               }
            };
         }
      } else if (saveSvgFunction != null) {
         saveSvgFunction.dispose();
         saveSvgFunction = null;
      }
   }

   private static void writeAtomically(final Path selectedPath, final String svgContent) throws IOException {
      final Path target = selectedPath.toAbsolutePath();
      final Path parent = target.getParent();
      if (parent == null)
         throw new IOException("The selected SVG path has no parent directory: " + selectedPath);

      // A sibling temporary file keeps a failed write from truncating an existing destination and permits an atomic move
      // on file systems that support it. The fallback retains the complete-write-before-replace invariant.
      final Path temporary = Files.createTempFile(parent, ".previewer-svg-", ".tmp");
      try {
         Files.writeString(temporary, svgContent, UTF_8);
         try {
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
         } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, REPLACE_EXISTING);
         }
      } catch (final IOException ex) {
         try {
            Files.deleteIfExists(temporary);
         } catch (final IOException cleanupException) {
            ex.addSuppressed(cleanupException);
         }
         throw ex;
      }
   }

   /**
    * @return true if the browser currently shows the given document; a location that java.net.URI rejects never matches
    */
   private boolean isShowing(final @Nullable URI document) {
      final var shown = MiscUtils.toURI(browser.getUrl());
      return document != null && shown != null && isSameDocument(document, shown);
   }

   private static boolean isSameDocument(final URI left, final URI right) {
      final String leftScheme = left.getScheme();
      final String rightScheme = right.getScheme();
      return (leftScheme == null ? rightScheme == null : leftScheme.equalsIgnoreCase(rightScheme)) && Objects.equals(left
         .getRawSchemeSpecificPart(), right.getRawSchemeSpecificPart());
   }

   /**
    * Sets a callback that can override browser navigation before the embedded browser follows the target URI.
    * Navigation events include automatic page and frame loads, so this callback does not indicate a user link activation.
    * <p>
    * Return {@code true} when the target was handled elsewhere and the embedded browser should cancel its native
    * navigation. Return {@code false} to let the browser continue normally.
    */
   public void setShouldOverrideNavigation(final @Nullable Predicate<URI> shouldOverrideNavigation) {
      this.shouldOverrideNavigation = shouldOverrideNavigation;
   }

   /**
    * Sets the HTTP(S) link handler; returning true cancels the link's embedded navigation.
    * <p>
    * The handler receives the parsed target for policy checks and the location as the browser serialized it. The parsed
    * target may percent-encode characters that the browser keeps literal, see {@link MiscUtils#toBrowserURI}.
    */
   public void setWebLinkHandler(final @Nullable BiPredicate<URI, String> webLinkHandler) {
      this.webLinkHandler = webLinkHandler;
   }

   /**
    * Enables or disables HTTP(S) link handling for the current page and later loads. Handling starts once a page has loaded.
    * While disabled, pages keep their native link behavior, including a page that received the handling earlier.
    */
   public void setWebLinksEnabled(final boolean enabled) {
      UI.run(() -> {
         if (!browser.isDisposed()) {
            webLinkListener.setEnabled(enabled);
         }
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

         svgSavingDocument = null;
         setSvgSavingEnabled(false);
         return browser.setText(content);
      });
   }

   public CompletionStage<@Nullable Void> navigateTo(final Path target) {
      return navigateTo(target, false);
   }

   public CompletionStage<@Nullable Void> navigateTo(Path target, final boolean enableSvgSaving) {
      if (SystemUtils.IS_OS_WINDOWS && target.toString().contains("~")) {
         try {
            target = target.toRealPath(); // resolve 8.3 short paths, this is required to make save/restore BrowserScrollPos work reliably
         } catch (final IOException ex) {
            Plugin.log().error(ex);
         }
      }
      return navigateTo(target.toUri(), enableSvgSaving);
   }

   public CompletionStage<@Nullable Void> navigateTo(final URI target) {
      return navigateTo(target, false);
   }

   private CompletionStage<@Nullable Void> navigateTo(final URI target, final boolean enableSvgSaving) {
      return navigateTo(target.toString(), enableSvgSaving ? target : null);
   }

   private ProgressListener onPageLoaded = new ProgressAdapter() {};

   public CompletionStage<@Nullable Void> navigateTo(final String url) {
      return navigateTo(url, null);
   }

   private CompletionStage<@Nullable Void> navigateTo(final String url, final @Nullable URI svgSavingTarget) {
      return UI.supply(() -> {
         if (browser.isDisposed())
            return CompletableFuture.failedStage(new IllegalStateException("Browser is already disposed"));

         browser.removeProgressListener(onPageLoaded);

         final var future = new CompletableFuture<@Nullable Void>();
         onPageLoaded = new ProgressAdapter() {
            @Override
            public void completed(final ProgressEvent event) {
               browser.removeProgressListener(this);
               if (isShowing(svgSavingTarget)) {
                  svgSavingDocument = svgSavingTarget;
                  setSvgSavingEnabled(true);
               }
               future.complete(null);
            }
         };
         browser.addProgressListener(onPageLoaded);

         // Remove the bridge before loading passthrough content. For generated previews, the completion listener installs
         // it only after confirming that the browser did not redirect to another document.
         if (svgSavingTarget == null) {
            svgSavingDocument = null;
            setSvgSavingEnabled(false);
         }
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
      webLinkListener.dispose();
      final var saveSvgFunction = this.saveSvgFunction;
      if (saveSvgFunction != null) {
         saveSvgFunction.dispose();
      }
      clipboard.dispose();
      browser.dispose();
   }
}
