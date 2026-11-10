package com.fpetrola.cerco.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What git knows about the working tree under a root, asked to git itself, which understands linked worktrees: the
 * Java files changed, added or untracked (what a quick run looks at when no hook names a file), and whether HEAD
 * holds a path (a baseline that HEAD knows and the working tree lacks was deleted, not absent).
 */
public record GitTree(Path root) {

  public List<Path> changedJava() {
    Stream<String> changed = ask("diff", "--name-only", "-z", "--relative", "--diff-filter=d", "HEAD").stream();
    Stream<String> untracked = ask("ls-files", "-z", "--others", "--exclude-standard").stream();
    return Stream.concat(changed, untracked).flatMap(names -> Stream.of(names.split("\0")))
        .filter(name -> name.endsWith(".java")).distinct().sorted()
        .map(name -> root.resolve(name).toAbsolutePath()).toList();
  }

  public boolean tracksInHead(String relativePath) {
    return ask("cat-file", "-e", "HEAD:./" + relativePath).isPresent();
  }

  private Optional<String> ask(String... arguments) {
    List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
    command.addAll(List.of(arguments));
    try {
      Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return process.waitFor() == 0 ? Optional.of(output) : Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException("could not run git under " + root, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while asking git under " + root, e);
    }
  }
}
