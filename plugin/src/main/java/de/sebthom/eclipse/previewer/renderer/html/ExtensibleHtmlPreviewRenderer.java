/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.SystemUtils;
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
import de.sebthom.eclipse.previewer.util.MiscUtils;
import net.sf.jstuff.core.collection.LRUMap;
import net.sf.jstuff.core.collection.tuple.Tuple2;
import net.sf.jstuff.core.exception.Exceptions;
import net.sf.jstuff.core.functional.ThrowingFunction;

/**
 * @author Sebastian Thomschke
 */
public class ExtensibleHtmlPreviewRenderer implements PreviewRenderer {

   @FunctionalInterface
   public interface LocalFileLinkHandler {
      /**
       * @param path existing local file path resolved from the clicked browser link
       * @param target file URI for {@code path}, with any URI fragment removed
       * @return true if the target was handled and browser navigation should be cancelled
       */
      boolean openLocalFileLink(Path path, URI target);
   }

   private static final class PageState {
      Tuple2<Integer, Integer> scrollPos = Tuple2.create(0, 0);
      float zoomLevel = 1.0f;
   }

   private static final Path RENDER_CACHE_ROOT = SystemUtils.getJavaIoTmpDir().toPath().resolve(Plugin.PLUGIN_ID);

   private LRUMap<String, PageState> pageStates = new LRUMap<>(500);
   private @Nullable String currentPageStateKey;

   private final RenderCache renderCacheOfEditors = new RenderCacheUsingSourceContentHash("render_cache_editors");
   private final RenderCache renderCacheOfFiles = new RenderCacheByLastModified("render_cache_files");

   private final List<PreviewRendererExtension<HtmlPreviewRenderer>> renderers = new ArrayList<>();
   private PreviewRendererExtension<HtmlPreviewRenderer> passthroughHtmlRenderer = lateNonNull();
   private PreviewRendererExtension<HtmlPreviewRenderer> passthroughXmlRenderer = lateNonNull();

   private BrowserWrapper browser = lateNonNull();
   private @Nullable LocalFileLinkHandler localFileLinkHandler;
   private @Nullable Path currentRenderedContentPath;

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
      browser.setShouldOverrideNavigation(this::tryOpenLocalFileLink);
      loadRenderersFromExtensionPoints();
   }

   private boolean tryOpenLocalFileLink(final URI target) {
      final Path path = toRegularFilePath(target);
      if (path == null)
         return false;

      final boolean internalNavigation = isInternalNavigation(path);
      final boolean sameDocumentNavigation = isSameDocumentNavigation(target, path);
      if (internalNavigation || sameDocumentNavigation || isRenderCachePath(path))
         return false;

      final URI targetWithoutFragment = path.toUri();
      final var handler = localFileLinkHandler;
      return handler != null && handler.openLocalFileLink(path, targetWithoutFragment);
   }

   private boolean isInternalNavigation(final Path target) {
      final var currentRenderedContentPath = this.currentRenderedContentPath;
      if (currentRenderedContentPath == null)
         return false;

      // SWT/WebView can report more than one navigation event for the generated preview page. Keep treating the current
      // rendered output as an internal page load so it is never opened as a linked file editor.
      return currentRenderedContentPath.equals(target);
   }

   private static @Nullable Path toRegularFilePath(final URI target) {
      if (!"file".equalsIgnoreCase(target.getScheme()))
         return null;

      try {
         final Path path = Path.of(MiscUtils.withoutFragment(target));
         if (!Files.isRegularFile(path))
            return null;
         return toCanonicalPath(path);
      } catch (final RuntimeException ex) {
         return null;
      }
   }

   private static boolean isRenderCachePath(final Path path) {
      return path.startsWith(toCanonicalPath(RENDER_CACHE_ROOT));
   }

   private static Path toCanonicalPath(final Path path) {
      final Path absolutePath = path.toAbsolutePath().normalize();
      try {
         return absolutePath.toRealPath();
      } catch (final IOException ex) {
         return absolutePath;
      }
   }

   private static boolean isSameDocument(final URI left, final URI right) {
      final String leftScheme = left.getScheme();
      final String rightScheme = right.getScheme();
      return (leftScheme == null ? rightScheme == null : leftScheme.equalsIgnoreCase(rightScheme)) //
            && Objects.equals(left.getRawSchemeSpecificPart(), right.getRawSchemeSpecificPart());
   }

   private boolean isSameDocumentNavigation(final URI target, final Path targetPath) {
      final URI current = MiscUtils.toURI(browser.getUrl());
      if (current == null)
         return false;

      final Path currentPath = toRegularFilePath(current);
      if (currentPath != null)
         return currentPath.equals(targetPath);

      return isSameDocument(current, target);
   }

   /**
    * Sets the handler used when a clicked link in rendered HTML resolves to an existing local file.
    * <p>
    * This renderer handles the browser-specific work first: internal preview page loads and same-document anchors are
    * ignored, then this class accepts only {@code file:} links, strips URI fragments, converts the target to a
    * {@link Path}, and verifies that it is a regular file. The handler only decides what Eclipse should do with that
    * resolved local file.
    */
   public void setLocalFileLinkHandler(final @Nullable LocalFileLinkHandler localFileLinkHandler) {
      this.localFileLinkHandler = localFileLinkHandler;
   }

   @Override
   public void dispose() {
      renderers.forEach(ext -> ext.renderer.dispose());
      renderers.clear();
      browser.dispose();
   }

   @Override
   public float getZoom() {
      final var pageStateKey = currentPageStateKey;
      if (pageStateKey == null)
         return 1.0f;
      final var pageState = pageStates.get(pageStateKey);
      return pageState == null ? 1.0f : pageState.zoomLevel;
   }

   @Override
   public synchronized void setZoom(final float level) {
      final var pageStateKey = currentPageStateKey;
      if (pageStateKey != null) {
         final var pageState = pageStates.computeIfAbsent(pageStateKey, k -> new PageState());
         pageState.zoomLevel = level;
         browser.setZoom(level);
      }
   }

   private synchronized void navigateTo(final ContentSource source, final Path renderedContentPath) {
      var pageStateKey = currentPageStateKey;
      if (pageStateKey != null) {
         final var pageState = pageStates.get(pageStateKey);
         if (pageState != null) {
            pageState.scrollPos = browser.getScrollPos();
         }
      }

      pageStateKey = currentPageStateKey = source.path().toString();
      final var pageState = pageStates.computeIfAbsent(pageStateKey, k -> new PageState());
      currentRenderedContentPath = toCanonicalPath(renderedContentPath);
      browser.navigateTo(renderedContentPath).thenRun(() -> {
         if (pageState.zoomLevel != 1.0f) {
            browser.setZoom(pageState.zoomLevel);
         }
         if (pageState.scrollPos.get1() > 0 || pageState.scrollPos.get2() > 0) {
            browser.setScrollPos(pageState.scrollPos);
         }
      });
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
         navigateTo(source, renderedContentPath);
         return true;
      }

      if (source.isSynced()) {
         if (passthroughHtmlRenderer.supports(source) || passthroughXmlRenderer.supports(source)) {
            navigateTo(source, path);
            return true;
         }
      } else {
         if (passthroughHtmlRenderer.supports(source)) {
            navigateTo(source, asNonNull(renderCache.computeIfAbsent(source, sourceArg -> {
               final var htmlBuilder = new StringBuilder();
               htmlBuilder.append(source.contentAsString());
               adjustHTML(path, htmlBuilder);
               return htmlBuilder;
            }, "html")));
            return true;
         }

         if (passthroughXmlRenderer.supports(source)) {
            navigateTo(source, asNonNull(renderCache.computeIfAbsent(source, sourceArg -> source.contentAsString(), "xml")));
            return true;
         }
      }
      return false;
   }

   private void adjustHTML(final Path path, final StringBuilder html) {
      // add meta footer
      html.append("<!-- " + path.toUri() + " @ " + MiscUtils.getCurrentTime() + " -->");

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
      return passthroughHtmlRenderer.supports(source) || passthroughXmlRenderer.supports(source) || renderers.stream() //
         .anyMatch(renderer -> renderer.supports(source));
   }
}
