/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

import de.sebthom.eclipse.commons.ui.Colors;

/**
 * @author Sebastian Thomschke
 */
public final class MiscUtils {

   public static String getCurrentTime() {
      return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
   }

   public static void setMarkdown(final StyledText styledText, final String markdown) {
      final var markdownDirectivesPattern = Pattern.compile("" //
            + "\\*\\*(.*?)\\*\\*" + '|' // bold
            + "```(.*?)```" + '|' // code block
            + "`([^`]*)`"); // inline code

      final var plainText = new StringBuilder();
      final var styleRanges = new ArrayList<StyleRange>();
      int previousEndIndex = 0;
      final Matcher markdownDirectivesMatcher = markdownDirectivesPattern.matcher(markdown);
      while (markdownDirectivesMatcher.find()) {
         plainText.append(markdown, previousEndIndex, markdownDirectivesMatcher.start());

         // Determine the style based on the matched group
         final var styleRange = new StyleRange();
         styleRange.start = plainText.length();
         if (markdownDirectivesMatcher.group(1) != null) { // bold
            plainText.append(markdownDirectivesMatcher.group(1));
            styleRange.length = markdownDirectivesMatcher.group(1).length();
            styleRange.fontStyle = SWT.BOLD;
         } else if (markdownDirectivesMatcher.group(2) != null) { // code block
            plainText.append(markdownDirectivesMatcher.group(2));
            styleRange.length = markdownDirectivesMatcher.group(3).length();
            styleRange.font = JFaceResources.getTextFont();
            styleRange.background = Colors.GRAY;
         } else if (markdownDirectivesMatcher.group(3) != null) { // inline code
            plainText.append(markdownDirectivesMatcher.group(3));
            styleRange.length = markdownDirectivesMatcher.group(3).length();
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

   private MiscUtils() {
   }
}
