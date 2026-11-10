package com.fpetrola.cerco.project;

import java.util.List;

/** A method as its type declares it: its name, the types it takes and the type it answers with, by simple name. */
public record Operation(String name, List<String> parameters, String returns) {

  public boolean takesNothing() {
    return parameters.isEmpty();
  }
}
