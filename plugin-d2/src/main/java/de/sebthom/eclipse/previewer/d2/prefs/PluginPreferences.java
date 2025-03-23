/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.d2.prefs;

import java.io.IOException;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import de.sebthom.eclipse.previewer.d2.Plugin;
import de.sebthom.eclipse.previewer.d2.renderer.D2NativeRenderer;
import de.sebthom.eclipse.previewer.d2.renderer.D2Renderer;
import net.sf.jstuff.core.io.RuntimeIOException;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferences {

   public static final IPersistentPreferenceStore STORE = Plugin.get().getPreferenceStore();

   public static final String PREF_D2_NATIVE_EXE = "d2NativeExe";

   public static final class Initializer extends AbstractPreferenceInitializer {

      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_D2_NATIVE_EXE, "d2");
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

   public static D2Renderer getD2Renderer() {
      return D2NativeRenderer.INSTANCE;
   }

   public static String getD2NativeExe() {
      return STORE.getString(PREF_D2_NATIVE_EXE);
   }

   private PluginPreferences() {
   }
}
