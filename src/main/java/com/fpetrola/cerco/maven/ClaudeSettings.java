package com.fpetrola.cerco.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;

/**
 * Merges the gate's hooks and permission rules into an existing .claude/settings.json without touching what else
 * is there. Running it twice leaves the file as it was: a hook is found again by its marker and replaced in place.
 */
public final class ClaudeSettings {
  private final ObjectMapper mapper = new ObjectMapper();

  public String merged(String existing, List<HookSpec> hooks, List<String> denies) {
    ObjectNode root = parse(existing);
    ObjectNode hookGroups = root.withObject("/hooks");
    for (HookSpec hook : hooks) {
      install(hookGroups.withArray(hook.event()), hook);
    }
    ArrayNode deny = root.withObject("/permissions").withArray("deny");
    for (String rule : denies) {
      if (!contains(deny, rule)) {
        deny.add(rule);
      }
    }
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not write the settings", e);
    }
  }

  private ObjectNode parse(String existing) {
    if (existing.isBlank()) {
      return mapper.createObjectNode();
    }
    try {
      JsonNode node = mapper.readTree(existing);
      if (!node.isObject()) {
        throw new IllegalArgumentException(".claude/settings.json does not hold a JSON object");
      }
      return (ObjectNode) node;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(".claude/settings.json is not valid JSON: " + e.getOriginalMessage(), e);
    }
  }

  private void install(ArrayNode groups, HookSpec hook) {
    Optional<ObjectNode> existing = existingCommand(groups, hook.marker());
    if (existing.isPresent()) {
      existing.get().put("command", hook.command()).put("timeout", hook.timeoutSeconds());
      return;
    }
    ObjectNode group = groups.addObject();
    hook.matcher().ifPresent(matcher -> group.put("matcher", matcher));
    group.withArray("hooks").addObject().put("type", "command").put("command", hook.command())
        .put("timeout", hook.timeoutSeconds());
  }

  private Optional<ObjectNode> existingCommand(ArrayNode groups, String marker) {
    for (JsonNode group : groups) {
      for (JsonNode command : group.withArray("hooks")) {
        if (command.path("command").asText("").contains(marker) && command.isObject()) {
          return Optional.of((ObjectNode) command);
        }
      }
    }
    return Optional.empty();
  }

  private boolean contains(ArrayNode array, String text) {
    for (JsonNode element : array) {
      if (element.asText("").equals(text)) {
        return true;
      }
    }
    return false;
  }
}
