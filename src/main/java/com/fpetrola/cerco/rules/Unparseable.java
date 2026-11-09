package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.ArrayList;
import java.util.List;

/** A file the parser could not read: nothing else can be checked in it, so that is a finding of its own. */
public final class Unparseable implements Rule {

  @Override
  public String id() {
    return "parseable";
  }

  @Override
  public String principle() {
    return "the gate sees every file";
  }

  @Override
  public String description() {
    return "a source file the parser rejects cannot be checked, so it fails until it parses";
  }

  @Override
  public String remedy() {
    return "fix the syntax, or if the file uses a newer Java than the parser knows, report it as a gate limitation";
  }

  @Override
  public Inspection inspection() {
    List<Finding> findings = new ArrayList<>();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        file.problem().ifPresent(problem ->
            findings.add(new Finding(id(), file.name(), 1, file.name() + "|parse", problem)));
      }

      @Override
      public List<Finding> findings() {
        return List.copyOf(findings);
      }
    };
  }
}
