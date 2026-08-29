/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.preprocessor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.Test;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.markdown.preprocessor.MarkdownDiagramPreprocessor.DiagramType;

/**
 * Verifies source-side diagram fence detection and the no-op path when preprocessing is disabled.
 *
 * @author Sebastian Thomschke
 */
class MarkdownDiagramPreprocessorTest {

   private record TestContentSource(@NonNull String content, boolean failWhenRead) implements ContentSource {

      TestContentSource(final String content) {
         this(content, false);
      }

      private void assertReadable() {
         if (failWhenRead)
            throw new AssertionError("Disabled preprocessing must not read the source.");
      }

      @Override
      public InputStream contentAsInputStream() {
         assertReadable();
         return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      }

      @Override
      public Reader contentAsReader() {
         assertReadable();
         return new StringReader(content);
      }

      @Override
      public String contentAsString() {
         assertReadable();
         return content;
      }

      @Override
      public List<IContentType> contentTypes() {
         return List.of();
      }

      @Override
      public boolean isSnapshot() {
         return true;
      }

      @Override
      public boolean isSynced() {
         return false;
      }

      @Override
      public long lastModified() {
         return 0;
      }

      @Override
      public Path path() {
         return Path.of("diagram.md");
      }
   }

   @Test
   void replacesPikchrFenceAndPreservesLayoutModifiers() throws Exception {
      final var source = new TestContentSource("~~~PiKcHr toggle center\nbox \"Hello\"\n~~~\n");
      final MarkdownPreprocessingResult result = MarkdownDiagramPreprocessor.preprocess(source, Set.of(DiagramType.PIKCHR), false);

      assertNotSame(source, result.source());
      assertTrue(result.source().contentAsString().contains("PREVIEWER_DIAGRAM_BLOCK_"));
      final var renderedHtml = new StringBuilder("<p>" + result.source().contentAsString().trim() + "</p>");
      result.applyHtmlReplacements(renderedHtml);
      assertTrue(renderedHtml.toString().contains("previewer-pikchr-center"));
      assertTrue(renderedHtml.toString().contains("window.previewerPikchrRuntimePromise"));
      assertFalse(renderedHtml.toString().contains("~~~PiKcHr"));
   }

   @Test
   void leavesPikchrAndPicFencesUntouchedWhenNotSelected() throws Exception {
      final var pikchrSource = new TestContentSource("```pikchr\nbox\n```\n");
      final var picSource = new TestContentSource("```pic\nbox\n```\n");

      assertSame(pikchrSource, MarkdownDiagramPreprocessor.preprocess(pikchrSource, Set.of(DiagramType.GRAPHVIZ, DiagramType.PLANTUML),
         false).source());
      assertSame(picSource, MarkdownDiagramPreprocessor.preprocess(picSource, Set.of(DiagramType.values()), false).source());
   }

   @Test
   void doesNotReadSourceWhenAllPreprocessingIsDisabled() throws Exception {
      final var source = new TestContentSource("```pikchr\nbox\n```\n", true);

      assertSame(source, MarkdownDiagramPreprocessor.preprocess(source, Set.of(), false).source());
   }
}
