package com.fpetrola.cerco.hooks;

import com.fpetrola.cerco.json.JsonException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The entry point Claude Code's hooks call: {@code guard} answers a PreToolUse in this JVM, {@code quick} and
 * {@code audit} hand the input to Maven and turn a red gate into the exit code that blocks.
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
        "audit", () -> maven("audit", input));
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

  private int quick(String input) {
    HookInput hook = HookInput.parse(input);
    if (!hook.blank() && !hook.filePath().endsWith(".java")) {
      return 0;
    }
    return maven("quick", input);
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
