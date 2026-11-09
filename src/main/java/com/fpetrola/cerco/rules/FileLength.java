package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.ArrayList;
import java.util.List;

/** How long a file is. A long file is several concepts sharing a name. */
public final class FileLength implements BudgetRule {
  private final int ceiling;

  public FileLength(int ceiling) {
    this.ceiling = ceiling;
  }

  @Override
  public String id() {
    return "file-length";
  }

  @Override
  public String principle() {
    return "cohesion: one file, one concept";
  }

  @Override
  public String description() {
    return "a file over " + ceiling + " lines holds more than one responsibility; files the baseline froze longer "
        + "may only shrink";
  }

  @Override
  public String remedy() {
    return "split it along the responsibilities it already has: the groups of methods that share fields are the "
        + "classes waiting to be named";
  }

  @Override
  public int ceiling() {
    return ceiling;
  }

  @Override
  public Measuring measuring() {
    List<Measure> measures = new ArrayList<>();
    return new Measuring() {
      @Override
      public void visit(ParsedFile file) {
        measures.add(new Measure(file.name() + "|file", file.name(), 1, file.lines(), file.lines() + " lines"));
      }

      @Override
      public List<Measure> measures() {
        return List.copyOf(measures);
      }
    };
  }
}
