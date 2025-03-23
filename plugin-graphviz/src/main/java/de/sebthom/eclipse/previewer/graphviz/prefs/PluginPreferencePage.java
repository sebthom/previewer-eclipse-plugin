/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.prefs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jface.preference.BooleanFieldEditor;
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

      final var rendererCombo = new ComboFieldEditor(PluginPreferences.PREF_GRAPHVIZ_RENDERER, "Graphviz Renderer:", new String[][] { //
         {"GraphViz dot (external)", PluginPreferences.PREF_GRAPHVIZ_RENDERER_NATIVE}, //
         {"viz.js (built-in)", PluginPreferences.PREF_GRAPHVIZ_RENDERER_EMBEDDED} //
      }, parent);
      addField(rendererCombo);

      addField(new GroupFieldEditor("External renderer", parent, group -> List.of( //
         new FileFieldEditor(PluginPreferences.PREF_GRAPHVIZ_NATIVE_EXE, "GraphViz dot Executable", group) {
            @Override
            protected boolean checkState() {
               final var renderer = rendererCombo.getPreferenceStore().getString(rendererCombo.getPreferenceName());

               if (!PluginPreferences.PREF_GRAPHVIZ_RENDERER_NATIVE.equals(renderer))
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
         }, //
         new BooleanFieldEditor(PluginPreferences.PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_EMBEDDED,
            "Use embedded renderer when native renderer is unavailable", group)) //
      ));
   }

   public PluginPreferencePage() {
      super(FieldEditorPreferencePage.GRID);
   }

   @Override
   public void init(final IWorkbench workbench) {
      setPreferenceStore(PluginPreferences.STORE);
   }
}
