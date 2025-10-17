/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.prefs;

import java.util.List;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import de.sebthom.eclipse.commons.prefs.fieldeditor.GroupFieldEditor;
import de.sebthom.eclipse.commons.prefs.fieldeditor.IntFieldEditor;
import de.sebthom.eclipse.commons.prefs.fieldeditor.PasswordFieldEditor;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

   @Override
   protected void createFieldEditors() {
      final var parent = getFieldEditorParent();

      addField(new ComboFieldEditor(PluginPreferences.PREF_MARKDOWN_RENDERER, "Markdown Renderer:", new String[][] { //
         {"CommonMark (= offline rendering)", "commonmark"}, //
         {"GitHub Markdown API (= online rendering)", "github"} //
      }, parent));

      addField(new GroupFieldEditor("GitHub API", parent, group -> List.of( //
         new StringFieldEditor(PluginPreferences.PREF_GITHUB_API_URL, "API Endpoint URL", group), //
         new IntFieldEditor(PluginPreferences.PREF_GITHUB_API_RESONSE_TIMEOUT, "API Resonse Timeout (seconds)", group, 3), //
         new PasswordFieldEditor(PluginPreferences.PREF_GITHUB_API_TOKEN, "API Token", group), //
         new ComboFieldEditor(PluginPreferences.PREF_GITHUB_API_MARKDOWN_MODE, "Render Mode:", new String[][] { //
            {"markdown", "markdown"}, //
            {"gfm", "gfm"} //
         }, group), //
         new BooleanFieldEditor(PluginPreferences.PREF_GITHUB_API_FALLBACK_TO_COMMONMARK,
            "Use CommonMark renderer when offline or GitHub Markdown API is unavailable", group)) //
      ));

      addField(new BooleanFieldEditor(PluginPreferences.PREF_RENDER_MERMAID_DIAGRAMS, "Render Mermaid diagrams", parent));
   }

   public PluginPreferencePage() {
      super(FieldEditorPreferencePage.GRID);
   }

   @Override
   public void init(final IWorkbench workbench) {
      setPreferenceStore(PluginPreferences.STORE);
   }
}
