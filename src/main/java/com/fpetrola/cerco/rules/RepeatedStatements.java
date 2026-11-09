package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
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
public final class RepeatedStatements implements Rule {
  private static final int WINDOW = 6;
  private final Enclosing enclosing = new Enclosing();

  /** Where a run was first seen; the file and member strings are shared across every window of the same block. */
  private record Site(String file, String member, int line) {
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
  public boolean perFile() {
    return false;
  }

  @Override
  public Inspection inspection() {
    Map<Long, Site> seen = new HashMap<>();
    List<Finding> findings = new ArrayList<>();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        file.unit().ifPresent(unit -> scan(file, unit, seen, findings));
      }

      @Override
      public List<Finding> findings() {
        return List.copyOf(findings);
      }
    };
  }

  private void scan(ParsedFile file, CompilationUnit unit, Map<Long, Site> seen, List<Finding> out) {
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
