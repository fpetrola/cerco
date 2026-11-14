package com.fpetrola.cerco.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SealFileTest {

  @Test
  void signaturesComeBackAsTheyWereWrittenGroupedByType(@TempDir Path dir) throws IOException {
    Set<String> signatures = new TreeSet<>(List.of("p.Box class", "p.Box#<init>(int)",
        "<T extends java.util.List<String>> p.Box#held(java.util.function.Supplier<T>): T", "p.Shown interface",
        "p.Shown#show(String): void", "p.Outer.Inner#say(\"x\"): void", "q.Only#MAX: int"));
    SealFile file = new SealFile(dir.resolve("m.yaml"));
    file.write("m", signatures);
    assertEquals(signatures, file.signatures());
    String text = Files.readString(dir.resolve("m.yaml"));
    assertTrue(text.contains("  \"p.Box\":\n    kind: class\n    members:\n"), text);
  }

  @Test
  void anEmptySealIsStillASeal(@TempDir Path dir) {
    SealFile file = new SealFile(dir.resolve("_root.yaml"));
    file.write("", Set.of());
    assertTrue(Files.isRegularFile(dir.resolve("_root.yaml")));
    assertEquals(Set.of(), file.signatures());
  }

  @Test
  void aSealOfAnEarlierVersionIsReadAndBecomesYamlOnTheNextWrite(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("m.txt"), "p.Shown interface\np.Shown#show(String): void\n");
    Seal seal = new Seal(dir);
    assertTrue(seal.covers("m"));
    assertEquals(List.of("p.Shown interface", "p.Shown#show(String): void"), seal.signed("m"));
    seal.grow("m", List.of("p.Shown#hide(): void"));
    assertFalse(Files.exists(dir.resolve("m.txt")));
    assertEquals(3, new SealFile(dir.resolve("m.yaml")).signatures().size());
  }
}
