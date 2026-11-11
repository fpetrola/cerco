package com.fpetrola.cerco.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/** One hook as .claude/settings.json declares it: the event, the tool matcher, the command, its timeout. */
public record HookSpec(String event, Optional<String> matcher, String command, int timeoutSeconds, String marker) {

  /** Whether a command already in the settings is this hook, written by an earlier install. */
  boolean wrote(JsonNode command) {
    return command.path("command").asText("").contains(marker);
  }

  /** Writes what this hook runs into a command of the settings. */
  ObjectNode into(ObjectNode command) {
    return command.put("type", "command").put("command", this.command).put("timeout", timeoutSeconds);
  }

  /** Writes which tools this hook is for into its group, when it is for some only. */
  void matching(ObjectNode group) {
    matcher.ifPresent(tools -> group.put("matcher", tools));
  }
}
