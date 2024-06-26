/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.content.IContentType;

import de.sebthom.eclipse.previewer.api.PreviewRenderer;

/**
 * @author Sebastian Thomschke
 */
public class PreviewRendererRegistration {

   public final Class<PreviewRenderer> implementationClass;
   public final List<IContentType> contentTypes = new ArrayList<>();
   public final List<String> fileExtensions = new ArrayList<>();
   public final List<String> filePathPatterns = new ArrayList<>();

   public PreviewRendererRegistration(final Class<PreviewRenderer> implementationClass) {
      this.implementationClass = implementationClass;
   }
}
