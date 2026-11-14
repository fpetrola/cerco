package com.fpetrola.cerco.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
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

  private Run stop(Path root, String red, boolean active) throws Exception {
    Path mvn = root.resolve("mvn");
    Files.writeString(mvn, "#!/bin/sh\ncat > /dev/null\necho '" + red + "'\nexit 1\n");
    mvn.toFile().setExecutable(true);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = new HookMain(List.of("audit", "--mvn", mvn.toString()), new ByteArrayInputStream(
        ("{\"hook_event_name\":\"Stop\",\"stop_hook_active\":" + active + "}").getBytes(StandardCharsets.UTF_8)),
        new PrintStream(out), new PrintStream(err), Map.of("CLAUDE_PROJECT_DIR", root.toString())).run();
    return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void theStopHookBlocksWhileTheRedChangesAndNotForTheSameRedAgain(@TempDir Path root) throws Exception {
    assertEquals(2, stop(root, "[ERROR] cerco RED: 1 new finding(s) (12 ms)", false).code());
    assertEquals(2, stop(root, "[ERROR] cerco RED: 2 new finding(s) (15 ms)", true).code());
    Run same = stop(root, "[ERROR] cerco RED: 2 new finding(s) (40 ms)", true);
    assertEquals(0, same.code());
    assertTrue(same.out().contains("systemMessage"), same.out());
    assertEquals(2, stop(root, "[ERROR] cerco RED: 2 new finding(s) (9 ms)", false).code());
  }
}
