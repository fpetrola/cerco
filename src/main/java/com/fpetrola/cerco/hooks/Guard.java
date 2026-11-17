package com.fpetrola.cerco.hooks;

import java.util.List;

/**
 * The PreToolUse gatekeeper: the baseline, the exemptions and the hook settings are not the agent's to change, and
 * the checks are not optional. Permission rules already refuse direct edits; this refuses the shell's ways around
 * them, and only those: reading, diffing or listing the gate's files is nobody's business to stop.
 */
public final class Guard {
  private static final List<String> PROTECTED = List.of(com.fpetrola.cerco.model.Layout.SEALED, ".claude/settings.json");

  public Decision decide(HookInput input) {
    String command = input.command();
    if (command.isBlank()) {
      return Decision.allowing();
    }
    if (command.contains("-Dcerco.skip")) {
      return Decision.of("-Dcerco.skip switches the design gate off, and the gate is not optional: fix the findings "
          + "it reports instead. If a rule is wrong here, a human decides that with an exemption (mvn cerco:approve).");
    }
    ShellCommand shell = new ShellCommand(command);
    if (PROTECTED.stream().anyMatch(shell::writesTo) || shell.writesWhole(com.fpetrola.cerco.model.Layout.HOME)) {
      return Decision.of("This command writes to the gate's own files (.cerco/sealed/ or .claude/settings.json). The "
          + "baseline only shrinks through the gate itself, exemptions are approved by a human in a terminal "
          + "(mvn cerco:approve), and the hooks stay as installed. Fix the code the gate complained about.");
    }
    return Decision.allowing();
  }
}
