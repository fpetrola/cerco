package com.fpetrola.cerco.autopsy;

import com.fpetrola.cerco.baseline.BaselineStore;
import com.fpetrola.cerco.baseline.Key;
import com.fpetrola.cerco.model.MissingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Whether an autopsy form says enough to be filed. It cannot judge the analysis, only make sure there is one: a cause
 * in words of its own, places around the fix that were looked at and exist, the other places that share the cause,
 * the principle, and a rule proposed or a reason why none fits.
 */
public final class FormCheck {
  static final int CAUSE_LENGTH = 150;
  static final int PRINCIPLE_LENGTH = 40;
  static final int REASON_LENGTH = 100;
  static final int COVERED_LENGTH = 150;
  private final References references;
  private final Catalog catalog;
  private final Set<String> rules;
  private final BaselineStore frozen;
  private final Map<String, BiFunction<Form, Solved, List<String>>> candidates = Map.of(
      "rule:", this::proposed,
      "covered-by:", this::covered,
      "none:", this::declined,
      "same-as:", this::repeated);

  /** With the rules the running cerco gates with, and the baseline that says what each one had marked. */
  public FormCheck(References references, Catalog catalog, Set<String> rules, BaselineStore frozen) {
    this.references = references;
    this.catalog = catalog;
    this.rules = rules;
    this.frozen = frozen;
  }

  public List<String> missing(Form form, Solved solved) {
    List<String> missing = new ArrayList<>();
    String cause = form.section(Form.CAUSE);
    atLeast(missing, cause, CAUSE_LENGTH, Form.CAUSE + ": what was missing from the model that let this shape appear");
    if (!cause.isBlank() && catalog.causes().contains(catalog.normalized(cause))) {
      missing.add(Form.CAUSE + ": word for word the cause of an autopsy already filed; if it is the same cause, "
          + "say so with `same-as:` under " + Form.CANDIDATE + " and still describe this case");
    }
    missing.addAll(model(form.model()));
    missing.addAll(context(form.section(Form.CONTEXT), solved.files()));
    missing.addAll(elsewhere(form.section(Form.ELSEWHERE)));
    atLeast(missing, form.section(Form.PRINCIPLE), PRINCIPLE_LENGTH, Form.PRINCIPLE + ": the design principle at stake");
    missing.addAll(candidate(form, solved));
    return missing;
  }

  private List<String> model(String named) {
    List<String> ids = Stream.of(MissingModel.values()).map(MissingModel::id).toList();
    return ids.contains(named) ? List.of() : List.of(Form.CAUSE + ": a line `" + Form.MODEL + " <id>` naming which "
        + "model was missing, one of " + String.join(", ", ids)
        + (named.isEmpty() ? "" : " (`" + named + "` is none of them)"));
  }

  private List<String> context(String text, Set<String> fixed) {
    Set<References.Place> places = references.in(text);
    boolean around = places.stream().anyMatch(place -> !fixed.contains(place.file()));
    return places.size() >= 2 && around ? List.of() : List.of(Form.CONTEXT + ": at least two places (path:line, "
        + "relative to the project root, that exist) you read to understand why the code had this shape, at least "
        + "one of them outside the file(s) that were fixed");
  }

  private List<String> elsewhere(String text) {
    boolean answered = !references.in(text).isEmpty()
        || (text.toLowerCase().startsWith("none") && text.length() >= PRINCIPLE_LENGTH);
    return answered ? List.of() : List.of(Form.ELSEWHERE + ": other places (path:line) where the same missing "
        + "model shows, or `none` with how you looked");
  }

  private List<String> candidate(Form form, Solved solved) {
    Optional<BiFunction<Form, Solved, List<String>>> kind = candidates.entrySet().stream()
        .filter(entry -> form.candidate().startsWith(entry.getKey())).map(Map.Entry::getValue).findFirst();
    return kind.map(check -> check.apply(form, solved)).orElse(List.of(Form.CANDIDATE + ": start with "
        + "`rule: <new-rule-id>` and describe what it would detect with an example (path:line), or `same-as: <autopsy id>` "
        + "when an autopsy already filed proposed the rule, or `covered-by: <rule id>` when a rule this cerco has "
        + "already led to the missing model, or `none:` and why no rule could catch this, which waits until a "
        + "person accepts it"));
  }

  /** A rule this cerco already gates with is no proposal: it found the symptom, or it could have and did not. */
  private List<String> proposed(Form form, Solved solved) {
    String text = form.section(Form.CANDIDATE);
    String id = form.named("rule:");
    if (id.equals(solved.ruleId())) {
      return List.of(Form.CANDIDATE + ": `" + id + "` already exists and is the rule that found the symptom; "
          + "propose the rule that would have caught the cause, or say why this one is enough with `covered-by: "
          + id + "`");
    }
    if (rules.contains(id)) {
      return List.of(Form.CANDIDATE + ": `" + id + "` is a rule this cerco already has; propose a new one, or say "
          + "with `covered-by: " + id + "` why what it told was enough");
    }
    List<String> missing = new ArrayList<>();
    atLeast(missing, text, REASON_LENGTH, Form.CANDIDATE + ": what the rule would detect and what it would tell");
    if (references.in(text).isEmpty()) {
      missing.add(Form.CANDIDATE + ": an example of what the rule would catch, as path:line");
    }
    return missing;
  }

  /** An existing rule was enough: it had marked this code, and what it told led to the model that was missing. */
  private List<String> covered(Form form, Solved solved) {
    String id = form.named("covered-by:");
    if (!rules.contains(id)) {
      return List.of(Form.CANDIDATE + ": `covered-by: " + id + "` names no rule this cerco gates with");
    }
    List<String> missing = new ArrayList<>();
    atLeast(missing, form.section(Form.CANDIDATE), COVERED_LENGTH, Form.CANDIDATE + ": why what `" + id
        + "` told about this code already led to the model that was missing");
    boolean marked = id.equals(solved.ruleId()) || frozen.read(id).keys().stream()
        .anyMatch(key -> solved.files().contains(new Key(key).file()));
    if (!marked) {
      missing.add(Form.CANDIDATE + ": `" + id + "` neither lost the findings of this autopsy nor has any frozen in "
          + "the file(s) that were fixed, so it did not mark this code; name the rule that did, or propose one");
    }
    return missing;
  }

  private List<String> declined(Form form, Solved solved) {
    List<String> missing = new ArrayList<>();
    atLeast(missing, form.section(Form.CANDIDATE), REASON_LENGTH, Form.CANDIDATE + ": why no deterministic rule could catch this");
    return missing;
  }

  private List<String> repeated(Form form, Solved solved) {
    String id = form.named("same-as:");
    if (!catalog.holds(id)) {
      return List.of(Form.CANDIDATE + ": `same-as: " + id + "` names no filed autopsy");
    }
    return new Backlog(catalog).proposes(id).isEmpty() ? List.of(Form.CANDIDATE + ": `same-as: " + id + "` names "
        + "an autopsy that proposes no rule; `same-as:` joins a rule already asked for, so name one that asks for a "
        + "rule, or propose one with `rule:`") : List.of();
  }

  private void atLeast(List<String> missing, String text, int length, String what) {
    if (text.strip().length() < length) {
      missing.add(what + " (at least " + length + " characters)");
    }
  }
}
