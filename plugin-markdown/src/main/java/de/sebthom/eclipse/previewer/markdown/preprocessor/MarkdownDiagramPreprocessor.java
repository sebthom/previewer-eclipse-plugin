/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.preprocessor;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.GraphvizRendering;
import de.sebthom.eclipse.previewer.markdown.Plugin;
import de.sebthom.eclipse.previewer.pikchr.PikchrRendering;
import de.sebthom.eclipse.previewer.plantuml.PlantUmlRendering;

/**
 * Replaces supported diagram fenced code blocks in Markdown with renderer-specific HTML placeholders.
 *
 * @author Sebastian Thomschke
 */
public final class MarkdownDiagramPreprocessor {

   /**
    * Captures a validated Markdown source range and the diagram data required to replace it.
    */
   private record Candidate(int start, int end, @NonNull String indentation, @NonNull DiagramFence fence, @NonNull String source) {
   }

   /**
    * Represents one parsed diagram fence with its shared renderer type and occurrence-specific modifiers.
    */
   private record DiagramFence(@NonNull DiagramType type, @NonNull List<String> modifiers) {

      static @Nullable DiagramFence fromInfo(final @Nullable String info) {
         if (info == null)
            return null;

         final String trimmed = info.trim();
         if (trimmed.isEmpty())
            return null;

         final String[] tokens = trimmed.split("\\s+");
         final String language = tokens[0].toLowerCase(Locale.ROOT);
         for (final DiagramType type : DiagramType.values()) {
            if (!type.languages.contains(language))
               continue;

            final var modifiers = new ArrayList<String>(Math.max(0, tokens.length - 1));
            for (int idx = 1; idx < tokens.length; idx++) {
               modifiers.add(tokens[idx].toLowerCase(Locale.ROOT));
            }
            return new DiagramFence(type, List.copyOf(modifiers));
         }
         return null;
      }
   }

   /**
    * Identifies the bundled fence renderers that callers can enable for one Markdown render.
    * <p>
    * This type is public only for coordination within the Markdown bundle; its package is not exported as plug-in API.
    */
   public enum DiagramType {
      GRAPHVIZ(Set.of("dot", "graphviz")) {
         @Override
         String render(final String source, final ContentSource context, final List<String> modifiers, final boolean useDarkTheme)
               throws IOException {
            return GraphvizRendering.renderToHtmlFragment(source, context);
         }
      },
      PIKCHR(Set.of("pikchr")) {
         @Override
         String render(final String source, final ContentSource context, final List<String> modifiers, final boolean useDarkTheme) {
            return PikchrRendering.renderToHtmlFragment(source, modifiers, useDarkTheme);
         }
      },
      PLANTUML(Set.of("plantuml", "puml", "iuml", "pu")) {
         @Override
         String render(final String source, final ContentSource context, final List<String> modifiers, final boolean useDarkTheme)
               throws IOException {
            return PlantUmlRendering.renderToHtmlFragment(fencedSourceToPlantUmlSource(source));
         }
      };

      private final Set<String> languages;

      DiagramType(final Set<String> languages) {
         this.languages = languages;
      }

      abstract String render(String source, ContentSource context, List<String> modifiers, boolean useDarkTheme) throws IOException;
   }

   private static final class PreprocessedContentSource implements ContentSource {
      private final ContentSource delegate;
      private final String content;

      PreprocessedContentSource(final ContentSource delegate, final String content) {
         this.delegate = delegate;
         this.content = content;
      }

      @Override
      public InputStream contentAsInputStream() {
         return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      }

      @Override
      public Reader contentAsReader() {
         return new StringReader(content);
      }

      @Override
      public String contentAsString() {
         return content;
      }

      @Override
      public List<IContentType> contentTypes() {
         return delegate.contentTypes();
      }

      @Override
      public boolean isSnapshot() {
         return true;
      }

      @Override
      public boolean isSynced() {
         return delegate.isSynced();
      }

      @Override
      public long lastModified() throws IOException {
         return delegate.lastModified();
      }

      @Override
      public Path path() {
         return delegate.path();
      }
   }

   private record SourceRange(int start, int end) {
   }

   private static final String PLACEHOLDER_PREFIX = "PREVIEWER_DIAGRAM_BLOCK_";
   private static final Parser SOURCE_SPAN_PARSER = asNonNull(Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build());

   private static List<Candidate> collectCandidates(final String markdown, final Set<DiagramType> enabledTypes) {
      final var candidates = new ArrayList<Candidate>();
      SOURCE_SPAN_PARSER.parse(markdown).accept(new AbstractVisitor() {
         @Override
         public void visit(final @NonNullByDefault({}) FencedCodeBlock block) {
            final DiagramFence fence = DiagramFence.fromInfo(block.getInfo());
            if (fence == null)
               return;
            // Apply selection after parsing so syntax validation can reparse a fence without sentinel "all enabled" flags.
            // This also preserves Pikchr's identity before a downstream Markdown engine can normalize it to the "pic" alias.
            if (!enabledTypes.contains(fence.type()))
               return;

            final SourceRange range = sourceRange(block);
            if (range == null || range.end() > markdown.length())
               return;

            final Candidate candidate = validate(markdown, range, block, fence);
            if (candidate != null) {
               candidates.add(candidate);
            }
         }
      });
      return candidates;
   }

   private static char fenceChar(final FencedCodeBlock block) {
      final String fenceCharacter = block.getFenceCharacter();
      return fenceCharacter == null || fenceCharacter.isEmpty() ? '\0' : fenceCharacter.charAt(0);
   }

   private static int fenceLength(final FencedCodeBlock block) {
      final Integer openingFenceLength = block.getOpeningFenceLength();
      return openingFenceLength == null ? 0 : openingFenceLength;
   }

   private static String fencedSourceToPlantUmlSource(final String source) {
      if (hasPlantUmlDelimiters(source) || source.isBlank())
         return source;

      // Markdown fences already delimit one PlantUML diagram; PlantUML still requires explicit source markers.
      return "@startuml\n" + stripTrailingLineBreaks(source) + "\n@enduml\n";
   }

   private static boolean hasClosingFence(final String markdown, final char fenceChar, final int fenceLength) {
      int lineStart = nextLineStart(markdown, lineEnd(markdown, 0));
      while (lineStart < markdown.length()) {
         final int lineEnd = lineEnd(markdown, lineStart);
         if (isClosingFence(markdown.substring(lineStart, lineEnd), fenceChar, fenceLength))
            return true;
         lineStart = nextLineStart(markdown, lineEnd);
      }
      return false;
   }

   private static boolean hasPlantUmlDelimiters(final String source) {
      boolean hasStart = false;
      boolean hasEnd = false;
      int lineStart = 0;
      while (lineStart < source.length()) {
         final int lineEnd = lineEnd(source, lineStart);
         final String line = source.substring(lineStart, lineEnd).stripLeading().toLowerCase(Locale.ROOT);
         hasStart = hasStart || line.startsWith("@start");
         hasEnd = hasEnd || line.startsWith("@end");
         lineStart = nextLineStart(source, lineEnd);
      }
      return hasStart && hasEnd;
   }

   private static boolean isClosingFence(final String line, final char fenceChar, final int fenceLength) {
      int idx = skipUpToThreeLeadingSpaces(line);
      int count = 0;
      while (idx < line.length() && line.charAt(idx) == fenceChar) {
         count++;
         idx++;
      }
      return count >= fenceLength && line.substring(idx).trim().isEmpty();
   }

   private static int lineEnd(final String text, final int start) {
      int idx = start;
      while (idx < text.length()) {
         final char ch = text.charAt(idx);
         if (ch == '\r' || ch == '\n') {
            break;
         }
         idx++;
      }
      return idx;
   }

   private static int nextLineStart(final String text, final int lineEnd) {
      if (lineEnd >= text.length())
         return text.length();
      if (text.charAt(lineEnd) == '\r' && lineEnd + 1 < text.length() && text.charAt(lineEnd + 1) == '\n')
         return lineEnd + 2;
      return lineEnd + 1;
   }

   public static MarkdownPreprocessingResult preprocess(final ContentSource source, final Set<DiagramType> enabledTypes,
         final boolean useDarkTheme) throws IOException {
      // Mermaid is rendered after Markdown conversion and never enters this source preprocessor. With no source-side
      // renderer selected, preserving the original ContentSource avoids an unnecessary snapshot read and parse.
      if (enabledTypes.isEmpty())
         return MarkdownPreprocessingResult.unchanged(source);

      final String markdown = source.contentAsString();
      final List<Candidate> candidates = collectCandidates(markdown, enabledTypes);
      if (candidates.isEmpty())
         return MarkdownPreprocessingResult.unchanged(source);

      final var placeholders = new LinkedHashMap<String, String>();
      final var processedMarkdown = new StringBuilder(markdown);
      candidates.sort(Comparator.comparingInt(Candidate::start).reversed());

      for (int idx = 0; idx < candidates.size(); idx++) {
         final Candidate candidate = candidates.get(idx);
         try {
            final String replacementHtml = candidate.fence.type.render(candidate.source, source, candidate.fence.modifiers, useDarkTheme);
            final String placeholder = PLACEHOLDER_PREFIX + idx + "_" + candidate.fence.type.name().toLowerCase(Locale.ROOT) + "_" + Integer
               .toUnsignedString(candidate.source.hashCode(), 36);
            placeholders.put(placeholder, replacementHtml);
            processedMarkdown.replace(candidate.start, candidate.end, candidate.indentation + placeholder);
         } catch (final IOException | RuntimeException ex) {
            Plugin.log().debug(ex);
         }
      }

      if (placeholders.isEmpty())
         return MarkdownPreprocessingResult.unchanged(source);

      return new MarkdownPreprocessingResult(new PreprocessedContentSource(source, processedMarkdown.toString()), placeholders);
   }

   private static int skipUpToThreeLeadingSpaces(final String line) {
      int idx = 0;
      while (idx < 3 && idx < line.length() && line.charAt(idx) == ' ') {
         idx++;
      }
      return idx;
   }

   private static @Nullable SourceRange sourceRange(final FencedCodeBlock block) {
      int start = Integer.MAX_VALUE;
      int end = -1;
      for (final SourceSpan span : block.getSourceSpans()) {
         if (span.getInputIndex() < 0)
            return null;
         start = Math.min(start, span.getInputIndex());
         end = Math.max(end, span.getInputIndex() + span.getLength());
      }
      return start == Integer.MAX_VALUE || end < start ? null : new SourceRange(start, end);
   }

   private static String stripTrailingLineBreaks(final String source) {
      int end = source.length();
      while (end > 0 && (source.charAt(end - 1) == '\r' || source.charAt(end - 1) == '\n')) {
         end--;
      }
      return source.substring(0, end);
   }

   private static @Nullable Candidate validate(final String markdown, final SourceRange range, final FencedCodeBlock block,
         final DiagramFence fence) {
      final String candidateMarkdown = markdown.substring(range.start(), range.end());
      final String firstLine = candidateMarkdown.substring(0, lineEnd(candidateMarkdown, 0));
      final int fenceLength = fenceLength(block);
      final char fenceChar = fenceChar(block);
      final int fenceStart = skipUpToThreeLeadingSpaces(firstLine);

      int fenceEnd = fenceStart;
      while (fenceEnd < firstLine.length() && firstLine.charAt(fenceEnd) == fenceChar) {
         fenceEnd++;
      }
      if (fenceEnd - fenceStart < fenceLength)
         return null;

      final String info = firstLine.substring(fenceEnd).trim();
      // Source spans must cover the complete fenced block; otherwise replacing the range could corrupt the Markdown.
      if (!fence.equals(DiagramFence.fromInfo(info)) || !hasClosingFence(candidateMarkdown, fenceChar, fenceLength))
         return null;

      return new Candidate(range.start(), range.end(), firstLine.substring(0, fenceStart), fence, asNonNull(block.getLiteral()));
   }

   private MarkdownDiagramPreprocessor() {
   }
}
