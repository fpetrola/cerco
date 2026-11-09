package com.fpetrola.cerco.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

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
      return mapper.readValue(existing, ObjectNode.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(".claude/settings.json is not valid JSON: " + e.getOriginalMessage(), e);
    }
  }

  private void install(ArrayNode groups, HookSpec hook) {
    if (replaced(groups, hook)) {
      return;
    }
    ObjectNode group = groups.addObject();
    hook.matcher().ifPresent(matcher -> group.put("matcher", matcher));
    group.withArray("hooks").addObject().put("type", "command").put("command", hook.command())
        .put("timeout", hook.timeoutSeconds());
  }

  /** Finds the hook again by its marker and rewrites it in place, matcher included; false when it is not there. */
  private boolean replaced(ArrayNode groups, HookSpec hook) {
    for (int g = 0; g < groups.size(); g++) {
      ObjectNode group = groups.get(g).deepCopy();
      ArrayNode commands = group.withArray("hooks");
      for (int i = 0; i < commands.size(); i++) {
        if (commands.get(i).path("command").asText("").contains(hook.marker())) {
          ObjectNode command = commands.get(i).deepCopy();
          command.put("command", hook.command()).put("timeout", hook.timeoutSeconds());
          commands.set(i, command);
          hook.matcher().ifPresent(matcher -> group.put("matcher", matcher));
          groups.set(g, group);
          return true;
        }
      }
    }
    return false;
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
