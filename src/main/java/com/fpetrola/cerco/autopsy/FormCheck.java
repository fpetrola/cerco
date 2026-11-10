package com.fpetrola.cerco.autopsy;

import com.fpetrola.cerco.model.MissingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
  private final References references;
  private final Catalog catalog;
  private final Map<String, Function<String, List<String>>> candidates = Map.of(
      "rule:", this::proposed,
      "none:", this::declined,
      "same-as:", this::repeated);

  public FormCheck(References references, Catalog catalog) {
    this.references = references;
    this.catalog = catalog;
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
    missing.addAll(candidate(form.section(Form.CANDIDATE)));
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

  private List<String> candidate(String text) {
    String first = text.lines().findFirst().orElse("").strip();
    Optional<Map.Entry<String, Function<String, List<String>>>> kind = candidates.entrySet().stream()
        .filter(entry -> first.startsWith(entry.getKey())).findFirst();
    return kind.map(entry -> entry.getValue().apply(text)).orElse(List.of(Form.CANDIDATE + ": start with "
        + "`rule: <new-rule-id>` and describe what it would detect with an example (path:line), or `same-as: <autopsy id>` "
        + "when an autopsy already filed proposed the rule, or `none:` and why no rule could catch this, which waits "
        + "until a person accepts it"));
  }

  private List<String> proposed(String text) {
    List<String> missing = new ArrayList<>();
    atLeast(missing, text, REASON_LENGTH, Form.CANDIDATE + ": what the rule would detect and what it would tell");
    if (references.in(text).isEmpty()) {
      missing.add(Form.CANDIDATE + ": an example of what the rule would catch, as path:line");
    }
    return missing;
  }

  private List<String> declined(String text) {
    List<String> missing = new ArrayList<>();
    atLeast(missing, text, REASON_LENGTH, Form.CANDIDATE + ": why no deterministic rule could catch this");
    return missing;
  }

  private List<String> repeated(String text) {
    String id = text.lines().findFirst().orElse("").strip().substring("same-as:".length()).strip();
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
