package com.fpetrola.cerco.hooks;

import java.util.List;

/** Maven's failure output with the launcher's boilerplate removed, so what the agent reads is the gate's report. */
public record MavenOutput(String raw) {
  private static final List<String> BOILERPLATE = List.of(
      "[Help 1]", "To see the full stack trace", "Re-run Maven using", "For more information about the errors",
      "cwiki.apache.org", "Failed to execute goal", "[ERROR] -> ", "[ERROR] \n",
      "sun.misc.Unsafe", "A terminally deprecated method", "Please consider reporting this");

  public String forAgent() {
    StringBuilder out = new StringBuilder();
    for (String line : raw.split("\n")) {
      if (BOILERPLATE.stream().noneMatch(line::contains)) {
        out.append(line.replaceFirst("^\\[ERROR\\] ?", "")).append('\n');
      }
    }
    return out.toString().strip() + "\n";
  }
}
