/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;

import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public final class StringUtils {

   private static final Pattern[] RESOURCE_TAG_PATTERNS = new Pattern[] { //
      Pattern.compile("<img[^>]*src=[\"']([^\"']*)[\"']"), //
      Pattern.compile("<script[^>]*src=[\"']([^\"']*)[\"']"), //
      Pattern.compile("<link[^>]*href=[\"']([^\"^]*)[\"']"), //
      Pattern.compile("<a[^>]*href=[\"']([^\"']*)[\"']")};

   private static boolean isRelativeOrLocalPath(final String path) {
      return !Strings.startsWithIgnoreCase(path, "http://") //
            && !Strings.startsWithIgnoreCase(path, "https://") //
            && !Strings.startsWithIgnoreCase(path, "ftp://") //
            && Strings.startsWithIgnoreCase(path, "file:/") //
            && !path.startsWith("//") //
            && !path.startsWith("/");
   }

   public static boolean htmlContainsRelativeOrLocalPaths(final CharSequence html) {
      for (final Pattern pattern : RESOURCE_TAG_PATTERNS) {
         final Matcher matcher = pattern.matcher(html);
         while (matcher.find()) {
            final String path = asNonNull(matcher.group(1));
            if (isRelativeOrLocalPath(path))
               return true;
         }
      }
      return false;
   }

   public static String htmlInfoBox(final String htmlContent) {
      return """

         <style>
         #previewer_infobox {
           position: fixed;
           top: 2px;
           right: 2px;
           opacity: 0.8;
           padding: 1px 4px 1px 4px;
           background-color: #f9f9f9;
           border: 1px solid #ccc;
           box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
           border-radius: 3px;
           font-size: 0.8em;
           font-family: Tahoma, sans-serif;
         }
         #previewer_infobox.minimized {
           width: 10px;
         }
         </style>

         <div id="previewer_infobox">
           <button style="border:none; padding:0px; background:none;"
             onmouseover="this.style.backgroundColor='#C0DCF3';"
             onmouseout="this.style.background='transparent';"
             onclick="previewer_infobox_toggle()">&#11166;</button>
           <span>
            """ + htmlContent + """
           </span>
         </div>

         <script>
           function previewer_infobox_toggle() {
             if(document.querySelector('#previewer_infobox').classList.contains("minimized")) {
               document.querySelector('#previewer_infobox').classList.remove('minimized');
               document.querySelector('#previewer_infobox > span').style.display = 'inline';
               document.querySelector('#previewer_infobox button').innerHTML = '&#11166;';
             } else {
               document.querySelector('#previewer_infobox').classList.add('minimized');
               document.querySelector('#previewer_infobox > span').style.display = 'none';
               document.querySelector('#previewer_infobox button').innerHTML = '&#11164;';
             }
           }
         </script>

         """;
   }

   public static String htmlSvgWithHoverDownloadButton(final String svgContent) {
      return Strings.replaceEach("""

         <div id="${SVG_ID}-outer">
           <button class="download-button" id="${SVG_ID}-download">Download SVG</button>
           <div id="${SVG_ID}-inner">${SVG}</div>
         </div>

         <style>
           #${SVG_ID}-outer {
             position: relative;
           }

           #${SVG_ID}-outer:hover .download-button {
             display: block;
           }

           #${SVG_ID}-outer .download-button {
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
           document.getElementById("${SVG_ID}-download").addEventListener("click", function () {
             try {
               const svgString = document.getElementById("${SVG_ID}-inner").innerHTML;
               const blob = new Blob([svgString], { type: "image/svg+xml;charset=utf-8" });
               if (window.navigator.msSaveOrOpenBlob) {
                 window.navigator.msSaveOrOpenBlob(blob, "graphic.svg");
               } else {
                 const downloadLink = document.createElement("a");
                 downloadLink.href = URL.createObjectURL(blob);
                 downloadLink.download = "graphic.svg";
                 document.body.appendChild(downloadLink);
                 downloadLink.click();
                 document.body.removeChild(downloadLink);
               }
            } catch (err) {
              alert(err);
            }
           });
         </script>

         """, "${SVG_ID}", RandomStringUtils.insecure().nextAlphabetic(16), "${SVG}", svgContent);
   }

   public static String htmlSvgZoomControls() {
      return """

         <style>
           media print {
             .no-print, .no-print * { display: none !important; }
           }
           #zoom-controls {
             cursor: move;
             opacity: 0.8;
             z-index:10;
             position: fixed;
             top: 5px;
             left: 5px;
             background-color: #f1f1f1;
             padding: 5px;
             border: 1px solid #ccc;
             border-radius: 5px;
             box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
           }
           #zoom-controls button {
             cursor: default;
             margin: 2px;
             padding-left: 5px;
             padding-right: 5px;
             font-family: sans-serif;
             font-size: 0.8em;
           }
         </style>

         <div id="zoom-controls" class="no-zoom no-print">
           <button id="zoom-in">+</button>
           <button id="zoom-reset">Reset</button>
           <button id="zoom-out">-</button>
         </div>

         <script>
           function makeMovable(elem) {
             elem.addEventListener('mousedown', function(e) {
               const offsetX = e.clientX - elem.getBoundingClientRect().left;
               const offsetY = e.clientY - elem.getBoundingClientRect().top;

               function mouseMoveHandler(e) {
                 elem.style.left = (e.clientX - offsetX) + "px";
                 elem.style.top = (e.clientY - offsetY) + "px";
               }

               function mouseUpHandler() {
                 document.removeEventListener('mousemove', mouseMoveHandler);
                 document.removeEventListener('mouseup', mouseUpHandler);
               }

               document.addEventListener('mousemove', mouseMoveHandler);
               document.addEventListener('mouseup', mouseUpHandler);
             });
           }
           makeMovable(document.getElementById('zoom-controls'));

           function getZoomLevel(elem) {
             const transform = elem.style.transform;
             if (!transform) return 1;

             const scaleMatch = transform.match(/scale\\(([^)]+)\\)/);
             return scaleMatch ? parseFloat(scaleMatch[1]) : 1;
           }
           function setZoom(elem, level) {
             elem.style.transform = "scale(" + level + ")";
             elem.style.transformOrigin = '0 0';
           }

           document.getElementById('zoom-in').addEventListener('click', function () {
             document.querySelectorAll('svg').forEach(function (elem) {
               setZoom(elem, getZoomLevel(elem) + 0.1);
             });
           });

           document.getElementById('zoom-out').addEventListener('click', function () {
             document.querySelectorAll('svg').forEach(function (elem) {
               setZoom(elem, getZoomLevel(elem) - 0.1);
             });
           });

           document.getElementById('zoom-reset').addEventListener('click', function () {
             document.querySelectorAll('svg').forEach(function (elem) {
               setZoom(elem, 1);
             });
           });

           if (window.navigator.userAgent.match(/MSIE|Trident|Edge/)) {
             if (window.NodeList && !NodeList.prototype.forEach) {
               NodeList.prototype.forEach = Array.prototype.forEach;
             }
             if (window.HTMLCollection && !HTMLCollection.prototype.forEach) {
               HTMLCollection.prototype.forEach = Array.prototype.forEach;
             }
           }
         </script>

         """;
   }

   public static void jsonEscape(final Reader input, final Appendable out) throws IOException {
      int c;

      while ((c = input.read()) != -1) {
         switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> {
               if (c < ' ' || c >= '\u0080' && c < '\u00a0' || c >= '\u2000' && c < '\u2100') {
                  out.append(String.format("\\u%04x", c));
               } else {
                  out.append((char) c);
               }
            }
         }
      }
   }

   public static String minifyCss(final InputStream in) throws IOException {
      final var minifiedCss = new StringBuilder();
      boolean inComment = false;
      boolean inString = false;
      char stringDelimiter = ' ';

      try (var reader = new BufferedReader(new InputStreamReader(in))) {
         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            for (int i = 0; i < line.length(); i++) {
               final char c = line.charAt(i);

               // handle string literals
               if (inString) {
                  minifiedCss.append(c);
                  if (c == stringDelimiter) {
                     inString = false;
                  }
                  continue;
               }

               // handle comments
               if (inComment) {
                  if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                     inComment = false;
                     i++;
                  }
                  continue;
               }

               // detect comment start
               if (c == '/' && i + 1 < line.length()) {
                  final char next = line.charAt(i + 1);
                  if (next == '*') {
                     inComment = true;
                     i++;
                     continue;
                  }
               }

               // detect string literals
               if (c == '"' || c == '\'') {
                  inString = true;
                  stringDelimiter = c;
               }

               // add character to output if not in a comment
               if (!inComment) {
                  if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                     // ignore whitespace
                     if (minifiedCss.length() > 0 && !Character.isWhitespace(minifiedCss.charAt(minifiedCss.length() - 1))) {
                        minifiedCss.append(' ');
                     }
                  } else {
                     minifiedCss.append(c);
                  }
               }
            }
         }
      }

      Strings.replaceEach(minifiedCss, //
         new String[] {";}", " {", "{ ", " ;", "; ", " :", ": ", " :", ", "}, //
         new String[] {"}", "{", "{", ";", ";", ":", ":", ":", ","});
      return minifiedCss.toString();
   }

   private StringUtils() {
   }
}
