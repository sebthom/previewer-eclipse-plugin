/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.markdown.Plugin;

/**
 * Provides lightweight Git discovery and privacy-gated classification of files for GitHub rendering.
 *
 * @author Sebastian Thomschke
 */
public final class GitUtils {

   // This is a privacy gate, so the entire remote URL and exact github.com host must match; both URI and SCP-like Git forms are accepted.
   private static final Pattern GITHUB_REPOSITORY_URL = Pattern.compile(
      "^(?:(?:https?|git|ssh)://(?:[^/@]+@)?github\\.com(?::\\d+)?/|(?:[^@/:]+@)?github\\.com:)([^/]+)/([^/#?]+?)(?:\\.git)?/?$",
      Pattern.CASE_INSENSITIVE);

   public static boolean isFileInGitRepo(final Path path) {
      return findGitRepoRoot(path) != null;
   }

   public static @Nullable Path findGitRepoRoot(final Path path) {
      Path currentPath = path.toAbsolutePath();
      while (currentPath != null) {
         if (Files.exists(currentPath.resolve(".git")))
            return currentPath;
         currentPath = currentPath.getParent();
      }
      return null;
   }

   public static @Nullable String getGitRepoUrl(final Path path) {
      final Path repoRoot = findGitRepoRoot(path);
      if (repoRoot == null)
         return null;

      final Path configFilePath = repoRoot.resolve(".git/config");
      if (!Files.exists(configFilePath))
         return null;

      try (var reader = Files.newBufferedReader(configFilePath)) {
         boolean inRemoteOriginSection = false;
         String line;

         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if ("[remote \"origin\"]".equals(line)) {
               inRemoteOriginSection = true;
            } else if (inRemoteOriginSection) {
               if (line.startsWith("url = "))
                  return line.substring(6).trim();
               else if (line.startsWith("[")) {
                  inRemoteOriginSection = false;
               }
            }
         }
      } catch (final IOException ex) {
         Plugin.log().error(ex);
      }
      return null;
   }

   public static String @Nullable [] getGitHubOrgAndRepo(final Path path) {
      try {
         return JGitRepositoryInspector.getGitHubOrgAndRepo(path);
      } catch (final Exception | LinkageError ex) {
         // Explicit GitHub rendering predates the optional JGit integration, so retain its origin-only context fallback when JGit cannot load.
      }

      final var gitRepoUrl = getGitRepoUrl(path);
      return gitRepoUrl == null ? null : getGitHubOrgAndRepo(gitRepoUrl);
   }

   public static String @Nullable [] getGitHubOrgAndRepo(final String url) {
      final var matcher = GITHUB_REPOSITORY_URL.matcher(url);
      if (matcher.matches())
         return new String[] {asNonNull(matcher.group(1)), asNonNull(matcher.group(2))};
      return null;
   }

   /**
    * @return {@code true} only when repository metadata positively identifies the file as suitable for automatic GitHub API rendering
    */
   public static boolean isFileEligibleForGitHubRendering(final Path path) {
      try {
         // Keep every JGit-typed reference behind this call so the bundle still loads when the optional bundle is absent.
         return JGitRepositoryInspector.isFileEligibleForGitHubRendering(path);
      } catch (final Exception | LinkageError ex) {
         // Automatic mode is a privacy boundary: unavailable or inconclusive inspection must keep the content local.
         if (Plugin.isInitialized()) {
            Plugin.log().debug(ex);
         }
         return false;
      }
   }

   private GitUtils() {
   }
}
