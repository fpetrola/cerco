package com.fpetrola.cerco.autopsy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The rules the filed autopsies ask for, most asked first: a rule proposed once is an idea, one that keeps coming
 * back from unrelated fixes is the gate's next rule. `same-as:` counts for the rule of the autopsy it names.
 */
public final class Backlog {
  private final Catalog catalog;

  public Backlog(Catalog catalog) {
    this.catalog = catalog;
  }

  /** One proposed rule and the autopsies that asked for it. */
  public record Wanted(String ruleId, List<String> autopsies) {
  }

  public List<Wanted> wanted() {
    Map<String, Form> done = catalog.done();
    Map<String, List<String>> byRule = new TreeMap<>();
    done.forEach((id, form) -> byRule.computeIfAbsent(proposal(done, form, 0), rule -> new ArrayList<>()).add(id));
    byRule.remove("");
    return byRule.entrySet().stream().map(entry -> new Wanted(entry.getKey(), List.copyOf(entry.getValue())))
        .sorted(Comparator.comparingInt((Wanted wanted) -> -wanted.autopsies().size()).thenComparing(Wanted::ruleId))
        .toList();
  }

  /** The rule an autopsy proposes, following `same-as:` to the autopsy it names; empty when it proposes none. */
  private String proposal(Map<String, Form> done, Form form, int depth) {
    String first = form.section(Form.CANDIDATE).lines().findFirst().orElse("").strip();
    String rule = first.startsWith("rule:") ? word(first.substring("rule:".length())) : "";
    String same = first.startsWith("same-as:") ? word(first.substring("same-as:".length())) : "";
    boolean follow = !same.isEmpty() && done.containsKey(same) && depth < done.size();
    return follow ? proposal(done, done.get(same), depth + 1) : rule;
  }

  private String word(String text) {
    String stripped = text.strip();
    int space = stripped.indexOf(' ');
    return space < 0 ? stripped : stripped.substring(0, space);
  }
}
