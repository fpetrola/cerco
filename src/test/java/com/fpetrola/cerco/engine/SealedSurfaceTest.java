package com.fpetrola.cerco.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The sealed surface through the engine: adopted once, grown only by a signature, shrunk freely. */
class SealedSurfaceTest {
  private static final String SHOWN = "package p; public interface Shown { void show(String text); }";
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
  private final Settings sealing = new Settings(400, 40, Settings.DEFAULTS.utilitySuffixes(),
      Settings.DEFAULTS.factoryPrefixes(), Settings.DEFAULTS.generatedGlobs(), 10, false, List.of(), true, List.of(),
      Optional.of("public"));

  private Path write(Path root, String name, String code) throws IOException {
    Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, code);
    return file;
  }

  private Scope whole(Path root) {
    return new Scope("all", new Sources(root, Settings.DEFAULTS.generatedGlobs())
        .under(List.of(root.resolve("src/main/java"), root.resolve("m2/src/main/java"))), true);
  }

  private Scope only(Path root, Path file) {
    return new Scope("edit", new Sources(root, Settings.DEFAULTS.generatedGlobs()).under(List.of(file.getParent()))
        .stream().filter(source -> source.absolute().equals(file)).toList(), false);
  }

  private List<String> unsigned(Outcome outcome) {
    return outcome.fresh().stream().filter(finding -> finding.rule().equals("sealed-surface"))
        .map(Finding::detail).toList();
  }

  private List<String> seal(Path root, String module) throws IOException {
    return Files.readAllLines(root.resolve(".cerco/surface").resolve(module + ".txt"));
  }

  private Engine adopted(Path root) throws IOException {
    write(root, "src/main/java/p/Shown.java", SHOWN);
    Engine engine = new Engine(sealing, root, "1.0", clock);
    engine.freeze(whole(root));
    assertEquals(List.of(), unsigned(engine.run(whole(root))));
    return engine;
  }

  @Test
  void aProjectNeverSealedIsSealedAsItStands(@TempDir Path root) throws IOException {
    adopted(root);
    assertEquals(List.of("p.Shown interface", "p.Shown#show(String): void"), seal(root, "_root"));
  }

  @Test
  void aNewPublicMethodIsRedOnTheEditItself(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    Path shown = write(root, "src/main/java/p/Shown.java",
        SHOWN.replace("}", " void hide(); }").replace("); } void", "); void"));
    Outcome edit = engine.run(only(root, shown));
    assertEquals(List.of("p.Shown#hide(): void is not in the sealed surface of the project"), unsigned(edit));
    assertFalse(edit.green());
  }

  @Test
  void aRenameIsANewMemberAndTheOldOneLeavesTheSealByItself(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    write(root, "src/main/java/p/Shown.java", SHOWN.replace("show(", "display("));
    Outcome run = engine.run(whole(root));
    assertEquals(List.of("p.Shown#display(String): void is not in the sealed surface of the project"),
        unsigned(run));
    assertEquals(List.of("p.Shown interface"), seal(root, "_root"));
  }

  @Test
  void takingAMemberBackShrinksTheSealAndOwesNothing(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    write(root, "src/main/java/p/Shown.java", "package p; public interface Shown { }");
    Outcome run = engine.run(whole(root));
    assertEquals(List.of(), unsigned(run));
    assertTrue(run.owed().isEmpty(), run.owed().toString());
    assertEquals(List.of("p.Shown interface"), seal(root, "_root"));
  }

  @Test
  void aModuleAddedAfterTheSealStartsEmpty(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    write(root, "m2/src/main/java/q/Other.java", "package q; public class Other { }");
    Outcome run = engine.run(whole(root));
    assertEquals(List.of("q.Other class is not in the sealed surface of m2"), unsigned(run));
  }

  @Test
  void withoutTheSettingThereIsNoSeal(@TempDir Path root) throws IOException {
    write(root, "src/main/java/p/Shown.java", SHOWN);
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(whole(root));
    engine.run(whole(root));
    assertFalse(Files.exists(root.resolve(".cerco/surface")));
  }
}
