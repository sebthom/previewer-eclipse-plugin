/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.localization;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;

import de.sebthom.eclipse.commons.localization.MessagesInitializer;

/**
 * @author Sebastian Thomschke
 */
public final class Messages {

   private static final String BUNDLE_NAME = Messages.class.getPackageName() + ".messages";

   // Keys with default values directly assigned in this class are only used by Java classes.
   // Keys without default values are loaded from messages.properties, because they are also referenced in plugin.xml

   // CHECKSTYLE:IGNORE .* FOR NEXT 100 LINES

   public static String PluginName = lateNonNull();

   static {
      MessagesInitializer.initializeMessages(BUNDLE_NAME, Messages.class);
   }

   private Messages() {
   }
}
