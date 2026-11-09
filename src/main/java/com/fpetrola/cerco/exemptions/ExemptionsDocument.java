package com.fpetrola.cerco.exemptions;

import java.util.ArrayList;
import java.util.List;

/** The root of .cerco/exemptions.yaml as the YAML reader fills it in. */
public final class ExemptionsDocument {
  private List<ExemptionEntry> exemptions = new ArrayList<>();

  public List<ExemptionEntry> getExemptions() {
    return exemptions;
  }

  public void setExemptions(List<ExemptionEntry> exemptions) {
    this.exemptions = exemptions;
  }
}
