package com.fpetrola.cerco.hooks;

import com.fpetrola.cerco.json.JsonException;
import com.fpetrola.cerco.json.JsonQuote;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The entry point Claude Code's hooks call: {@code guard} answers a PreToolUse in this JVM, {@code quick} and
 * {@code audit} hand the input to Maven and turn a red gate into the exit code that blocks; the audit at the end of
 * a turn blocks only while the red changes.
 */
public final class HookMain {
  private final List<String> args;
  private final InputStream in;
  private final PrintStream out;
  private final PrintStream err;
  private final Map<String, String> env;

  public HookMain(List<String> args, InputStream in, PrintStream out, PrintStream err, Map<String, String> env) {
    this.args = args;
    this.in = in;
    this.out = out;
    this.err = err;
    this.env = env;
  }

  public static void main(String[] args) {
    System.exit(new HookMain(List.of(args), System.in, System.out, System.err, System.getenv()).run());
  }

  public int run() {
    if (args.isEmpty()) {
      err.println("usage: HookMain guard|quick|audit [--mvn <path to mvn>]");
      return 1;
    }
    String input = new Terminal().interactive() ? "" : new StdinText(in).text();
    Map<String, Supplier<Integer>> goals = Map.of(
        "guard", () -> guard(input),
        "quick", () -> quick(input),
        "audit", () -> stop(input));
    Supplier<Integer> action = goals.get(args.get(0));
    if (action == null) {
      err.println("cerco: unknown hook goal " + args.get(0));
      return 1;
    }
    try {
      return action.get();
    } catch (JsonException e) {
      err.println("cerco: unreadable hook input, letting the call through: " + e.getMessage());
      return 0;
    }
  }

  private int guard(String input) {
    Decision decision = new Guard().decide(HookInput.parse(input));
    if (decision.denied()) {
      out.println(decision.toHookJson());
    }
    return 0;
  }

  /**
   * After an edit of a .java file, or after a shell command that wrote something (a heredoc, an interpreter, an
   * in-place sed): the files git sees as changed are then the scope, so the gate sees what the editor did not.
   */
  private int quick(String input) {
    HookInput hook = HookInput.parse(input);
    if (hook.blank()) {
      return maven("quick", input);
    }
    if (hook.tool().equals("Bash")) {
      return new ShellCommand(hook.command()).writesAnything() ? maven("quick", input) : 0;
    }
    return hook.filePath().endsWith(".java") ? maven("quick", input) : 0;
  }

  /**
   * The audit at the end of a turn blocks while the agent has something to do. Coming back from a block with the
   * same red means it had nothing: then the session ends and the person sees the report, instead of a loop.
   */
  private int stop(String input) {
    ByteArrayOutputStream report = new ByteArrayOutputStream();
    int code = new MavenRun(mavenPath(), root(), "audit", input).run(new PrintStream(report, true, StandardCharsets.UTF_8));
    String text = report.toString(StandardCharsets.UTF_8);
    LastStop last = new LastStop(root().resolve("target").resolve("cerco").resolve("stop-report.txt"));
    if (code == 0) {
      last.clear();
      return 0;
    }
    if (HookInput.parse(input).stopHookActive() && last.repeats(text)) {
      out.println("{\"systemMessage\":" + new JsonQuote("cerco is still red, with the same report the agent was "
          + "stopped with; it had nothing left to do, so the session ends here for a person to read it:\n" + text)
          .quoted() + "}");
      return 0;
    }
    last.blockedWith(text);
    err.print(text);
    return code;
  }

  private int maven(String goal, String input) {
    return new MavenRun(mavenPath(), root(), goal, input).run(err);
  }

  private String mavenPath() {
    int at = args.indexOf("--mvn");
    return at >= 0 && at + 1 < args.size() ? args.get(at + 1) : "mvn";
  }

  private Path root() {
    return Path.of(env.getOrDefault("CLAUDE_PROJECT_DIR", System.getProperty("user.dir")));
  }
}
