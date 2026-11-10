package com.fpetrola.cerco.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MavenOutputTest {

  @Test
  void aBrokenPluginStillSaysWhatBroke() {
    String raw = """
        [ERROR] Failed to execute goal com.fpetrola:cerco-maven-plugin:0.4.0:quick (default-cli) on project oozx: \
        Execution default-cli of goal quick failed: could not open the git repository -> [Help 1]
        [ERROR]\s
        [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
        [ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/PluginExecutionException
        """;
    assertEquals("Execution default-cli of goal quick failed: could not open the git repository\n",
        new MavenOutput(raw).forAgent());
  }
}
