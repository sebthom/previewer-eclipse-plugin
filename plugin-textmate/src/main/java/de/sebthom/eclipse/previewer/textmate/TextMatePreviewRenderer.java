/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.textmate;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;
import static org.eclipse.tm4e.registry.TMEclipseRegistryPlugin.getGrammarRegistryManager;
import static org.eclipse.tm4e.ui.TMUIPlugin.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IRegistryOptions;
import org.eclipse.tm4e.core.registry.Registry;
import org.eclipse.tm4e.registry.IGrammarDefinition;
import org.eclipse.tm4e.registry.ITMScope;
import org.eclipse.tm4e.ui.samples.ISample;
import org.eclipse.tm4e.ui.text.TMPresentationReconciler;
import org.eclipse.tm4e.ui.themes.ITheme;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;

/**
 * @author Sebastian Thomschke
 */
public class TextMatePreviewRenderer implements PreviewRenderer {

   private static IGrammarSource.ContentType detectContentType(final Path path) {
      final String name = path.getFileName().toString().toLowerCase();
      if (name.endsWith(".json") || name.endsWith(".tmlanguage.json"))
         return IGrammarSource.ContentType.JSON;
      if (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".tmlanguage.yaml"))
         return IGrammarSource.ContentType.YAML;
      // default to XML/plist
      return IGrammarSource.ContentType.XML;
   }

   private final TMPresentationReconciler reconciler = new TMPresentationReconciler();
   private SourceViewer viewer = lateNonNull();

   private float zoom = 1.0f;
   private @Nullable Font zoomedFont;

   private @Nullable ITheme selectedTheme;
   private Link themeLink = lateNonNull();

   private @Nullable Path lastPath;
   private long lastModified;
   private boolean lastSynced;
   private int lastContentHash;

   private final IDocumentListener placeholderUpdatingDocumentListener = new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(final DocumentEvent event) {
      }

      @Override
      public void documentChanged(final DocumentEvent event) {
         final boolean isDocumentEmpty = event.fDocument.getLength() == 0;
         if (placeholderVisible != isDocumentEmpty) {
            placeholderVisible = isDocumentEmpty;
            final var st = viewer.getTextWidget();
            if (!st.isDisposed()) {
               st.redraw();
            }
         }
      }
   };
   private boolean placeholderVisible = true;
   private final PaintListener placeholderPainter = e -> {
      if (!placeholderVisible)
         return;
      final StyledText st = viewer.getTextWidget();
      if (st.isDisposed())
         return;
      final var old = e.gc.getForeground();
      e.gc.setForeground(st.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
      e.gc.drawText("Type or paste sample text...", st.getLeftMargin(), st.getTopMargin(), true);
      e.gc.setForeground(old);
   };

   private void applyZoom() {
      final var textWidget = viewer.getTextWidget();
      if (textWidget == null || textWidget.isDisposed())
         return;

      UI.run(() -> {
         if (textWidget.isDisposed())
            return;
         final Font baseFont = JFaceResources.getTextFont();
         final Font newFont = FontDescriptor.createFrom(baseFont) //
            .setHeight(Math.max(1, Math.round(baseFont.getFontData()[0].getHeight() * zoom))) //
            .createFont(UI.getDisplay());

         final var old = zoomedFont;
         zoomedFont = newFont;
         textWidget.setFont(newFont);
         if (old != null && !old.isDisposed()) {
            old.dispose();
         }
      });
   }

   private Menu buildThemeMenu(final Control anchor) {
      final var menu = new Menu(anchor.getShell(), SWT.POP_UP);

      // First entry: use associated/default theme
      final var useAssoc = new MenuItem(menu, SWT.CHECK);
      useAssoc.setText("Use associated/default");
      useAssoc.setSelection(selectedTheme == null);
      useAssoc.addListener(SWT.Selection, e -> setTheme(null));

      @SuppressWarnings("unused")
      final var seperator = new MenuItem(menu, SWT.SEPARATOR);

      // List themes with check mark on the selected one
      final String selectedId = selectedTheme == null ? null : selectedTheme.getId();
      for (final ITheme theme : getThemeManager().getThemes()) {
         final var mi = new MenuItem(menu, SWT.CHECK);
         mi.setText(theme.getName());
         mi.setSelection(selectedId != null && selectedId.equals(theme.getId()));
         mi.addListener(SWT.Selection, e -> setTheme(theme));
      }

      return menu;
   }

   @Override
   public void dispose() {
      final var vf = zoomedFont;
      if (vf != null && !vf.isDisposed()) {
         vf.dispose();
      }
      zoomedFont = null;

      final var st = viewer.getTextWidget();
      if (!st.isDisposed()) {
         st.removePaintListener(placeholderPainter);
      }
   }

   @Override
   public float getZoom() {
      return zoom;
   }

   @Override
   public void init(final Composite parent) {
      final var root = new Composite(parent, SWT.NONE);
      root.setLayout(GridLayoutFactory.fillDefaults().spacing(0, 0).numColumns(1).create());

      // custom toolbar with right padding: link + spacer
      final var toolBar = new Composite(root, SWT.NONE);
      toolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
      toolBar.setLayout(GridLayoutFactory.fillDefaults().extendedMargins(0, 16, 0, 0).numColumns(2).create());

      themeLink = new Link(toolBar, SWT.NONE);
      themeLink.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
      themeLink.setText("Theme:");
      themeLink.addListener(SWT.Selection, e -> {
         final Menu menu = buildThemeMenu(themeLink);
         final Rectangle rect = themeLink.getBounds();
         final Point pt = themeLink.toDisplay(0, rect.height);
         menu.setLocation(pt.x, pt.y);
         menu.setVisible(true);
      });

      viewer = new SourceViewer(root, null, null, false, org.eclipse.swt.SWT.BORDER | org.eclipse.swt.SWT.V_SCROLL
            | org.eclipse.swt.SWT.H_SCROLL);
      viewer.getTextWidget().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      viewer.configure(new SourceViewerConfiguration() {
         @Override
         public IPresentationReconciler getPresentationReconciler(final @Nullable ISourceViewer sourceViewer) {
            return reconciler;
         }
      });

      // setting up placeholder text
      viewer.getTextWidget().addPaintListener(placeholderPainter);
      final var document = new Document();
      viewer.setDocument(document);
      document.addDocumentListener(placeholderUpdatingDocumentListener);

      setTheme(null);
   }

   private Registry newTemporaryRegistry() {
      return new Registry(new IRegistryOptions() {
         @Override
         public @Nullable Collection<String> getInjections(final String scopeName) {
            return getGrammarRegistryManager().getInjections(ITMScope.parse(scopeName));
         }
      }) {
         @Override
         protected @Nullable IGrammarSource _grammarSourceForScopeName(final String scopeName) {
            final IGrammarSource src = super._grammarSourceForScopeName(scopeName);
            if (src != null)
               return src;

            // Fallback to TM4E manager: provide sources for included grammars
            for (final IGrammarDefinition def : getGrammarRegistryManager().getDefinitions()) {
               final ITMScope scope = def.getScope();
               if (scopeName.equals(scope.getQualifiedName()) || scopeName.equals(scope.getName()))
                  return new IGrammarSource() {
                     @Override
                     public long getLastModified() {
                        return def.getLastModified();
                     }

                     @Override
                     public Reader getReader() throws IOException {
                        return new InputStreamReader(def.getInputStream(), StandardCharsets.UTF_8);
                     }

                     @Override
                     public URI getURI() {
                        return def.getURI();
                     }
                  };
            }
            return null;
         }

         @Override
         public @Nullable IGrammar grammarForScopeName(final String scopeName) {
            final IGrammar grammar = super.grammarForScopeName(scopeName);
            return grammar == null //
                  ? getGrammarRegistryManager().getGrammarForScope(ITMScope.parse(scopeName))
                  : grammar;
         }

         @Override
         public @Nullable IGrammar loadGrammar(final String initialScopeName) {
            final IGrammar grammar = super.loadGrammar(initialScopeName);
            return grammar == null //
                  ? getGrammarRegistryManager().getGrammarForScope(ITMScope.parse(initialScopeName))
                  : grammar;
         }
      };
   }

   @Override
   public boolean render(final ContentSource source, final boolean forceCacheUpdate) throws IOException {
      final Path path = source.path();
      final boolean isSynced = source.isSynced();

      String content = null;
      long modified = 0L;
      int contentHash = 0;
      if (isSynced) {
         modified = source.lastModified();
      } else {
         content = source.contentAsString();
         contentHash = content.hashCode();
      }

      final boolean needsUpdate = forceCacheUpdate //
            || lastSynced != isSynced //
            || (isSynced ? lastModified != modified : lastContentHash != contentHash) //
            || !Objects.equals(lastPath, path);
      lastPath = path;
      lastModified = modified;
      lastSynced = isSynced;
      lastContentHash = contentHash;

      // always claim we can render these sources (extension filtering happens at extension layer)
      if (!needsUpdate)
         return true;

      final var gs = isSynced //
            ? IGrammarSource.fromFile(path)
            : IGrammarSource.fromString(detectContentType(path), asNonNull(content));

      final IGrammar grammar = newTemporaryRegistry().addGrammar(gs);

      final ISample[] samples = getSampleManager().getSamples(grammar.getScopeName());
      final String sampleText = samples.length > 0 ? samples[0].getContent() : "";

      UI.run(() -> {
         reconciler.setGrammar(grammar);
         setTheme(selectedTheme);
         asNonNull(viewer.getDocument()).set(sampleText);
         applyZoom();
      });
      return true;
   }

   private void setTheme(@Nullable ITheme theme) {
      selectedTheme = theme;
      if (theme == null) {
         final var grammar = reconciler.getGrammar();
         theme = grammar == null //
               ? getThemeManager().getDefaultTheme()
               : getThemeManager().getThemeForScope(grammar.getScopeName());
      }
      reconciler.setTheme(theme);
      final StyledText styledText = viewer.getTextWidget();
      styledText.setFont(JFaceResources.getTextFont());
      styledText.setForeground(null);
      styledText.setBackground(null);
      theme.initializeViewerColors(styledText);
      themeLink.setText("Theme: <a>" + theme.getName() + "</a>");
      themeLink.pack();
      final var parent = themeLink.getParent();
      if (parent != null && !parent.isDisposed()) {
         parent.layout(true, true);
      }
   }

   @Override
   public void setZoom(final float level) {
      zoom = level <= 0 ? 1.0f : level;
      applyZoom();
   }
}
