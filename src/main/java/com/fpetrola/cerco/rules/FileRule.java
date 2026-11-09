package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import java.util.ArrayList;
import java.util.List;

/** A rule that looks at one compilation unit at a time and forgets it. */
public abstract class FileRule implements Rule {
  protected final Enclosing enclosing = new Enclosing();

  @Override
  public Inspection inspection() {
    List<Finding> findings = new ArrayList<>();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        file.unit().ifPresent(unit -> findings.addAll(inspect(file, unit)));
      }

      @Override
      public List<Finding> findings() {
        return List.copyOf(findings);
      }
    };
  }

  protected abstract List<Finding> inspect(ParsedFile file, CompilationUnit unit);

  protected Finding finding(ParsedFile file, Node node, String key, String detail) {
    return new Finding(id(), file.name(), line(node), file.name() + "|" + key, detail);
  }

  protected int line(Node node) {
    return node.getBegin().map(position -> position.line).orElse(1);
  }

  protected String clipped(String text) {
    String flat = text.replaceAll("\\s+", " ");
    return flat.length() <= 70 ? flat : flat.substring(0, 67) + "...";
  }
}
