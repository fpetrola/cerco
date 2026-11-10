package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import java.util.List;

/**
 * A rule that keeps what it saw in each file and answers at the end, when the project is whole and can say whose a
 * type or a method is. What it keeps is its own and holds no syntax tree, so a run has one tree alive at a time.
 *
 * @param <S> what the rule remembers between files
 */
public abstract class GatheringRule<S> implements Rule {
  protected final Enclosing enclosing = new Enclosing();

  @Override
  public final Inspection inspection(Project project) {
    S state = start();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        file.unit().ifPresent(unit -> gather(file, unit, state));
      }

      @Override
      public List<Finding> findings() {
        return conclude(state, project);
      }
    };
  }

  protected abstract S start();

  protected abstract void gather(ParsedFile file, CompilationUnit unit, S state);

  protected abstract List<Finding> conclude(S state, Project project);

  protected int line(Node node) {
    return node.getBegin().map(position -> position.line).orElse(1);
  }

  protected String clipped(String text) {
    String flat = text.replaceAll("\\s+", " ");
    return flat.length() <= 70 ? flat : flat.substring(0, 67) + "...";
  }
}
