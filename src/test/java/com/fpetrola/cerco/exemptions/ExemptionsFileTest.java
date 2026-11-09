package com.fpetrola.cerco.exemptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExemptionsFileTest {
  private final Set<String> rules = Set.of("no-switch", "no-instanceof");

  private Loaded load(Path dir, String yaml, int cap) throws IOException {
    Path file = dir.resolve("exemptions.yaml");
    Files.writeString(file, yaml);
    return new ExemptionsFile(file, rules, cap).load();
  }

  @Test
  void readsAWellFormedFile(@TempDir Path dir) throws IOException {
    Loaded loaded = load(dir, """
        exemptions:
          - rule: no-switch
            target: pipeline/zx-opcodes/**
            reason: the Z80 decoder is the parsing boundary and a switch over opcodes is the table itself
            category: decision
            until: 2027-03-31
          - rule: no-instanceof
            target: oop/zx-transform/**
            reason: JavaParser nodes are dispatched by type until the visitors land, tracked in T-77
            category: migration
            budget: 12
        """, 10);
    assertEquals(List.of(), loaded.problems());
    assertEquals(2, loaded.exemptions().size());
    assertEquals(new Limit.Until(LocalDate.of(2027, 3, 31)), loaded.exemptions().get(0).limit());
    assertEquals(new Limit.Budget(12), loaded.exemptions().get(1).limit());
    assertTrue(loaded.digest().isPresent());
  }

  @Test
  void namesEveryProblem(@TempDir Path dir) throws IOException {
    Loaded loaded = load(dir, """
        exemptions:
          - rule: no-such-rule
            target: ""
            reason: short
            category: whim
          - rule: no-switch
            target: a/**
            reason: a reason that is certainly long enough to count
            category: decision
            until: 2027-01-01
            budget: 3
        """, 10);
    assertEquals(0, loaded.exemptions().size());
    assertEquals(2, loaded.problems().size());
    assertTrue(loaded.problems().get(0).contains("rule 'no-such-rule'"));
    assertTrue(loaded.problems().get(0).contains("target is empty"));
    assertTrue(loaded.problems().get(0).contains("at least 20 characters"));
    assertTrue(loaded.problems().get(0).contains("category"));
    assertTrue(loaded.problems().get(1).contains("exactly one limit"));
  }

  @Test
  void enforcesTheCap(@TempDir Path dir) throws IOException {
    StringBuilder yaml = new StringBuilder("exemptions:\n");
    for (int i = 0; i < 3; i++) {
      yaml.append("  - rule: no-switch\n    target: a").append(i).append("/**\n")
          .append("    reason: a reason that is certainly long enough to count\n    category: decision\n")
          .append("    budget: 1\n");
    }
    Loaded loaded = load(dir, yaml.toString(), 2);
    assertEquals(1, loaded.problems().size());
    assertTrue(loaded.problems().get(0).contains("cap is 2"));
  }

  @Test
  void unreadableYamlIsAProblemNotACrash(@TempDir Path dir) throws IOException {
    Loaded loaded = load(dir, "exemptions: [unclosed", 10);
    assertEquals(1, loaded.problems().size());
    assertTrue(loaded.problems().get(0).contains("not readable"));
  }

  @Test
  void aMissingFileMeansNoExemptions(@TempDir Path dir) {
    Loaded loaded = new ExemptionsFile(dir.resolve("none.yaml"), rules, 10).load();
    assertEquals(Loaded.none(), loaded);
  }
}
