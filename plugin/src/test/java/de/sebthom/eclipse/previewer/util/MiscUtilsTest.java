/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;
import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import de.sebthom.eclipse.commons.ui.Colors;

/**
 * The style ranges produced by {@link MiscUtils#setMarkdown} must cover exactly the text of each directive.
 * No browser runtime is needed, but the {@link StyledText} widget loads native SWT libraries. These only resolve when the test
 * classpath is limited to the current platform's SWT bundle, so this test shares the opt-in switch of the other native SWT tests.
 *
 * @author Sebastian Thomschke
 */
@EnabledIfSystemProperty(named = "previewer.tests.browser", matches = "default|ie|edge")
class MiscUtilsTest {

   private @Nullable Display display;
   private StyledText styledText = lateNonNull();

   @BeforeEach
   void createStyledText() {
      final var newDisplay = new Display();
      display = newDisplay;
      styledText = new StyledText(new Shell(newDisplay), SWT.NONE);
   }

   @AfterEach
   void disposeDisplay() {
      final var currentDisplay = display;
      if (currentDisplay != null) {
         // Only one Display may exist per thread, so a failed test must not leave it alive for the following SWT tests.
         currentDisplay.dispose();
      }
   }

   @Test
   void stylesSingleLineCodeBlock() {
      MiscUtils.setMarkdown(styledText, "a ```code``` b");

      assertEquals("a code b", styledText.getText());
      final var ranges = styledText.getStyleRanges();
      assertEquals(1, ranges.length);
      assertEquals(2, ranges[0].start);
      assertEquals(4, ranges[0].length);
      assertEquals(JFaceResources.getTextFont(), ranges[0].font);
      assertEquals(Colors.GRAY, ranges[0].background);
   }

   @Test
   void showsEscapedDirectiveCharactersLiterally() {
      // Exception messages and file paths embedded in a message must not end or start a directive.
      MiscUtils.setMarkdown(styledText, "**a\\*\\*b\\\\** \\`c\\` `d\\`e` \\x");

      assertEquals("a**b\\ `c` d`e \\x", styledText.getText());
      final var ranges = styledText.getStyleRanges();
      assertEquals(2, ranges.length);
      assertEquals(0, ranges[0].start);
      assertEquals(5, ranges[0].length);
      assertEquals(SWT.BOLD, ranges[0].fontStyle);
      assertEquals(10, ranges[1].start);
      assertEquals(3, ranges[1].length);
      assertEquals(JFaceResources.getTextFont(), ranges[1].font);
   }

   @Test
   void escapesTextSoItIsShownAsIs() {
      final String untrusted = "C:\\a**b`c\\\\d ```e```";
      MiscUtils.setMarkdown(styledText, "Failed: **" + MiscUtils.escapeMarkdown(untrusted) + "**");

      assertEquals("Failed: " + untrusted, styledText.getText());
      final var ranges = styledText.getStyleRanges();
      assertEquals(1, ranges.length);
      assertEquals(8, ranges[0].start);
      assertEquals(untrusted.length(), ranges[0].length);
      assertEquals(SWT.BOLD, ranges[0].fontStyle);
   }

   @Test
   void keepsEscapedStackTraceTextInsideFences() {
      final String stackTrace = "java.lang.IllegalStateException: bad `value` **here**\n\tat Foo.bar(Foo.java:1)";
      MiscUtils.setMarkdown(styledText, "Reason:\n```" + MiscUtils.escapeMarkdown(stackTrace) + "```\n");

      assertEquals("Reason:\n" + stackTrace + "\n", styledText.getText());
   }

   @Test
   void stylesBoldAndInlineCode() {
      MiscUtils.setMarkdown(styledText, "**bold** and `code`");

      assertEquals("bold and code", styledText.getText());
      final var ranges = styledText.getStyleRanges();
      assertEquals(2, ranges.length);
      assertEquals(0, ranges[0].start);
      assertEquals(4, ranges[0].length);
      assertEquals(SWT.BOLD, ranges[0].fontStyle);
      assertEquals(9, ranges[1].start);
      assertEquals(4, ranges[1].length);
      assertEquals(JFaceResources.getTextFont(), ranges[1].font);
      assertNull(ranges[1].background);
   }
}
