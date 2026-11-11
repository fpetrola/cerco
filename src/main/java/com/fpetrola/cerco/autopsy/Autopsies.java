package com.fpetrola.cerco.autopsy;

import com.fpetrola.cerco.baseline.BaselineStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every finding taken out of the baseline asks why the code had it. Fixing it is the symptom going away; the cause is
 * usually a piece of model that was missing, and it shows elsewhere too, as other smells that look unrelated. So the
 * gate owes an autopsy for each rule whose findings went away, and stays red until its form says what the cause
 * was, where it was looked for, and which rule would catch it next time. Done autopsies stay in the catalog; the
 * rules they propose are the gate's own backlog, and stay owed until they are built.
 */
public final class Autopsies {
  private final Ledger ledger;
  private final Catalog catalog;
  private final FormCheck check;

  public Autopsies(Path root, Set<String> rules, BaselineStore frozen) {
    this.ledger = new Ledger(root.resolve(".cerco").resolve("autopsies"));
    this.catalog = new Catalog(root.resolve("autopsies"));
    this.check = new FormCheck(new References(root), catalog, rules, frozen);
  }

  /** Owes an autopsy for each rule that lost findings in this run, or adds them to the one already owed for it. */
  public void owe(List<Solved> solved, LocalDate today) {
    for (Solved one : solved) {
      if (!one.keys().isEmpty()) {
        Ledger.Entry entry = ledger.owe(ledger.openFor(one.ruleId()).map(Ledger.Entry::id)
            .orElseGet(() -> freshId(today, one.ruleId())), one);
        Path form = catalog.pending(entry.id());
        String text = Files.exists(form) ? catalog.read(form) : new Template().text(entry.id(), entry.ruleId());
        catalog.write(form, withSymptom(text, entry));
      }
    }
  }

  /**
   * What the owed autopsies come to: the ones still owed, and the complete ones that say no rule could catch it
   * (`none:`), which only a person files. One waits at a time: while it does, every other autopsy proposes a rule.
   */
  public record Settled(List<Owed> owed, List<String> awaiting) {
  }

  /** Files every form that is complete and proposes a rule, and answers with the ones still owed or waiting. */
  public Settled settle() {
    List<Owed> owed = new ArrayList<>();
    List<String> awaiting = new ArrayList<>();
    List<String> complete = new ArrayList<>();
    for (Ledger.Entry entry : ledger.open()) {
      Path file = catalog.pending(entry.id());
      if (!Files.exists(file)) {
        catalog.write(file, withSymptom(new Template().text(entry.id(), entry.ruleId()), entry));
      }
      Form form = Form.parse(catalog.read(file));
      List<String> missing = missing(form, entry, awaiting);
      if (!missing.isEmpty()) {
        owed.add(new Owed(entry.id(), entry.ruleId(), "autopsies/pending/" + entry.id() + ".md", missing));
        continue;
      }
      (form.declines() ? awaiting : complete).add(entry.id());
    }
    accept(complete);
    return new Settled(owed, awaiting);
  }

  private List<String> missing(Form form, Ledger.Entry entry, List<String> awaiting) {
    List<String> missing = check.missing(form, entry.solved());
    if (!missing.isEmpty() || !form.declines() || awaiting.isEmpty()) {
      return missing;
    }
    return List.of(Form.CANDIDATE + ": autopsies/pending/" + awaiting.get(0) + ".md already says no rule could "
        + "catch its case and waits for a person; while it does, every other autopsy proposes a rule, with `rule:` "
        + "or with `same-as:` an autopsy that asks for one, or names the rule that was enough with `covered-by:`");
  }

  /** Files these autopsies; for the `none:` ones waiting, a person read them and accepts them. */
  public void accept(List<String> ids) {
    ids.forEach(id -> {
      catalog.file(id);
      ledger.settle(id);
    });
  }

  /** The rules the filed autopsies propose that this cerco does not have and nobody declined. */
  public Map<String, Map<String, Long>> modelsByRule() {
    return catalog.modelsByRule();
  }

  public List<Owed> unbuilt(Set<String> known, List<String> declined) {
    return new Backlog(catalog).unbuilt(known, declined);
  }

  private String freshId(LocalDate today, String ruleId) {
    String base = today + "-" + ruleId;
    String id = base;
    for (int n = 2; catalog.holds(id) || ledger.holds(id); n++) {
      id = base + "-" + n;
    }
    return id;
  }

  private String withSymptom(String text, Ledger.Entry entry) {
    StringBuilder body = new StringBuilder("Findings of `" + entry.ruleId() + "` that went away (written by cerco):\n\n");
    entry.keys().forEach(key -> body.append("- `").append(key).append("`\n"));
    String marker = "## " + Form.SYMPTOM + "\n";
    int start = text.indexOf(marker);
    if (start < 0) {
      return text;
    }
    int from = start + marker.length();
    int next = text.indexOf("\n## ", from);
    String rest = next < 0 ? "" : text.substring(next);
    return text.substring(0, from) + "\n" + body + rest;
  }
}
