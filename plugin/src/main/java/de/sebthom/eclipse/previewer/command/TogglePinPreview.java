/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.command;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.commons.command.ToggleCommand;
import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.ui.PreviewView;

/**
 * Toggle pinning the Preview to the current editor (disables auto-switching on editor activation changes).
 *
 * When pinned, the Preview stays attached to the current editor and does not switch when you activate a different editor.
 * Live Preview still applies to the pinned editor if enabled.
 *
 * @author Sebatian Thomschke
 */
public class TogglePinPreview extends ToggleCommand {

   public static final String COMMAND_ID = TogglePinPreview.class.getName();

   public static boolean isPinned() {
      return isEnabled(COMMAND_ID);
   }

   @Override
   public @Nullable Object execute(final ExecutionEvent event) throws ExecutionException {
      super.execute(event);

      final PreviewView previewView = UI.findView(PreviewView.ID);
      if (previewView != null) {
         if (isPinned()) {
            // Pinned: keep current link; optionally focus the linked editor
            previewView.openEditor();
         } else {
            // Unpinned: immediately follow the currently active editor
            previewView.linkToActiveEditor();
         }
      }
      return null;
   }
}
