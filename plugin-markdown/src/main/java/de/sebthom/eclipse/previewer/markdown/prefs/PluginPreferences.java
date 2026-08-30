/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.prefs;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import de.sebthom.eclipse.previewer.markdown.Plugin;
import de.sebthom.eclipse.previewer.markdown.renderer.CommonMarkRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.GitHubMarkdownRenderer;
import de.sebthom.eclipse.previewer.markdown.renderer.MarkdownRenderer;
import de.sebthom.eclipse.previewer.markdown.util.GitUtils;
import net.sf.jstuff.core.io.RuntimeIOException;

/**
 * Defines and persists Markdown renderer and embedded-content preferences.
 *
 * @author Sebastian Thomschke
 */
public final class PluginPreferences {

   static final String MARKDOWN_RENDERER_COMMONMARK = "commonmark";
   static final String MARKDOWN_RENDERER_GITHUB = "github";
   static final String MARKDOWN_RENDERER_GITHUB_AUTOMATIC = "github-automatic";

   public static final class Initializer extends AbstractPreferenceInitializer {

      @Override
      public void initializeDefaultPreferences() {
         STORE.setDefault(PREF_MARKDOWN_RENDERER, MARKDOWN_RENDERER_COMMONMARK);

         STORE.setDefault(PREF_GITHUB_API_URL, "https://api.github.com");
         STORE.setDefault(PREF_GITHUB_API_MARKDOWN_MODE, "gfm");
         STORE.setDefault(PREF_GITHUB_API_FALLBACK_TO_COMMONMARK, true);
         STORE.setDefault(PREF_GITHUB_API_RESONSE_TIMEOUT, 5);

         STORE.setDefault(PREF_RENDER_MERMAID_DIAGRAMS, true);
         STORE.setDefault(PREF_RENDER_PIKCHR_DIAGRAMS, true);
         STORE.setDefault(PREF_RENDER_PLANTUML_AND_GRAPHVIZ_DIAGRAMS, true);
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
   public static final String PREF_RENDER_PIKCHR_DIAGRAMS = "renderPikchrDiagrams";
   public static final String PREF_RENDER_PLANTUML_AND_GRAPHVIZ_DIAGRAMS = "renderPlantUmlAndGraphvizDiagrams";

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

   public static MarkdownRenderer getMarkdownRenderer(final Path sourcePath) {
      final String configuredRenderer = STORE.getString(PREF_MARKDOWN_RENDERER);
      if (MARKDOWN_RENDERER_GITHUB.equals(configuredRenderer))
         return GitHubMarkdownRenderer.INSTANCE;

      // Automatic mode is allow-list based: only a positive repository and ignore check may send source content off-machine.
      if (MARKDOWN_RENDERER_GITHUB_AUTOMATIC.equals(configuredRenderer) && GitUtils.isFileEligibleForGitHubRendering(sourcePath))
         return GitHubMarkdownRenderer.INSTANCE;

      return CommonMarkRenderer.INSTANCE;
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

   public static boolean isRenderPikchrDiagrams() {
      return STORE.getBoolean(PREF_RENDER_PIKCHR_DIAGRAMS);
   }

   public static boolean isRenderPlantUmlAndGraphvizDiagrams() {
      return STORE.getBoolean(PREF_RENDER_PLANTUML_AND_GRAPHVIZ_DIAGRAMS);
   }

   private PluginPreferences() {
   }
}
