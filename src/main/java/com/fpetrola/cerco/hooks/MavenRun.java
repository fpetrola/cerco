package com.fpetrola.cerco.hooks;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * One Maven run of a gate goal, offline and quiet, fed the hook's input. A red gate comes back as exit code 2 with
 * the report on stderr, which is how a hook tells Claude Code to stop and show the agent why.
 */
public record MavenRun(String maven, Path root, String goal, String stdin) {

  public int run(PrintStream err) {
    List<String> command = List.of(maven, "-q", "-o", "-B", "-f", root.resolve("pom.xml").toString(),
        "-Dcerco.hook=true", "com.fpetrola:cerco-maven-plugin:" + goal);
    try {
      ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
      builder.environment().put("MAVEN_OPTS", "-Xmx1g");
      Process process = builder.start();
      try (OutputStream in = process.getOutputStream()) {
        in.write(stdin.getBytes(StandardCharsets.UTF_8));
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int code = process.waitFor();
      if (code == 0) {
        return 0;
      }
      err.print(new MavenOutput(output).forAgent());
      return 2;
    } catch (IOException e) {
      throw new UncheckedIOException("could not run Maven as " + maven, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for Maven", e);
    }
  }
}
