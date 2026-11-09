package com.fpetrola.cerco.exemptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExemptingTest {
  private final Context context = new Context(LocalDate.of(2026, 11, 9), "1.0", 0);
  private final Finding inOpcodes = new Finding("no-switch", "pipeline/zx-opcodes/src/main/java/Z80.java", 10,
      "k1", "d");
  private final Finding elsewhere = new Finding("no-switch", "oop/zx-oop/src/main/java/Foo.java", 10, "k2", "d");
  private final Finding otherRule = new Finding("no-instanceof", "pipeline/zx-opcodes/src/main/java/Z80.java", 11,
      "k3", "d");

  private Exemption exemption(Limit limit) {
    return new Exemption(1, "no-switch", "pipeline/zx-opcodes/**", "the decoder is the parsing boundary", "decision",
        limit);
  }

  @Test
  void coversByRuleAndPathAndLeavesTheRest() {
    Applied applied = new Exempting(List.of(exemption(new Limit.Budget(5))), context)
        .apply(List.of(inOpcodes, elsewhere, otherRule), true);
    assertEquals(List.of(elsewhere, otherRule), applied.remaining());
    assertEquals(List.of(inOpcodes), applied.exempted());
    assertEquals(List.of(), applied.problems());
    assertTrue(applied.notes().get(0).contains("covers 1, budget 1 of 5"));
  }

  @Test
  void expiredExemptionsAreProblems() {
    Applied applied = new Exempting(List.of(exemption(new Limit.Until(LocalDate.of(2026, 1, 1)))), context)
        .apply(List.of(inOpcodes), true);
    assertEquals(1, applied.problems().size());
    assertTrue(applied.problems().get(0).contains("expired on 2026-01-01"));
  }

  @Test
  void anExemptionCoveringNothingIsStaleOnlyInAFullRun() {
    Exempting exempting = new Exempting(List.of(exemption(new Limit.Budget(5))), context);
    assertEquals(1, exempting.apply(List.of(elsewhere), true).problems().size());
    assertEquals(0, exempting.apply(List.of(elsewhere), false).problems().size());
  }
}
