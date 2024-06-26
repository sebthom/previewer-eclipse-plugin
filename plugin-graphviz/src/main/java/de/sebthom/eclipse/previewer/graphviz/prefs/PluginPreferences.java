/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.graphviz.prefs;

import java.io.IOException;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import de.sebthom.eclipse.previewer.graphviz.Plugin;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphVizNativeRenderer;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizEmbeddedRenderer;
import de.sebthom.eclipse.previewer.graphviz.renderer.GraphvizRenderer;
import net.sf.jstuff.core.io.RuntimeIOException;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferences {

   public static final class Initializer extends AbstractPreferenceInitializer {

      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_GRAPHVIZ_RENDERER, "java");
         STORE.setDefault(PREF_GRAPHVIZ_NATIVE_EXE, "dot");
         STORE.setDefault(PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_JAVA, true);
      }
   }

   public static final IPersistentPreferenceStore STORE = Plugin.get().getPreferenceStore();

   public static final String PREF_GRAPHVIZ_RENDERER = "graphvizRenderer";
   public static final String PREF_GRAPHVIZ_NATIVE_EXE = "graphvizNativeExe";
   public static final String PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_JAVA = "graphvizNativeFallbackToJava";

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

   public static GraphvizRenderer getGraphvizRenderer() {
      return "native".equals(STORE.getString(PREF_GRAPHVIZ_RENDERER)) //
            ? GraphVizNativeRenderer.INSTANCE
            : GraphvizEmbeddedRenderer.INSTANCE;
   }

   public static String getGraphvizNativeExe() {
      return STORE.getString(PREF_GRAPHVIZ_NATIVE_EXE);
   }

   public static int getGraphvizNativeFallbackToJava() {
      return STORE.getInt(PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_JAVA);
   }

   private PluginPreferences() {
   }
}
