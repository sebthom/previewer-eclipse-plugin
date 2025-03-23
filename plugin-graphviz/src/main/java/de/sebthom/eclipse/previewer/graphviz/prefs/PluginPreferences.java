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

   public static final IPersistentPreferenceStore STORE = Plugin.get().getPreferenceStore();

   public static final String PREF_GRAPHVIZ_RENDERER = "graphvizRenderer";
   public static final String PREF_GRAPHVIZ_RENDERER_EMBEDDED = "embedded";
   public static final String PREF_GRAPHVIZ_RENDERER_NATIVE = "native";
   public static final String PREF_GRAPHVIZ_NATIVE_EXE = "graphvizNativeExe";
   public static final String PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_EMBEDDED = "graphvizNativeFallbackToEmbedded";

   public static final class Initializer extends AbstractPreferenceInitializer {
      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_GRAPHVIZ_RENDERER, PREF_GRAPHVIZ_RENDERER_EMBEDDED);
         STORE.setDefault(PREF_GRAPHVIZ_NATIVE_EXE, "dot");
         STORE.setDefault(PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_EMBEDDED, true);
      }
   }

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
      final boolean wantsNative = PREF_GRAPHVIZ_RENDERER_NATIVE.equals(STORE.getString(PREF_GRAPHVIZ_RENDERER));
      if (wantsNative) {
         final boolean nativeAvailable = GraphVizNativeRenderer.INSTANCE.isAvailable();
         final boolean fallbackAllowed = STORE.getBoolean(PREF_GRAPHVIZ_NATIVE_FALLBACK_TO_EMBEDDED);
         if (nativeAvailable || !fallbackAllowed)
            return GraphVizNativeRenderer.INSTANCE;
      }
      return GraphvizEmbeddedRenderer.INSTANCE;
   }

   public static String getGraphvizNativeExe() {
      return STORE.getString(PREF_GRAPHVIZ_NATIVE_EXE);
   }

   private PluginPreferences() {
   }
}
