package com.fpetrola.cerco.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * A shell command line read the way the shell reads it, approximately: quotes group words, {@code ;}, {@code &&},
 * {@code ||}, {@code |} and newlines separate simple commands, and a redirection names its target. Enough to tell
 * writing to a file from mentioning it.
 */
public final class ShellCommand {
  private final List<SimpleCommand> simples;

  public ShellCommand(String line) {
    SimpleCommandBuilder builder = new SimpleCommandBuilder();
    new ShellLexer(line).tokens().forEach(token -> token.applyTo(builder));
    this.simples = builder.finish();
  }

  public boolean mentions(String path) {
    return simples.stream().anyMatch(simple -> simple.mentions(path));
  }

  /**
   * A write to the directory as a whole, any write after a {@code cd} into it, or an {@code xargs} writer fed by a
   * command that named it: what it holds goes with it.
   */
  public boolean writesWhole(String dir) {
    boolean inside = false;
    boolean named = false;
    for (SimpleCommand simple : simples) {
      inside = inside || simple.changesDirectoryInto(dir);
      if (simple.writes() && (inside || simple.names(dir)) || (named && simple.writesWhatItIsFed())) {
        return true;
      }
      named = named || simple.names(dir);
    }
    return false;
  }

  public boolean writesAnything() {
    return simples.stream().anyMatch(SimpleCommand::writes);
  }

  /**
   * A write to the path in some simple command, a write in a command that works inside it after a {@code cd}, or an
   * {@code xargs} running a writer after a command that named it.
   */
  public boolean writesTo(String path) {
    boolean inside = false;
    boolean named = false;
    for (SimpleCommand simple : simples) {
      inside = inside || simple.changesDirectoryInto(path);
      if (simple.writesTo(path) || (inside && simple.writes()) || (named && simple.writesWhatItIsFed())) {
        return true;
      }
      named = named || simple.mentions(path);
    }
    return false;
  }

  /** Builds simple commands from tokens: words accumulate, a redirection claims the next word, a separator closes. */
  static final class SimpleCommandBuilder {
    private final List<SimpleCommand> done = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();
    private final StringBuilder pending = new StringBuilder();
    private boolean inWord;
    private boolean nextIsTarget;

    void word(String text) {
      pending.append(text);
      inWord = true;
    }

    void space() {
      flush();
    }

    /** A redirection: a bare descriptor stuck to it ({@code 2>}) is dropped; a file target follows when it writes. */
    void redirect(boolean toFile) {
      if (inWord && pending.toString().matches("\\d+|&")) {
        pending.setLength(0);
        inWord = false;
      }
      flush();
      nextIsTarget = toFile;
    }

    void separate() {
      flush();
      done.add(new SimpleCommand(List.copyOf(words), List.copyOf(targets)));
      words.clear();
      targets.clear();
    }

    List<SimpleCommand> finish() {
      separate();
      return List.copyOf(done);
    }

    private void flush() {
      if (!inWord) {
        return;
      }
      (nextIsTarget ? targets : words).add(pending.toString());
      pending.setLength(0);
      inWord = false;
      nextIsTarget = false;
    }
  }
}
