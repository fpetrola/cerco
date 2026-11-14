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
    store.markFrozen(LocalDate.of(2026, 11, 9), java.util.List.of("no-switch"));
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

  @Test
  void aFileWithConflictMarkersIsNamed(@TempDir Path dir) throws java.io.IOException {
    BaselineStore store = new BaselineStore(dir);
    Files.writeString(dir.resolve("frozen"), "<<<<<<< Updated upstream\na\n=======\nc\n>>>>>>> Stashed changes\n");
    IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> store.lines("frozen"));
    assertTrue(failure.getMessage().contains("frozen has git conflict markers"), failure.getMessage());
  }

  @Test
  void whatCercoRecordsOfItsRunsKeepsTheCommittedSide(@TempDir Path dir) throws java.io.IOException {
    BaselineStore store = new BaselineStore(dir);
    Files.writeString(dir.resolve("self"),
        "x\ty\n<<<<<<< Updated upstream\na\tb\n=======\nc\td\n>>>>>>> Stashed changes\nz\tw\n");
    assertEquals(List.of("x\ty", "a\tb", "z\tw"), store.lines("self"));
    assertEquals("x\ty\na\tb\nz\tw\n", Files.readString(dir.resolve("self")));
  }
}
