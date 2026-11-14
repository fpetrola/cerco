package com.fpetrola.cerco.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.surface.Proposal;
import com.fpetrola.cerco.surface.Seal;
import java.time.LocalDate;
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
  void aRunOverAFewFilesNeverShrinksTheSeal(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    Path other = write(root, "src/main/java/p/Other.java", "package p; public interface Other { void go(); }");
    engine.run(whole(root));
    engine.run(only(root, other));
    assertTrue(seal(root, "_root").contains("p.Shown#show(String): void"));
    assertTrue(Files.readString(root.resolve("surface/pending/proposal.md")).contains("p.Other#go(): void"));
    assertEquals(List.of(), new Seal(root.resolve(".cerco/surface")).dropped());
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

  private Path grown(Path root) throws IOException {
    return write(root, "src/main/java/p/Shown.java",
        "package p; public interface Shown { void show(String text); void hide(); }");
  }

  private void complete(Path root) throws IOException {
    write(root, "src/test/java/p/ShownContract.java", "package p; abstract class ShownContract { void hidden(Shown shown) { shown.hide(); } }");
    Path proposal = root.resolve("surface/pending/proposal.md");
    String text = Files.readString(proposal);
    Files.writeString(proposal, text
        .replaceFirst("(## Need\\n\\n)<!--.*?-->", "$1" + "A view has to take its text away again when the data it "
            + "shows is gone, and nothing sealed can do that: show only puts text in, so the empty view would keep the "
            + "last value it was given.")
        .replaceFirst("(## Considered\\n\\n)<!--.*?-->", "$1p.Shown#show with an empty text: it reads as data.")
        .replaceFirst("(## Removes\\n\\n)<!--.*?-->", "$1none")
        .replaceFirst("(## Contract\\n\\n)<!--.*?-->", "$1src/test/java/p/ShownContract.java"));
  }

  @Test
  void anIncompleteProposalIsTheAgentsRed(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    grown(root);
    Outcome run = engine.run(whole(root));
    assertEquals(1, unsigned(run).size());
    assertTrue(Files.readString(root.resolve("surface/pending/proposal.md")).contains("`p.Shown#hide(): void`"));
    assertTrue(run.notes().stream().anyMatch(note -> note.contains("Need")), run.notes().toString());
    assertTrue(run.awaitingSignature().isEmpty());
  }

  @Test
  void aCompleteProposalWaitsForAPersonAndTheirSignatureGrowsTheSeal(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    grown(root);
    engine.run(whole(root));
    complete(root);
    Outcome run = engine.run(whole(root));
    assertEquals(List.of(), unsigned(run));
    assertEquals(1, run.awaitingSignature().size());
    assertTrue(run.problems().stream().anyMatch(problem -> problem.contains("is complete")), run.problems().toString());
    Seal seal = new Seal(root.resolve(".cerco/surface"));
    new Proposal(root, seal).sign(run.awaitingSignature(), "a person", LocalDate.of(2026, 11, 13));
    assertTrue(seal(root, "_root").contains("p.Shown#hide(): void"));
    assertFalse(Files.exists(root.resolve("surface/pending/proposal.md")));
    assertTrue(Files.readString(root.resolve("surface/0001.md")).contains("signed by a person on 2026-11-13"));
    Outcome after = engine.run(whole(root));
    assertEquals(List.of(), unsigned(after));
    assertTrue(after.awaitingSignature().isEmpty());
  }

  @Test
  void whatLeftTheSealIsListedUntilTheNextSignature(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    write(root, "src/main/java/p/Shown.java", "package p; public interface Shown { }");
    engine.run(whole(root));
    Seal seal = new Seal(root.resolve(".cerco/surface"));
    assertEquals(List.of(": p.Shown#show(String): void"), seal.dropped());
    seal.grow("", List.of());
    assertEquals(List.of(), seal.dropped());
  }

  @Test
  void aConsideredMemberThatIsNotSealedDoesNotCount(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    grown(root);
    engine.run(whole(root));
    complete(root);
    Path proposal = root.resolve("surface/pending/proposal.md");
    Files.writeString(proposal, Files.readString(proposal).replace("p.Shown#show", "p.Shown#paint"));
    assertTrue(engine.run(whole(root)).notes().stream().anyMatch(note -> note.contains("Considered")));
  }

  @Test
  void aFirstDesignHasNothingSealedToConsider(@TempDir Path root) throws IOException {
    write(root, ".cerco/surface/_root.txt", "");
    Engine engine = new Engine(sealing, root, "1.0", clock);
    write(root, "m2/src/main/java/q/Other.java", "package q; public interface Other { void go(); }");
    engine.freeze(whole(root));
    engine.run(whole(root));
    write(root, "src/test/java/p/OtherContract.java", "package p; abstract class OtherContract { void went(q.Other other) { other.go(); } }");
    Path proposal = root.resolve("surface/pending/proposal.md");
    String filled = Files.readString(proposal)
        .replaceFirst("(## Need\\n\\n)<!--.*?-->", "$1" + "x".repeat(160))
        .replaceFirst("(## Removes\\n\\n)<!--.*?-->", "$1none")
        .replaceFirst("(## Contract\\n\\n)<!--.*?-->", "$1src/test/java/p/OtherContract.java");
    Files.writeString(proposal, filled.replaceFirst("(## Considered\\n\\n)<!--.*?-->", "$1none"));
    assertTrue(engine.run(whole(root)).notes().stream().anyMatch(note -> note.contains("Considered")));
    Files.writeString(proposal, filled.replaceFirst("(## Considered\\n\\n)<!--.*?-->",
        "$1none: m2 is new, its first design has nothing sealed to weigh"));
    Outcome run = engine.run(whole(root));
    assertEquals(2, run.awaitingSignature().size(), run.notes().toString());
  }

  @Test
  void aProjectWithoutCodeIsSealedEmptySoItsFirstDesignIsProposed(@TempDir Path root) throws IOException {
    Engine engine = new Engine(sealing, root, "1.0", clock);
    engine.freeze(whole(root));
    engine.run(whole(root));
    assertEquals(List.of(), seal(root, "_root"));
    grown(root);
    assertEquals(3, unsigned(engine.run(whole(root))).size());
  }

  @Test
  void aSignedMemberThatMovesToAnotherModuleNeedsNoNewContract(@TempDir Path root) throws IOException {
    Engine engine = adopted(root);
    Files.delete(root.resolve("src/main/java/p/Shown.java"));
    write(root, "m2/src/main/java/p/Shown.java", SHOWN);
    engine.run(whole(root));
    complete(root);
    write(root, "src/test/java/p/ShownContract.java", "package p; abstract class ShownContract { }");
    Outcome run = engine.run(whole(root));
    assertEquals(2, run.awaitingSignature().size(), run.notes().toString());
  }
}
