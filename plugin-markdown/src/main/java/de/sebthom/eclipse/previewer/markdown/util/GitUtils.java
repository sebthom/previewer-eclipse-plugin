/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.markdown.Plugin;

/**
 * @author Sebastian Thomschke
 */
public final class GitUtils {

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
      final var gitRepoUrl = getGitRepoUrl(path);
      return gitRepoUrl == null ? null : getGitHubOrgAndRepo(gitRepoUrl);
   }

   public static String @Nullable [] getGitHubOrgAndRepo(final String url) {
      final var pattern = Pattern.compile("github\\.com[:/](.+?)/(.+?)(\\.git)?$");
      final var matcher = pattern.matcher(url);
      if (matcher.find())
         return new String[] {asNonNull(matcher.group(1)), asNonNull(matcher.group(2))};
      return null;
   }

   private GitUtils() {
   }
}
