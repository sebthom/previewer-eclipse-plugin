/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Verifies that GitHub repository URLs are identified without accepting look-alike hosts or unrelated paths.
 *
 * @author Sebastian Thomschke
 */
class GitUtilsTest {

   @Test
   void extractsRepositoryCoordinatesFromSupportedGitHubUrls() {
      assertArrayEquals(new String[] {"owner", "repository"}, GitUtils.getGitHubOrgAndRepo("https://github.com/owner/repository.git"));
      assertArrayEquals(new String[] {"Owner", "Repository"}, GitUtils.getGitHubOrgAndRepo("git@github.com:Owner/Repository.git"));
      assertArrayEquals(new String[] {"owner", "repository"}, GitUtils.getGitHubOrgAndRepo("ssh://git@github.com/owner/repository"));
      assertArrayEquals(new String[] {"owner", "repository"}, GitUtils.getGitHubOrgAndRepo("git://github.com/owner/repository/"));
   }

   @Test
   void rejectsLookAlikeHostsAndNonRepositoryUrls() {
      assertNull(GitUtils.getGitHubOrgAndRepo("https://github.com.example.org/owner/repository.git"));
      assertNull(GitUtils.getGitHubOrgAndRepo("https://example.org/github.com/owner/repository.git"));
      assertNull(GitUtils.getGitHubOrgAndRepo("https://github.com/owner/repository/issues"));
      assertNull(GitUtils.getGitHubOrgAndRepo("file:///github.com/owner/repository.git"));
   }
}
