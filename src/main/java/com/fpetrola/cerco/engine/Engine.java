package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.autopsy.Autopsies;
import com.fpetrola.cerco.autopsy.Owed;
import com.fpetrola.cerco.autopsy.Solved;
import com.fpetrola.cerco.baseline.BaselineStore;
import com.fpetrola.cerco.baseline.CeilingReconciler;
import com.fpetrola.cerco.baseline.Key;
import com.fpetrola.cerco.baseline.Multiset;
import com.fpetrola.cerco.baseline.Reconciler;
import com.fpetrola.cerco.baseline.Reconciliation;
import com.fpetrola.cerco.exemptions.Applied;
import com.fpetrola.cerco.exemptions.Context;
import com.fpetrola.cerco.exemptions.Digest;
import com.fpetrola.cerco.exemptions.Exempting;
import com.fpetrola.cerco.exemptions.ExemptionsFile;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Layout;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.project.Recall;
import com.fpetrola.cerco.rules.BudgetRule;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import com.fpetrola.cerco.rules.SealedSurface;
import com.fpetrola.cerco.surface.Proposal;
import com.fpetrola.cerco.surface.Seal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * One gate over one root: feeds the scope's files through every rule one at a time, reconciles with the baseline
 * (which only shrinks), lets the approved exemptions speak, and answers with an outcome.
 */
public final class Engine {
  private final Settings settings;
  private final Rules rules;
  private final BaselineStore store;
  private final ExemptionsFile exemptionsFile;
  private final Path approvalFile;
  private final String projectVersion;
  private final Clock clock;
  private final Autopsies autopsies;
  private final Recall recall;
  private final Proposal proposal;
  private final Feed feed = new Feed(new Parser(), new Generated());
  private final Reconciler reconciler = new Reconciler();
  private final CeilingReconciler ceilings = new CeilingReconciler();
  private final Path root;

  public Engine(Settings settings, Path root, String projectVersion, Clock clock) {
    this.settings = settings;
    this.root = root;
    this.rules = new Rules(settings, root);
    this.store = new BaselineStore(new Layout(root).baseline());
    this.exemptionsFile = new ExemptionsFile(new Layout(root).exemptions(), rules.ids(),
        settings.maxExemptions());
    this.approvalFile = new Layout(root).approved();
    this.projectVersion = projectVersion;
    this.clock = clock;
    this.autopsies = new Autopsies(root, rules.ids(), store);
    this.proposal = new Proposal(root, new Seal(new Layout(root).seal()));
    this.recall = new Recall(root.resolve("target").resolve("cerco").resolve("project.json"));
  }

  public BaselineStore store() {
    return store;
  }

  public Rules rules() {
    return rules;
  }

  public ExemptionsFile exemptionsFile() {
    return exemptionsFile;
  }

  public Path approvalFile() {
    return approvalFile;
  }

  public Autopsies autopsies() {
    return autopsies;
  }

  public Witness witness() {
    return new Witness(store, rules);
  }

  public Outcome run(Scope scope) {
    long start = System.nanoTime();
    List<Rule> active = scope.rulesOf(rules);
    List<String> notes = new ArrayList<>();
    if (store.exists()) {
      active = adopt(active, scope.full(), notes);
    }
    Runs runs = new Runs(active);
    int parsed = fed(scope, runs, notes);
    Set<String> inScope = scope.names();
    Signed signed = new Signed(exemptionsFile, approvalFile, configDigest(), settings.isDefault(), scope.full());
    Tally tally = new Tally();
    List<Solved> solved = new ArrayList<>();
    List<Finding> unbaselined = unbaselined(active, runs);
    for (Rule rule : active.stream().filter(Rule::baselined).toList()) {
      Kept kept = new Kept(inScope, scope.full(), signed, rule.id(), root);
      rule.asBudget().ifPresentOrElse(
          budget -> tally.add(reconcile(budget, runs.measures(budget), inScope, kept, solved)),
          () -> tally.add(reconcile(rule, runs.findings(rule), inScope, kept, solved)));
    }
    Witness witness = witness();
    List<String> problems = signed.problemsSoFar();
    List<Owed> owed = owed(witness.fixedInCode(solved, runs.digests(), scope.full()), signed, problems);
    Applied applied = exempt(signed, tally.fresh(), scope.full());
    List<Finding> fresh = new ArrayList<>(applied.remaining());
    List<Finding> awaiting = proposed(unbaselined.stream()
        .filter(finding -> finding.rule().equals(SealedSurface.ID)).toList(), scope.full(), notes, problems);
    unbaselined.stream().filter(finding -> !awaiting.contains(finding)).forEach(fresh::add);
    problems.addAll(applied.problems());
    notes.addAll(applied.notes());
    if (store.exists()) {
      witnessed(witness, runs, scope);
      problems.addAll(witness.problems());
    } else {
      notes.add("no baseline is frozen yet: every finding counts as new until `mvn verify` freezes the project");
    }
    long millis = (System.nanoTime() - start) / 1_000_000;
    return new Outcome(scope.label(), parsed, fresh, applied.exempted(), tally.solved(),
        tally.frozen(), owed, notes, problems, millis, awaiting);
  }

  /**
   * Feeds the scope's files through the rules. A run over a few files starts from what the last whole run learnt
   * about the project, and a whole run leaves what it learnt for those.
   */
  private int fed(Scope scope, Runs runs, List<String> notes) {
    if (!scope.full()) {
      runs.remember(recall).ifPresent(notes::add);
    }
    int parsed = feed.each(scope.files(), runs::visit, runs::generated);
    feed.each(scope.tests(), runs::hear);
    if (scope.full()) {
      runs.sawTheWholeProject(recall);
    }
    return parsed;
  }

  /** What a run read is kept as witness: in place of what was kept when it read everything, beside it otherwise. */
  private void witnessed(Witness witness, Runs runs, Scope scope) {
    if (scope.full()) {
      witness.recordAll(runs.digests());
    } else {
      witness.recordSome(runs.digests());
    }
  }

  /** What the rules whose way out is a signature found: never frozen, never exempted. */
  private List<Finding> unbaselined(List<Rule> active, Runs runs) {
    return active.stream().filter(rule -> !rule.baselined()).flatMap(rule -> runs.findings(rule).stream()).toList();
  }

  /**
   * The surface the seal lacks goes into the one proposal, rewritten on a whole run. While the proposal lacks
   * something, the findings are the agent's; once it is complete they await a person's signature, and the agent may
   * stop.
   */
  private List<Finding> proposed(List<Finding> unsigned, boolean full, List<String> notes, List<String> problems) {
    if (full) {
      proposal.settle(unsigned);
    }
    if (unsigned.isEmpty()) {
      return List.of();
    }
    List<String> missing = proposal.missing();
    if (!missing.isEmpty()) {
      notes.addAll(missing);
      return List.of();
    }
    problems.add(".cerco/surface/pending/proposal.md is complete: a person reads it and signs with `mvn cerco:approve`, "
        + "or says no and the new members are taken back. Not yours; tell them and stop.");
    return unsigned;
  }

  /** Freezes the project as it stands: every current finding and every oversize measure become the baseline. */
  public int freeze(Scope scope) {
    List<Rule> active = rules.all().stream().filter(Rule::baselined).toList();
    Runs runs = new Runs(active);
    feed.each(scope.files(), runs::visit);
    feed.each(scope.tests(), runs::hear);
    int total = 0;
    for (Rule rule : active) {
      total += rule.asBudget().map(budget -> freeze(budget, runs.measures(budget)))
          .orElseGet(() -> freeze(rule, runs.findings(rule)));
    }
    store.markFrozen(LocalDate.now(clock), active.stream().map(Rule::id).toList());
    witness().recordAll(runs.digests());
    return total;
  }

  /**
   * Rules the freeze never saw: in a full run they are frozen now, as the project stands, and noted; in a partial
   * run they wait for the next full one, so that a module's worth of findings is not mistaken for the project's.
   */
  private List<Rule> adopt(List<Rule> active, boolean fullScope, List<String> notes) {
    Set<String> frozen = store.frozenRules();
    List<Rule> arrived = active.stream().filter(rule -> rule.baselined() && !frozen.contains(rule.id())).toList();
    if (arrived.isEmpty()) {
      return active;
    }
    if (!fullScope) {
      notes.add("rules without a baseline yet, skipped until a full run (mvn cerco:audit) freezes them: "
          + arrived.stream().map(Rule::id).collect(Collectors.joining(", ")));
      return active.stream().filter(rule -> !rule.baselined() || frozen.contains(rule.id())).toList();
    }
    arrived.forEach(rule -> notes.add("rule " + rule.id() + " is new to this project: its current findings were "
        + "frozen into the baseline now, and only shrink from here"));
    return active;
  }

  private boolean adopting(Rule rule) {
    return store.exists() && !store.frozenRules().contains(rule.id());
  }

  /**
   * What was fixed in this run owes an autopsy; every one already owed is checked, and filed when complete. A rule
   * a filed autopsy proposes is owed until this cerco has it, or a person declined it with a valid signature.
   */
  private List<Owed> owed(List<Solved> solved, Signed signed, List<String> problems) {
    if (!settings.autopsies() || !store.exists()) {
      return List.of();
    }
    autopsies.owe(solved, LocalDate.now(clock));
    Autopsies.Settled settled = autopsies.settle();
    settled.awaiting().forEach(id -> problems.add("the autopsy .cerco/autopsies/pending/" + id + ".md says no rule could "
        + "catch its case (`none:`), and only a person accepts that: read it and sign with `mvn cerco:approve`, or "
        + "have it propose a rule"));
    List<Owed> owed = new ArrayList<>(settled.owed());
    owed.addAll(autopsies.unbuilt(rules.known(), signed.valid() ? settings.declinedRules() : List.of()));
    return owed;
  }

  private Reconciliation reconcile(Rule rule, List<Finding> findings, Set<String> inScope, Kept kept,
      List<Solved> solved) {
    if (adopting(rule)) {
      freeze(rule, findings);
      store.extendFrozen(List.of(rule.id()));
    }
    Multiset stored = store.read(rule.id());
    Multiset baseline = stored.where(kept::entry);
    Reconciliation reconciliation = reconciler.reconcile(baseline, inScope, findings, stored.where(kept::departed),
        rule::narrows);
    if (store.exists() && !reconciliation.updated().sameAs(stored)) {
      store.write(rule.id(), reconciliation.updated());
      solved.add(new Solved(rule.id(), reconciliation.solvedAmong(stored.where(kept::entry))));
    }
    return reconciliation;
  }

  private CeilingReconciler.Outcome reconcile(BudgetRule rule, List<Measure> measures, Set<String> inScope,
      Kept kept, List<Solved> solved) {
    if (adopting(rule)) {
      freeze(rule, measures);
      store.extendFrozen(List.of(rule.id()));
    }
    Map<String, Integer> stored = store.readCeilings(rule.id());
    Map<String, Integer> baseline = new TreeMap<>(stored);
    baseline.keySet().removeIf(key -> !kept.entry(key));
    int gone = (int) stored.keySet().stream().filter(kept::gone).count();
    CeilingReconciler.Outcome outcome = ceilings.reconcile(rule, baseline, inScope, measures).plusSolved(gone);
    if (store.exists() && !outcome.updated().equals(stored)) {
      store.writeCeilings(rule.id(), outcome.updated());
      solved.add(new Solved(rule.id(), stored.keySet().stream()
          .filter(key -> baseline.containsKey(key) && !outcome.updated().containsKey(key))
          .toList()));
    }
    return outcome;
  }

  private int freeze(Rule rule, List<Finding> findings) {
    Multiset frozen = Multiset.of(findings.stream().map(Finding::key).toList());
    store.write(rule.id(), frozen);
    return frozen.total();
  }

  private int freeze(BudgetRule rule, List<Measure> measures) {
    Map<String, Integer> frozen = ceilings.freeze(rule, measures);
    store.writeCeilings(rule.id(), frozen);
    return frozen.size();
  }

  private Applied exempt(Signed signed, List<Finding> fresh, boolean fullScope) {
    return signed.applied(fresh, new Context(LocalDate.now(clock), projectVersion, 0), fullScope);
  }

  /**
   * Which frozen entries of one rule stay in the baseline for this run. In a full run an entry whose file is gone
   * is solved, and one a signed exemption covers is released, to come back as a finding that exemption covers.
   */
  private record Kept(Set<String> inScope, boolean full, Signed signed, String ruleId, Path root) {

    boolean entry(String key) {
      String file = new Key(key).file();
      return !full || (inScope.contains(file) && !signed.releases(ruleId, file));
    }

    boolean gone(String key) {
      return full && !inScope.contains(new Key(key).file());
    }

    /** Whether the entry's file is gone: out of a whole run, or off the disk for a run over a few files. */
    boolean departed(String key) {
      return full ? gone(key) : !java.nio.file.Files.exists(root.resolve(new Key(key).file()));
    }
  }

  public String configDigest() {
    return Digest.of(settings.fingerprint()).hex();
  }
}
