package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.SourceFile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Parses sources with JavaParser, in parallel, without comments: the rules read structure, not prose. */
public final class Parser {
  private final ParserConfiguration configuration = new ParserConfiguration()
      .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
      .setAttributeComments(false);

  public List<ParsedFile> parseAll(List<SourceFile> files) {
    return files.parallelStream().map(this::parse).sorted(Comparator.comparing(ParsedFile::name)).toList();
  }

  public ParsedFile parse(SourceFile file) {
    try {
      return parse(file, new String(Files.readAllBytes(file.absolute()), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file.absolute(), e);
    }
  }

  public ParsedFile parse(SourceFile file, String text) {
    int lines = (int) text.lines().count();
    ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(text);
    Optional<CompilationUnit> unit = result.isSuccessful() ? result.getResult() : Optional.empty();
    if (unit.isPresent()) {
      return new ParsedFile(file, unit, Optional.empty(), lines);
    }
    String problem = result.getProblems().stream().map(Problem::getVerboseMessage).findFirst()
        .orElse("the parser produced no tree");
    return new ParsedFile(file, Optional.empty(), Optional.of(problem.replaceAll("\\s+", " ")), lines);
  }
}
