package com.fpetrola.cerco.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.exemptions.Approval;
import com.fpetrola.cerco.exemptions.Digest;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.model.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {
  private static final String SWITCHY = "package p; class A { int f(int x) { switch (x) { default: return 1; } } }";
  private static final String CLEAN = "package p; class B { int f(int x) { return x; } }";
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);

  private Path write(Path root, String name, String code) throws IOException {
    Path file = root.resolve("src/main/java/p").resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, code);
    return file;
  }

  private Scope scope(Path root, boolean full) {
    return new Scope("test", new Sources(root, Settings.DEFAULTS.generatedGlobs())
        .under(List.of(root.resolve("src/main/java"))), full);
  }

  @Test
  void freezesThenCatchesOnlyWhatIsNewAndShrinksWhatWasFixed(@TempDir Path root) throws IOException {
    write(root, "A.java", SWITCHY);
    Path b = write(root, "B.java", CLEAN);
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    assertEquals(1, engine.freeze(scope(root, true).files()));
    assertTrue(engine.store().exists());

    Outcome same = engine.run(scope(root, true));
    assertTrue(same.green(), same.toString());
    assertEquals(1, same.frozen());

    Files.writeString(b, SWITCHY.replace("class A", "class B"));
    Outcome worse = engine.run(scope(root, true));
    assertFalse(worse.green());
    assertEquals(1, worse.fresh().size());
    assertEquals("no-switch", worse.fresh().get(0).rule());
    assertEquals("src/main/java/p/B.java", worse.fresh().get(0).file());

    Files.writeString(b, CLEAN);
    write(root, "A.java", CLEAN.replace("class B", "class A"));
    Outcome better = engine.run(scope(root, true));
    assertTrue(better.green());
    assertEquals(1, better.solved());
    assertEquals(0, engine.store().read("no-switch").total());
  }

  @Test
  void aPartialScopeLeavesOtherFilesEntriesAlone(@TempDir Path root) throws IOException {
    Path a = write(root, "A.java", SWITCHY);
    write(root, "B.java", SWITCHY.replace("class A", "class B"));
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Outcome partial = engine.run(new Scope("quick", List.of(new SourceFile(root, a)), false));
    assertTrue(partial.green());
    assertEquals(2, engine.store().read("no-switch").total());
  }

  @Test
  void exemptionsApplyOnlyWhenSigned(@TempDir Path root) throws IOException {
    write(root, "A.java", CLEAN.replace("class B", "class A"));
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    write(root, "A.java", SWITCHY);
    Path yaml = root.resolve(".cerco/exemptions.yaml");
    Files.writeString(yaml, """
        exemptions:
          - rule: no-switch
            target: src/main/java/p/**
            reason: a reason that is certainly long enough to count
            category: migration
            until: 2027-01-01
        """);
    Outcome unsigned = engine.run(scope(root, true));
    assertFalse(unsigned.green());
    assertEquals(1, unsigned.fresh().size());
    assertTrue(unsigned.problems().get(0).contains("not approved"));

    String digest = new Digest(Files.readAllBytes(yaml)).hex();
    Approval.of(Optional.of(digest), engine.configDigest(), true, "fernando", LocalDate.of(2026, 11, 9))
        .write(engine.approvalFile());
    Outcome signed = engine.run(scope(root, true));
    assertTrue(signed.green(), signed.toString());
    assertEquals(1, signed.exempted().size());
    assertTrue(signed.notes().get(0).contains("expires in"));
  }

  @Test
  void aNonDefaultConfigurationNeedsASignatureToo(@TempDir Path root) throws IOException {
    write(root, "A.java", CLEAN.replace("class B", "class A"));
    Settings loosened = new Settings(400, 40, Settings.DEFAULTS.utilitySuffixes(), Settings.DEFAULTS.factoryPrefixes(),
        Settings.DEFAULTS.generatedGlobs(), 10, false, List.of("no-switch"));
    Engine engine = new Engine(loosened, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Outcome outcome = engine.run(scope(root, true));
    assertFalse(outcome.green());
    assertTrue(outcome.problems().get(0).contains("configuration"));
  }
}
