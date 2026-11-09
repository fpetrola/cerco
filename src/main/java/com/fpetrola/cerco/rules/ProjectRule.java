package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/**
 * A rule that needs the whole project before it can answer: it gathers what it needs from each file into a state of
 * its own and concludes at the end. Never runs on a single edit.
 *
 * @param <S> what the rule remembers between files
 */
public abstract class ProjectRule<S> implements Rule {
  protected final Enclosing enclosing = new Enclosing();

  @Override
  public final boolean perFile() {
    return false;
  }

  @Override
  public final Inspection inspection() {
    S state = start();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        file.unit().ifPresent(unit -> gather(file, unit, state));
      }

      @Override
      public List<Finding> findings() {
        return conclude(state);
      }
    };
  }

  protected abstract S start();

  protected abstract void gather(ParsedFile file, CompilationUnit unit, S state);

  protected abstract List<Finding> conclude(S state);

  protected String clipped(String text) {
    String flat = text.replaceAll("\\s+", " ");
    return flat.length() <= 70 ? flat : flat.substring(0, 67) + "...";
  }
}
