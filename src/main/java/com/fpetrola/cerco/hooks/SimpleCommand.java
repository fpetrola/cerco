package com.fpetrola.cerco.hooks;

import java.util.List;
import java.util.Set;

/** One simple command of a shell line: its words, with redirection targets set apart. */
public record SimpleCommand(List<String> words, List<String> redirectTargets) {
  private static final Set<String> WRITERS = Set.of("rm", "mv", "cp", "tee", "truncate", "install", "rsync", "ln",
      "chmod", "chown", "touch", "dd", "shred", "unlink", "rmdir", "mkdir", "patch");
  private static final Set<String> INTERPRETERS = Set.of("python", "python3", "ruby", "node", "perl", "php", "sh",
      "bash", "zsh", "eval", "jq", "yq", "sqlite3");
  private static final Set<String> GIT_WRITERS = Set.of("checkout", "restore", "rm", "clean", "stash", "reset",
      "update-index", "mv", "apply", "revert", "switch");
  private static final Set<String> FIND_WRITERS = Set.of("-delete", "-exec", "-execdir", "-ok", "-okdir");
  private static final Set<String> PREFIXES = Set.of("sudo", "command", "exec", "nohup", "time");

  /** The command's name, past any {@code VAR=value} assignments and prefixes like {@code sudo}. */
  public String head() {
    return words.stream()
        .filter(word -> !word.matches("[A-Za-z_][A-Za-z0-9_]*=.*") && !PREFIXES.contains(word))
        .findFirst().orElse("");
  }

  public boolean mentions(String path) {
    return words.stream().anyMatch(word -> word.contains(path))
        || redirectTargets.stream().anyMatch(word -> word.contains(path));
  }

  /** Names the directory itself, not something inside it. */
  public boolean names(String dir) {
    return words.stream().map(word -> word.replaceAll("/+$", ""))
        .anyMatch(word -> word.equals(dir) || word.endsWith("/" + dir));
  }

  public boolean changesDirectoryInto(String path) {
    return head().equals("cd") && mentions(path);
  }

  /** Writes something, somewhere: a writing command, an in-place editor, an interpreter, or a redirection. */
  public boolean writes() {
    return isWriter() || !redirectTargets.isEmpty();
  }

  /** Writes to that path in particular, as far as the words show. */
  public boolean writesTo(String path) {
    return redirectTargets.stream().anyMatch(word -> word.contains(path)) || (mentions(path) && isWriter());
  }

  /** An {@code xargs} that runs a writing command over whatever the pipe feeds it. */
  public boolean writesWhatItIsFed() {
    return head().equals("xargs") && words.stream().anyMatch(WRITERS::contains);
  }

  private boolean isWriter() {
    return WRITERS.contains(head()) || editsInPlace() || INTERPRETERS.contains(head()) || gitWrites()
        || findWrites() || writesWhatItIsFed();
  }

  private boolean editsInPlace() {
    return (head().equals("sed") || head().equals("perl"))
        && words.stream().anyMatch(word -> word.matches("-[a-zA-Z]*i[a-zA-Z]*(=.*)?|--in-place(=.*)?"));
  }

  private boolean gitWrites() {
    return head().equals("git") && words.stream().skip(1).filter(word -> !word.startsWith("-")).findFirst()
        .map(GIT_WRITERS::contains).orElse(false);
  }

  private boolean findWrites() {
    return head().equals("find") && words.stream().anyMatch(FIND_WRITERS::contains);
  }
}
