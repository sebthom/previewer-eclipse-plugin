/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors
 * SPDX-License-Identifier: EPL-2.0
 */
package de.sebthom.eclipse.previewer.util;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;

import net.sf.jstuff.core.types.Decorator;

/**
 * @author <a href="https://sebthom.de/">Sebastian Thomschke</a>
 */
public final class SerializablePath implements Decorator<Path>, Path, Serializable {

   private static final long serialVersionUID = 1L;

   public static SerializablePath of(final Path path) {
      return new SerializablePath(path);
   }

   private final String path;
   private transient Path wrapped;

   private SerializablePath(final Path wrapped) {
      this.wrapped = wrapped;
      path = wrapped.toString();
   }

   @Override
   public int compareTo(final Path other) {
      return wrapped.compareTo(other);
   }

   @Override
   public boolean endsWith(final Path other) {
      return wrapped.endsWith(other);
   }

   @Override
   public boolean equals(final @Nullable Object other) {
      if (other instanceof final SerializablePath otherPath)
         return wrapped.equals(otherPath.wrapped);
      else if (other instanceof Path)
         return wrapped.equals(other);

      return false;
   }

   @Override
   public @Nullable Path getFileName() {
      return wrapped.getFileName();
   }

   @Override
   public FileSystem getFileSystem() {
      return wrapped.getFileSystem();
   }

   @Override
   public Path getName(final int index) {
      return wrapped.getName(index);
   }

   @Override
   public int getNameCount() {
      return wrapped.getNameCount();
   }

   @Override
   public @Nullable Path getParent() {
      return wrapped.getParent();
   }

   @Override
   public @Nullable Path getRoot() {
      return wrapped.getRoot();
   }

   @Override
   public Path getWrapped() {
      return wrapped;
   }

   @Override
   public int hashCode() {
      return wrapped.hashCode();
   }

   @Override
   public boolean isAbsolute() {
      return wrapped.isAbsolute();
   }

   @Override
   public boolean isWrappedGettable() {
      return true;
   }

   @Override
   public boolean isWrappedSettable() {
      return false;
   }

   @Override
   public Iterator<Path> iterator() {
      return wrapped.iterator();
   }

   @Override
   public Path normalize() {
      return wrapped.normalize();
   }

   private void readObject(final java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      wrapped = Path.of(path);
   }

   @Override
   public WatchKey register(final WatchService watcher, final Kind<?>[] events, final Modifier... modifiers) throws IOException {
      return wrapped.register(watcher, events, modifiers);
   }

   @Override
   public Path relativize(final Path other) {
      return wrapped.relativize(other);
   }

   @Override
   public Path resolve(final Path other) {
      return wrapped.resolve(other);
   }

   @Override
   public void setWrapped(final Path wrapped) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean startsWith(final Path other) {
      return wrapped.startsWith(other);
   }

   @Override
   public Path subpath(final int beginIndex, final int endIndex) {
      return wrapped.subpath(beginIndex, endIndex);
   }

   @Override
   public Path toAbsolutePath() {
      return wrapped.toAbsolutePath();
   }

   @Override
   public Path toRealPath(final LinkOption... options) throws IOException {
      return wrapped.toRealPath(options);
   }

   @Override
   public String toString() {
      return path;
   }

   @Override
   public URI toUri() {
      return wrapped.toUri();
   }

   private void writeObject(final java.io.ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
   }
}
