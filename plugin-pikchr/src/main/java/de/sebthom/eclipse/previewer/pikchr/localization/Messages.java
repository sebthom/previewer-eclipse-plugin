/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.pikchr.localization;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.*;

import de.sebthom.eclipse.commons.localization.MessagesInitializer;

/**
 * Loads the localized strings referenced by the Pikchr bundle metadata.
 *
 * @author Sebastian Thomschke
 */
public final class Messages {

   private static final String BUNDLE_NAME = Messages.class.getPackageName() + ".messages";

   // Keys without default values are loaded from messages.properties because plugin.xml also references them.
   // CHECKSTYLE:IGNORE .* FOR NEXT 10 LINES

   public static String PluginName = lateNonNull();

   static {
      MessagesInitializer.initializeMessages(BUNDLE_NAME, Messages.class);
   }

   private Messages() {
   }
}
