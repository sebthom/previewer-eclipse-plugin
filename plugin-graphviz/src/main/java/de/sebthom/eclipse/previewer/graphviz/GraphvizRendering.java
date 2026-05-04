/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.content.IContentType;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.graphviz.prefs.PluginPreferences;

/**
 * Renders DOT source through the configured Graphviz renderer.
 *
 * @author Sebastian Thomschke
 */
public final class GraphvizRendering {

   private static final class DotContentSource implements ContentSource {
      private final ContentSource delegate;
      private final String content;

      DotContentSource(final ContentSource delegate, final String content) {
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
         return List.of();
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

   public static String renderToHtmlFragment(final String dotSource, final ContentSource source) throws IOException {
      final var html = new StringBuilder();
      renderToHtmlFragment(dotSource, source, html);
      return html.toString();
   }

   public static void renderToHtmlFragment(final String dotSource, final ContentSource source, final Appendable out) throws IOException {
      PluginPreferences.getGraphvizRenderer().dotToHTML(new DotContentSource(source, dotSource), out);
   }

   private GraphvizRendering() {
   }
}
