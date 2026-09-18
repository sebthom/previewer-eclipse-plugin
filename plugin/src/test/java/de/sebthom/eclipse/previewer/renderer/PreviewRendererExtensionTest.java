/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.junit.jupiter.api.Test;

import de.sebthom.eclipse.previewer.util.ContentSources.ContentSourceSnapshot;

/**
 * Checks renderer chooser names and backward compatibility with unnamed extension contributions.
 *
 * @author Sebastian Thomschke
 */
class PreviewRendererExtensionTest {

   private static IConfigurationElement configuration(final Map<String, String> attributes) {
      return (IConfigurationElement) Proxy.newProxyInstance(IConfigurationElement.class.getClassLoader(), new Class<?>[] {
         IConfigurationElement.class}, (proxy, method, args) -> switch (method.getName()) {
            case "createExecutableExtension" -> new Object();
            case "getAttribute" -> attributes.get(requireNonNull(args)[0]);
            case "getChildren" -> new IConfigurationElement[0];
            default -> throw new AssertionError("Unexpected configuration call: " + method);
         });
   }

   @Test
   void usesTheConfiguredDisplayName() throws CoreException {
      final var extension = new PreviewRendererExtension<>(configuration(Map.of("name", "JSON Tree", "file-extensions", "json")));
      assertEquals("JSON Tree", extension.name);
      assertTrue(extension.supports(new ContentSourceSnapshot(Path.of("grammar.tmLanguage.json"), "{}", 0, List.of())));
      assertFalse(extension.supports(new ContentSourceSnapshot(Path.of("sample.vb"), "", 0, List.of())));
   }

   @Test
   void retainsCompatibilityWithUnnamedContributions() throws CoreException {
      for (final var attributes : List.of(Map.<String, String>of(), Map.of("name", ""), Map.of("name", "  "))) {
         final var extension = new PreviewRendererExtension<>(configuration(attributes));
         assertEquals("Object", extension.name);
      }
   }
}
