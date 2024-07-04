/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui;

import static net.sf.jstuff.core.validation.NullAnalysisHelper.lateNonNull;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/**
 * @author Sebastian Thomschke
 */
public final class PreviewView extends ViewPart {

   public static final String ID = PreviewView.class.getName();

   private PreviewComposite content = lateNonNull();

   @Override
   public void createPartControl(final Composite parent) {
      content = new PreviewComposite(parent, this);
   }

   @Override
   public void dispose() {
      content.dispose();
      super.dispose();
   }

   public void forceRefresh() {
      content.forceRefresh();
   }

   public void openEditor() {
      content.openEditor();
   }

   @Override
   public void setFocus() {
      content.setFocus();
   }

   public float getZoom() {
      return content.getZoom();
   }

   public void setZoom(final float zoom) {
      content.setZoom(zoom);
   }
}
