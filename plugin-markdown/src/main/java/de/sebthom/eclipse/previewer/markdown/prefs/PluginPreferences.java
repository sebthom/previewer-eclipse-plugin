/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.prefs;

import java.io.IOException;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import de.sebthom.eclipse.previewer.markdown.Plugin;
import de.sebthom.eclipse.previewer.markdown.renderer.CommonMarkRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.GitHubMarkdownRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.MarkdownRenderer;
import net.sf.jstuff.core.io.RuntimeIOException;

/**
 * @author Sebastian Thomschke
 */
public final class PluginPreferences {

   public static final class Initializer extends AbstractPreferenceInitializer {

      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_MARKDOWN_RENDERER, "commonmark");

         STORE.setDefault(PREF_GITHUB_API_URL, "https://api.github.com");
         STORE.setDefault(PREF_GITHUB_API_MARKDOWN_MODE, "gfm");
         STORE.setDefault(PREF_GITHUB_API_FALLBACK_TO_COMMONMARK, true);
         STORE.setDefault(PREF_GITHUB_API_RESONSE_TIMEOUT, 5);

         STORE.setDefault(PREF_RENDER_MERMAID_DIAGRAMS, true);
      }
   }

   public static final IPersistentPreferenceStore STORE = Plugin.get().getPreferenceStore();

   public static final String PREF_MARKDOWN_RENDERER = "markdownRenderer";

   public static final String PREF_GITHUB_API_URL = "githubApiUrl";
   public static final String PREF_GITHUB_API_TOKEN = "githubApiToken";
   public static final String PREF_GITHUB_API_MARKDOWN_MODE = "githubApiMarkdownMode";
   public static final String PREF_GITHUB_API_FALLBACK_TO_COMMONMARK = "githubApiFallbackToCommonmark";
   public static final String PREF_GITHUB_API_RESONSE_TIMEOUT = "githubApiResponseTimeout";

   public static final String PREF_RENDER_MERMAID_DIAGRAMS = "renderMermaidDiagrams";

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

   public static MarkdownRenderer getMarkdownRenderer() {
      return "github".equals(STORE.getString(PREF_MARKDOWN_RENDERER)) //
            ? GitHubMarkdownRenderer.INSTANCE
            : CommonMarkRenderer.INSTANCE;
   }

   public static int getGithubApiResonseTimeout() {
      return STORE.getInt(PREF_GITHUB_API_RESONSE_TIMEOUT);
   }

   public static String getGithubApiMarkdownRenderMode() {
      return STORE.getString(PREF_GITHUB_API_MARKDOWN_MODE);
   }

   public static String getGithubApiUrl() {
      return STORE.getString(PREF_GITHUB_API_URL);
   }

   public static boolean isGithubApiFallbackToCommonMark() {
      return STORE.getBoolean(PREF_GITHUB_API_FALLBACK_TO_COMMONMARK);
   }

   public static String getGithubApiToken() {
      return STORE.getString(PREF_GITHUB_API_TOKEN);
   }

   public static boolean isRenderMermaidDiagrams() {
      return STORE.getBoolean(PREF_RENDER_MERMAID_DIAGRAMS);
   }

   private PluginPreferences() {
   }
}
