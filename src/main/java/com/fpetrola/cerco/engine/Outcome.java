package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import java.util.List;

/** Everything one run has to say. Green means nothing new and nothing wrong with the exemptions or the approval. */
public record Outcome(String label, int parsed, List<Finding> fresh, List<Finding> exempted, int solved, int frozen,
    List<String> notes, List<String> problems, long millis) {

  public boolean green() {
    return fresh.isEmpty() && problems.isEmpty();
  }
}
