/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml.prefs;

import java.io.IOException;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import de.sebthom.eclipse.previewer.plantuml.Plugin;
import net.sf.jstuff.core.io.RuntimeIOException;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferences {

   public static final class Initializer extends AbstractPreferenceInitializer {

      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_PLANTUML_LAYOUT_ENGINE, "smetana");
         STORE.setDefault(PREF_GRAPHVIZ_DOT_EXE, "dot");
      }
   }

   public static final IPersistentPreferenceStore STORE = Plugin.get().getPreferenceStore();

   public static final String PREF_PLANTUML_LAYOUT_ENGINE = "plantumlLayoutEngine";
   public static final String PREF_GRAPHVIZ_DOT_EXE = "graphvizDotExe";

   public static void addListener(final IPropertyChangeListener listener) {
      STORE.addPropertyChangeListener(listener);
   }

   public static void removeListener(final IPropertyChangeListener listener) {
      STORE.removePropertyChangeListener(listener);
   }

   public static void save() {
      if (STORE.needsSaving()) {
         try {
            STORE.save();
         } catch (final IOException ex) {
            throw new RuntimeIOException(ex);
         }
      }
   }

   public static String getPlantUmlLayoutEngine() {
      return STORE.getString(PREF_PLANTUML_LAYOUT_ENGINE);
   }

   public static String getGraphvizDotExe() {
      return STORE.getString(PREF_GRAPHVIZ_DOT_EXE);
   }

   private PluginPreferences() {
   }
}
