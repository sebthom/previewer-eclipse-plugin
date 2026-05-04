/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.preprocessor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * Carries Markdown content after source preprocessing and HTML replacements to apply after Markdown rendering.
 *
 * @author Sebastian Thomschke
 */
public record MarkdownPreprocessingResult(@NonNull ContentSource source, @NonNull Map<String, String> htmlReplacements) {

   public static MarkdownPreprocessingResult unchanged(final ContentSource source) {
      return new MarkdownPreprocessingResult(source, Map.of());
   }

   public void applyHtmlReplacements(final StringBuilder html) {
      if (htmlReplacements.isEmpty())
         return;

      String replaced = html.toString();
      for (final var entry : htmlReplacements.entrySet()) {
         replaced = placeholderParagraphPattern(entry.getKey()).matcher(replaced).replaceAll(Matcher.quoteReplacement(entry.getValue()));
         replaced = replaced.replace(entry.getKey(), entry.getValue());
      }
      html.setLength(0);
      html.append(replaced);
   }

   private static Pattern placeholderParagraphPattern(final String placeholder) {
      return Pattern.compile("<p(?:\\s+[^>]*)?>\\s*" + Pattern.quote(placeholder) + "\\s*</p>");
   }
}
