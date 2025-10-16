/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.util;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.ISharedDocumentAdapter;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.SharedDocumentAdapter;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.SharedDocumentAdapterWrapper;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.texteditor.IDocumentProvider;

import de.sebthom.eclipse.previewer.Plugin;
import de.sebthom.eclipse.previewer.api.ContentSource;

/**
 * Utilities for working with Eclipse Compare editors and their document content.
 *
 * <p>
 * Provides helpers to locate the active Compare editor, connect to a shared
 * document for one side (left/right), and resolve that side into a {@link ContentSource}.
 * </p>
 *
 * @author Sebastian Thomschke
 */
public final class CompareEditorUtils {

   /**
    * Side of a Compare editor to reference.
    */
   public enum CompareEditorSide {
      LEFT,
      RIGHT
   }

   /**
    * Holds a CompareEditor and its associated CompareEditorInput.
    */
   public record CompareEditorRef(@NonNull IEditorPart editor, @NonNull CompareEditorInput input) {
   }

   /**
    * Connected shared-document context for a Compare side.
    *
    * <p>
    * Disconnects automatically on {@link #close()}.
    * </p>
    */
   public record SharedDocContext(@NonNull ISharedDocumentAdapter adapter, @NonNull IDocumentProvider provider, @NonNull IEditorInput key,
         @NonNull IDocument doc, @NonNull String virtualPathName) implements AutoCloseable {

      @Override
      public void close() {
         adapter.disconnect(provider, key);
      }
   }

   public static final String COMPARE_EDITOR_ID = "org.eclipse.compare.CompareEditor";

   /**
    * Connects to the shared document of the given side of a Compare input.
    *
    * @param input the Compare editor input
    * @param preferredSide which side (LEFT/RIGHT) to reference
    * @return a connected {@link SharedDocContext}, or {@code null} if not available
    */
   public static @Nullable SharedDocContext connectSharedDocument(final CompareEditorInput input, final CompareEditorSide preferredSide) {
      final ITypedElement elem = getCompareElement(input, preferredSide);
      if (elem == null)
         return null;
      final ISharedDocumentAdapter sda = SharedDocumentAdapterWrapper.getAdapter(elem);
      if (sda == null)
         return null;
      final IEditorInput key = sda.getDocumentKey(elem);
      if (key == null)
         return null;
      final IDocumentProvider prov = SharedDocumentAdapter.getDocumentProvider(key);
      if (prov == null)
         return null;
      try {
         sda.connect(prov, key);
      } catch (final Exception ex) {
         return null;
      }
      final IDocument doc = prov.getDocument(key);
      if (doc == null) {
         sda.disconnect(prov, key);
         return null;
      }
      return new SharedDocContext(sda, prov, key, doc, virtualPathName(elem));
   }

   /**
    * Returns the active Compare editor and input if the given part reference is a Compare editor.
    *
    * @return a {@link CompareEditorRef} wrapper, or {@code null} if not a Compare editor
    */
   public static @Nullable CompareEditorRef getCompareEditor(final IEditorReference editorRef) {
      if (!COMPARE_EDITOR_ID.equals(editorRef.getId()))
         return null;

      final IEditorPart editor = editorRef.getEditor(false);
      if (editor != null && editor.getEditorInput() instanceof final CompareEditorInput compareInput)
         return new CompareEditorRef(editor, compareInput);
      return null;
   }

   /**
    * Selects the typed element for the requested side of a Compare input (falling back to the other side if null).
    *
    * @param input the Compare editor input
    * @param preferredSide which side (LEFT/RIGHT) to reference
    * @return the selected {@link ITypedElement}, or {@code null} if unavailable
    */
   private static @Nullable ITypedElement getCompareElement(final CompareEditorInput input, final CompareEditorSide preferredSide) {
      final Object result = input.getCompareResult();
      if (!(result instanceof final ICompareInput ci))
         return null;

      return switch (preferredSide) {
         case LEFT -> {
            final var elem = ci.getLeft();
            yield elem == null ? ci.getRight() : elem;
         }
         case RIGHT -> {
            final var elem = ci.getRight();
            yield elem == null ? ci.getLeft() : elem;
         }
      };
   }

   /**
    * Resolves the content of the requested side of a Compare input to a {@link ContentSource}.
    *
    * <p>
    * If the side adapts to an {@link IFile}, the file-backed source is returned;
    * otherwise streamed content is read (respecting {@link IEncodedStreamContentAccessor} if present).
    * </p>
    *
    * @param input the Compare editor input
    * @param preferredSide which side (LEFT/RIGHT) to reference
    * @return a {@link ContentSource}, or {@code null} if the side cannot be resolved
    */
   public static @Nullable ContentSource resolve(final CompareEditorInput input, final CompareEditorSide preferredSide) {
      final var elem = getCompareElement(input, preferredSide);
      if (elem == null) {
         Plugin.log().warn("No element in CompareEditor.");
         return null;
      }

      final IFile file = Adapters.adapt(elem, IFile.class);
      if (file != null)
         return ContentSources.of(file);

      if (elem instanceof final IStreamContentAccessor sca) {
         try (InputStream is = sca.getContents()) {
            if (is != null) {
               @SuppressWarnings("null")
               final Charset cs = elem instanceof final IEncodedStreamContentAccessor esa //
                     ? Charset.forName(Objects.toString(esa.getCharset(), StandardCharsets.UTF_8.name()))
                     : StandardCharsets.UTF_8;
               return ContentSources.of(virtualPathName(elem), IOUtils.toString(is, cs));
            }
         } catch (final Exception ex) {
            Plugin.log().error(ex);
         }
      }

      Plugin.log().warn("Unsupported element " + elem + " in CompareEditor.");
      return null;
   }

   /**
    * Builds a stable virtual path for a non-file compare element.
    *
    * @param elem the compare element
    * @return a virtual path like {@code /virtual/compare/<name>}
    */
   private static String virtualPathName(final ITypedElement elem) {
      final String name = elem.getName();
      return "/virtual/compare/" + (name == null ? "unknown" : name);
   }

   private CompareEditorUtils() {
   }
}
