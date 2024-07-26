/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.drawio;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * @author Sebastian Thomschke
 */
public class DrawIoHtmlPreviewRenderer implements HtmlPreviewRenderer {

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));

      out.append("<!DOCTYPE html>");
      out.append("<html>");
      out.append("<head>");
      out.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
      out.append("<script src='https://www.draw.io/js/viewer.min.js'></script>");
      out.append("</head>");
      out.append("<body>\n\n");
      if (SystemUtils.IS_OS_WINDOWS) {
         out.append(
            """
               <div id="ieNotSupported" style="display: none; padding: 20px; background-color: #f44336; color: white; text-align: center; font-size: 18px;">
                 Previewing draw.io diagrams is not supported using the Internet Explorer WebView.<br/>
                 <br/>
                 Please switch to Edge WebView2 under <b>Window &gt; Preferences &gt; Previewer &gt; Web View Implementation</b>.
               </div>

               <script>
                 if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
                   document.getElementById('ieNotSupported').style.display = 'block';
                 }
               </script>
               """);
      }

      out.append("""
         <div id='drawio-diagram'></div>
         <script>
         function escapeHTML(text) {
           const escapeMap = {
             "&": "&amp;",
             "'": "&#x27;",
             "`": "&#x60;",
             '"': "&quot;",
             "<": "&lt;",
             ">": "&gt;",
           }
           return text.replace(/[&'`"<>]/g, char => escapeMap[char])
         }

         // https://www.drawio.com/doc/faq/embed-html-options
         const mxgraphDataJSON = JSON.stringify({
           editable: false,
           nav: false,
           toolbar: null,
           edit: null,
           move: false,
           resize: false,
           lightbox: false,
           xml: `""" + source.contentAsString() + """
         `
         })

         document.getElementById('drawio-diagram').innerHTML = `<div class="mxgraph"
           style="border:1px solid transparent;"
           data-mxgraph="${escapeHTML(mxgraphDataJSON)}"></div>`
         window.GraphViewer.processElements()
         </script>
         """);
      out.append(StringUtils.htmlInfoBox(shortPath + " " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
