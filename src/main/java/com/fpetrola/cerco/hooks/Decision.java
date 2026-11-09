package com.fpetrola.cerco.hooks;

import com.fpetrola.cerco.json.JsonQuote;

/** The guard's answer to one tool call: let it through, or refuse it with the reason the agent will read. */
public record Decision(boolean denied, String reason) {

  public static Decision of(String reason) {
    return new Decision(true, reason);
  }

  public static Decision allowing() {
    return new Decision(false, "");
  }

  /** The PreToolUse JSON Claude Code expects on stdout for a refusal. */
  public String toHookJson() {
    return "{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\","
        + "\"permissionDecisionReason\":" + new JsonQuote(reason).quoted() + "}}";
  }
}
