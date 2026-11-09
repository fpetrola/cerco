package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.baseline.BaselineStore;
import com.fpetrola.cerco.baseline.CeilingReconciler;
import com.fpetrola.cerco.baseline.Multiset;
import com.fpetrola.cerco.baseline.Reconciler;
import com.fpetrola.cerco.baseline.Reconciliation;
import com.fpetrola.cerco.exemptions.Applied;
import com.fpetrola.cerco.exemptions.Approval;
import com.fpetrola.cerco.exemptions.Context;
import com.fpetrola.cerco.exemptions.Digest;
import com.fpetrola.cerco.exemptions.Exempting;
import com.fpetrola.cerco.exemptions.ExemptionsFile;
import com.fpetrola.cerco.exemptions.Loaded;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.rules.BudgetRule;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final Feed feed = new Feed(new Parser(), new Generated());
  private final Reconciler reconciler = new Reconciler();
  private final CeilingReconciler ceilings = new CeilingReconciler();

  public Engine(Settings settings, Path root, String projectVersion, Clock clock) {
    this.settings = settings;
    this.rules = new Rules(settings);
    this.store = new BaselineStore(root.resolve(".cerco").resolve("baseline"));
    this.exemptionsFile = new ExemptionsFile(root.resolve(".cerco").resolve("exemptions.yaml"), rules.ids(),
        settings.maxExemptions());
    this.approvalFile = root.resolve(".cerco").resolve("approved");
    this.projectVersion = projectVersion;
    this.clock = clock;
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

  public Outcome run(Scope scope) {
    long start = System.nanoTime();
    List<Rule> active = rules.all().stream().filter(rule -> scope.full() || rule.perFile()).toList();
    Runs runs = new Runs(active);
    int parsed = feed.each(scope.files(), runs::visit);
    Set<String> inScope = scope.files().stream().map(SourceFile::name).collect(Collectors.toSet());
    Tally tally = new Tally();
    for (Rule rule : active) {
      rule.asBudget().ifPresentOrElse(budget -> tally.add(reconcile(budget, runs.measures(budget), inScope)),
          () -> tally.add(reconcile(rule, runs.findings(rule), inScope)));
    }
    List<String> problems = new ArrayList<>();
    Applied applied = exempt(tally.fresh(), scope.full(), problems);
    problems.addAll(applied.problems());
    List<String> notes = new ArrayList<>(applied.notes());
    if (!store.exists()) {
      notes.add("no baseline is frozen yet: every finding counts as new until `mvn verify` freezes the project");
    }
    long millis = (System.nanoTime() - start) / 1_000_000;
    return new Outcome(scope.label(), parsed, applied.remaining(), applied.exempted(), tally.solved(),
        tally.frozen(), notes, problems, millis);
  }

  /** Freezes the project as it stands: every current finding and every oversize measure become the baseline. */
  public int freeze(List<SourceFile> files) {
    List<Rule> active = rules.all();
    Runs runs = new Runs(active);
    feed.each(files, runs::visit);
    int total = 0;
    for (Rule rule : active) {
      total += rule.asBudget().map(budget -> freeze(budget, runs.measures(budget)))
          .orElseGet(() -> freeze(rule, runs.findings(rule)));
    }
    store.markFrozen(LocalDate.now(clock));
    return total;
  }

  private Reconciliation reconcile(Rule rule, List<Finding> findings, Set<String> inScope) {
    Multiset baseline = store.read(rule.id());
    Reconciliation reconciliation = reconciler.reconcile(baseline, inScope, findings);
    if (store.exists() && !reconciliation.updated().sameAs(baseline)) {
      store.write(rule.id(), reconciliation.updated());
    }
    return reconciliation;
  }

  private CeilingReconciler.Outcome reconcile(BudgetRule rule, List<Measure> measures, Set<String> inScope) {
    Map<String, Integer> stored = store.readCeilings(rule.id());
    CeilingReconciler.Outcome outcome = ceilings.reconcile(rule, stored, inScope, measures);
    if (store.exists() && !outcome.updated().equals(stored)) {
      store.writeCeilings(rule.id(), outcome.updated());
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

  private Applied exempt(List<Finding> fresh, boolean fullScope, List<String> problems) {
    Loaded loaded = exemptionsFile.load();
    problems.addAll(loaded.problems());
    problems.addAll(Approval.read(approvalFile).problems(loaded.digest(), configDigest(), settings.isDefault()));
    if (!problems.isEmpty()) {
      return Applied.untouched(fresh);
    }
    Context context = new Context(LocalDate.now(clock), projectVersion, 0);
    return new Exempting(loaded.exemptions(), context).apply(fresh, fullScope);
  }

  public String configDigest() {
    return Digest.of(settings.fingerprint()).hex();
  }
}
