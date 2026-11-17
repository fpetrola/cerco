package com.fpetrola.cerco.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayoutTest {

  private static void write(Path root, String path, String text) throws IOException {
    Files.createDirectories(root.resolve(path).getParent());
    Files.writeString(root.resolve(path), text);
  }

  @Test
  void aProjectKeptTheOldWayMovesUnderCercoWithWhatWasSignedIntact(@TempDir Path root) throws IOException {
    write(root, ".cerco/baseline/frozen", "2026-11-01\n");
    write(root, ".cerco/exemptions.yaml", "exemptions: []\n");
    write(root, ".cerco/approved", "signed\n");
    write(root, ".cerco/surface/_root.yaml", "seal\n");
    write(root, ".cerco/autopsies/one.owed", "owed\n");
    write(root, "autopsies/pending/one.md", "form\n");
    write(root, "surface/pending/proposal.md", "proposal\n");
    Layout layout = new Layout(root);
    layout.migrate();
    assertEquals("2026-11-01\n", Files.readString(layout.baseline().resolve("frozen")));
    assertEquals("exemptions: []\n", Files.readString(layout.exemptions()));
    assertEquals("signed\n", Files.readString(layout.approved()));
    assertEquals("seal\n", Files.readString(layout.seal().resolve("_root.yaml")));
    assertEquals("owed\n", Files.readString(layout.owed().resolve("one.owed")));
    assertEquals("form\n", Files.readString(layout.autopsies().resolve("pending/one.md")));
    assertEquals("proposal\n", Files.readString(layout.proposal()));
    assertFalse(Files.exists(root.resolve("autopsies")));
    assertFalse(Files.exists(root.resolve("surface")));
    layout.migrate();
    assertEquals("signed\n", Files.readString(layout.approved()));
  }
}
