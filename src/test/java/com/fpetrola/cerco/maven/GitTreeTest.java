package com.fpetrola.cerco.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitTreeTest {
  @TempDir
  Path dir;

  @Test
  void aLinkedWorktreeReportsItsChangesAndItsHead() throws Exception {
    Path main = dir.resolve("main");
    Files.createDirectories(main.resolve("m/src"));
    git(main, "init", "-q");
    Files.writeString(main.resolve("m/src/Kept.java"), "class Kept {}");
    Files.writeString(main.resolve("m/src/Gone.java"), "class Gone {}");
    git(main, "add", ".");
    git(main, "commit", "-q", "-m", "first");
    Path linked = dir.resolve("linked");
    git(main, "worktree", "add", "-q", linked.toString());

    Files.writeString(linked.resolve("m/src/Kept.java"), "class Kept { int x; }");
    Files.delete(linked.resolve("m/src/Gone.java"));
    Files.writeString(linked.resolve("m/src/New.java"), "class New {}");
    Files.writeString(linked.resolve("m/notes.txt"), "not java");

    GitTree module = new GitTree(linked.resolve("m"));
    assertEquals(List.of(linked.resolve("m/src/Kept.java").toAbsolutePath(),
        linked.resolve("m/src/New.java").toAbsolutePath()), module.changedJava());
    assertTrue(module.tracksInHead("src/Gone.java"));
    assertFalse(module.tracksInHead("src/New.java"));
  }

  @Test
  void outsideGitNothingIsChangedOrTracked() {
    GitTree tree = new GitTree(dir);
    assertEquals(List.of(), tree.changedJava());
    assertFalse(tree.tracksInHead(".cerco/sealed/baseline/frozen"));
  }

  private static void git(Path where, String... arguments) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-C",
        where.toString()));
    command.addAll(List.of(arguments));
    assertEquals(0, new ProcessBuilder(command).inheritIO().start().waitFor());
  }
}
