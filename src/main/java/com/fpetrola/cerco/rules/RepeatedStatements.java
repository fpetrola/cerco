package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The same run of statements appearing twice: a method that was never extracted. */
public final class RepeatedStatements extends ProjectRule<RepeatedStatements.Seen> {
  private static final int WINDOW = 6;

  /** Where a run was first seen; the file and member strings are shared across every window of the same block. */
  public record Site(String file, String member, int line) {
  }

  /** Every run seen so far by its digest, and the repeats found. */
  public record Seen(Map<Long, Site> runs, List<Finding> repeats) {
  }

  @Override
  public String id() {
    return "no-repeated-statements";
  }

  @Override
  public String principle() {
    return "abstraction: a thing done twice has a name";
  }

  @Override
  public String description() {
    return "a run of " + WINDOW + " consecutive statements that also appears elsewhere is a method nobody extracted; "
        + "the two copies will drift apart";
  }

  @Override
  public String remedy() {
    return "extract the run into one method and call it from both places; if the copies differ in a detail, that "
        + "detail is the parameter";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a static helper or a utility class both call: the shape still has no owner",
        new Hypothesis(MissingModel.SHAPE_REPEATED,
            "the copies differ only in literals or names",
            "one method, the difference a parameter"),
        new Hypothesis(MissingModel.SHAPE_REPEATED,
            "they differ in a call",
            "a strategy, a lambda, or a template method in a common supertype"),
        new Hypothesis(MissingModel.SHAPE_REPEATED,
            "they sit in sibling types",
            "pull it up into the supertype, or a default method"),
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "they sit in unrelated types",
            "an object both are built from"));
  }

  @Override
  protected Seen start() {
    return new Seen(new HashMap<>(), new ArrayList<>());
  }

  @Override
  protected List<Finding> conclude(Seen seen) {
    return List.copyOf(seen.repeats());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen state) {
    Map<Long, Site> seen = state.runs();
    List<Finding> out = state.repeats();
    String name = file.name();
    for (BlockStmt block : unit.findAll(BlockStmt.class)) {
      List<Statement> statements = block.getStatements();
      if (statements.size() < WINDOW) {
        continue;
      }
      String member = enclosing.of(block);
      for (int i = 0; i + WINDOW <= statements.size(); i++) {
        long digest = digest(statements.subList(i, i + WINDOW));
        int line = statements.get(i).getBegin().map(position -> position.line).orElse(1);
        Site first = seen.putIfAbsent(digest, new Site(name, member, line));
        if (first != null) {
          out.add(new Finding(id(), name, line, name + "|" + member + "|" + Long.toHexString(digest),
              WINDOW + " statements in " + member + " repeat " + first.file() + ":" + first.line()
                  + " (" + first.member() + ")"));
        }
      }
    }
  }

  private long digest(List<Statement> statements) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      for (Statement statement : statements) {
        sha.update(statement.toString().replaceAll("\\s+", " ").getBytes(StandardCharsets.UTF_8));
        sha.update((byte) '\n');
      }
      return ByteBuffer.wrap(sha.digest()).getLong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JVM", e);
    }
  }
}
