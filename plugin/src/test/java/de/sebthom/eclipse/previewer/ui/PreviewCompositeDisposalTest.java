/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.PreviewRenderer;

/**
 * Closing a Preview view or editor disposes an ancestor of the host, which SWT releases without calling the host's
 * {@code dispose()} override. Renderers must still be disposed exactly once, while their controls are alive.
 * Enable explicitly with previewer.tests.browser because ordinary builds may have no graphical session.
 *
 * @author Sebastian Thomschke
 */
@EnabledIfSystemProperty(named = "previewer.tests.browser", matches = "default|ie|edge")
class PreviewCompositeDisposalTest {

   /** Records each disposal and whether the renderer's own control was still usable at that time. */
   static final class CountingRenderer implements PreviewRenderer {
      int disposals;
      boolean controlAliveOnDispose;
      private @Nullable Label control;

      @Override
      public void init(final Composite parent) {
         control = new Label(parent, SWT.NONE);
      }

      @Override
      public boolean render(final ContentSource source, final boolean forceCacheUpdate) {
         return false;
      }

      @Override
      public float getZoom() {
         return 1;
      }

      @Override
      public void setZoom(final float level) {
      }

      @Override
      public void dispose() {
         disposals++;
         final var label = control;
         controlAliveOnDispose = label != null && !label.isDisposed();
      }
   }

   private static IConfigurationElement configuration(final PreviewRenderer renderer) {
      return (IConfigurationElement) Proxy.newProxyInstance(IConfigurationElement.class.getClassLoader(), new Class<?>[] {
         IConfigurationElement.class}, (proxy, method, args) -> switch (method.getName()) {
            case "createExecutableExtension" -> renderer;
            case "getName" -> "previewRenderer";
            case "getAttribute" -> null;
            case "getChildren" -> new IConfigurationElement[0];
            default -> throw new AssertionError("Unexpected configuration call: " + method);
         });
   }

   private @Nullable Display display;
   private Shell shell = lateNonNull();
   private final CountingRenderer renderer = new CountingRenderer();

   @BeforeEach
   void createShell() {
      final var newDisplay = new Display();
      display = newDisplay;
      shell = new Shell(newDisplay);
   }

   @AfterEach
   void disposeDisplay() {
      final var currentDisplay = display;
      if (currentDisplay == null)
         return;
      // Only one Display may exist per thread, so a failed test must not leave it alive for the following SWT tests.
      try {
         if (!shell.isDisposed()) {
            shell.dispose();
         }
      } finally {
         currentDisplay.dispose();
      }
   }

   private PreviewComposite createHost() {
      return new PreviewComposite(shell, SWT.NONE, false, new IConfigurationElement[] {configuration(renderer)});
   }

   @Test
   void disposesRenderersWhenAnAncestorIsDisposed() {
      createHost();

      shell.dispose();

      assertEquals(1, renderer.disposals);
      assertTrue(renderer.controlAliveOnDispose);
   }

   @Test
   void disposesRenderersOnceWhenTheHostIsDisposedBeforeItsAncestor() {
      final var host = createHost();

      host.dispose();
      shell.dispose();

      assertEquals(1, renderer.disposals);
      assertTrue(renderer.controlAliveOnDispose);
   }
}
