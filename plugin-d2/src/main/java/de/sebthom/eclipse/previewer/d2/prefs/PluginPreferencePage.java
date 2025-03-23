/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.d2.prefs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

      addField(new GroupFieldEditor("External renderer", parent, group -> List.of( //
         new FileFieldEditor(PluginPreferences.PREF_D2_NATIVE_EXE, "D2 Executable", group) {
            @Override
            protected boolean checkState() {
               String msg = null;
               final String path = getTextControl().getText().trim();
               if (path.isEmpty()) {
                  msg = "The name of the D2 command must be specified!";
               } else if (path.contains("/") || path.contains("\\")) {
                  if (!Files.exists(Path.of(path))) {
                     msg = "Given path does not point to an existing file!";
                  } else if (!Files.isExecutable(Path.of(path))) {
                     msg = "Given path does not point to an executable file!";
                  }
               } else {
                  if (SystemUtils.findExecutable(path, false) == null) {
                     msg = "The D2 executable cannot be found on PATH!";
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
