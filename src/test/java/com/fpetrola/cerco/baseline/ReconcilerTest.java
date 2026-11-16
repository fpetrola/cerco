package com.fpetrola.cerco.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fpetrola.cerco.model.Finding;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReconcilerTest {

  private Finding at(String key, int line) {
    return new Finding("r", new Key(key).file(), line, key, "d");
  }

  @Test
  void countsBeyondTheFrozenOnesAreFreshAndMissingOnesAreSolved() {
    Multiset baseline = new Multiset();
    baseline.add("a/A.java|k1", 2);
    baseline.add("a/A.java|k2", 1);
    baseline.add("b/B.java|k9", 1);
    Reconciliation result = new Reconciler().reconcile(baseline, Set.of("a/A.java"),
        List.of(at("a/A.java|k1", 30), at("a/A.java|k1", 10), at("a/A.java|k1", 20), at("a/A.java|k3", 5)),
        new Multiset(), (frozen, spot) -> false);
    assertEquals(List.of(at("a/A.java|k3", 5), at("a/A.java|k1", 30)), result.fresh());
    assertEquals(1, result.solved());
    assertEquals(2, result.frozen());
    assertEquals(2, result.updated().count("a/A.java|k1"));
    assertEquals(0, result.updated().count("a/A.java|k2"));
    assertEquals(1, result.updated().count("b/B.java|k9"));
  }

  @Test
  void aFrozenFindingWhoseFileMovedStaysFrozenUnderTheNewPathAndIsNotSolved() {
    Multiset baseline = new Multiset();
    Multiset departed = new Multiset();
    departed.add("old/A.java|A.k1", 1);
    departed.add("old/A.java|A.k2", 1);
    Reconciliation result = new Reconciler().reconcile(baseline, Set.of("new/A.java"),
        List.of(at("new/A.java|A.k1", 3), at("new/A.java|A.k3", 4)), departed, (frozen, spot) -> false);
    assertEquals(List.of(at("new/A.java|A.k3", 4)), result.fresh());
    assertEquals(1, result.frozen());
    assertEquals(1, result.updated().count("new/A.java|A.k1"));
    assertEquals(0, result.updated().count("old/A.java|A.k1"));
    assertEquals(List.of("old/A.java|A.k2"), result.solvedAmong(departed));
    assertEquals(1, result.solved());
  }
}
