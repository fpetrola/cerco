package com.fpetrola.cerco.maven;

import java.nio.file.Files;
import java.nio.file.Path;

/** Where .cerco lives: the nearest ancestor that already has one, else the directory the build was started from. */
public final class RootLocator {

  public Path locate(Path basedir, Path executionRoot) {
    Path candidate = basedir;
    while (candidate != null) {
      if (Files.isDirectory(candidate.resolve(".cerco"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    return executionRoot;
  }
}
