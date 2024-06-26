/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.command;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.ui.PreviewView;

/**
 * @author Sebatian Thomschke
 */
public class RefreshPreview extends AbstractHandler {

   @Override
   public @Nullable Object execute(final ExecutionEvent event) throws ExecutionException {
      final PreviewView previewView = UI.findView(PreviewView.ID);
      if (previewView != null) {
         previewView.forceRefresh();
      }
      return null;
   }

}
