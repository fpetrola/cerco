package com.fpetrola.cerco.project;

import java.util.Set;

/** A hierarchy of the project: its topmost type and every type under it, the top included. */
public record Family(String root, Set<String> members) {

  public boolean holds(String type) {
    return members.contains(type);
  }

  /** How many types sit under the top. */
  public int subtypes() {
    return members.size() - 1;
  }
}
