/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.services.IDisposable;

import de.sebthom.eclipse.commons.ui.Colors;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.Plugin;
import net.sf.jstuff.core.graphic.RGB;

/**
 * @author Sebastian Thomschke
 */
public final class MiscUtils {

   private static final String HEX_DIGITS = "0123456789ABCDEF";

   /**
    * Disposes a contributed renderer, logging instead of propagating its failure so that the caller can still release
    * the remaining renderers and its own resources. Linkage errors are included because a contribution's classes can
    * become unresolvable while it is still referenced, for example after its bundle was updated or uninstalled.
    */
   public static void disposeOrLog(final IDisposable disposable) {
      try {
         disposable.dispose();
      } catch (final RuntimeException | LinkageError ex) {
         Plugin.log().error(ex);
      }
   }

   public static String getCurrentTime() {
      return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
   }

   public static boolean isDarkEclipseTheme() {
      final var bgColor = UI.supply(() -> UI.getShell().getBackground());
      return new RGB(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()).getBrightnessFast() < 128;
   }

   /**
    * Applies basic Markdown formatting to a {@link StyledText} widget.
    * <p>
    * This method parses a limited subset of Markdown syntax in the provided text
    * and applies corresponding SWT style ranges to visually format the text.
    * Supported Markdown directives include:
    * </p>
    * <ul>
    * <li><b>Bold</b> — <code>**text**</code></li>
    * <li><b>Inline code</b> — <code>`code`</code></li>
    * <li><b>Code block</b> — <code>```block```</code></li>
    * </ul>
    *
    * <p>
    * <b>Example:</b>
    * </p>
    * <pre>{@code
    * var styledText = new StyledText(parent, SWT.NONE);
    * MiscUtils.setMarkdown(styledText, "This is **bold**, `inline code`, and ```block code```.");
    * }</pre>
    *
    * @param styledText the {@link StyledText} control to which the formatted text will be applied
    * @param markdown the input string containing Markdown-formatted text
    */
   public static void setMarkdown(final StyledText styledText, final String markdown) {
      // Named groups keep each branch bound to its own alternative when the pattern is edited.
      final var markdownDirectivesPattern = Pattern.compile("" //
            + "\\*\\*(?<bold>.*?)\\*\\*" + '|' // bold
            + "```(?<block>.*?)```" + '|' // code block
            + "`(?<code>[^`]*)`"); // inline code

      final var plainText = new StringBuilder();
      final var styleRanges = new ArrayList<StyleRange>();
      int previousEndIndex = 0;
      final Matcher markdownDirectivesMatcher = markdownDirectivesPattern.matcher(markdown);
      while (markdownDirectivesMatcher.find()) {
         plainText.append(markdown, previousEndIndex, markdownDirectivesMatcher.start());

         final var styleRange = new StyleRange();
         styleRange.start = plainText.length();
         final @Nullable String bold = markdownDirectivesMatcher.group("bold");
         final @Nullable String block = markdownDirectivesMatcher.group("block");
         final @Nullable String code = markdownDirectivesMatcher.group("code");
         if (bold != null) {
            plainText.append(bold);
            styleRange.length = bold.length();
            styleRange.fontStyle = SWT.BOLD;
         } else if (block != null) {
            plainText.append(block);
            styleRange.length = block.length();
            styleRange.font = JFaceResources.getTextFont();
            styleRange.background = Colors.GRAY;
         } else if (code != null) {
            plainText.append(code);
            styleRange.length = code.length();
            styleRange.font = JFaceResources.getTextFont();
         }
         styleRanges.add(styleRange);
         previousEndIndex = markdownDirectivesMatcher.end();
      }

      plainText.append(markdown.substring(previousEndIndex));
      styledText.setText(plainText.toString());
      for (final StyleRange styleRange : styleRanges) {
         styledText.setStyleRange(styleRange);
      }
   }

   public static @Nullable URI toURI(final @Nullable String url) {
      if (url == null || url.isBlank())
         return null;
      try {
         return new URI(url);
      } catch (final IllegalArgumentException | URISyntaxException ex) {
         return null;
      }
   }

   /**
    * Parses a URL as serialized by the embedded browser, for example a link's {@code href} or the current page location.
    * <p>
    * Browsers follow the WHATWG URL standard, which keeps some characters literal that {@link URI} rejects, such as a
    * {@code |} in a query. If strict parsing fails, such characters are percent-encoded in the path, query and fragment.
    * Valid escapes are kept, so already encoded data is not encoded again. The scheme and authority are never altered;
    * a URL whose authority {@link URI} rejects stays unparsable.
    * <p>
    * This is not a WHATWG parser. It relies on the browser's serialization, which already converted backslashes in
    * HTTP(S) paths to slashes and removed tabs and newlines. Use {@link #toURI} where a rejected location must fail closed.
    */
   public static @Nullable URI toBrowserURI(final @Nullable String url) {
      final URI strict = toURI(url);
      if (strict != null || url == null)
         return strict;

      final int schemeEnd = url.indexOf(':');
      if (schemeEnd < 1)
         return null;
      int repairStart = schemeEnd + 1;
      if (url.startsWith("//", repairStart)) {
         repairStart += 2;
         while (repairStart < url.length() && "/?#".indexOf(url.charAt(repairStart)) < 0) {
            repairStart++;
         }
      }

      final var repaired = new StringBuilder(url.length() + 16).append(url, 0, repairStart);
      boolean inQuery = false;
      boolean inFragment = false;
      for (int index = repairStart; index < url.length();) {
         final int codePoint = url.codePointAt(index);
         if (codePoint == '#' && !inFragment) {
            inFragment = true;
            repaired.append('#');
         } else if (codePoint == '?' && !inQuery && !inFragment) {
            inQuery = true;
            repaired.append('?');
         } else if (isLiteralURIChar(url, index, inQuery || inFragment)) {
            repaired.append((char) codePoint);
         } else {
            for (final byte octet : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
               repaired.append('%').append(HEX_DIGITS.charAt(octet >> 4 & 0xF)).append(HEX_DIGITS.charAt(octet & 0xF));
            }
         }
         index += Character.charCount(codePoint);
      }
      return toURI(repaired.toString());
   }

   /**
    * @return true if {@link URI} accepts the character at the given index literally in a path, or in a query or fragment
    */
   private static boolean isLiteralURIChar(final String url, final int index, final boolean inQueryOrFragment) {
      final char ch = url.charAt(index);
      if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || "-._~!$&'()*+,;=:@/?".indexOf(ch) >= 0)
         return true;
      // java.net.URI follows RFC 2732, which permits brackets in queries and fragments but not in paths.
      if (ch == '[' || ch == ']')
         return inQueryOrFragment;
      if (ch == '%')
         return index + 2 < url.length() && HexFormat.isHexDigit(url.charAt(index + 1)) && HexFormat.isHexDigit(url.charAt(index + 2));
      return false;
   }

   public static URI withoutFragment(final URI target) {
      if (target.getRawFragment() == null)
         return target;
      try {
         return new URI(target.getScheme(), target.getUserInfo(), target.getHost(), target.getPort(), target.getPath(), target.getQuery(),
            null);
      } catch (final URISyntaxException ex) {
         return target;
      }
   }

   private MiscUtils() {
   }
}
