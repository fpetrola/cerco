package com.fpetrola.cerco.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaudeSettingsTest {
  private final String existing = """
      {"hooks":{"PostToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"echo verdicts","timeout":10}]}]},
       "permissions":{"deny":["Bash(*--no-verify*)"]}}
      """;
  private final List<HookSpec> hooks = List.of(
      new HookSpec("PreToolUse", Optional.of("Bash"), "java HookMain guard", 20, "HookMain guard"),
      new HookSpec("Stop", Optional.empty(), "java HookMain audit", 900, "HookMain audit"));
  private final List<String> denies = List.of("Edit(.cerco/**)", "Bash(*--no-verify*)");

  @Test
  void keepsWhatWasThereAndAddsWhatWasNot() {
    String merged = new ClaudeSettings().merged(existing, hooks, denies);
    assertTrue(merged.contains("echo verdicts"));
    assertTrue(merged.contains("java HookMain guard"));
    assertTrue(merged.contains("java HookMain audit"));
    assertTrue(merged.contains("Edit(.cerco/**)"));
    assertEquals(1, merged.split("no-verify").length - 1);
  }

  @Test
  void runningTwiceChangesNothingAndReplacesInPlace() {
    ClaudeSettings settings = new ClaudeSettings();
    String once = settings.merged(existing, hooks, denies);
    assertEquals(once, settings.merged(once, hooks, denies));
    String changed = settings.merged(once, List.of(
        new HookSpec("Stop", Optional.empty(), "java HookMain audit --mvn /x", 600, "HookMain audit")), denies);
    assertTrue(changed.contains("--mvn /x"));
    assertEquals(1, changed.split("HookMain audit").length - 1);
  }

  @Test
  void startsFromNothing() {
    String merged = new ClaudeSettings().merged("", hooks, denies);
    assertTrue(merged.contains("\"PreToolUse\""));
    assertTrue(merged.contains("\"deny\""));
  }
}
