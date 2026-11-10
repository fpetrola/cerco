package com.fpetrola.cerco.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.baseline.Multiset;
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
    assertTrue(better.fresh().isEmpty() && better.problems().isEmpty(), better.toString());
    assertEquals(1, better.solved());
    assertEquals(1, better.owed().size(), "a fix owes an autopsy");
    assertEquals(0, engine.store().read("no-switch").total());
  }

  @Test
  void aFindingGoneWhileItsCodeStayedTheSameIsALooseningOnlyAPersonAccepts(@TempDir Path root) throws IOException {
    Path a = write(root, "A.java", SWITCHY);
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Multiset switches = engine.store().read("no-switch");
    switches.add("src/main/java/p/A.java|p.A.g(int)|switch(y)", 1);
    engine.store().write("no-switch", switches);
    engine.store().write("no-package-cycles", Multiset.of(List.of("src/main/java/p/A.java|p|p -> q -> p")));

    Outcome loosened = engine.run(scope(root, true));
    assertTrue(loosened.fresh().isEmpty() && loosened.owed().isEmpty(), "a rule change owes no autopsy");
    assertEquals(1, loosened.problems().size(), loosened.toString());
    assertTrue(loosened.problems().get(0).startsWith("2 frozen finding(s) left the baseline"));
    assertFalse(engine.run(scope(root, true)).green(), "it stays red after the run that saw it");

    engine.witness().accept();
    assertTrue(engine.run(scope(root, true)).green());
    Files.writeString(a, CLEAN.replace("class B", "class A"));
    Outcome fixed = engine.run(new Scope("quick", List.of(new SourceFile(root, a)), false));
    assertTrue(fixed.problems().isEmpty() && fixed.owed().size() == 1, fixed.toString());
  }

  @Test
  void aRuleGoneFromCercoAndChangedDefaultsWaitForAPerson(@TempDir Path root) throws IOException {
    write(root, "A.java", SWITCHY);
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    engine.store().extendFrozen(List.of("no-goto"));
    engine.store().write("no-goto", Multiset.of(List.of("src/main/java/p/A.java|p.A|goto")));
    Path self = root.resolve(".cerco/baseline/self");
    Files.writeString(self, Files.readString(self).replaceFirst("[0-9a-f]{64}\tdefaults", "0\tdefaults"));

    Outcome gone = engine.run(scope(root, true));
    assertTrue(gone.fresh().isEmpty() && gone.owed().isEmpty(), gone.toString());
    assertEquals(2, gone.problems().size(), gone.toString());
    assertTrue(gone.problems().get(0).contains("[no-goto]"));
    assertTrue(gone.problems().get(1).contains("defaults"));

    engine.witness().accept();
    assertTrue(engine.run(scope(root, true)).green());
    assertFalse(engine.store().frozenRules().contains("no-goto"));
    assertEquals(0, engine.store().read("no-goto").total());
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
        Settings.DEFAULTS.generatedGlobs(), 10, false, List.of("no-switch"), true, List.of());
    Engine engine = new Engine(loosened, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Outcome outcome = engine.run(scope(root, true));
    assertFalse(outcome.green());
    assertTrue(outcome.problems().get(0).contains("configuration"));
  }

  @Test
  void aRuleThatArrivesAfterTheFreezeIsFrozenByTheNextFullRunAndSkippedByPartialOnes(@TempDir Path root)
      throws IOException {
    Path a = write(root, "A.java", "package p; class A { Object f(Object o) { return (String) o; } }");
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Files.writeString(root.resolve(".cerco/baseline/frozen"), "2026-11-01\nno-switch\n");
    Files.deleteIfExists(root.resolve(".cerco/baseline/no-cast.txt"));

    Outcome partial = engine.run(new Scope("quick", List.of(new SourceFile(root, a)), false));
    assertTrue(partial.green(), partial.toString());
    assertTrue(partial.notes().get(0).contains("skipped until a full run"));
    assertTrue(partial.notes().get(0).contains("no-cast"));

    Outcome full = engine.run(scope(root, true));
    assertTrue(full.green(), full.toString());
    assertTrue(full.notes().stream().anyMatch(note -> note.contains("rule no-cast is new to this project")));
    assertEquals(1, engine.store().read("no-cast").total());
    assertTrue(engine.store().frozenRules().contains("no-cast"));
    assertEquals(Optional.of("2026-11-01"), engine.store().frozenOn());

    Files.writeString(a, "package p; class A { Object f(Object o) { return (String) o; }"
        + " Object g(Object o) { return (Integer) o; } }");
    Outcome worse = engine.run(new Scope("quick", List.of(new SourceFile(root, a)), false));
    assertFalse(worse.green());
    assertEquals("no-cast", worse.fresh().get(0).rule());
  }

  @Test
  void aFullRunDropsTheEntriesOfFilesThatAreGoneAndAPartialOneKeepsThem(@TempDir Path root) throws IOException {
    Path a = write(root, "A.java", SWITCHY);
    Path b = write(root, "B.java", SWITCHY.replace("class A", "class B"));
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    assertEquals(2, engine.store().read("no-switch").total());

    Files.delete(b);
    Outcome partial = engine.run(new Scope("quick", List.of(new SourceFile(root, a)), false));
    assertTrue(partial.green());
    assertEquals(0, partial.solved());
    assertEquals(2, engine.store().read("no-switch").total());

    Outcome full = engine.run(scope(root, true));
    assertTrue(full.fresh().isEmpty() && full.problems().isEmpty(), full.toString());
    assertEquals(1, full.solved());
    assertEquals(1, full.owed().size(), "code that is gone owes an autopsy too");
    assertEquals(1, full.frozen());
    assertEquals(1, engine.store().read("no-switch").total());
    assertTrue(engine.store().read("no-switch").keys().iterator().next().startsWith("src/main/java/p/A.java"));
  }

  @Test
  void aSignedExemptionReleasesTheFrozenFindingsItCoversFromTheBaseline(@TempDir Path root) throws IOException {
    write(root, "A.java", SWITCHY);
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root, true).files());
    Path yaml = root.resolve(".cerco/exemptions.yaml");
    Files.writeString(yaml, """
        exemptions:
          - rule: no-switch
            target: src/main/java/p/A.java
            reason: a human decided the switch stays here, and said so
            category: decision
            budget: 1
        """);
    String digest = new Digest(Files.readAllBytes(yaml)).hex();
    Approval.of(Optional.of(digest), engine.configDigest(), true, "fernando", LocalDate.of(2026, 11, 9))
        .write(engine.approvalFile());

    Outcome full = engine.run(scope(root, true));
    assertTrue(full.green(), full.toString());
    assertEquals(1, full.exempted().size());
    assertEquals(0, full.frozen());
    assertEquals(0, engine.store().read("no-switch").total());

    Outcome again = engine.run(scope(root, true));
    assertTrue(again.green(), again.toString());
    assertEquals(1, again.exempted().size());
  }
}
