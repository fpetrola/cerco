package com.fpetrola.cerco.autopsy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.engine.Outcome;
import com.fpetrola.cerco.engine.Scope;
import com.fpetrola.cerco.engine.Sources;
import com.fpetrola.cerco.exemptions.Approval;
import com.fpetrola.cerco.model.Settings;
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

class AutopsiesTest {
  private static final String SWITCHY = "package p;\nclass A {\n  int f(int x) {\n    switch (x) { default: return 1; }\n  }\n}\n";
  private static final String CLEAN = "package p;\nclass A {\n  int f(int x) {\n    return x;\n  }\n}\n";
  private static final String OTHER = "package p;\nclass B {\n  int g(int x) {\n    return x + 1;\n  }\n}\n";
  private static final String FORM_ID = "2026-11-10-no-switch";
  private static final String PENDING = "autopsies/pending/" + FORM_ID + ".md";
  private static final String CAUSE = "The value x stood for a kind of thing that the model never had as a type, so "
      + "every place that needed to behave differently per kind asked the integer what it was instead of asking an "
      + "object to do it.";
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);

  private Engine fixedOnce(Path root, Settings settings) throws IOException {
    write(root, "src/main/java/p/A.java", SWITCHY);
    write(root, "src/main/java/p/B.java", OTHER);
    Engine engine = new Engine(settings, root, "1.0", clock);
    engine.freeze(scope(root).files());
    write(root, "src/main/java/p/A.java", CLEAN);
    return engine;
  }

  private void write(Path root, String name, String text) throws IOException {
    Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private Scope scope(Path root) {
    return new Scope("test", new Sources(root, Settings.DEFAULTS.generatedGlobs())
        .under(List.of(root.resolve("src/main/java"))), true);
  }

  private String filled(String cause, String candidate) {
    return "# Autopsy " + FORM_ID + " (no-switch)\n\n## Symptom\n\n## Cause\n\n" + cause
        + "\n\nmodel: variation-as-data\n\n## Context\n\n"
        + "- src/main/java/p/A.java:3 the method that switched\n- src/main/java/p/B.java:3 where x comes from\n\n"
        + "## Same cause elsewhere\n\n- src/main/java/p/B.java:4 the same integer travels on\n\n"
        + "## Principle\n\nPolymorphism over type tests: an object does what its kind does.\n\n"
        + "## Candidate rule\n\n" + candidate + "\n";
  }

  private String proposing(String rule) {
    return "rule: " + rule + "\nAn int parameter that a switch or an if chain compares against literals is a kind "
        + "without a type; the rule would ask for the type, as in src/main/java/p/A.java:4.";
  }

  @Test
  void aFixOwesAnAutopsyAndStaysRedUntilItsFormIsComplete(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);

    Outcome fixed = engine.run(scope(root));
    assertFalse(fixed.green(), fixed.toString());
    assertTrue(fixed.fresh().isEmpty());
    assertEquals(1, fixed.solved());
    assertEquals(1, fixed.owed().size());
    Owed owed = fixed.owed().get(0);
    assertEquals(PENDING, owed.form());
    assertEquals("no-switch", owed.ruleId());
    String form = Files.readString(root.resolve(PENDING));
    assertTrue(form.contains("src/main/java/p/A.java|"), form);
    assertTrue(Files.exists(root.resolve(".cerco/autopsies/" + FORM_ID + ".owed")));

    write(root, PENDING, filled(CAUSE, proposing("no-type-code")));
    Outcome done = engine.run(scope(root));
    assertTrue(done.green(), done.toString());
    assertTrue(Files.exists(root.resolve("autopsies/" + FORM_ID + ".md")));
    assertFalse(Files.exists(root.resolve(PENDING)));
    assertFalse(Files.exists(root.resolve(".cerco/autopsies/" + FORM_ID + ".owed")));
  }

  @Test
  void theTemplateAsGivenSaysNothing(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    List<String> missing = engine.run(scope(root)).owed().get(0).missing();
    assertEquals(6, missing.size(), missing.toString());
  }

  @Test
  void theCauseNamesTheMissingModelFromTheVocabulary(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    engine.run(scope(root));
    String complete = filled(CAUSE, proposing("no-type-code"));
    write(root, PENDING, complete.replace("model: variation-as-data", ""));
    List<String> missing = engine.run(scope(root)).owed().get(0).missing();
    assertEquals(1, missing.size(), missing.toString());
    assertTrue(missing.get(0).contains("dispatch-from-outside"), missing.get(0));
    write(root, PENDING, complete.replace("variation-as-data", "missing-visitor"));
    missing = engine.run(scope(root)).owed().get(0).missing();
    assertTrue(missing.get(0).contains("`missing-visitor` is none of them"), missing.get(0));

    write(root, PENDING, complete);
    engine.run(scope(root));
    assertEquals(java.util.Map.of("no-switch", java.util.Map.of("variation-as-data", 1L)),
        new Catalog(root.resolve("autopsies")).modelsByRule());
  }

  @Test
  void deletingTheFormBringsItBack(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    engine.run(scope(root));
    Files.delete(root.resolve(PENDING));
    Outcome again = engine.run(scope(root));
    assertEquals(1, again.owed().size());
    assertTrue(Files.exists(root.resolve(PENDING)));
  }

  @Test
  void placesMustExistAndOneMustLieOutsideTheFix(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    engine.run(scope(root));
    String onlyTheFix = filled(CAUSE, proposing("kind-without-type"))
        .replace("src/main/java/p/B.java:3", "src/main/java/p/A.java:2");
    write(root, PENDING, onlyTheFix);
    assertTrue(engine.run(scope(root)).owed().get(0).missing().get(0).startsWith(Form.CONTEXT));
    write(root, PENDING, filled(CAUSE, proposing("kind-without-type")).replace("B.java:3", "B.java:99"));
    assertTrue(engine.run(scope(root)).owed().get(0).missing().get(0).startsWith(Form.CONTEXT));
  }

  @Test
  void aCauseCopiedFromAFiledAutopsyAndAnUnknownSameAsAreRefused(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    write(root, "autopsies/2026-11-01-no-cast.md", filled(CAUSE, proposing("kind-without-type")));
    engine.run(scope(root));
    write(root, PENDING, filled(CAUSE, "same-as: 2026-10-01-nothing"));
    List<String> missing = engine.run(scope(root)).owed().get(0).missing();
    assertEquals(2, missing.size(), missing.toString());

    write(root, PENDING, filled(CAUSE.replace("never had", "did not have"), "same-as: 2026-11-01-no-cast"));
    assertEquals(List.of("kind-without-type"), engine.run(scope(root)).owed().stream().map(Owed::ruleId).toList());
    assertEquals(List.of(new Backlog.Wanted("kind-without-type", List.of("2026-11-01-no-cast", FORM_ID))),
        new Backlog(new Catalog(root.resolve("autopsies"))).wanted());
  }

  @Test
  void aProposedRuleIsOwedUntilCercoHasItOrAPersonDeclinesIt(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    engine.run(scope(root));
    write(root, PENDING, filled(CAUSE, proposing("kind-without-type")));
    Outcome filed = engine.run(scope(root));
    assertFalse(filed.green());
    assertTrue(Files.exists(root.resolve("autopsies/" + FORM_ID + ".md")));
    Owed rule = filed.owed().get(0);
    assertEquals("kind-without-type", rule.ruleId());
    assertEquals("autopsies/" + FORM_ID + ".md", rule.form());
    assertTrue(rule.missing().get(0).contains(FORM_ID), rule.missing().toString());

    Settings declining = new Settings(400, 40, Settings.DEFAULTS.utilitySuffixes(), Settings.DEFAULTS.factoryPrefixes(),
        Settings.DEFAULTS.generatedGlobs(), 10, false, List.of(), true, List.of("kind-without-type"));
    Engine declined = new Engine(declining, root, "1.0", clock);
    assertEquals(1, declined.run(scope(root)).owed().size(), "a decline nobody signed declines nothing");
    Approval.of(Optional.empty(), Optional.of(declined.configDigest()), "fernando", LocalDate.of(2026, 11, 10))
        .write(declined.approvalFile());
    assertTrue(declined.run(scope(root)).green());
  }

  @Test
  void aSecondFixOfTheSameRuleJoinsTheOpenAutopsy(@TempDir Path root) throws IOException {
    write(root, "src/main/java/p/A.java", SWITCHY);
    write(root, "src/main/java/p/B.java", SWITCHY.replace("class A", "class B"));
    Engine engine = new Engine(Settings.DEFAULTS, root, "1.0", clock);
    engine.freeze(scope(root).files());
    write(root, "src/main/java/p/A.java", CLEAN);
    engine.run(scope(root));
    write(root, "src/main/java/p/B.java", OTHER);
    Outcome both = engine.run(scope(root));
    assertEquals(1, both.owed().size(), both.toString());
    assertTrue(Files.readString(root.resolve(PENDING)).contains("src/main/java/p/B.java|"));
    assertTrue(Files.readString(root.resolve(PENDING)).contains("src/main/java/p/A.java|"));
  }

  @Test
  void switchedOffNothingIsOwed(@TempDir Path root) throws IOException {
    Settings off = new Settings(400, 40, Settings.DEFAULTS.utilitySuffixes(), Settings.DEFAULTS.factoryPrefixes(),
        Settings.DEFAULTS.generatedGlobs(), 10, false, List.of(), false, List.of());
    Engine engine = fixedOnce(root, off);
    Outcome outcome = engine.run(scope(root));
    assertTrue(outcome.owed().isEmpty());
    assertFalse(Files.exists(root.resolve("autopsies")));
  }

  private static final String NONE = "none: the shape is a choice of the hardware being emulated, read off a manual "
      + "page, and nothing in the code tells it from a choice of the programmer.";

  @Test
  void aNoneWaitsForAPersonWhoFilesIt(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    engine.run(scope(root));
    write(root, PENDING, filled(CAUSE, NONE));
    Outcome waiting = engine.run(scope(root));
    assertFalse(waiting.green(), waiting.toString());
    assertTrue(waiting.owed().isEmpty() && waiting.fresh().isEmpty(), "the agent owes nothing more: a person does");
    assertTrue(waiting.problems().get(0).contains(PENDING), waiting.problems().toString());
    assertFalse(Files.exists(root.resolve("autopsies/" + FORM_ID + ".md")));

    engine.autopsies().accept(engine.autopsies().settle().awaiting());
    assertTrue(Files.exists(root.resolve("autopsies/" + FORM_ID + ".md")));
    assertTrue(engine.run(scope(root)).green());
  }

  @Test
  void whileANoneWaitsEveryOtherAutopsyProposesARule(@TempDir Path root) throws IOException {
    write(root, "src/main/java/p/A.java", CLEAN);
    write(root, "src/main/java/p/B.java", OTHER);
    Autopsies autopsies = new Autopsies(root);
    String key = "src/main/java/p/A.java|A.f|x";
    autopsies.owe(List.of(new Solved("no-switch", List.of(key)), new Solved("no-cast", List.of(key))),
        LocalDate.of(2026, 11, 10));
    write(root, "autopsies/pending/2026-11-10-no-cast.md", filled(CAUSE, NONE));
    write(root, PENDING, filled(CAUSE.replace("never had", "did not have"), NONE));

    Autopsies.Settled first = autopsies.settle();
    assertEquals(List.of("2026-11-10-no-cast"), first.awaiting());
    assertEquals(List.of(FORM_ID), first.owed().stream().map(Owed::id).toList());
    assertTrue(first.owed().get(0).missing().get(0).contains("waits for a person"), first.owed().toString());

    autopsies.accept(first.awaiting());
    assertEquals(List.of(FORM_ID), autopsies.settle().awaiting());
  }

  @Test
  void sameAsMustJoinARuleAlreadyAskedFor(@TempDir Path root) throws IOException {
    Engine engine = fixedOnce(root, Settings.DEFAULTS);
    write(root, "autopsies/2026-11-01-hardware.md", filled(CAUSE.replace("never had", "had no"), NONE));
    engine.run(scope(root));
    write(root, PENDING, filled(CAUSE, "same-as: 2026-11-01-hardware"));
    List<String> missing = engine.run(scope(root)).owed().get(0).missing();
    assertEquals(1, missing.size(), missing.toString());
    assertTrue(missing.get(0).contains("proposes no rule"), missing.toString());
  }
}
