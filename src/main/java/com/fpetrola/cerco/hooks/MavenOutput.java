package com.fpetrola.cerco.hooks;

import java.util.List;

/**
 * Maven's failure output with the launcher's boilerplate removed, so what the agent reads is the gate's report, and
 * the failure's own message without Maven's prefix (the only text there is when the plugin itself broke).
 */
public record MavenOutput(String raw) {
  private static final List<String> BOILERPLATE = List.of(
      "To see the full stack trace", "Re-run Maven using", "For more information about the errors",
      "cwiki.apache.org", "[ERROR] -> ", "[ERROR] \n",
      "sun.misc.Unsafe", "A terminally deprecated method", "Please consider reporting this");

  public String forAgent() {
    StringBuilder out = new StringBuilder();
    for (String line : raw.split("\n")) {
      if (BOILERPLATE.stream().noneMatch(line::contains)) {
        out.append(line.replaceFirst("^\\[ERROR\\] ?", "")
            .replaceFirst("^Failed to execute goal .*? on project [^:]+: (.*?)( -> \\[Help 1\\])?$", "$1")).append('\n');
      }
    }
    return out.toString().strip() + "\n";
  }
}
