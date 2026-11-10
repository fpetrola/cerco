package com.fpetrola.cerco.autopsy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

  public Autopsies(Path root) {
    this.ledger = new Ledger(root.resolve(".cerco").resolve("autopsies"));
    this.catalog = new Catalog(root.resolve("autopsies"));
    this.check = new FormCheck(new References(root), catalog);
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

  /** Files every form that is complete and answers with the ones still owed. */
  public List<Owed> settle() {
    List<Owed> owed = new ArrayList<>();
    for (Ledger.Entry entry : ledger.open()) {
      Path form = catalog.pending(entry.id());
      if (!Files.exists(form)) {
        catalog.write(form, withSymptom(new Template().text(entry.id(), entry.ruleId()), entry));
      }
      List<String> missing = check.missing(Form.parse(catalog.read(form)), entry.solved());
      if (missing.isEmpty()) {
        catalog.file(entry.id());
        ledger.settle(entry.id());
      } else {
        owed.add(new Owed(entry.id(), entry.ruleId(), "autopsies/pending/" + entry.id() + ".md", missing));
      }
    }
    return owed;
  }

  /** The rules the filed autopsies propose that this cerco does not have and nobody declined. */
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
