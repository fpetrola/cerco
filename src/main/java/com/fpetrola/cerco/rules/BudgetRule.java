package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.List;
import java.util.Optional;

/**
 * A rule that measures sizes: each measured thing has a ceiling, the default one unless the baseline froze it higher,
 * and a frozen ceiling only ever comes down.
 */
public interface BudgetRule extends Rule {

  int ceiling();

  Measuring measuring();

  default List<Measure> measure(List<ParsedFile> files) {
    Measuring measuring = measuring();
    files.forEach(measuring::visit);
    return measuring.measures();
  }

  @Override
  default boolean perFile() {
    return true;
  }

  /** Without a baseline, everything over the default ceiling is a finding. */
  @Override
  default Inspection inspection() {
    Measuring measuring = measuring();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        measuring.visit(file);
      }

      @Override
      public List<Finding> findings() {
        return measuring.measures().stream().filter(m -> m.value() > ceiling()).map(m -> finding(m, ceiling()))
            .toList();
      }
    };
  }

  @Override
  default Optional<BudgetRule> asBudget() {
    return Optional.of(this);
  }

  default Finding finding(Measure measure, int ceiling) {
    return new Finding(id(), measure.file(), measure.line(), measure.key(),
        measure.detail() + " (ceiling " + ceiling + ")");
  }
}
