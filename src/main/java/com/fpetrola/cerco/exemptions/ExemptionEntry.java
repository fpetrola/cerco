package com.fpetrola.cerco.exemptions;

/** One entry as the YAML reader fills it in; the bean shape is what the reader needs, nothing else reads it. */
public final class ExemptionEntry {
  private String rule = "";
  private String target = "";
  private String reason = "";
  private String category = "";
  private String until = "";
  private String untilVersion = "";
  private Integer budget;

  public String getRule() {
    return rule;
  }

  public void setRule(String rule) {
    this.rule = rule;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getUntil() {
    return until;
  }

  public void setUntil(String until) {
    this.until = until;
  }

  public String getUntilVersion() {
    return untilVersion;
  }

  public void setUntilVersion(String untilVersion) {
    this.untilVersion = untilVersion;
  }

  public Integer getBudget() {
    return budget;
  }

  public void setBudget(Integer budget) {
    this.budget = budget;
  }
}
