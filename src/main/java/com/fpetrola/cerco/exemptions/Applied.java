package com.fpetrola.cerco.exemptions;

import com.fpetrola.cerco.model.Finding;
import java.util.List;

/** Fresh findings after the exemptions had their say: what remains, what they covered, and how they stand. */
public record Applied(List<Finding> remaining, List<Finding> exempted, List<String> notes, List<String> problems) {

  public static Applied untouched(List<Finding> fresh) {
    return new Applied(fresh, List.of(), List.of(), List.of());
  }
}
