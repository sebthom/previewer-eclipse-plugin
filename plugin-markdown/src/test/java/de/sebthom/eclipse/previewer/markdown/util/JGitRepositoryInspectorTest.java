/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the privacy-sensitive repository and ignore checks used by automatic GitHub Markdown rendering.
 *
 * @author Sebastian Thomschke
 */
class JGitRepositoryInspectorTest {

   @TempDir
   Path tempDir = lateNonNull();

   @Test
   void keepsTheRendererStableWhenANonIgnoredFileIsAddedToTheIndex() throws Exception {
      try (var git = initRepository("git@github.com:owner/repository.git")) {
         final Path markdownFile = writeFile(git, "notes.md");

         assertTrue(JGitRepositoryInspector.isFileEligibleForGitHubRendering(markdownFile));
         git.add().addFilepattern("notes.md").call();
         assertTrue(JGitRepositoryInspector.isFileEligibleForGitHubRendering(markdownFile));
      }
   }

   @Test
   void excludesFilesMatchedByRepositoryIgnoreSources() throws Exception {
      try (var git = initRepository("https://github.com/owner/repository.git")) {
         final Path ignoredDescendant = writeFile(git, "ignored-directory/private.md");
         final Path ignoredFile = writeFile(git, "ignored-file.md");
         final Path trackedFile = writeFile(git, "tracked-file.md");
         git.add().addFilepattern("tracked-file.md").call();
         writeFile(git, ".gitignore", "ignored-directory/\nignored-file.md\ntracked-file.md\n");

         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(ignoredDescendant));
         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(ignoredFile));
         // Git does not consider an indexed path ignored, even when a later ignore rule matches it.
         assertTrue(JGitRepositoryInspector.isFileEligibleForGitHubRendering(trackedFile));
      }
   }

   @Test
   @SuppressWarnings("resource")
   void excludesRepositoryAdministrationFiles() throws Exception {
      try (var git = initRepository("https://github.com/owner/repository.git")) {
         final Path metadataFile = git.getRepository().getDirectory().toPath().resolve("private.md");
         Files.writeString(metadataFile, "# Private metadata\n");

         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(metadataFile));
      }
   }

   @Test
   @SuppressWarnings("resource")
   void excludesFilesMatchedByInfoAndConfiguredExcludeFiles() throws Exception {
      try (var git = initRepository("https://github.com/owner/repository")) {
         final Path infoExcludedFile = writeFile(git, "info-private.md");
         final Path infoExcludeFile = git.getRepository().getDirectory().toPath().resolve("info/exclude");
         // JGit's minimal repository initialization does not create the optional info directory until another operation needs it.
         Files.createDirectories(infoExcludeFile.getParent());
         Files.writeString(infoExcludeFile, "info-private.md\n");

         final Path configuredExcludeFile = tempDir.resolve("global-ignore");
         Files.writeString(configuredExcludeFile, "configured-private.md\n");
         final var config = git.getRepository().getConfig();
         config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_EXCLUDESFILE, configuredExcludeFile
            .toString());
         config.save();
         final Path configuredExcludedFile = writeFile(git, "configured-private.md");

         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(infoExcludedFile));
         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(configuredExcludedFile));
      }
   }

   @Test
   void requiresAnExistingFileInsideARepositoryWithAGitHubRemote() throws Exception {
      assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(tempDir.resolve("missing.md")));

      try (var git = initRepository("https://example.org/owner/repository.git")) {
         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(writeFile(git, "notes.md")));
      }
   }

   @Test
   void usesTheSameUnambiguousGitHubRemoteForEligibilityAndApiContext() throws Exception {
      try (var git = initRepository("https://example.org/owner/repository.git")) {
         setRemoteUrl(git, "github", "https://github.com/owner/repository.git");
         final Path markdownFile = writeFile(git, "notes.md");

         assertTrue(JGitRepositoryInspector.isFileEligibleForGitHubRendering(markdownFile));
         assertArrayEquals(new String[] {"owner", "repository"}, GitUtils.getGitHubOrgAndRepo(markdownFile));

         setRemoteUrl(git, "other-github", "https://github.com/other/repository.git");
         assertFalse(JGitRepositoryInspector.isFileEligibleForGitHubRendering(markdownFile));
         assertNull(GitUtils.getGitHubOrgAndRepo(markdownFile));

         setRemoteUrl(git, "origin", "https://github.com/primary/repository.git");
         assertTrue(JGitRepositoryInspector.isFileEligibleForGitHubRendering(markdownFile));
         assertArrayEquals(new String[] {"primary", "repository"}, GitUtils.getGitHubOrgAndRepo(markdownFile));
      }
   }

   private Git initRepository(final String remoteUrl) throws Exception {
      @SuppressWarnings("null")
      final @NonNull Git git = Git.init().setDirectory(tempDir.resolve("repository").toFile()).call();
      setRemoteUrl(git, "origin", remoteUrl);
      return git;
   }

   @SuppressWarnings("resource")
   private void setRemoteUrl(final Git git, final String remoteName, final String remoteUrl) throws IOException {
      final var config = git.getRepository().getConfig();
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_KEY_URL, remoteUrl);
      config.save();
   }

   private Path writeFile(final Git git, final String relativePath) throws IOException {
      return writeFile(git, relativePath, "# Markdown\n");
   }

   @SuppressWarnings("resource")
   private Path writeFile(final Git git, final String relativePath, final String content) throws IOException {
      final Path file = git.getRepository().getWorkTree().toPath().resolve(relativePath);
      Files.createDirectories(file.getParent());
      return Files.writeString(file, content);
   }
}
