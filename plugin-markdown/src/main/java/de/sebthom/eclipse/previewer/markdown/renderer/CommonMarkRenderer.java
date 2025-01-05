/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.renderer;

import java.io.IOException;
import java.util.List;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * @author Sebastian Thomschke
 */
public class CommonMarkRenderer implements MarkdownRenderer {

   public static final CommonMarkRenderer INSTANCE = new CommonMarkRenderer();

   private final Parser parser;
   private final HtmlRenderer renderer;

   @SuppressWarnings("null")
   protected CommonMarkRenderer() {
      final var extensions = List.of( //
         AutolinkExtension.create(), //
         FootnotesExtension.create(), //
         HeadingAnchorExtension.create(), //
         ImageAttributesExtension.create(), //
         InsExtension.create(), //
         StrikethroughExtension.create(), //
         TablesExtension.create(), //
         TaskListItemsExtension.create(), //
         YamlFrontMatterExtension.create());
      parser = Parser.builder().extensions(extensions).build();
      renderer = HtmlRenderer.builder().extensions(extensions).build();
   }

   @Override
   public void markdownToHTML(final ContentSource source, final Appendable out) throws IOException {
      try (var reader = source.contentAsReader()) {
         renderer.render(parser.parseReader(reader), out);
      }
   }
}
