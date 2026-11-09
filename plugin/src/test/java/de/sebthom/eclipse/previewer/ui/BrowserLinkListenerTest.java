/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises native link dispatch, frame boundaries, and saved HTML compatibility without launching an external browser.
 * Enable explicitly with previewer.tests.browser because ordinary builds may have no graphical session or browser runtime.
 *
 * @author Sebastian Thomschke
 */
@EnabledIfSystemProperty(named = "previewer.tests.browser", matches = "default|ie|edge")
class BrowserLinkListenerTest {

   @TempDir
   Path directory = lateNonNull();

   private final List<URI> opened = new ArrayList<>();
   private final Set<URI> embeddedTargets = new HashSet<>();
   private final List<RuntimeException> errors = new ArrayList<>();
   private final AtomicInteger frameRequests = new AtomicInteger();
   private @Nullable HttpServer server;
   private @Nullable ExecutorService requests;
   private @Nullable Display display;
   private @Nullable Shell shell;
   private Browser browser = lateNonNull();
   private @Nullable BrowserLinkListener listener;
   private String root = lateNonNull();
   private boolean enabled = true;
   private boolean loaded;

   @BeforeEach
   void createBrowser() throws Exception {
      final HttpServer server = this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      requests = Executors.newCachedThreadPool();
      server.setExecutor(requests);
      server.createContext("/", exchange -> {
         final var path = exchange.getRequestURI().getPath();
         if ("/slow-page".equals(path)) {
            try {
               Thread.sleep(2500);
            } catch (final InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
         if ("/frame".equals(path)) {
            frameRequests.incrementAndGet();
         }
         final var html = "/bridge-probe".equals(path) ? """
            <!doctype html><html><head><meta http-equiv='X-UA-Compatible' content='IE=edge'></head><body><script>
            window.addEventListener('message', function(event) {
              if (event.source !== window.parent || event.data !== 'probe') return;
              document.__previewerTakeWebLink = function() { return ['frame-1', '%s/frame-escape']; };
              document.title = '__previewerWebLinkRequest-frame-1';
              var result = 'missing';
              if (typeof window.__previewerOpenWebLink === 'function') {
                try {
                  result = window.__previewerOpenWebLink('%s/frame-escape') ? 'opened' : 'rejected';
                } catch (error) {
                  result = 'blocked';
                }
              }
              window.parent.postMessage('bridge-probe:' + result, '*');
            });
            window.parent.postMessage('bridge-probe:ready', '*');
            </script></body></html>
            """.formatted(root, root)
               : "<html><body><a id='anchor' href='#section'>Anchor</a><p id='section'>Section</p>" + "<a id='remote' href='" + root
                     + "/next'>Next</a></body></html>";
         respond(exchange, "text/html; charset=UTF-8", html);
      });
      server.start();
      root = "http://127.0.0.1:" + server.getAddress().getPort();
      final var display = this.display = new Display();
      final var shell = this.shell = new Shell(display);
      browser = BrowserTestSupport.createBrowser(shell);
      browser.addProgressListener(ProgressListener.completedAdapter(event -> loaded = true));
      final var listener = this.listener = new BrowserLinkListener(browser, target -> enabled && !embeddedTargets.contains(target) && opened
         .add(target), errors::add);
      listener.setEnabled(true);
   }

   @AfterEach
   void disposeBrowser() {
      if (listener != null) {
         listener.dispose();
      }
      if (shell != null) {
         shell.dispose();
      }
      if (display != null) {
         display.dispose();
      }
      if (server != null) {
         server.stop(0);
      }
      if (requests != null) {
         requests.shutdownNow();
      }
   }

   @Test
   void routesLinksAfterLoading() throws Exception {
      final var source = directory.resolve("preview.html");
      // Pages retain native behavior until the listener is installed after loading.
      load(source, page(1));
      await(() -> loaded);
      // Loading completes at DOMContentLoaded, before the frame. Its request reaches the server only after the browser
      // decided not to intercept the frame navigation.
      await(() -> frameRequests.get() == 1);
      assertTrue(opened.isEmpty(), "Automatic iframe loads must stay embedded");
      click("nested");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/dest?token=a%2Fb#section"), opened.get(0));

      final var svgBridge = new BrowserFunction(browser, "previewerSaveSvg", true, new String[0]) {
         @Override
         @NonNullByDefault({})
         public Object function(final Object[] arguments) {
            return true;
         }
      };
      try {
         click("blank");
         await(() -> opened.size() == 2);
         click("svg");
         await(() -> opened.size() == 3);
         assertEquals(Boolean.TRUE, evaluate("return typeof window.previewerSaveSvg === 'function';"));
         assertEquals(Boolean.TRUE, evaluate("return typeof window.__previewerOpenWebLink === 'undefined';"));
      } finally {
         svgBridge.dispose();
      }

      // A reload replaces the document, so the listener must be installed again.
      load(source, page(2));
      await(() -> loaded && Boolean.TRUE.equals(evaluate("return document.body.getAttribute('data-generation') === '2';")));
      click("normal");
      await(() -> opened.size() == 4);

      enabled = false;
      loaded = false;
      click("normal");
      await(() -> loaded && browser.getUrl().startsWith(root + "/dest"));
      assertEquals(4, opened.size());
      enabled = true;
      // The Java handler owns same-document policy; simulate its rejection of this already-open URL.
      embeddedTargets.add(opened.get(0));
      click("anchor");
      await(() -> browser.getUrl().endsWith("#section"));
      assertEquals(4, opened.size());
      click("remote");
      await(() -> opened.size() == 5);
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(strings = {"script-src 'none'", "script-src 'none'; connect-src 'none'"})
   void preservesSavedHtmlLocationEncodingBaseAndCsp(final String policy) throws Exception {
      final var source = directory.resolve("saved.html");
      final var html = "<!doctype html><html><head><meta charset='windows-1252'>" + "<meta http-equiv='Content-Security-Policy' content=\""
            + policy + "\">" + "<base href='" + root + "/relative/'></head><body><p id='text'>Café</p>"
            + "<a id='link' href='next'>Next</a></body></html>";
      final var bytes = html.getBytes(Charset.forName("windows-1252"));
      Files.write(source, bytes);
      assertTrue(browser.setUrl(source.toRealPath().toUri().toString()));
      await(() -> loaded);
      assertEquals(source.toRealPath(), Path.of(URI.create(browser.getUrl())).toRealPath());
      assertEquals("Café", evaluate("return document.getElementById('text').textContent;"));
      assertEquals(policy, evaluate("return document.querySelector('meta[http-equiv]').content;"));
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/relative/next"), opened.get(0));
      assertArrayEquals(bytes, Files.readAllBytes(source));
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(strings = {"target", "document", "window", "stopped"})
   void honorsPageCancellation(final String handlerLocation) throws Exception {
      final String receiver = "document".equals(handlerLocation) || "window".equals(handlerLocation) ? handlerLocation
            : "document.getElementById('link')";
      loadInteractivePage("<a id='link' href='" + root + "/cancelled'>Cancel</a>", receiver
            + ".addEventListener('click', function(event) { window.pageHandlerRan = true; event.preventDefault();" + ("stopped".equals(
               handlerLocation) ? "event.stopPropagation();" : "") + "});");
      final String currentUrl = browser.getUrl();
      click("link");
      assertEquals(Boolean.TRUE, evaluate("return window.pageHandlerRan === true;"));
      assertNoPendingLink();
      assertTrue(opened.isEmpty(), "Cancelled page actions must not open an external browser");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(strings = {"target", "document", "window"})
   void opensTheDestinationChosenByPage(final String handlerLocation) throws Exception {
      final String receiver = "target".equals(handlerLocation) ? "document.getElementById('link')" : handlerLocation;
      loadInteractivePage("<a id='link' href='" + root + "/original'>Retarget</a>", receiver
            + ".addEventListener('click', function(event) { window.pageHandlerRan = true;" + "document.getElementById('link').href = '"
            + root + "/retargeted?token=a%2Fb#section'; });");
      click("link");
      assertEquals(Boolean.TRUE, evaluate("return window.pageHandlerRan === true;"));
      await(() -> opened.size() == 1);
      assertEquals(List.of(URI.create(root + "/retargeted?token=a%2Fb#section")), opened);
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @CsvSource({"target, false", "document.body, false", "document, false", "window, false", "target, true", "document.body, true",
      "document, true", "window, true"})
   void preservesHandlersThatStopPropagation(final String handlerLocation, final boolean capture) throws Exception {
      final String receiver = "target".equals(handlerLocation) ? "document.getElementById('link')" : handlerLocation;
      loadInteractivePage("<a id='link' href='" + root + "/stopped'>Follow</a>", receiver + ".addEventListener('click', function(event) {"
            + "window.pageHandlerRan = true; event.stopPropagation(); }, " + capture + ");");
      click("link");
      assertEquals(Boolean.TRUE, evaluate("return window.pageHandlerRan === true;"));
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/stopped"), opened.get(0));
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(strings = {"target", "document.body", "document", "window"})
   void honorsRemainingCaptureHandlersAfterPropagationStops(final String handlerLocation) throws Exception {
      final String receiver = "target".equals(handlerLocation) ? "document.getElementById('link')" : handlerLocation;
      loadInteractivePage("<a id='link' href='" + root + "/original'>Follow</a>", receiver
            + ".addEventListener('click', function(event) { event.stopPropagation(); }, true);" + receiver
            + ".addEventListener('click', function() { document.getElementById('link').href = '" + root + "/retargeted'; }, true);");
      final String currentUrl = browser.getUrl();
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(List.of(URI.create(root + "/retargeted")), opened);
      assertTrue(browser.execute(receiver + ".addEventListener('click', function(event) { event.preventDefault(); }, true);"));
      click("link");
      assertNoPendingLink();
      assertEquals(1, opened.size(), "A later handler at the stopped receiver must still be able to cancel");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(booleans = {true, false})
   void retainsWindowObserverWhenCaptureHandlersAreAddedDuringDispatch(final boolean nestedClick) throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/window'>Follow</a>",
         "window.addEventListener('click', function(event) { if (!window.registered) { window.registered = true;"
               + "window.addEventListener('click', function(nextEvent) { nextEvent.preventDefault(); }, true);" + (nestedClick
                     ? "document.getElementById('link').click();"
                     : "") + "} event.stopPropagation(); }, true);");
      final String currentUrl = browser.getUrl();
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(List.of(URI.create(root + "/window")), opened);
      click("link");
      assertNoPendingLink();
      assertEquals(1, opened.size(), "New capture handlers apply to nested and later clicks, but not the active phase");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void preservesQueuedLinkBehaviorAndPageTitle() throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/native'>Follow</a>", "document.title = 'Preview title'; window.pageClicks = 0;"
            + "document.getElementById('link').addEventListener('click', function() {" + "this.href = '" + root
            + "/native?clicks=' + (++window.pageClicks); });");
      final String currentUrl = browser.getUrl();
      assertTrue(browser.execute("document.getElementById('link').click(); document.getElementById('link').click();"
            + "document.title = 'Updated preview title';"));
      await(() -> opened.size() == 2);
      assertEquals(List.of(URI.create(root + "/native?clicks=1"), URI.create(root + "/native?clicks=2")), opened);
      await(() -> "Updated preview title".equals(evaluate("return document.title;")));
      assertEquals(currentUrl, browser.getUrl());

      enabled = false;
      loaded = false;
      click("link");
      await(() -> loaded && browser.getUrl().equals(root + "/native?clicks=3"));
      assertEquals(2, opened.size(), "The disabled preference must retain native navigation without running page handlers twice");
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void rejectsNonWebRequestsThroughTheQueue() throws Exception {
      loadInteractivePage("<p>Preview</p>", "");
      final String currentUrl = browser.getUrl();
      assertTrue(browser.execute("document.__previewerTakeWebLink = function() { return ['test-1', 'file:///blocked']; };"
            + "document.__previewerFinishWebLink = function(id, handled) { window.queueResult = handled; };"
            + "document.title = 'Queue notification';"));
      await(() -> Boolean.FALSE.equals(evaluate("return window.queueResult;")));
      assertTrue(opened.isEmpty());
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void honorsCancellationRegisteredDuringClick() throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/cancelled'>Cancel</a>",
         "document.getElementById('link').addEventListener('click', function() {"
               + "window.addEventListener('click', function(event) { window.pageHandlerRan = true; event.preventDefault(); }); });");
      final String currentUrl = browser.getUrl();
      click("link");
      assertEquals(Boolean.TRUE, evaluate("return window.pageHandlerRan === true;"));
      assertNoPendingLink();
      assertTrue(opened.isEmpty(), "Handlers installed before bubbling reaches their receiver can still cancel the click");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void opensImageMapLinksExternally() throws Exception {
      loadInteractivePage("<img usemap='#links' src='pixel.gif' alt='Links'><map name='links'>"
            + "<area id='link' shape='rect' coords='0,0,10,10' href='" + root + "/image-map' alt='Target'></map>", "");
      final String currentUrl = browser.getUrl();
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/image-map"), opened.get(0));
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void resolvesSavedHtmlFragmentsAgainstTheExternalBase() throws Exception {
      final var source = directory.resolve("external-base.html");
      load(source, "<!doctype html><html><head><base href='" + root + "/manual'></head>"
            + "<body><a id='link' href='#section'>Section</a></body></html>");
      await(() -> loaded);
      final String currentUrl = browser.getUrl();
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/manual#section"), opened.get(0));
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void opensEmptyLinksAgainstTheExternalBase() throws Exception {
      final var source = directory.resolve("empty-link.html");
      load(source, "<!doctype html><html><head><base href='" + root + "/manual'></head>"
            + "<body><a id='link' href=''>Manual</a></body></html>");
      await(() -> loaded);
      final String currentUrl = browser.getUrl();
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/manual"), opened.get(0));
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void deniesRequestsFromCrossOriginFrame() throws Exception {
      loadInteractivePage("<iframe id='probe'></iframe>" + "<a id='top-link' href='" + root + "/top-link'>Top</a>",
         "window.addEventListener('message', function(event) {"
               + "if (event.source === document.getElementById('probe').contentWindow && event.origin === '" + root + "'"
               + " && typeof event.data === 'string' && event.data.indexOf('bridge-probe:') === 0) {"
               + "window.frameProbeResult = event.data.substring('bridge-probe:'.length); } });"
               + "document.getElementById('probe').src = '" + root + "/bridge-probe';");
      // A real HTTP frame has a different origin from the saved parent. The probe forges a queue request in its own
      // document and changes its own title; Java must only ever read requests from the top document. It also reports
      // whether any native link callback is reachable from the frame, which must stay "missing" or be refused.
      // Wait for its acknowledgement so a probe that never ran cannot pass as a refused request.
      await(() -> "ready".equals(evaluate("return window.frameProbeResult;")));
      assertTrue(browser.execute("document.getElementById('probe').contentWindow.postMessage('probe', '" + root + "');"));
      await(() -> Boolean.TRUE.equals(evaluate(
         "return typeof window.frameProbeResult === 'string' && window.frameProbeResult !== 'ready';")));
      final Object result = evaluate("return window.frameProbeResult;");
      assertTrue(List.of("missing", "blocked", "rejected").contains(result), () -> "Frame callback result: " + result);
      assertNoPendingLink();
      assertTrue(opened.isEmpty(), "A cross-origin frame must not invoke the external-link handler");

      click("top-link");
      await(() -> opened.size() == 1);
      assertEquals(URI.create(root + "/top-link"), opened.get(0));
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(strings = {"follow", "cancel", "retarget", "stop"})
   void routesOpenShadowLinksAfterPageHandlers(final String action) throws Exception {
      loadInteractivePage("<div id='host'></div>", "");
      assumeTrue(Boolean.TRUE.equals(evaluate("return typeof document.getElementById('host').attachShadow === 'function';")),
         "This browser does not support shadow DOM");
      final String pageHandler = switch (action) {
         case "cancel" -> "event.preventDefault();";
         case "retarget" -> "shadow.querySelector('a').href = '" + root + "/shadow-retargeted?token=a%2Fb#section';";
         case "stop" -> "event.stopPropagation();";
         default -> "";
      };
      assertTrue(browser.execute("var shadow = document.getElementById('host').attachShadow({mode: 'open'});"
            + "shadow.innerHTML = \"<a href='" + root + "/shadow'><span id='nested'>Follow</span></a>\";"
            + "shadow.addEventListener('click', function(event) { window.shadowHandlerRan = true;"
            + "window.shadowClickWasComposed = event.composed;" + pageHandler + "});"));
      final String currentUrl = browser.getUrl();
      // HTMLElement.click() creates a composed click; the legacy MouseEvents fixture does not cross a shadow boundary.
      assertTrue(browser.execute("document.getElementById('host').shadowRoot.getElementById('nested').click();"));
      if ("cancel".equals(action)) {
         assertNoPendingLink();
         assertTrue(opened.isEmpty(), "A shadow-root handler must be able to cancel external routing");
      } else {
         await(() -> opened.size() == 1);
         assertEquals(URI.create(root + ("retarget".equals(action) ? "/shadow-retargeted?token=a%2Fb#section" : "/shadow")), opened.get(0));
      }
      assertEquals(Boolean.TRUE, evaluate("return window.shadowHandlerRan === true;"));
      assertEquals(Boolean.TRUE, evaluate("return window.shadowClickWasComposed === true;"));
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void leavesSavedHtmlUntouchedWhileDisabled() throws Exception {
      asNonNull(listener).setEnabled(false);
      load(directory.resolve("saved-disabled.html"), "<!doctype html><html><head><meta http-equiv='X-UA-Compatible' content='IE=edge'>"
            + "</head><body><a id='link' href='" + root + "/native-disabled'>Follow</a></body></html>");
      await(() -> loaded);
      assertEquals(Boolean.TRUE, evaluate("return typeof document.__previewerWebLinkListener === 'undefined';"),
         "A disabled listener must not install any link handling");
      click("link");
      await(() -> browser.getUrl().equals(root + "/native-disabled"));
      assertTrue(opened.isEmpty());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @ParameterizedTest
   @ValueSource(booleans = {true, false})
   void honorsWindowHandlersRegisteredWhileLoading(final boolean cancel) throws Exception {
      // Inline scripts register their window handlers before the listener is installed after loading, so the listener's
      // registration hook never sees them.
      load(directory.resolve("inline.html"), "<!doctype html><html><head><meta http-equiv='X-UA-Compatible' content='IE=edge'>"
            + "</head><body><a id='link' href='" + root + "/original'>Follow</a><script>"
            + "window.addEventListener('click', function(event) {" + (cancel ? "event.preventDefault();"
                  : "document.getElementById('link').href = '" + root + "/retargeted';") + "}, true);</script></body></html>");
      await(() -> loaded);
      final String currentUrl = browser.getUrl();
      click("link");
      if (cancel) {
         pumpEvents(1_000);
         assertNoPendingLink();
         assertTrue(opened.isEmpty(), "A page handler that cancels the click must win");
      } else {
         await(() -> opened.size() == 1);
         assertEquals(List.of(URI.create(root + "/retargeted")), opened);
      }
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void appliesRuntimeTogglesToTheCurrentPage() throws Exception {
      final var listener = asNonNull(this.listener);
      listener.setEnabled(false);
      load(directory.resolve("toggle.html"), "<!doctype html><html><head><meta http-equiv='X-UA-Compatible' content='IE=edge'>"
            + "</head><body><a id='link' href='" + root + "/toggle'>Follow</a></body></html>");
      await(() -> loaded);
      final String currentUrl = browser.getUrl();

      listener.setEnabled(true);
      click("link");
      await(() -> opened.size() == 1);
      assertEquals(List.of(URI.create(root + "/toggle")), opened);
      assertEquals(currentUrl, browser.getUrl());

      listener.setEnabled(false);
      click("link");
      await(() -> browser.getUrl().equals(root + "/toggle"));
      assertEquals(1, opened.size(), "Switching off must restore native navigation without a reload");
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void followsLinkNativelyWhenJavaNeverReadsTheRequest() throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/unread'>Follow</a>", "");
      // Detaching the Java side loses every wake-up signal, like a coalesced title change or a page whose title cannot be
      // set. The page-side listener stays installed and must not swallow the click.
      asNonNull(listener).dispose();
      click("link");
      await(() -> browser.getUrl().equals(root + "/unread"));
      assertTrue(opened.isEmpty());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void leavesServerSideImageMapsToNativeNavigation() throws Exception {
      loadInteractivePage("<a href='" + root + "/server-map'><img id='map' ismap src='pixel.gif' alt='Map' width='10' height='10'></a>",
         "");
      click("map");
      await(() -> browser.getUrl().startsWith(root + "/server-map"));
      assertTrue(opened.isEmpty(), "Native activation appends the click coordinates, which a queued href would drop");
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void leavesUncancelableClicksToNativeNavigation() throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/slow-page'>Follow</a>", "");
      // A script-dispatched click that is not cancelable still activates the link, and preventDefault() cannot stop it.
      // The slow response keeps this document loaded, so Java could still read a request that was queued anyway.
      assertTrue(browser.execute("var event = document.createEvent('MouseEvents');"
            + "event.initMouseEvent('click',true,false,window,0,0,0,0,0,false,false,false,false,0,null);"
            + "document.getElementById('link').dispatchEvent(event);"));
      await(() -> browser.getUrl().equals(root + "/slow-page"));
      assertTrue(opened.isEmpty(), "A click that cannot be cancelled must not also open the link externally");
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void ignoresClickEventsThatAreNotMouseEvents() throws Exception {
      loadInteractivePage("<a id='link' href='" + root + "/next'>Follow</a>", "");
      final String currentUrl = browser.getUrl();
      // Only a MouseEvent click activates a link. A plain Event named "click" does nothing natively.
      assertTrue(browser.execute("var event = document.createEvent('Event'); event.initEvent('click', true, true);"
            + "document.getElementById('link').dispatchEvent(event);"));
      pumpEvents(1_000);
      assertTrue(opened.isEmpty(), "A click event that the browser would not follow must not open the link externally");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void ignoresNonBubblingClicksInsideLinks() throws Exception {
      loadInteractivePage("<a href='" + root + "/next'><span id='inner'>Follow</span></a>", "");
      final String currentUrl = browser.getUrl();
      // A click activates an ancestor link only while bubbling, so this dispatch has no native effect.
      assertTrue(browser.execute("document.getElementById('inner').dispatchEvent(new MouseEvent('click', {bubbles: false,"
            + " cancelable: true}));"));
      pumpEvents(1_000);
      assertTrue(opened.isEmpty(), "A click that does not activate the link must not open it externally");
      assertEquals(currentUrl, browser.getUrl());
      assertTrue(errors.isEmpty(), errors::toString);
   }

   @Test
   void releasesARequestWhoseAnswerNeverArrives() throws Exception {
      // Discarding Java's answer leaves the first request taken but unfinished, as when completing it fails.
      loadInteractivePage("<a id='first' href='" + root + "/first'>First</a><a id='second' href='" + root + "/second'>Second</a>",
         "document.__previewerFinishWebLink = function() {};");
      final String currentUrl = browser.getUrl();
      click("first");
      await(() -> opened.size() == 1);
      click("second");
      // Requests are served in order, so the second one waits until the watchdog releases the first after 10 seconds.
      pumpEvents(5_000);
      assertEquals(1, opened.size(), "A later request must wait behind the taken request");
      await(() -> opened.size() == 2);
      assertEquals(List.of(URI.create(root + "/first"), URI.create(root + "/second")), opened);
      assertEquals(currentUrl, browser.getUrl(), "A released request may already be open externally and must not navigate");
      assertTrue(errors.isEmpty(), errors::toString);
   }

   private static void respond(final HttpExchange exchange, final String contentType, final String content) throws IOException {
      final var body = content.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      // -1 announces an empty body; 0 would select chunked transfer encoding.
      exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
      try (var output = exchange.getResponseBody()) {
         output.write(body);
      }
   }

   /**
    * Loads the content and then runs the script, like a page that registers its handlers after loading, e.g. once a
    * client-side renderer has finished. The listener is installed by then, so the script can replace its functions and its
    * window handlers pass through the listener's registration hook.
    */
   private void loadInteractivePage(final String content, final String script) throws Exception {
      load(directory.resolve("interactive.html"), "<!doctype html><html><head>" + "<meta http-equiv='X-UA-Compatible' content='IE=edge'>"
            + "</head><body>" + content + "</body></html>");
      await(() -> loaded);
      if (!script.isEmpty()) {
         assertTrue(browser.execute(script), script);
      }
   }

   private String page(final int generation) {
      return "<!doctype html><html><head><meta http-equiv='X-UA-Compatible' content='IE=edge'>" + "</head><body data-generation='"
            + generation + "'>" + "<a id='normal' href='" + root + "/dest?token=a%2Fb#section'><span id='nested'>Normal</span></a>"
            + "<a id='blank' target='_blank' href='" + root + "/dest?blank=1'>Blank</a>"
            + "<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'>" + "<a xlink:href='" + root
            + "/dest?svg=1'><text id='svg'>SVG</text></a></svg>" + "<iframe src='" + root + "/frame'></iframe></body></html>";
   }

   private void load(final Path source, final String html) throws Exception {
      Files.writeString(source, html);
      loaded = false;
      assertTrue(browser.setUrl(source.toRealPath().toUri().toString()));
   }

   private @Nullable Object evaluate(final String script) {
      try {
         return browser.evaluate(script);
      } catch (final RuntimeException ex) {
         // Readiness probes can run before the native document is available.
         return null;
      }
   }

   private void assertNoPendingLink() {
      // Delivery is asynchronous. Reject an unprocessed request as well as checking the native handler's result.
      assertNull(browser.evaluate("return document.__previewerTakeWebLink();"), "Unexpected queued web link");
   }

   private void click(final String id) {
      assertTrue(browser.execute("var event = document.createEvent('MouseEvents');"
            + "event.initMouseEvent('click',true,true,window,0,0,0,0,0,false,false,false,false,0,null);" + "document.getElementById('" + id
            + "').dispatchEvent(event);"));
   }

   private void pumpEvents(final long millis) throws Exception {
      final long deadline = System.nanoTime() + millis * 1_000_000L;
      while (System.nanoTime() < deadline) {
         if (display != null && !display.readAndDispatch()) {
            Thread.sleep(10);
         }
      }
   }

   private void await(final BooleanSupplier condition) throws Exception {
      BrowserTestSupport.await(asNonNull(display), condition);
   }
}
