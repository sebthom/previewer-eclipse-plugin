/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml.prefs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.sebthom.eclipse.commons.prefs.fieldeditor.GroupFieldEditor;
import net.sf.jstuff.core.SystemUtils;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

   @Override
   protected void createFieldEditors() {
      final var parent = getFieldEditorParent();

      final var layoutEngineCombo = new ComboFieldEditor(PluginPreferences.PREF_PLANTUML_LAYOUT_ENGINE, "PlantUML Layout Engine:",
         new String[][] { //
            {"GraphViz DOT (external)", "dot"}, //
            {"Smetana (built-in)", "smetana"} //
         }, parent);
      addField(layoutEngineCombo);

      addField(new GroupFieldEditor("External layout engine", parent, group -> List.of( //
         new FileFieldEditor(PluginPreferences.PREF_GRAPHVIZ_DOT_EXE, "GraphViz dot Executable", group) {
            @Override
            protected boolean checkState() {
               final var layoutEngine = layoutEngineCombo.getPreferenceStore().getString(layoutEngineCombo.getPreferenceName());

               if (!"smetana".equals(layoutEngine))
                  return true;

               String msg = null;
               final String path = getTextControl().getText().trim();
               if (path.isEmpty()) {
                  msg = "The name of the GraphViz dot command must be specified!";
               } else if (path.contains("/") || path.contains("\\")) {
                  if (!Files.exists(Path.of(path))) {
                     msg = "Given path does not point to an existing file!";
                  } else if (!Files.isExecutable(Path.of(path))) {
                     msg = "Given path does not point to an executable file!";
                  }
               } else {
                  if (SystemUtils.findExecutable(path, false) == null) {
                     msg = "The GraphViz dot executable cannot be found on PATH!";
                  }
               }

               if (msg != null) { // error
                  showErrorMessage(msg);
                  return false;
               }

               clearErrorMessage();
               return true;
            }
         })));
   }

   public PluginPreferencePage() {
      super(FieldEditorPreferencePage.GRID);
   }

   @Override
   public void init(final IWorkbench workbench) {
      setPreferenceStore(PluginPreferences.STORE);
   }
}
