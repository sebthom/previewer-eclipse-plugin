/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.pikchr;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * Creates embeddable Pikchr placeholders and the browser-side scripts that replace them with rendered SVG.
 *
 * @author Sebastian Thomschke
 */
public final class PikchrRendering {

   // The published JavaScript API forwards these flags unchanged to Pikchr, so the values must match pikchr.h.
   private static final int PIKCHR_DARK_MODE = 0x0002;
   private static final int PIKCHR_PLAINTEXT_ERRORS = 0x0001;

   private static final AtomicLong PLACEHOLDER_IDS = new AtomicLong();

   private static @Nullable File pikchrJS;

   /**
    * Adds the shared Pikchr runtime and styles to an HTML page.
    * <p>
    * The page must declare UTF-8 before this method is called. The vendored Emscripten single-file build stores its
    * Wasm payload in UTF-8 code units, so decoding the script with a legacy page charset corrupts the embedded binary.
    */
   public static void appendPageSupport(final Appendable out) throws IOException {
      out.append("<script src='" + getPikchrJS().toURI() + "'></script>");
      out.append("""
         <style>
         .previewer-pikchr-diagram {
           clear: both;
         }
         .previewer-pikchr-center [id$='-outer'] {
           margin-left: auto;
           margin-right: auto;
           width: max-content;
           max-width: 100%;
         }
         .previewer-pikchr-float-left {
           clear: none;
           float: left;
           margin-right: 1em;
         }
         .previewer-pikchr-float-right {
           clear: none;
           float: right;
           margin-left: 1em;
         }
         .previewer-pikchr-indent {
           margin-left: 3em;
         }
         .previewer-pikchr-error .download-button {
           display: none !important;
         }
         .previewer-pikchr-output.previewer-pikchr-error-message {
           color: #c62828;
           font-family: monospace;
           white-space: pre-wrap;
         }
         </style>
         """);
   }

   private static synchronized File getPikchrJS() throws IOException {
      var pikchrJS = PikchrRendering.pikchrJS;
      if (pikchrJS == null) {
         PikchrRendering.pikchrJS = pikchrJS = Plugin.resources().extract(Constants.PIKCHR_JS);
      }
      return pikchrJS;
   }

   private static String layoutClass(final Collection<String> modifiers) {
      for (final String modifier : modifiers) {
         switch (modifier.toLowerCase(Locale.ROOT)) {
            case "center":
               return " previewer-pikchr-center";
            case "float-left":
               return " previewer-pikchr-float-left";
            case "float-right":
               return " previewer-pikchr-float-right";
            case "indent":
               return " previewer-pikchr-indent";
            default:
               // GitHub's "toggle" token controls source/result disclosure, which the Preview view does not
               // expose. Keep scanning so the first following layout token still applies. Unknown tokens are
               // treated the same way for forward compatibility.
         }
      }
      return "";
   }

   public static String renderToHtmlFragment(final String source, final Collection<String> modifiers, final boolean useDarkTheme) {
      final String outputId = "previewer-pikchr-output-" + PLACEHOLDER_IDS.incrementAndGet();
      final String diagramId = outputId + "-diagram";
      // Base64 preserves the exact UTF-8 source without allowing Pikchr text to terminate the surrounding script element.
      final String encodedSource = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
      final int flags = PIKCHR_PLAINTEXT_ERRORS | (useDarkTheme ? PIKCHR_DARK_MODE : 0);
      final var html = new StringBuilder();

      html.append("<div class='previewer-pikchr-diagram" + layoutClass(modifiers) + "' id='" + diagramId + "'>");
      html.append(StringUtils.htmlSvgWithHoverDownloadButton("<span class='previewer-pikchr-output' id='" + outputId
            + "'>Rendering Pikchr diagram...</span>"));
      html.append("""
          <script>
          (function () {
            var output = document.getElementById('$$OUTPUT_ID$$');
            var diagram = document.getElementById('$$DIAGRAM_ID$$');

           function showError(error) {
             diagram.classList.add('previewer-pikchr-error');
             output.classList.add('previewer-pikchr-error-message');
             output.textContent = error && error.message ? error.message : String(error);
           }

           if (document.documentMode || /MSIE|Trident/.test(window.navigator.userAgent)) {
             showError('Previewing Pikchr diagrams is not supported using the Internet Explorer WebView.\\n\\n'
               + 'Switch to Edge WebView2 under Window > Preferences > Previewer > Web View Implementation.');
             return;
           }

            try {
              var bytes = Uint8Array.from(atob('$$SOURCE$$'), function (character) { return character.charCodeAt(0); });
              var source = new TextDecoder('utf-8').decode(bytes);
              // One Wasm instance is sufficient for every fence in the page and avoids repeating its asynchronous initialization.
               var runtime = window.previewerPikchrRuntimePromise
                 || (window.previewerPikchrRuntimePromise = window.loadPikchr());
               runtime.then(function (pikchr) {
                 // The callable form returns only SVG. render() preserves the negative width and diagnostic text.
                 var result = pikchr.render(source, 'previewer-pikchr-svg', $$FLAGS$$);
                 if (result.width < 0) {
                  showError(result.svg);
                  return;
                }

                // Parse and import the output instead of assigning innerHTML. This validates SVG and prevents a
                // Pikchr error string from becoming active HTML.
                var svgDocument = new DOMParser().parseFromString(result.svg, 'image/svg+xml');
                var svg = svgDocument.documentElement;
               if (!svg || svg.localName !== 'svg' || svg.namespaceURI !== 'http://www.w3.org/2000/svg'
                     || svgDocument.querySelector('parsererror')) {
                 throw new Error('Pikchr returned invalid SVG output.');
               }
               while (output.firstChild) output.removeChild(output.firstChild);
               output.appendChild(document.importNode(svg, true));
             }).catch(showError);
           } catch (error) {
             showError(error);
           }
         })();
         </script>
         """.replace("$$OUTPUT_ID$$", outputId).replace("$$DIAGRAM_ID$$", diagramId).replace("$$SOURCE$$", encodedSource).replace(
         "$$FLAGS$$", Integer.toString(flags)));
      html.append("</div>");
      return html.toString();
   }

   private PikchrRendering() {
   }
}
