package com.fpetrola.cerco.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HookMainTest {

  private record Run(int code, String out, String err) {
  }

  private Run run(String goal, String stdin) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = new HookMain(List.of(goal), new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
        new PrintStream(out), new PrintStream(err), Map.of()).run();
    return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void guardDeniesWithTheJsonClaudeCodeExpects() {
    Run run = run("guard", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"rm -rf .cerco\"}}");
    assertEquals(0, run.code());
    assertTrue(run.out().contains("\"permissionDecision\":\"deny\""));
  }

  @Test
  void guardStaysSilentForAnOrdinaryCommand() {
    Run run = run("guard", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}");
    assertEquals(0, run.code());
    assertEquals("", run.out());
  }

  @Test
  void quickIgnoresEditsThatAreNotJava() {
    Run run = run("quick", "{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"/x/README.md\"}}");
    assertEquals(0, run.code());
    assertEquals("", run.err());
  }

  @Test
  void unreadableInputLetsTheCallThrough() {
    Run run = run("guard", "{not json");
    assertEquals(0, run.code());
    assertTrue(run.err().contains("unreadable hook input"));
  }

  @Test
  void unknownGoalIsAnError() {
    assertEquals(1, run("dance", "").code());
  }
}
