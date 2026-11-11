package com.fpetrola.cerco.exemptions;

import java.util.ArrayList;
import java.util.List;

/** The root of .cerco/exemptions.yaml as the YAML reader fills it in. */
public final class ExemptionsDocument {
  private List<ExemptionEntry> exemptions = new ArrayList<>();

  /** What the file listed, as read: nobody changes it from here on. */
  public List<ExemptionEntry> entries() {
    return List.copyOf(exemptions);
  }

  public void setExemptions(List<ExemptionEntry> exemptions) {
    this.exemptions = exemptions;
  }
}
