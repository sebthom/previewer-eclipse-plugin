/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.plantuml;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.asNonNull;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageRegistry;
import org.osgi.framework.BundleContext;

import de.sebthom.eclipse.commons.AbstractEclipsePlugin;
import de.sebthom.eclipse.commons.BundleResources;
import de.sebthom.eclipse.commons.logging.PluginLogger;
import de.sebthom.eclipse.commons.logging.StatusFactory;
import net.sf.jstuff.core.reflection.Fields;

/**
 * @author Sebastian Thomschke
 */
public class Plugin extends AbstractEclipsePlugin {

   /**
    * during runtime you can get ID with getBundle().getSymbolicName()
    */
   public static final String PLUGIN_ID = asNonNull(Plugin.class.getPackage()).getName().replace('_', '-');

   private static @Nullable Plugin instance;

   /**
    * @return the shared instance
    */
   public static Plugin get() {
      return asNonNull(instance, "Default plugin instance is still null.");
   }

   public static boolean isInitialized() {
      return instance != null;
   }

   public static PluginLogger log() {
      return get().getLogger();
   }

   public static BundleResources resources() {
      return get().getBundleResources();
   }

   public static StatusFactory status() {
      return get().getStatusFactory();
   }

   @Override
   public BundleResources getBundleResources() {
      var bundleResources = this.bundleResources;
      if (bundleResources == null) {
         bundleResources = this.bundleResources = new BundleResources(this, "src/main/resources");
      }
      return bundleResources;
   }

   @Override
   protected void initializeImageRegistry(final ImageRegistry registry) {
      for (final var field : Constants.class.getFields()) {
         if (Fields.isStatic(field) && field.getType() == String.class && field.getName().startsWith("IMAGE_")) {
            final String imagePath = Fields.read(null, field);
            if (imagePath != null) {
               registerImage(registry, imagePath);
            }
         }
      }
   }

   @Override
   public void start(final BundleContext context) throws Exception {
      super.start(context);
      instance = this;
   }

   @Override
   public void stop(final BundleContext context) throws Exception {
      instance = null;
      super.stop(context);
   }
}
