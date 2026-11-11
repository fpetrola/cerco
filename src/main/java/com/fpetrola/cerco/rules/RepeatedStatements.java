package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Family;
import com.fpetrola.cerco.project.Project;
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

  private static final Hypothesis SAME_MEMBER = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "both copies sit in one member", "a method of that type the member calls twice, or a loop over what differs");
  private static final Hypothesis SAME_TYPE = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "both copies sit in members of one type", "one method of that type both members call");
  private static final Hypothesis SIBLINGS = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "they sit in types of one hierarchy", "pull it up into the common type, as a template method when a step varies");
  private static final Hypothesis UNRELATED = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "they sit in unrelated types", "an object both are built from, which does this once");

  /** Where a run was seen; the file, type and member strings are shared across every window of the same block. */
  public record Site(String file, String type, String member, int line) {

    /** Which model two copies point to by where they sit: the nearer they are, the smaller what is missing. */
    Reading with(Site first, Project project) {
      if (member.equals(first.member)) {
        return SAME_MEMBER.because("both copies sit in `" + member + "`");
      }
      if (type.equals(first.type)) {
        return SAME_TYPE.because("both copies sit in `" + type + "`");
      }
      Family family = project.family(type);
      return family.holds(first.type)
          ? SIBLINGS.because("`" + type + "` and `" + first.type + "` are both a `" + family.root() + "`")
          : UNRELATED.because("nothing of the project is above both `" + type + "` and `" + first.type + "`");
    }

    /** Where it is, as a finding points at it. */
    String where() {
      return file + ":" + line + " (" + member + ")";
    }
  }

  /** A run found again: what it reports, and the two places it sits in. */
  public record Repeat(Finding finding, Site here, Site first) {

    Finding read(Project project) {
      return finding.read(here.with(first, project));
    }
  }

  /** Every run seen so far by its digest, and the repeats found. */
  public record Seen(Map<Long, Site> runs, List<Repeat> repeats) {
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
        SAME_MEMBER, SAME_TYPE, SIBLINGS, UNRELATED);
  }

  @Override
  protected Seen start() {
    return new Seen(new HashMap<>(), new ArrayList<>());
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    return seen.repeats().stream().map(repeat -> repeat.read(project)).toList();
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen state) {
    Map<Long, Site> seen = state.runs();
    List<Repeat> out = state.repeats();
    String name = file.name();
    for (BlockStmt block : unit.findAll(BlockStmt.class)) {
      List<Statement> statements = block.getStatements();
      if (statements.size() < WINDOW) {
        continue;
      }
      String member = enclosing.of(block);
      String type = enclosing.typeOf(block).replaceAll(".*\\.", "");
      for (int i = 0; i + WINDOW <= statements.size(); i++) {
        long digest = digest(statements.subList(i, i + WINDOW));
        int line = statements.get(i).getBegin().map(position -> position.line).orElse(1);
        Site here = new Site(name, type, member, line);
        Site first = seen.putIfAbsent(digest, here);
        if (first != null) {
          out.add(new Repeat(new Finding(id(), name, line, name + "|" + member + "|" + Long.toHexString(digest),
              WINDOW + " statements in " + member + " repeat " + first.where()), here, first));
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
