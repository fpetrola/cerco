package com.fpetrola.cerco.exemptions;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  /** What is wrong with the entry as written, for a file that knows those rules and those categories. */
  List<String> wrong(Set<String> knownRules, Set<String> categories) {
    List<String> wrong = new ArrayList<>();
    if (!knownRules.contains(rule)) {
      wrong.add("rule '" + rule + "' is not one of " + knownRules.stream().sorted().toList());
    }
    if (target.isBlank()) {
      wrong.add("target is empty; it is a glob over paths relative to the project root");
    }
    if (reason.strip().length() < 20) {
      wrong.add("reason must say why in at least 20 characters");
    }
    if (!categories.contains(category)) {
      wrong.add("category must be one of " + categories.stream().sorted().toList());
    }
    return wrong;
  }

  /** The one limit the entry sets; what is wrong with it is added to the list when there is none, or several. */
  Optional<Limit> limit(List<String> wrong) {
    List<Limit> limits = new ArrayList<>();
    if (!until.isBlank()) {
      date(wrong).ifPresent(date -> limits.add(new Limit.Until(date)));
    }
    if (!untilVersion.isBlank()) {
      limits.add(new Limit.UntilVersion(untilVersion.strip()));
    }
    Optional.ofNullable(budget).ifPresent(count -> limits.add(new Limit.Budget(count)));
    if (limits.size() != 1) {
      wrong.add("exactly one limit is required: until (a date), untilVersion, or budget (a count)");
      return Optional.empty();
    }
    return Optional.of(limits.get(0));
  }

  private Optional<LocalDate> date(List<String> wrong) {
    try {
      return Optional.of(LocalDate.parse(until.strip()));
    } catch (DateTimeParseException e) {
      wrong.add("until must be a date like 2027-03-31, not '" + until + "' (" + e.getMessage() + ")");
      return Optional.empty();
    }
  }

  /** The exemption this entry asks for, under that limit. */
  Exemption exemption(int ordinal, Limit limit) {
    return new Exemption(ordinal, rule, target, reason.strip(), category, limit);
  }
}
