package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.project.TypeName;
import com.fpetrola.cerco.rules.Decision.Case;
import com.fpetrola.cerco.rules.Decision.Effect;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What deciding over a set of values may hide, and at one place which of them: what each case means or does living
 * outside the cases, a state kept as a value, an absence kept as null, a value with no type, types told apart, or
 * kinds that are a library's.
 */
final class DecisionDiagnosis {
  private final Clues clues = new Clues();
  private final Hypothesis answered = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "every case only answers a value, or the place asks whether the value is one of some",
      "a field or a method on the enum or type, answered by each case");
  private final Hypothesis behaved = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "the cases do different work, and the value is fixed for the object's life",
      "subtypes, or a body per constant: each case becomes the one that does its work");
  private final Hypothesis state = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the branches reassign the value they test",
      "a state object per value, each knowing what comes next");
  private final Hypothesis absent = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the arms test whether something is there",
      "a null object, or a mandatory collaborator");
  private final Hypothesis untyped = new Hypothesis(MissingModel.VALUE_WITHOUT_A_TYPE,
      "the cases are strings, numbers or loose constants",
      "a type or an enum for the value, owning what the cases decide");
  private final Hypothesis kinds = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the cases are types",
      "a method on the common type, or a visitor the family accepts");
  private final Hypothesis foreign = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "the kinds or the constants are a library's, so no behaviour can be added to them",
      "a type of the project at the boundary, made once from the library's, and the decision on that");

  List<Hypothesis> hypotheses() {
    return List.of(answered, behaved, state, absent, untyped, kinds, foreign);
  }

  /** Which hypothesis the code at one decision shows, when it can be told. */
  Optional<Reading> read(Decision decision, Project project) {
    return new At(decision, project).reading();
  }

  /** One decision read against the project. */
  private final class At {
    private final Decision decision;
    private final Project project;
    private final Set<Case> cases;
    private final String selector;

    At(Decision decision, Project project) {
      this.decision = decision;
      this.project = project;
      this.cases = decision.cases();
      this.selector = decision.selector().text();
    }

    Optional<Reading> reading() {
      return clues.first(this::types, this::reassigned, this::absence, this::foreignCode, this::loose, this::ofAnEnum,
          this::looseConstants);
    }

    private String decided() {
      return selector.isEmpty() ? "" : " over `" + selector + "`";
    }

    private String comparedWith() {
      return selector.isEmpty() ? "" : ", compared with `" + selector + "`";
    }

    private Optional<Reading> types() {
      return Optional.of(kinds.because("the cases are types, told apart" + decided()))
          .filter(reading -> cases.contains(Case.TYPE));
    }

    private Optional<Reading> reassigned() {
      return Optional.of(state.because("the branches reassign `" + selector + "`, the value they test"))
          .filter(reading -> decision.effect() == Effect.REASSIGN);
    }

    private Optional<Reading> absence() {
      return Optional.of(absent.because(selector.isEmpty() ? "every arm tests whether something is there"
          : "every arm tests whether `" + selector + "` is there")).filter(reading -> cases.equals(Set.of(Case.ABSENT)));
    }

    /** A type code asked of an object with a method the project does not declare: the kinds are a library's. */
    private Optional<Reading> foreignCode() {
      return decision.selector().asked().filter(Decision.Asked::called).map(Decision.Asked::member)
          .filter(method -> project.isWhole() && project.declaring(method, 0).isEmpty())
          .map(method -> foreign.because("`" + method + "()` is not a method of this project: what it answers is a "
              + "library's"));
    }

    private Optional<Reading> loose() {
      boolean plain = cases.contains(Case.TEXT) || cases.contains(Case.NUMBER);
      String what = cases.contains(Case.TEXT) ? "texts" : "numbers";
      return Optional.of(untyped.because("the cases are " + what + comparedWith()))
          .filter(reading -> plain && EnumSet.of(Case.TEXT, Case.NUMBER, Case.ABSENT).containsAll(cases));
    }

    /**
     * The enum of the project the cases name: the one the selector was declared as, the one its owner declares it
     * as, or of those that have every constant named, the smallest.
     */
    private Optional<Declared> owner() {
      Optional<String> declared = decision.selector().declared().filter(project::declares).map(TypeName::simple);
      Optional<String> answered = decision.selector().asked().flatMap(asked -> asked.type().filter(project::declares)
          .map(TypeName::simple).flatMap(project::declared)
          .flatMap(owner -> owner.field(asked.member()).or(() -> owner.answer(asked.member()))));
      List<Declared> having = decision.names().isEmpty() ? List.of() : project.enumsWith(decision.names());
      return declared.or(() -> answered).flatMap(project::declared).filter(Declared::isEnum)
          .or(() -> having.stream().min(Comparator.comparingInt((Declared one) -> one.constants().size())));
    }

    private Optional<Reading> ofAnEnum() {
      return owner().filter(owner -> cases.contains(Case.NAMED)
              && EnumSet.of(Case.NAMED, Case.ABSENT).containsAll(cases))
          .map(owner -> decision.effect() == Effect.ACT
              ? behaved.because(told(owner, "the cases do different work per constant of `" + owner.name() + "`"))
              : answered.because(told(owner, decision.effect() == Effect.ANSWER
                  ? "every case only answers a value per constant of `" + owner.name() + "`"
                  : "it asks whether `" + selector + "` is one of some constants of `" + owner.name() + "`")));
    }

    private String told(Declared owner, String what) {
      String whose = decision.inside().equals(owner.name()) ? "`" + owner.name() + "` decides over its own constants: "
          : "";
      return whose + what + decision.selector().asked().map(asked -> ", asked of `" + asked.of() + "`").orElse("");
    }

    /** Names written as constants that no enum of the project has: plain constants of its own, or a library's. */
    private Optional<Reading> looseConstants() {
      String named = decision.names().stream().limit(3).map(name -> "`" + name + "`")
          .collect(Collectors.joining(", "));
      boolean ours = decision.names().stream().anyMatch(project::declaresField);
      return Optional.of(ours ? untyped.because("the cases are constants (" + named + ") of no enum: plain values "
              + "with names") : foreign.because("the constants (" + named + ") are not this project's"))
          .filter(reading -> project.isWhole() && cases.equals(Set.of(Case.NAMED)) && !decision.names().isEmpty());
    }
  }
}
