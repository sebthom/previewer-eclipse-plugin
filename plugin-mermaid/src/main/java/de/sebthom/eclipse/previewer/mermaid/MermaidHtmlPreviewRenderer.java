/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.mermaid;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public class MermaidHtmlPreviewRenderer implements HtmlPreviewRenderer {

   private File mermaidJS;

   private static final String MERMAID_INIT_SCRIPT = """
      <script>
        try {
          mermaid.initialize({ startOnLoad: false, theme: '$$THEME$$' });
          mermaid.run({ querySelector: '#mermaid', }).then(() => {
            const svg = document.querySelector("#mermaid svg");
            const placeholder = document.getElementById("placeholder");
            const parent = placeholder.parentNode;
            parent.appendChild(svg);
            parent.removeChild(placeholder);
          });
        } catch(err) {
          alert(err);
        }
      </script>
      """;

   public MermaidHtmlPreviewRenderer() throws IOException {
      mermaidJS = Plugin.resources().extract(Constants.MERMAID_JS);
   }

   @Override
   public void dispose() {
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {

      out.append("""
         <!DOCTYPE html>
         <html>
         <head>
           <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
         </head>
         <body>
         """);

      if (MiscUtils.isDarkEclipseTheme()) {
         out.append("<style>html, body { background: #585858; color: #fff; }</style>");
      }

      final var shortPath = source.path().getParent().getFileName().resolve(asNonNull(source.path().getFileName()));

      if (SystemUtils.IS_OS_WINDOWS) {
         out.append(
            """
               <div id="ieNotSupported" style="display: none; padding: 20px; background-color: #f44336; color: white; text-align: center; font-size: 18px;">
                 Previewing Mermaid diagrams is not supported using the Internet Explorer WebView.<br/>
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

      out.append("<script src='" + mermaidJS.toURI() + "'></script>");
      out.append(StringUtils.htmlSvgWithHoverDownloadButton("<span id='placeholder'></span>"));
      out.append("""
         <pre id="mermaid" style="width:100%">
           """ + Strings.replace(source.contentAsString(), "<", "&lt;") + """
         </pre>
         """);
      out.append(MERMAID_INIT_SCRIPT.replace("$$THEME$$", MiscUtils.isDarkEclipseTheme() ? "dark" : "default"));
      out.append(StringUtils.htmlInfoBox(shortPath + " " + MiscUtils.getCurrentTime()));
      out.append("</body></html>");
   }
}
