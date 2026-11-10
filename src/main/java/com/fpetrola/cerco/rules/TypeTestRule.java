package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.GenericVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A rule that catches a value being asked its type, whatever the construct (instanceof, a cast): what such a test
 * hides is the same, so the family says it once, and reads each test against the project when the run ends.
 */
public abstract class TypeTestRule extends GatheringRule<List<TypeTest>> {
  private final TypeTestDiagnosis suspected = new TypeTestDiagnosis();

  /** The file a test is in, and who reads what surrounds it. */
  protected record In(ParsedFile file, TypeTests reader) {
  }

  @Override
  public final Diagnosis diagnosis() {
    return new Diagnosis(suspected.hypotheses(), disguises());
  }

  /** The known ways of keeping the test while satisfying this rule by the letter. */
  protected abstract String disguises();

  /** The nodes of a file this rule takes for type tests. */
  protected abstract List<Node> suspects(CompilationUnit unit);

  /** Each kind of suspect says in its own way what it tests and on what. */
  protected abstract GenericVisitor<TypeTest, In> asked();

  @Override
  protected final List<TypeTest> start() {
    return new ArrayList<>();
  }

  @Override
  protected final void gather(ParsedFile file, CompilationUnit unit, List<TypeTest> tests) {
    In in = new In(file, new TypeTests(unit, this::clipped));
    GenericVisitor<TypeTest, In> asked = asked();
    suspects(unit).stream().sorted(Node.NODE_BY_BEGIN_POSITION).map(node -> node.accept(asked, in))
        .forEach(tests::add);
  }

  @Override
  protected final List<Finding> conclude(List<TypeTest> tests, Project project) {
    Map<String, Set<String>> askers = suspected.askers(tests, project);
    return tests.stream().map(test -> suspected.read(test, project, askers).map(test.finding()::read)
        .orElse(test.finding())).toList();
  }
}
