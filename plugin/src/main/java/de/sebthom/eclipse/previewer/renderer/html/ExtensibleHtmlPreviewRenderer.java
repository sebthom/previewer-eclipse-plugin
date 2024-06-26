/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Composite;

import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;
import de.sebthom.eclipse.previewer.cache.RenderCache;
import de.sebthom.eclipse.previewer.cache.RenderCacheByLastModified;
import de.sebthom.eclipse.previewer.cache.RenderCacheUsingSourceContentHash;
import de.sebthom.eclipse.previewer.renderer.PreviewRendererExtension;
import de.sebthom.eclipse.previewer.ui.BrowserWrapper;
import net.sf.jstuff.core.exception.Exceptions;
import net.sf.jstuff.core.functional.ThrowingFunction;

/**
 * @author Sebastian Thomschke
 */
public class ExtensibleHtmlPreviewRenderer implements PreviewRenderer {

   private final RenderCache renderCacheOfEditors = new RenderCacheUsingSourceContentHash("render_cache_editors");
   private final RenderCache renderCacheOfFiles = new RenderCacheByLastModified("render_cache_files");

   private final List<PreviewRendererExtension<HtmlPreviewRenderer>> renderers = new ArrayList<>();
   private PreviewRendererExtension<HtmlPreviewRenderer> passthroughHtmlRenderer = lateNonNull();
   private PreviewRendererExtension<HtmlPreviewRenderer> passthroughXmlRenderer = lateNonNull();

   private BrowserWrapper browser = lateNonNull();

   private void loadRenderersFromExtensionPoints() {
      for (final IConfigurationElement ce : Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) {
         final String extensionName = ce.getName();
         if ("htmlPreviewRenderer".equals(extensionName)) {
            try {
               renderers.add(new PreviewRendererExtension<>(ce));
            } catch (final LinkageError | CoreException ex) {
               Plugin.log().error(ex);
            }
         }
      }

      passthroughHtmlRenderer = renderers.stream().filter(r -> r.renderer instanceof HtmlFilePreviewRenderer).findFirst().get();
      renderers.remove(passthroughHtmlRenderer);

      passthroughXmlRenderer = renderers.stream().filter(r -> r.renderer instanceof XmlPreviewRenderer).findFirst().get();
      renderers.remove(passthroughXmlRenderer);

   }

   @Override
   public void init(final Composite parent) {
      browser = new BrowserWrapper(parent);
      loadRenderersFromExtensionPoints();
   }

   @Override
   public void dispose() {
      renderers.forEach(ext -> ext.renderer.dispose());
      renderers.clear();
      browser.dispose();
   }

   @Override
   public boolean render(final ContentSource source, final boolean forceCacheUpdate) throws IOException {
      final var path = source.path();

      final var renderCache = source.isSynced() //
            ? renderCacheOfFiles
            : renderCacheOfEditors;

      final ThrowingFunction<ContentSource, @Nullable CharSequence, IOException> cacheFunction = sourceArg -> {
         Exception renderException = null;
         for (final var rendererExt : renderers) {
            if (rendererExt.supports(sourceArg)) {
               final var htmlBuilder = new StringBuilder();
               try {
                  rendererExt.renderer.renderToHtml(sourceArg, htmlBuilder);
               } catch (RuntimeException | IOException ex) {
                  renderException = ex;
                  break;
               }
               adjustHTML(path, htmlBuilder);
               return htmlBuilder;
            }
         }
         if (renderException != null) {
            Exceptions.throwSneakily(renderException);
         }
         return null;
      };

      final Path renderedContentPath = forceCacheUpdate //
            ? renderCache.replace(source, cacheFunction, "html")
            : renderCache.computeIfAbsent(source, cacheFunction, "html");

      if (renderedContentPath != null) {
         browser.navigateTo(renderedContentPath);
         return true;
      }

      if (source.isSynced()) {
         if (passthroughHtmlRenderer.supports(source) || passthroughXmlRenderer.supports(source)) {
            browser.navigateTo(path);
            return true;
         }
      } else {
         if (passthroughHtmlRenderer.supports(source)) {
            browser.navigateTo(asNonNull(renderCache.computeIfAbsent(source, sourceArg -> {
               final var htmlBuilder = new StringBuilder();
               htmlBuilder.append(source.contentAsString());
               adjustHTML(path, htmlBuilder);
               return htmlBuilder;
            }, "html")));
            return true;
         }

         if (passthroughXmlRenderer.supports(source)) {
            browser.navigateTo(asNonNull(renderCache.computeIfAbsent(source, sourceArg -> source.contentAsString(), "xml")));
            return true;
         }
      }
      return false;
   }

   private void adjustHTML(final Path path, final StringBuilder html) {
      // add meta footer
      html.append("<!-- " + path.toUri() + " @ " + LocalTime.now() + " -->");

      // add <base> tag to be able to resolve relatively referenced images
      final var headEndPos = html.indexOf("</head>");
      html.insert(headEndPos > -1 ? headEndPos : 0, "<base href='" + path.getParent().toUri() + "'>");

      // make # anchor tags work while having <base href> defined
      // see https://stackoverflow.com/questions/8108836/make-anchor-links-refer-to-the-current-page-when-using-base
      html.insert(headEndPos > -1 ? headEndPos : 0, """
         <script>
         document.addEventListener("click", function(event) {
           var elem = event.target;
           if (elem.tagName.toLowerCase() == "a" && elem.getAttribute("href").indexOf("#") === 0) {
             elem.href = location.href + elem.getAttribute("href");
           }
         });
         </script>
         """);
   }

   public boolean supports(final ContentSource source) {
      return passthroughHtmlRenderer.supports(source) || renderers.stream() //
         .anyMatch(renderer -> renderer.supports(source));
   }
}
