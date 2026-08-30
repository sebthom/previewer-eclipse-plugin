/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Uses optional JGit services to prove that a source belongs to GitHub and is not excluded from Git.
 *
 * @author Sebastian Thomschke
 */
final class JGitRepositoryInspector {

   @SuppressWarnings("resource")
   static boolean isFileEligibleForGitHubRendering(final Path sourcePath) throws Exception {
      final Path absoluteSourcePath = sourcePath.toAbsolutePath().normalize();
      if (!Files.isRegularFile(absoluteSourcePath))
         return false;

      final var repositoryBuilder = findRepositoryBuilder(absoluteSourcePath);
      if (repositoryBuilder == null)
         return false;

      try (var git = new Git(repositoryBuilder.build())) {
         // Git owns and closes this repository; declaring both as resources would close the same handle twice.
         @SuppressWarnings("null")
         final @NonNull Repository repository = git.getRepository();
         if (getGitHubOrgAndRepo(repository) == null)
            return false;

         final Path workTree = repository.getWorkTree().toPath().toAbsolutePath().normalize();
         if (!absoluteSourcePath.startsWith(workTree))
            return false;

         final Path repositoryDirectory = repository.getDirectory().toPath().toAbsolutePath().normalize();
         // Git metadata can live inside an ordinary work tree, but it is never repository content and status does not report it as ignored.
         if (absoluteSourcePath.startsWith(repositoryDirectory) || absoluteSourcePath.equals(workTree.resolve(".git")))
            return false;

         final Path relativeSourcePath = workTree.relativize(absoluteSourcePath);
         if (relativeSourcePath.getNameCount() == 0 || containsSymbolicLink(workTree, relativeSourcePath))
            return false;

         final String gitPath = toGitPath(relativeSourcePath);
         // Git ignore rules do not apply to indexed files. This also keeps ordinary git add stable because non-ignored files are eligible before indexing.
         if (repository.readDirCache().getEntry(gitPath) != null)
            return true;

         if (hasUnsupportedRelativeExcludesFile(repository))
            return false;

         final var pathAndAncestors = new ArrayList<String>();
         for (Path candidate = relativeSourcePath; candidate != null; candidate = candidate.getParent()) {
            pathAndAncestors.add(toGitPath(candidate));
         }

         // Directory filters recurse into all descendants. A target-only filter still reports an ignored hierarchy at its root without scanning siblings.
         final var ignoredPaths = git.status().addPath(gitPath).call().getIgnoredNotInIndex();
         // JGit reports an ignored hierarchy at its root, so a descendant must also be checked against each parent returned by status.
         return pathAndAncestors.stream().noneMatch(ignoredPaths::contains);
      }
   }

   static String @Nullable [] getGitHubOrgAndRepo(final Path sourcePath) throws IOException {
      final var repositoryBuilder = findRepositoryBuilder(sourcePath.toAbsolutePath().normalize());
      if (repositoryBuilder == null)
         return null;

      try (@SuppressWarnings("null")
      @NonNull
      Repository repository = repositoryBuilder.build()) {
         return getGitHubOrgAndRepo(repository);
      }
   }

   @SuppressWarnings("null")
   private static boolean containsSymbolicLink(final Path workTree, final Path relativeSourcePath) {
      Path candidate = workTree;
      for (final @NonNull Path segment : relativeSourcePath) {
         candidate = candidate.resolve(segment);
         // A linked file could expose content that is not actually stored in the GitHub repository.
         if (Files.isSymbolicLink(candidate))
            return true;
      }
      return false;
   }

   @SuppressWarnings("null")
   private static String @Nullable [] getGitHubOrgAndRepo(final Repository repository) {
      final var config = repository.getConfig();
      for (final @NonNull String remoteUrl : config.getStringList(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
         ConfigConstants.CONFIG_KEY_URL)) {
         final var githubRepository = GitUtils.getGitHubOrgAndRepo(remoteUrl);
         // Preserve origin's existing precedence when it identifies a GitHub repository.
         if (githubRepository != null)
            return githubRepository;
      }

      String @Nullable [] githubRepository = null;
      for (final String remoteName : config.getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION)) {
         for (final @NonNull String remoteUrl : config.getStringList(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName,
            ConfigConstants.CONFIG_KEY_URL)) {
            final var candidate = GitUtils.getGitHubOrgAndRepo(remoteUrl);
            if (candidate == null) {
               continue;
            }
            // GitHub repository paths are case-insensitive, so differently cased URLs still identify one API context.
            if (githubRepository == null) {
               githubRepository = candidate;
            } else if (!githubRepository[0].equalsIgnoreCase(candidate[0]) || !githubRepository[1].equalsIgnoreCase(candidate[1]))
               // Without a GitHub origin, choosing between different repositories would make links depend on remote iteration order.
               return null;
         }
      }
      return githubRepository;
   }

   private static @Nullable FileRepositoryBuilder findRepositoryBuilder(final Path absoluteSourcePath) {
      final Path searchDirectory = Files.isDirectory(absoluteSourcePath) ? absoluteSourcePath : absoluteSourcePath.getParent();
      if (searchDirectory == null)
         return null;

      final var repositoryBuilder = new FileRepositoryBuilder();
      // Anchor discovery to the source path: ambient GIT_DIR, GIT_WORK_TREE, or GIT_INDEX_FILE must not redirect this privacy decision.
      repositoryBuilder.findGitDir(searchDirectory.toFile()).setMustExist(true);
      return repositoryBuilder.getGitDir() == null ? null : repositoryBuilder;
   }

   private static boolean hasUnsupportedRelativeExcludesFile(final Repository repository) {
      final String excludesFile = repository.getConfig().getString(ConfigConstants.CONFIG_CORE_SECTION, null,
         ConfigConstants.CONFIG_KEY_EXCLUDESFILE);
      if (excludesFile == null || excludesFile.isBlank() || excludesFile.startsWith("~/") || excludesFile.startsWith("~\\"))
         return false;

      // JGit versions resolve relative core.excludesFile values inconsistently; treating them as inconclusive avoids leaking an ignored file.
      return !Path.of(excludesFile).isAbsolute();
   }

   private static String toGitPath(final Path path) {
      return path.toString().replace('\\', '/');
   }

   private JGitRepositoryInspector() {
   }
}
