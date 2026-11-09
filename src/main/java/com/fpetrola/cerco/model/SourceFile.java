package com.fpetrola.cerco.model;

import java.io.File;
import java.nio.file.Path;

/** A Java source file, known by its path relative to the gated root: that name is what the baseline remembers. */
public record SourceFile(Path root, Path absolute, String name) {

  public SourceFile(Path root, Path absolute) {
    this(root, absolute, root.relativize(absolute).toString().replace(File.separatorChar, '/'));
  }
}
