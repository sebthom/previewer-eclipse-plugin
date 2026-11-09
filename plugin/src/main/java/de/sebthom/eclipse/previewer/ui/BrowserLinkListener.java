/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import java.net.URI;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.TitleListener;

import de.sebthom.eclipse.previewer.util.MiscUtils;

/**
 * Routes HTML link activation after page handlers through a document-scoped queue unaffected by CSP.
 * Shares the same listener between early and post-load installation. Routing starts disabled; see {@link #setEnabled}.
 *
 * @author Sebastian Thomschke
 */
final class BrowserLinkListener {

   private static final String INSTALL_SCRIPT = """
      (function() {
        if (document.__previewerWebLinkListener) return;
        var pendingLinks = [];
        var nextLinkId = 0;
        var requestPrefix = new Date().getTime().toString(36) + "-" + Math.random().toString(36).substring(2) + "-";
        var titlePrefix = "__previewerWebLinkRequest-";
        // Java normally answers within milliseconds. These limits only recover a queue whose wake-up signal was lost or
        // could not be sent at all, e.g. after the page removed its head element so that the title setter does nothing.
        var signalRetryDelay = 250;
        var nativeFallbackDelay = 2000;
        var takenReleaseDelay = 10000;
        var savedTitle;
        var signalTitle;
        var watchdogTimer;
        function rememberPageTitle() {
          // Preserve a title changed by the page while a request was pending.
          if (document.title !== signalTitle) savedTitle = document.title;
        }
        function notifyPendingLink() {
          var pending = pendingLinks[0];
          rememberPageTitle();
          if (!pending.signals++) pending.headSince = new Date().getTime();
          // A distinct value per retry remains a title change even if the engine coalesced an earlier signal.
          signalTitle = titlePrefix + pending.id + (pending.signals > 1 ? "-" + pending.signals : "");
          document.title = signalTitle;
          clearTimeout(watchdogTimer);
          watchdogTimer = setTimeout(checkPendingLink, signalRetryDelay);
        }
        function checkPendingLink() {
          var pending = pendingLinks[0];
          if (!pending) return;
          var waited = new Date().getTime() - pending.headSince;
          if (!pending.taken) {
            // Java never read the request. Fall back to the native action rather than swallowing the click.
            if (waited >= nativeFallbackDelay) completePendingLink(pending, true);
            else notifyPendingLink();
          } else if (waited >= takenReleaseDelay) {
            // Java read the request but its answer never arrived. It may already have opened the link externally, so
            // release the queue without a native fallback that could open the link a second time.
            completePendingLink(pending, false);
          } else {
            watchdogTimer = setTimeout(checkPendingLink, signalRetryDelay);
          }
        }
        function completePendingLink(pending, followNatively) {
          rememberPageTitle();
          pendingLinks.shift();
          if (pendingLinks.length) {
            notifyPendingLink();
          } else {
            clearTimeout(watchdogTimer);
            document.title = savedTitle;
          }
          // Page handlers already ran. A detached anchor performs only the declined link's native action; unlike other
          // elements, HTML lets an <a> element navigate while it is not connected to a document.
          if (followNatively) pending.link.click();
        }
        document.__previewerTakeWebLink = function() {
          var pending = pendingLinks[0];
          if (!pending || pending.taken) return null;
          pending.taken = true;
          return [pending.id, pending.target];
        };
        document.__previewerFinishWebLink = function(id, handled) {
          var pending = pendingLinks[0];
          // A request released by the watchdog no longer accepts a late answer.
          if (!pending || pending.id !== id) return;
          completePendingLink(pending, !handled);
        };
        function enqueueLink(element, target) {
          var link = document.createElement("a");
          for (var index = 0; index < element.attributes.length; index++) {
            var attribute = element.attributes[index];
            if (!/^on/i.test(attribute.name)) link.setAttribute(attribute.name, attribute.value);
          }
          link.href = target;
          if (!pendingLinks.length) savedTitle = document.title;
          pendingLinks.push({id: requestPrefix + (++nextLinkId), target: target, link: link, taken: false, signals: 0});
          if (pendingLinks.length === 1) notifyPendingLink();
        }
        var addWindowListener = window.addEventListener;
        var removeWindowListener = window.removeEventListener;
        var windowListener;
        function moveWindowListener() {
          var previous = windowListener;
          if (previous) {
            var activeEvent = window.event;
            if (activeEvent && activeEvent.type === "click" && activeEvent.currentTarget === window
                && activeEvent.eventPhase === 1) {
              // A listener added during this phase is absent from its snapshot. Keep the old observer for that
              // dispatch, while nested and subsequent clicks use the new observer after the added page handler.
              // Only the innermost active click is remembered: if a page handler dispatches a nested click that adds
              // the listener, the outer click is not routed and keeps its native navigation. Tracking every active
              // dispatch would risk routing one click twice, for a case that needs nested script clicks.
              // Handlers registered without this hook, through EventTarget.prototype.addEventListener or a reference
              // saved before installation, can follow the observer. If an earlier window handler stopped propagation,
              // such a handler can no longer cancel or redirect a routed link. Giving up routing whenever propagation
              // stops at the window would break pages that stop it deliberately, so this case is accepted.
              previous.pendingEvent = activeEvent;
              setTimeout(function() { removeWindowListener.call(window, "click", previous, true); }, 0);
            } else {
              removeWindowListener.call(window, "click", previous, true);
            }
          }
          var listener = windowListener = function(event) {
            if (listener === windowListener || listener.pendingEvent === event) handleClick(event);
          };
          addWindowListener.call(window, "click", listener, true);
        }
        // Window has no capturing ancestor. Keep its observer after page registrations so a window-level
        // stopPropagation() still reaches us after the remaining handlers can cancel or retarget the link.
        window.addEventListener = function(type) {
          var result = addWindowListener.apply(this, arguments);
          if (this === window && type === "click") moveWindowListener();
          return result;
        };
        function handleClick(event) {
          // Java writes this flag on every load and preference change; a document keeps the listener installed while
          // routing was enabled after the preference is switched off.
          if (document.__previewerWebLinksEnabled === false) return;
          // Only a MouseEvent click follows a link natively; a plain Event named "click" must stay without effect.
          if (event.defaultPrevented || !(event instanceof MouseEvent) || event.button !== 0) return;
          // Outside an open shadow root, event.target is its host. Use the actual event path for both finding
          // the link and observing page handlers across shadow boundaries and slots.
          var receivers = typeof event.composedPath === "function" ? event.composedPath() : [];
          if (!receivers.length) {
            // Older engines such as IE have no shadow DOM or composedPath().
            for (var receiver = event.target; receiver; receiver = receiver.parentNode) {
              receivers.push(receiver);
            }
            receivers.push(window);
          }
          var element = null;
          for (var index = 0; index < receivers.length; index++) {
            var candidate = receivers[index];
            // Native activation of a server-side image map appends the click coordinates to the URL. A queued href
            // would drop them, so leave such links to the browser.
            if (candidate.tagName && /^img$/i.test(candidate.tagName) && candidate.hasAttribute("ismap")) return;
            if (candidate.tagName && /^(a|area)$/i.test(candidate.tagName)) {
              // A click activates an ancestor link only while bubbling; a non-bubbling one keeps its native effect.
              if (index > 0 && !event.bubbles) return;
              element = candidate;
              break;
            }
          }
          if (!element) return;
          var receiverIndex = receivers.length - 1;
          var capturing = true;
          function addObserver() {
            var receiver = receivers[receiverIndex];
            var add = receiver === window ? addWindowListener : receiver.addEventListener;
            add.call(receiver, "click", finish, capturing);
          }
          function removeObserver() {
            var receiver = receivers[receiverIndex];
            var remove = receiver === window ? removeWindowListener : receiver.removeEventListener;
            remove.call(receiver, "click", finish, capturing);
          }
          function cleanup() {
            removeObserver();
            clearTimeout(cleanupTimer);
          }
          function finish(currentEvent) {
            if (currentEvent !== event) return;
            // IE runs target listeners in registration order without separate capture and bubble passes.
            // Our last target listener already follows both kinds of page handler there.
            if (capturing && receiverIndex === 0 && document.documentMode) {
              removeObserver();
              capturing = false;
            }
            if (!event.cancelBubble) {
              if (capturing) {
                removeObserver();
                if (receiverIndex > 0) receiverIndex--;
                else capturing = false;
                addObserver();
                return;
              }
              if (event.bubbles && receiverIndex + 1 < receivers.length) {
                removeObserver();
                receiverIndex++;
                addObserver();
                return;
              }
            }
            cleanup();
            if (event.defaultPrevented) return;
            // Read the final destination after page handlers, including changes to href or the document's base.
            // An empty href is still a link: it resolves to the document's base, which can be an external page.
            var href = element.getAttribute("href");
            if (href === null) href = element.getAttribute("xlink:href");
            if (href === null) return;
            var resolved = document.createElement("a");
            resolved.href = href;
            var target = resolved.href;
            // Even a raw #fragment may resolve to an external base. Java owns the same-document exception.
            if (!/^https?:/i.test(target)) return;
            // A script can dispatch a click that is not cancelable. preventDefault() cannot stop its native navigation,
            // so queueing it as well would open the link in both places.
            if (!event.cancelable) return;
            event.preventDefault();
            enqueueLink(element, target);
          }
          // Follow both phases after page handlers. Capture can stop before the target or its bubble handlers.
          // A page can suppress all later listeners with stopImmediatePropagation(); release unused observers.
          var cleanupTimer = setTimeout(cleanup, 0);
          finish(event);
        }
        moveWindowListener();
        document.__previewerWebLinkListener = true;
      })();
      """;

   // A document that received the listener while routing was enabled can be shown again after it was disabled, e.g.
   // when the browser restores it from its back-forward cache. Every load therefore writes the flag in both states, not
   // only on toggles.
   private static final String ENABLE_SCRIPT = INSTALL_SCRIPT + "document.__previewerWebLinksEnabled = true;";
   private static final String DISABLE_SCRIPT = "document.__previewerWebLinksEnabled = false;";

   private final Browser browser;
   private final BiPredicate<URI, String> handler;
   private final Consumer<RuntimeException> onError;
   private final LocationListener locationListener;
   private final ProgressListener progressListener;
   private final TitleListener titleListener;
   private boolean enabled;
   private boolean queuedLinkCheckScheduled;
   // Removing the listeners cannot cancel a queue check already scheduled with asyncExec.
   private boolean disposed;

   /**
    * @param handler receives the parsed target for policy checks and the location as the browser serialized it. Returns true
    *           if it handled the link, which cancels the embedded navigation.
    */
   BrowserLinkListener(final Browser browser, final BiPredicate<URI, String> handler, final Consumer<RuntimeException> onError) {
      this.browser = browser;
      this.handler = handler;
      this.onError = onError;
      locationListener = LocationListener.changedAdapter(event -> {
         if (event.top) {
            apply(false);
         }
      });
      // Some engines omit location-change events for a reload of the same URL. Installation is idempotent per document.
      progressListener = ProgressListener.completedAdapter(event -> apply(true));
      // GTK's BrowserFunction uses CSP-controlled XHR and permits calls from cross-origin frames despite its top flag.
      // A title notification only wakes Java; the request itself is read from the current top document.
      titleListener = event -> {
         if (!queuedLinkCheckScheduled && !browser.isDisposed()) {
            queuedLinkCheckScheduled = true;
            // IE delivers title changes synchronously. Read the queue after the originating script returns.
            browser.getDisplay().asyncExec(() -> {
               queuedLinkCheckScheduled = false;
               openQueuedLink();
            });
         }
      };
      browser.addLocationListener(locationListener);
      browser.addProgressListener(progressListener);
      browser.addTitleListener(titleListener);
      apply(false);
   }

   private boolean openLink(final String location) {
      final URI target = MiscUtils.toBrowserURI(location);
      if (target == null)
         return false;
      final String scheme = target.getScheme();
      // The queue contains page-controlled input; enforce the scheme boundary before invoking the handler.
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
         return false;
      try {
         return handler.test(target, location);
      } catch (final RuntimeException ex) {
         onError.accept(ex);
         return false;
      }
   }

   private void openQueuedLink() {
      if (disposed || browser.isDisposed())
         return;
      try {
         // The title is only a wake-up signal; read the request from the current top document, never from a frame's title.
         // Inspect every title event because browsers can coalesce our signal with a subsequent page title update.
         final Object result = browser.evaluate("return document.__previewerTakeWebLink ? document.__previewerTakeWebLink() : null;");
         if (!(result instanceof final Object[] request))
            return;
         if (request.length != 2 || !(request[0] instanceof final String requestId) || !(request[1] instanceof final String location)
               || !requestId.matches("[a-z0-9-]{1,128}"))
            return;
         final boolean handled = openLink(location);
         // The document-specific ID prevents a delayed response from completing a request after navigation or reload.
         if (!browser.isDisposed() && !browser.execute("if (document.__previewerFinishWebLink) document.__previewerFinishWebLink('"
               + requestId + "', " + handled + ");")) {
            onError.accept(new IllegalStateException("Cannot complete the preview web link request."));
         }
      } catch (final SWTException ex) {
         onError.accept(ex);
      }
   }

   private void apply(final boolean reportFailure) {
      if (disposed || browser.isDisposed())
         return;
      try {
         // Early events still attempt the update; only their error reporting is deferred.
         final boolean applied = browser.execute(enabled ? ENABLE_SCRIPT : DISABLE_SCRIPT);
         if (reportFailure && !applied) {
            onError.accept(new IllegalStateException("Cannot update the preview web link listener."));
         }
      } catch (final SWTException ex) {
         // The document can still be unavailable at a location change. Completion provides the final retry.
         if (reportFailure) {
            onError.accept(ex);
         }
      }
   }

   /**
    * Enables or disables link routing for the current document and all later ones. While disabled, no listener is installed,
    * and a listener already installed in a page leaves clicks to the browser. Must be called on the UI thread.
    */
   void setEnabled(final boolean enabled) {
      if (this.enabled == enabled)
         return;
      this.enabled = enabled;
      apply(true);
   }

   void dispose() {
      disposed = true;
      if (!browser.isDisposed()) {
         browser.removeLocationListener(locationListener);
         browser.removeProgressListener(progressListener);
         browser.removeTitleListener(titleListener);
      }
   }
}
