/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.ui.editorsupport;

import java.nio.file.Path;

import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;

import de.sebthom.eclipse.commons.ui.UI;
import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * @author Sebastian Thomschke
 */
public abstract class TrackedEditorContext implements AutoCloseable {

   public final IDocument document;
   public final IEditorPart editor;
   public final Path file;

   protected TrackedEditorContext(final IEditorPart editor, final Path file, final IDocument doc) {
      this.editor = editor;
      this.file = file;
      document = doc;
   }

   public void activateEditor() {
      final IWorkbenchPage page = UI.getActiveWorkbenchPage();
      if (page != null) {
         page.activate(editor);
      }
   }

   @Override
   public void close() {
      // default no-op; subclasses may release resources
   }

   public IEditorPart getEditor() {
      return editor;
   }

   public abstract ContentSource getSource();
}
