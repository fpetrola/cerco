package com.fpetrola.cerco.hooks;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The PreToolUse gatekeeper: the baseline, the exemptions and the hook settings are not the agent's to change, and
 * the checks are not optional. Permission rules already refuse direct edits; this refuses the shell's ways around
 * them.
 */
public final class Guard {
  private static final List<String> PROTECTED = List.of(".cerco", ".claude/settings.json");
  private static final Pattern WRITE_SHAPED = Pattern.compile(
      "(^|[\\s;&|(`$])(rm|mv|cp|tee|truncate|install|rsync|ln|chmod|chown|touch|dd|shred"
          + "|sed\\s+-[a-zA-Z]*i|perl\\s+-[a-zA-Z]*i|python3?|ruby|node"
          + "|git\\s+(checkout|restore|rm|clean|stash|reset|update-index))\\b|(^|[\\s\\d&])>");

  public Decision decide(HookInput input) {
    String command = input.command();
    if (command.isBlank()) {
      return Decision.allowing();
    }
    if (command.contains("-Dcerco.skip")) {
      return Decision.of("-Dcerco.skip switches the design gate off, and the gate is not optional: fix the findings "
          + "it reports instead. If a rule is wrong here, a human decides that with an exemption (mvn cerco:approve).");
    }
    boolean touchesProtected = PROTECTED.stream().anyMatch(command::contains);
    if (touchesProtected && WRITE_SHAPED.matcher(command).find()) {
      return Decision.of("This command writes to the gate's own files (.cerco/ or .claude/settings.json). The "
          + "baseline only shrinks through the gate itself, exemptions are approved by a human in a terminal "
          + "(mvn cerco:approve), and the hooks stay as installed. Fix the code the gate complained about.");
    }
    return Decision.allowing();
  }
}
