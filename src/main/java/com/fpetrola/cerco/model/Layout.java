package com.fpetrola.cerco.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where cerco keeps its files, all under .cerco: what only the gate and a person may write (the baseline, the
 * exemptions, the signature, the seal, the autopsies owed) in .cerco/sealed, the one place the guard protects; what the
 * agent writes (the autopsies, the surface proposal) beside it.
 */
public final class Layout {
  public static final String HOME = ".cerco";
  public static final String SEALED = HOME + "/sealed";
  private final Path root;

  public Layout(Path root) {
    this.root = root;
  }

  public Path guarded() {
    return root.resolve(SEALED);
  }

  public Path baseline() {
    return guarded().resolve("baseline");
  }

  public Path exemptions() {
    return guarded().resolve("exemptions.yaml");
  }

  public Path approved() {
    return guarded().resolve("approved");
  }

  public Path seal() {
    return guarded().resolve("surface");
  }

  public Path owed() {
    return guarded().resolve("autopsies");
  }

  public Path autopsies() {
    return root.resolve(HOME).resolve("autopsies");
  }

  public Path proposal() {
    return root.resolve(HOME).resolve("surface").resolve("pending").resolve("proposal.md");
  }

  /** A place as messages name it, relative to the root. */
  public String named(Path place) {
    return root.relativize(place).toString().replace('\\', '/');
  }

  /**
   * Moves a project kept the way versions before 0.34 kept it (.cerco/ all sealed, autopsies/ and surface/pending at
   * the root) to this layout, contents untouched, so what was signed stays signed.
   */
  public void migrate() {
    Path home = root.resolve(HOME);
    if (Files.isDirectory(guarded()) || !Files.isDirectory(home)) {
      return;
    }
    try {
      Files.createDirectories(guarded());
      for (String name : List.of("baseline", "exemptions.yaml", "approved", "surface", "autopsies")) {
        move(home.resolve(name), guarded().resolve(name));
      }
      move(root.resolve("autopsies"), autopsies());
      Path surface = root.resolve("surface");
      move(surface.resolve("pending"), proposal().getParent());
      if (Files.isDirectory(surface)) {
        try (var left = Files.list(surface)) {
          if (left.findAny().isEmpty()) {
            Files.delete(surface);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cerco: could not move " + HOME + " to the 0.34 layout", e);
    }
  }

  private void move(Path from, Path to) throws IOException {
    if (Files.exists(from)) {
      Files.createDirectories(to.getParent());
      Files.move(from, to);
    }
  }
}
