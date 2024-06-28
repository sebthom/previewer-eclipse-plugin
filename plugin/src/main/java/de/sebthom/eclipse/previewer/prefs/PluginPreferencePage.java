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
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.sebthom.eclipse.commons.prefs.fieldeditor.LabelFieldEditor;
import de.sebthom.eclipse.commons.ui.Tables;
import de.sebthom.eclipse.previewer.Constants;
import de.sebthom.eclipse.previewer.Plugin;
import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

   private final class TableFieldEditor extends FieldEditor {
      final GridData tableLayoutData = new GridData(GridData.FILL, GridData.CENTER, false, false, 1, 1);
      final IStructuredContentProvider contentProvider;

      TableFieldEditor(final Composite parent, final IStructuredContentProvider contentProvider) {
         this.contentProvider = contentProvider;
         doFillIntoGrid(parent, getNumberOfControls());
      }

      @Override
      protected void adjustForNumColumns(final int numColumns) {
         tableLayoutData.horizontalSpan = numColumns;
      }

      @Override
      protected void doFillIntoGrid(final Composite parent, final int numColumns) {
         adjustForNumColumns(numColumns);

         final var tableViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
         final var table = tableViewer.getTable();
         table.setLayoutData(tableLayoutData);
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
      }

      @Override
      protected void doLoad() {
         // nothing to do
      }

      @Override
      protected void doLoadDefault() {
         // nothing to do
      }

      @Override
      protected void doStore() {
         // nothing to do
      }

      @Override
      public int getNumberOfControls() {
         return 1;
      }
   }

   public PluginPreferencePage() {
      super(FieldEditorPreferencePage.GRID);
   }

   @Override
   protected void createFieldEditors() {
      final var parent = getFieldEditorParent();

      addField(new LabelFieldEditor("Previewer plugins:", parent));
      addField(new TableFieldEditor(parent, input -> //
      Arrays.stream(Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) //
         .filter(ce -> "previewRenderer".equals(ce.getName())).toArray()));
      addField(new LabelFieldEditor("HTML previewer plugins:", parent));
      addField(new TableFieldEditor(parent, input -> //
      Arrays.stream(Plugin.getExtensionConfigurations(Constants.EXTENSION_POINT_RENDERERS)) //
         .filter(ce -> "htmlPreviewRenderer".equals(ce.getName())).toArray()));
   }

   @Override
   public void init(final IWorkbench workbench) {
      setPreferenceStore(PluginPreferences.STORE);
   }
}
