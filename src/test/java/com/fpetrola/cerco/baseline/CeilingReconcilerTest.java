package com.fpetrola.cerco.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.rules.FileLength;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CeilingReconcilerTest {
  private final CeilingReconciler reconciler = new CeilingReconciler();
  private final FileLength rule = new FileLength(400);

  private Measure measure(String file, int value) {
    return new Measure(file + "|file", file, 1, value, value + " lines");
  }

  @Test
  void ceilingsComeDownNeverUpAndVanishUnderTheDefault() {
    Map<String, Integer> stored = Map.of("f1|file", 500, "f2|file", 450, "gone|file", 600, "other/x|file", 900);
    CeilingReconciler.Outcome outcome = reconciler.reconcile(rule, stored, Set.of("f1", "f2", "f3", "f4", "gone"),
        List.of(measure("f1", 480), measure("f2", 470), measure("f3", 410), measure("f4", 100)));
    assertEquals(2, outcome.fresh().size());
    assertEquals("f2|file", outcome.fresh().get(0).key());
    assertEquals("470 lines (ceiling 450)", outcome.fresh().get(0).detail());
    assertEquals("410 lines (ceiling 400)", outcome.fresh().get(1).detail());
    assertEquals(Map.of("f1|file", 480, "f2|file", 450, "other/x|file", 900), outcome.updated());
    assertEquals(1, outcome.solved());
    assertEquals(2, outcome.frozen());
  }

  @Test
  void freezingKeepsOnlyWhatIsOverTheDefault() {
    assertEquals(Map.of("big|file", 401), reconciler.freeze(rule, List.of(measure("big", 401), measure("ok", 400))));
  }
}
