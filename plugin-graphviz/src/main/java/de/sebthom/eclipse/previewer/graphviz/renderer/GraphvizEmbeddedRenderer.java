/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.renderer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.Constants;
import de.sebthom.eclipse.previewer.graphviz.Plugin;

/**
 * @author Sebastian Thomschke
 */
public enum GraphvizEmbeddedRenderer implements GraphvizRenderer {

   INSTANCE;

   private static final AtomicLong PLACEHOLDER_IDS = new AtomicLong();

   private final File vizJS;

   GraphvizEmbeddedRenderer() {
      try {
         vizJS = Plugin.resources().extract(Constants.VIZ_JS);
      } catch (final IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   @Override
   public void dotToHTML(final ContentSource source, final Appendable out) throws IOException {
      final String id = "previewer_graphviz_" + PLACEHOLDER_IDS.incrementAndGet();
      final String buttonId = id + "_download";
      final String innerId = id + "_inner";
      final String unsupportedId = id + "_unsupported";
      final String encodedDot = Base64.getEncoder().encodeToString(source.contentAsString().getBytes(StandardCharsets.UTF_8));

      out.append("""

         <div id='${UNSUPPORTED_ID}' class='previewer-graphviz-unsupported'>
           Previewing GraphViz diagrams using the embedded viz.js library is not supported using the Internet Explorer WebView.<br/>
           <br/>
           Please switch to Edge WebView2 under <b>Window &gt; Preferences &gt; Previewer &gt; Web View Implementation</b> or
           switch to the GraphViz DOT renderer under <b>Window &gt; Preferences &gt; Previewer &gt; GraphViz &gt; GraphViz renderer</b> .
         </div>
         <div id='${ID}'>
           <button class='download-button' id='${BUTTON_ID}'>Download SVG</button>
           <div id='${INNER_ID}'></div>
         </div>

         <style>
           #${UNSUPPORTED_ID} {
             display: none;
             padding: 20px;
             background-color: #f44336;
             color: white;
             text-align: center;
             font-size: 18px;
           }

           #${ID} {
             position: relative;
           }

           #${ID}:hover .download-button {
             display: block;
           }

           #${ID} .download-button {
             position: absolute;
             top: 10px;
             left: 10px;
             padding: 5px 10px;
             font-size: 12px;
             background-color: rgba(0, 0, 0, 0.7);
             color: white;
             border: none;
             border-radius: 5px;
             cursor: pointer;
             display: none;
           }
         </style>
         <script>
         (function() {
           try {
             const unsupportedWebView = window.navigator.userAgent.match(/MSIE|Trident|Edge/);
             const unsupportedMessage = document.getElementById('${UNSUPPORTED_ID}');
             if (unsupportedWebView) {
               unsupportedMessage.style.display = 'block';
               return;
             }

             const inner = document.getElementById('${INNER_ID}');
             const downloadButton = document.getElementById('${BUTTON_ID}');
             if (!inner || !downloadButton) {
               throw new Error('Graphviz diagram container was not found.');
             }
             downloadButton.addEventListener('click', function () {
               try {
                 const svgString = inner.innerHTML;
                 const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
                 if (window.navigator.msSaveOrOpenBlob) {
                   window.navigator.msSaveOrOpenBlob(blob, 'graphic.svg');
                 } else {
                   const downloadLink = document.createElement('a');
                   downloadLink.href = URL.createObjectURL(blob);
                   downloadLink.download = 'graphic.svg';
                   document.body.appendChild(downloadLink);
                   downloadLink.click();
                   document.body.removeChild(downloadLink);
                 }
               } catch (err) {
                 alert(err);
               }
             });
             const renderGraphviz = function() {
               Viz.instance().then(function(viz) {
                 const dotBytes = Uint8Array.from(atob('${DOT_SOURCE}'), function(ch) { return ch.charCodeAt(0); });
                 const dot = new TextDecoder('utf-8').decode(dotBytes);
                 const svg = viz.renderSVGElement(dot);
                 inner.textContent = '';
                 inner.appendChild(svg);
               }).catch(function(err) {
                 alert(err);
               });
             };
             if (window.Viz) {
               renderGraphviz();
             } else {
               const script = document.createElement('script');
               script.src = '${VIZ_JS}';
               script.onload = renderGraphviz;
               script.onerror = function() {
                 alert('Failed to load embedded Graphviz renderer.');
               };
               (document.head || document.documentElement).appendChild(script);
             }
           } catch(err){
             alert(err);
           }
         })();
         </script>
         """.replace("${VIZ_JS}", vizJS.toURI().toString()) //
         .replace("${ID}", id) //
         .replace("${BUTTON_ID}", buttonId) //
         .replace("${INNER_ID}", innerId) //
         .replace("${UNSUPPORTED_ID}", unsupportedId) //
         .replace("${DOT_SOURCE}", encodedDot));
   }
}
