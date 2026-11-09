package com.fpetrola.cerco.exemptions;

import java.time.LocalDate;

/** What a limit is judged against: the day, the project's version, and how many findings the exemption covered. */
public record Context(LocalDate today, String projectVersion, int matched) {

  public Context withMatched(int count) {
    return new Context(today, projectVersion, count);
  }
}
