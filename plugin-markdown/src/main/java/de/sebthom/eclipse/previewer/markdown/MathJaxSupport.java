/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown;

import java.io.File;

/**
 * Builds the browser-side bridge from GitHub-compatible math markers to MathJax.
 *
 * @author Sebastian Thomschke
 */
final class MathJaxSupport {

   private static final String INIT_SCRIPT = """
      <style>
      math-renderer[display='block'] { display: block; margin: 1em 0; text-align: center; }
      math-renderer.previewer-math-error { color: #cf222e; font-family: monospace; }
      math-renderer.previewer-math-unsupported { font-family: monospace; }
      </style>
      <script>
      (function() {
        var markers = document.querySelectorAll('math-renderer');
        if (!markers.length) return;

        // MathJax 4 uses JavaScript features that IE cannot parse, so do not load it in the legacy SWT backend.
        if (document.documentMode) {
          for (var markerIndex = 0; markerIndex < markers.length; markerIndex++) {
            markers[markerIndex].className += ' previewer-math-unsupported';
            markers[markerIndex].title = 'Math rendering requires the Edge browser backend.';
          }
          return;
        }

        window.MathJax = {
          loader: {
            // Keeping every loader path local prevents TeX from turning preview rendering into a network request.
            paths: { mathjax: "@@MATHJAX_ROOT@@" },
            load: ['[tex]/color', 'ui/safe']
          },
          tex: {
            // Autoload and \\require could request components that are intentionally not bundled with the plugin.
            packages: { '[-]': ['autoload', 'require'], '[+]': ['color'] }
          },
          options: {
            enableMenu: false,
            // Combined MathJax components enable their SRE-based accessibility pipeline by default. The preview
            // has no MathJax menu and does not bundle its speech worker, so disable both configuration entry points.
            enableEnrichment: false,
            enableSpeech: false,
            enableBraille: false,
            enableExplorer: false,
            menuOptions: {
              settings: {
                enrich: false,
                collapsible: false,
                speech: false,
                braille: false,
                assistiveMml: false
              }
            },
            // These limits apply to MathJax output only; they do not sanitize raw HTML in Markdown.
            safeOptions: {
              allow: { URLs: 'none', classes: 'none', cssIDs: 'none', styles: 'safe' }
            }
          },
          startup: { typeset: false }
        };

        function markFailed(marker, error) {
          marker.className += ' previewer-math-error';
          marker.title = error && error.message ? error.message : String(error);
          if (typeof console !== 'undefined' && console.error) console.error(error);
        }

        function markerContent(marker) {
          var source = (marker.textContent || '').trim();
          var display = marker.getAttribute('display') === 'block'
            || (' ' + marker.className + ' ').indexOf(' js-display-math ') >= 0;
          // GitHub can classify $$...$$ as inline in headings, so marker content determines the delimiter width.
          var delimiter = source.length >= 4
            && source.substring(0, 2) === '$$'
            && source.substring(source.length - 2) === '$$' ? '$$' : '$';
          if (source.length >= delimiter.length * 2
              && source.substring(0, delimiter.length) === delimiter
              && source.substring(source.length - delimiter.length) === delimiter) {
            source = source.substring(delimiter.length, source.length - delimiter.length).trim();
          }
          return { source: source, display: display };
        }

        var script = document.createElement('script');
        script.src = "@@MATHJAX_COMPONENT@@";
        script.onload = function() {
          MathJax.startup.promise.then(function() {
            var pending = Promise.resolve();
            for (var markerIndex = 0; markerIndex < markers.length; markerIndex++) {
              (function(marker) {
                // Serial conversion also works with MathJax releases before v4 serialized promises internally.
                pending = pending.then(function() {
                  var math = markerContent(marker);
                  var metrics = MathJax.getMetricsFor(marker, math.display);
                  return MathJax.tex2svgPromise(math.source, metrics).then(function(rendered) {
                    while (marker.firstChild) marker.removeChild(marker.firstChild);
                    marker.appendChild(rendered);
                  }).catch(function(error) {
                    markFailed(marker, error);
                  });
                });
              })(markers[markerIndex]);
            }
            return pending;
          }).catch(function(error) {
            for (var markerIndex = 0; markerIndex < markers.length; markerIndex++) markFailed(markers[markerIndex], error);
          });
        };
        script.onerror = function() {
          for (var markerIndex = 0; markerIndex < markers.length; markerIndex++) {
            markFailed(markers[markerIndex], new Error('Failed to load the bundled MathJax component.'));
          }
        };
        document.head.appendChild(script);
      })();
      </script>
      """;

   static String createInitializationScript(final File mathJaxComponent) {
      // File URIs can retain apostrophes but cannot retain raw double quotes, matching the template's
      // deliberate delimiter choice.
      final String componentUri = mathJaxComponent.toURI().toASCIIString();
      // URI.resolve(".") retains the extracted resource directory without depending on platform separators.
      final String rootUri = mathJaxComponent.toURI().resolve(".").toASCIIString();
      return INIT_SCRIPT.replace("@@MATHJAX_COMPONENT@@", componentUri).replace("@@MATHJAX_ROOT@@", rootUri);
   }

   static boolean containsMath(final CharSequence html) {
      // Both the GitHub API and the local CommonMark extension use this explicit marker.
      return html.toString().contains("<math-renderer");
   }

   private MathJaxSupport() {
   }
}
