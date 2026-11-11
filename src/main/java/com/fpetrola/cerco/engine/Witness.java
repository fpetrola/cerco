package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.autopsy.Solved;
import com.fpetrola.cerco.baseline.BaselineStore;
import com.fpetrola.cerco.baseline.Key;
import com.fpetrola.cerco.exemptions.Digest;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What the gate remembers between runs of the code and of itself: the digest of each file's code, of the whole
 * project at the last full run, and of this cerco's defaults. With it, a frozen finding that went away while its code
 * stayed the same is told apart from one that was fixed: the rule changed, not the code. That, a frozen rule this
 * cerco no longer has, and defaults that changed are a person's to accept (approve), never the agent's: the agent can
 * write cerco's code, and loosening a rule must not pass for fixing a finding.
 */
public final class Witness {
  private static final String DIGESTS = "digests";
  private static final String SELF = "self";
  private static final String LOOSENED = "loosened";
  private static final String DEFAULTS = "defaults";
  private static final String PROJECT = "project";
  private final BaselineStore store;
  private final Rules rules;
  private final Set<String> projectWide;

  public Witness(BaselineStore store, Rules rules) {
    this.store = store;
    this.rules = rules;
    this.projectWide = rules.projectWide();
  }

  /**
   * The solved keys whose code changed. The others are recorded as loosened: a per-file finding whose file has the
   * digest it had, a project-wide one when no file of the project changed since the last full run, and any finding
   * of a file that is now generated, since saying a tool wrote it is no fix and a person accepts it.
   */
  List<Solved> fixedInCode(List<Solved> solved, Digests digests, boolean full) {
    Map<String, String> before = read(DIGESTS);
    boolean projectSame = full && digests.ofAll().equals(read(SELF).get(PROJECT));
    List<String> loosened = new ArrayList<>(store.lines(LOOSENED));
    List<Solved> fixed = new ArrayList<>();
    for (Solved one : solved) {
      Map<Boolean, List<String>> untouched = one.keys().stream().collect(Collectors.partitioningBy(key ->
          digests.isGenerated(new Key(key).file()) || (projectWide.contains(one.ruleId()) ? projectSame
              : digests.sameAsIn(before, new Key(key).file()))));
      untouched.get(true).forEach(key -> loosened.add(one.ruleId() + "\t" + key));
      fixed.add(new Solved(one.ruleId(), untouched.get(false)));
    }
    store.writeLines(LOOSENED, loosened);
    return fixed;
  }

  /** Keeps the digests of the files a run over part of the project read, beside those already kept. */
  void recordSome(Digests digests) {
    write(DIGESTS, digests.over(read(DIGESTS)));
    Map<String, String> self = read(SELF);
    self.putIfAbsent(DEFAULTS, defaults());
    write(SELF, self);
  }

  /** Keeps the digests of a run that read the whole project, in place of what was kept, and the project's own. */
  void recordAll(Digests digests) {
    write(DIGESTS, digests.over(Map.of()));
    Map<String, String> self = read(SELF);
    self.putIfAbsent(DEFAULTS, defaults());
    self.put(PROJECT, digests.ofAll());
    write(SELF, self);
  }

  /** What only a person can settle: rules gone from this cerco, changed defaults, findings a rule stopped seeing. */
  public List<String> problems() {
    List<String> problems = new ArrayList<>();
    Set<String> vanished = vanished();
    if (!vanished.isEmpty()) {
      problems.add("rules frozen in the baseline that this cerco no longer has: " + vanished + ". A rule does not "
          + "go away by itself: a person accepts it with `mvn cerco:approve`, which drops their baselines");
    }
    String recorded = read(SELF).getOrDefault(DEFAULTS, defaults());
    if (!recorded.equals(defaults())) {
      problems.add("this cerco's defaults are not the ones the baseline was kept with (now "
          + Settings.DEFAULTS.fingerprint() + "): defaults change only when a person accepts it, `mvn cerco:approve`");
    }
    List<String> loosened = store.lines(LOOSENED);
    if (!loosened.isEmpty()) {
      problems.add(loosened.size() + " frozen finding(s) left the baseline while their code stayed the same, or "
          + "because their file is now generated, so a rule or a marking changed, not the code (.cerco/baseline/" + LOOSENED + ", the first: " + loosened.get(0) + "). A "
          + "person reviews that change of cerco and accepts it with `mvn cerco:approve`");
    }
    return problems;
  }

  /** A person accepted what problems() said: the rules gone, the defaults as they are, the findings loosened. */
  public void accept() {
    store.unfreeze(vanished());
    Map<String, String> self = read(SELF);
    self.put(DEFAULTS, defaults());
    write(SELF, self);
    store.writeLines(LOOSENED, List.of());
  }

  private Set<String> vanished() {
    Set<String> vanished = new TreeSet<>(store.frozenRules());
    vanished.removeAll(rules.known());
    return vanished;
  }

  private String defaults() {
    return Digest.of(Settings.DEFAULTS.fingerprint()).hex();
  }

  private Map<String, String> read(String name) {
    Map<String, String> values = new TreeMap<>();
    store.lines(name).stream().map(line -> line.split("\t", 2)).forEach(pair -> values.put(pair[1], pair[0]));
    return values;
  }

  private void write(String name, Map<String, String> values) {
    store.writeLines(name, values.entrySet().stream().map(entry -> entry.getValue() + "\t" + entry.getKey()).toList());
  }
}
