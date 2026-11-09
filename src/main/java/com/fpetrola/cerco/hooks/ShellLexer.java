package com.fpetrola.cerco.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts a shell line into tokens: quoted and bare words, redirections, separators and spaces. Each token kind knows
 * how to recognise itself at a position and how to feed a builder; the lexer only tries them in order.
 */
final class ShellLexer {
  private final String line;
  private final List<Reader> readers = List.of(new Quoted(), new Escaped(), new Redirection(), new Separator(),
      new Space(), new Bare());

  ShellLexer(String line) {
    this.line = line;
  }

  List<Token> tokens() {
    List<Token> tokens = new ArrayList<>();
    int at = 0;
    while (at < line.length()) {
      Lexeme lexeme = lexemeAt(at);
      tokens.add(lexeme.token());
      at = lexeme.end();
    }
    return tokens;
  }

  private Lexeme lexemeAt(int at) {
    return readers.stream().map(reader -> reader.read(line, at)).flatMap(Optional::stream).findFirst()
        .orElseThrow(() -> new IllegalStateException("no reader took the character at " + at + " of " + line));
  }

  /** A token and where the next one starts. */
  record Lexeme(Token token, int end) {
  }

  /** What a token does to the command being built. */
  interface Token {
    void applyTo(ShellCommand.SimpleCommandBuilder builder);
  }

  record Word(String text) implements Token {
    @Override
    public void applyTo(ShellCommand.SimpleCommandBuilder builder) {
      builder.word(text);
    }
  }

  record Redirect(boolean toFile) implements Token {
    @Override
    public void applyTo(ShellCommand.SimpleCommandBuilder builder) {
      builder.redirect(toFile);
    }
  }

  record Break() implements Token {
    @Override
    public void applyTo(ShellCommand.SimpleCommandBuilder builder) {
      builder.separate();
    }
  }

  record Blank() implements Token {
    @Override
    public void applyTo(ShellCommand.SimpleCommandBuilder builder) {
      builder.space();
    }
  }

  /** Recognises one kind of token at a position, or declines. */
  interface Reader {
    Optional<Lexeme> read(String line, int at);
  }

  /** A pattern-shaped reader: the match at the position is the token's text. */
  abstract static class Matching implements Reader {
    private final Pattern pattern;

    Matching(String regex) {
      this.pattern = Pattern.compile(regex);
    }

    @Override
    public Optional<Lexeme> read(String line, int at) {
      Matcher matcher = pattern.matcher(line).region(at, line.length());
      return matcher.lookingAt() ? Optional.of(new Lexeme(token(matcher), matcher.end())) : Optional.empty();
    }

    abstract Token token(Matcher matcher);
  }

  static final class Quoted extends Matching {
    Quoted() {
      super("'([^']*)'?|\"([^\"]*)\"?");
    }

    @Override
    Token token(Matcher matcher) {
      return new Word(Optional.ofNullable(matcher.group(1)).orElseGet(() -> matcher.group(2)));
    }
  }

  static final class Escaped extends Matching {
    Escaped() {
      super("\\\\(.)");
    }

    @Override
    Token token(Matcher matcher) {
      return new Word(matcher.group(1));
    }
  }

  /** {@code >}, {@code >>}, {@code >|} and {@code &>} write a file; {@code >&2} and {@code <...} do not. */
  static final class Redirection extends Matching {
    Redirection() {
      super(">&\\d+|>[>|]?|<<?-?|<&\\d*");
    }

    @Override
    Token token(Matcher matcher) {
      return new Redirect(matcher.group().startsWith(">") && !matcher.group().startsWith(">&"));
    }
  }

  static final class Separator extends Matching {
    Separator() {
      super("&&|\\|\\||[;|&\\n]");
    }

    @Override
    Token token(Matcher matcher) {
      return new Break();
    }
  }

  static final class Space extends Matching {
    Space() {
      super("[ \\t\\r]+");
    }

    @Override
    Token token(Matcher matcher) {
      return new Blank();
    }
  }

  static final class Bare extends Matching {
    Bare() {
      super("[^'\"\\\\><;|&\\n \\t\\r]+");
    }

    @Override
    Token token(Matcher matcher) {
      return new Word(matcher.group());
    }
  }
}
