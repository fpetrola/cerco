package com.fpetrola.cerco.exemptions;

import java.util.List;
import java.util.Optional;

/** The exemptions file as read: its entries, what is wrong with them, and the digest the approval must match. */
public record Loaded(List<Exemption> exemptions, List<String> problems, Optional<String> digest) {

  public static Loaded none() {
    return new Loaded(List.of(), List.of(), Optional.empty());
  }
}
