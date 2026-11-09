package com.fpetrola.cerco.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineStoreTest {

  @Test
  void roundTripsAndRemovesEmptyFiles(@TempDir Path dir) {
    BaselineStore store = new BaselineStore(dir.resolve("baseline"));
    assertFalse(store.exists());
    store.markFrozen(LocalDate.of(2026, 11, 9));
    assertTrue(store.exists());
    assertEquals(Optional.of("2026-11-09"), store.frozenOn());
    store.write("rule", Multiset.of(List.of("a|k", "a|k", "b|k with spaces")));
    assertEquals(2, store.read("rule").count("a|k"));
    assertEquals(3, store.read("rule").total());
    store.writeCeilings("sizes", Map.of("a|file", 500));
    assertEquals(Map.of("a|file", 500), store.readCeilings("sizes"));
    store.write("rule", new Multiset());
    assertFalse(Files.exists(dir.resolve("baseline/rule.txt")));
    assertEquals(0, store.read("rule").total());
  }
}
