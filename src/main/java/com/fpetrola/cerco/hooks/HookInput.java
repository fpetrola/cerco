package com.fpetrola.cerco.hooks;

import com.fpetrola.cerco.json.JsonParser;
import com.fpetrola.cerco.json.JsonValue;

/** What Claude Code tells a hook on stdin, reduced to the fields the gate acts on. */
public record HookInput(String event, String tool, String command, String filePath, boolean stopHookActive) {

  public static HookInput parse(String json) {
    if (json.isBlank()) {
      return new HookInput("", "", "", "", false);
    }
    return from(JsonParser.parse(json));
  }

  public static HookInput from(JsonValue value) {
    return new HookInput(
        value.string("hook_event_name").orElse(""),
        value.string("tool_name").orElse(""),
        value.string("tool_input", "command").orElse(""),
        value.string("tool_input", "file_path").orElse(""),
        value.flag("stop_hook_active"));
  }

  public boolean blank() {
    return event.isEmpty() && tool.isEmpty() && command.isEmpty() && filePath.isEmpty();
  }
}
