package com.fpetrola.cerco.exemptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalTest {

  @Test
  void unsignedExemptionsAndConfigurationAreProblems(@TempDir Path dir) {
    Approval none = Approval.read(dir.resolve("approved"));
    assertEquals(0, none.problems(Optional.empty(), "cfg", true).size());
    assertEquals(1, none.problems(Optional.of("abc"), "cfg", true).size());
    assertEquals(2, none.problems(Optional.of("abc"), "cfg", false).size());
  }

  @Test
  void aSignatureRoundTripsAndMatchesWhatItSigned(@TempDir Path dir) {
    Path file = dir.resolve("approved");
    Approval.of(Optional.of("abc"), Optional.of("cfg"), "fernando", LocalDate.of(2026, 11, 9)).write(file);
    Approval read = Approval.read(file);
    assertEquals("fernando", read.by());
    assertEquals("2026-11-09", read.on());
    assertEquals(0, read.problems(Optional.of("abc"), "cfg", false).size());
    assertEquals(1, read.problems(Optional.of("changed"), "cfg", false).size());
    assertTrue(read.problems(Optional.of("abc"), "other", false).get(0).contains("configuration"));
  }
}
