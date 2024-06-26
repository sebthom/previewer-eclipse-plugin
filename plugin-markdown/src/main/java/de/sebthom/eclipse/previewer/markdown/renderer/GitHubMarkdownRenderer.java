/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.renderer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.markdown.prefs.PluginPreferences;
import de.sebthom.eclipse.previewer.markdown.util.GitUtils;
import de.sebthom.eclipse.previewer.markdown.util.NetUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;
import net.sf.jstuff.core.io.stream.CharSequenceInputStream;

/**
 * Uses the online <a href="https://docs.github.com/en/rest/markdown/markdown">GitHub API for Markdown</a> to render markdown
 *
 * @author Sebastian Thomschke
 */
public final class GitHubMarkdownRenderer implements MarkdownRenderer {

   public static final GitHubMarkdownRenderer INSTANCE = new GitHubMarkdownRenderer();

   @Override
   public void markdownToHTML(final ContentSource source, final Appendable out) throws IOException {
      try (var reader = source.contentAsReader()) {

         final var githubRepo = GitUtils.getGitHubOrgAndRepo(source.path());

         final var jsonPayload = new StringBuilder();
         jsonPayload.append("{");
         jsonPayload.append("\"text\": \"");
         StringUtils.jsonEscape(reader, jsonPayload);
         jsonPayload.append('"');
         jsonPayload.append(", \"mode\": \"").append(PluginPreferences.getGithubApiMarkdownRenderMode()).append('"');
         if (githubRepo != null) {
            jsonPayload.append(", \"context\": \"").append(githubRepo[0]).append('/').append(githubRepo[1]).append('"');
         }
         jsonPayload.append("}");

         final var githubApiUrl = PluginPreferences.getGithubApiUrl();
         final var githubApiToken = PluginPreferences.getGithubApiToken();
         final var uri = URI.create(githubApiUrl + (githubApiUrl.endsWith("/") ? "" : "/") + "markdown");

         final var request = HttpRequest.newBuilder().uri(uri) //
            .header("Accept", "application/vnd.github.v3+json") //
            .header("Content-Type", "application/json") //
            .header("X-GitHub-Api-Version", "2022-11-28") //
            .timeout(Duration.ofSeconds(5)) //
            .POST(BodyPublishers.ofInputStream(() -> new CharSequenceInputStream(jsonPayload)));
         if (!githubApiToken.isBlank()) {
            request.header("Authorization", "token " + githubApiToken);
         }

         try {
            final var response = NetUtils.getHttpClient(uri) //
               .send(request.build(), BodyHandlers.ofString());

            if (response.statusCode() != 200)
               throw new IOException("HTTP " + response.statusCode() + " " + response.body());

            out.append(response.body());
         } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
         }
      }
   }

   protected GitHubMarkdownRenderer() {
   }
}
