/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.prefs;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.sebthom.eclipse.commons.ui.Tables;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

   @Override
   public void init(final IWorkbench workbench) {
      setPreferenceStore(PluginPreferences.STORE);
   }

   @Override
   protected Control createContents(final Composite parent) {
      final var container = new Composite(parent, SWT.NULL);
      container.setLayout(new GridLayout(1, false));

      new Label(container, SWT.NONE).setText("Previewer plugins:");
      createRendererTable(container, input -> //
      Arrays.stream(Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) //
         .filter(ce -> "previewRenderer".equals(ce.getName())).toArray());

      new Label(container, SWT.NONE).setText("HTML previewer plugins:");
      createRendererTable(container, input -> //
      Arrays.stream(Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) //
         .filter(ce -> "htmlPreviewRenderer".equals(ce.getName())).toArray());

      return container;
   }

   private TableViewer createRendererTable(final Composite parent, final IStructuredContentProvider contentProvider) {
      final var tableViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
      final var table = tableViewer.getTable();
      table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 4));
      table.setHeaderVisible(true);
      table.setLinesVisible(true);

      final var colContributor = new TableViewerColumn(tableViewer, SWT.NONE);
      colContributor.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public @Nullable String getText(final Object element) {
            final var ce = (IConfigurationElement) element;
            return ce.getContributor().getName();
         }
      });
      colContributor.getColumn().setText("Contributor ID");

      final var colClass = new TableViewerColumn(tableViewer, SWT.NONE);
      colClass.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public @Nullable String getText(final Object element) {
            final var ce = (IConfigurationElement) element;
            final var contributor = ce.getContributor().getName();
            final var renderer = asNonNull(ce.getAttribute("class"));
            return Strings.removeStart(renderer, contributor);
         }
      });
      colClass.getColumn().setText("Renderer");

      final var colFileExt = new TableViewerColumn(tableViewer, SWT.NONE);
      colFileExt.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public @Nullable String getText(final Object element) {
            final var ce = (IConfigurationElement) element;
            return ce.getAttribute("file-extensions");
         }
      });
      colFileExt.getColumn().setText("File Extensions");

      final var colFileName = new TableViewerColumn(tableViewer, SWT.NONE);
      colFileName.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public @Nullable String getText(final Object element) {
            final var ce = (IConfigurationElement) element;
            return ce.getAttribute("file-names");
         }
      });
      colFileName.getColumn().setText("File Names");

      final var colFilePatterns = new TableViewerColumn(tableViewer, SWT.NONE);
      colFilePatterns.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public @Nullable String getText(final Object element) {
            final var ce = (IConfigurationElement) element;
            return ce.getAttribute("file-patterns");
         }
      });
      colFilePatterns.getColumn().setText("File Patterns");

      final var colFileContentTypes = new TableViewerColumn(tableViewer, SWT.NONE);
      colFileContentTypes.setLabelProvider(new ColumnLabelProvider() {
         @Override
         public String getText(final Object element) {
            final var contentTypes = new ArrayList<String>();
            final var ce = (IConfigurationElement) element;
            for (final var contentType : ce.getChildren("content-type")) {
               final var id = contentType.getAttribute("id");
               if (id != null && !id.isBlank()) {
                  contentTypes.add(id);
               }
            }
            return Strings.join(contentTypes);
         }
      });
      colFileContentTypes.getColumn().setText("Content Types");

      tableViewer.setContentProvider(contentProvider);
      tableViewer.setInput("");
      Tables.autoResizeColumns(tableViewer);
      return tableViewer;
   }
}
