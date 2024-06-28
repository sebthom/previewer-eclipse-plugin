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
