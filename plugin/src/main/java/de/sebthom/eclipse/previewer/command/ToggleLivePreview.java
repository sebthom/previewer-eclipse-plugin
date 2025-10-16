/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.command;

import de.sebthom.eclipse.commons.command.ToggleCommand;

/**
 * @author Sebastian Thomschke
 */
public class ToggleLivePreview extends ToggleCommand {

   public static final String COMMAND_ID = ToggleLivePreview.class.getName();

   public static boolean isLivePreviewEnabled() {
      return isEnabled(COMMAND_ID);
   }

}
