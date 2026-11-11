package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Family;
import com.fpetrola.cerco.project.Location;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.project.TypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What asking a value its type may hide, and at one place which of them: a kind of a family of the project asked
 * from outside (an operation the family lacks, or a visitor), a thing of the project that arrives as less than it
 * is, a role, or a type that is not the project's at all.
 */
final class TypeTestDiagnosis {
  private final Clues clues = new Clues();
  private final Hypothesis method = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the tested type is a kind of a family of the project, asked in one place",
      "a method on the common type, implemented by each subtype");
  private final Hypothesis visitor = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the family is asked in several places, already accepts a visitor, or cannot depend on the module that asks",
      "a visitor the family accepts: each place that asks becomes one visitor");
  private final Hypothesis typed = new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
      "the value is the project's but arrives as less than it is: picked out of a mixed collection, or declared wider",
      "whoever holds it keeps it typed, or hands out the typed view");
  private final Hypothesis role = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "the test stands for a capability rather than a kind",
      "a method every element answers, with the empty answer in the common type");
  private final Hypothesis foreign = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "the tested type is a library's, so no method can be added to it",
      "a type of the project for what arrives, given where it comes in, once; the rest asks that type");
  private final Hypothesis untyped = new Hypothesis(MissingModel.VALUE_WITHOUT_A_TYPE,
      "the value travels as Object and is cast back where it is used",
      "the type it has, declared all the way: a parameter per type, a typed setting, a generic holder");

  List<Hypothesis> hypotheses() {
    return List.of(method, visitor, typed, role, foreign, untyped);
  }

  /** The members outside each family that test its types, by the family's top: how many places ask it. */
  Map<String, Set<String>> askers(List<TypeTest> tests, Project project) {
    Map<String, Set<String>> askers = new HashMap<>();
    for (TypeTest test : tests) {
      test.tested().filter(project::declares).map(TypeName::simple).map(project::family)
          .filter(test::outside)
          .ifPresent(family -> askers.computeIfAbsent(family.root(), k -> new TreeSet<>()).add(test.member()));
    }
    return askers;
  }

  /** Which hypothesis the code at one test shows, when the project can tell. */
  Optional<Reading> read(TypeTest test, Project project, Map<String, Set<String>> askers) {
    return test.tested().filter(tested -> !test.mute())
        .flatMap(tested -> new At(test, tested, project, askers).reading());
  }

  /** One test read against the project: each clue speaks or stays silent, and the first to speak is the reading. */
  private final class At {
    private final TypeTest test;
    private final TypeName name;
    private final String tested;
    private final Project project;
    private final Family family;
    private final int asking;

    At(TypeTest test, TypeName name, Project project, Map<String, Set<String>> askers) {
      this.test = test;
      this.name = name;
      this.tested = name.simple();
      this.project = project;
      this.family = project.family(tested);
      this.asking = askers.getOrDefault(family.root(), Set.of()).size();
    }

    Optional<Reading> reading() {
      if (!project.declares(name)) {
        return carried().or(() -> Optional.of(foreign.because("`" + tested + "` is not a type of this project")))
            .filter(reading -> project.isWhole());
      }
      return clues.first(this::self, this::picked, this::lookedUp, this::wider, this::own, this::played, this::narrowed,
          this::visited, this::elsewhere, this::around).or(() -> Optional.of(method.because(kind())));
    }

    private Optional<Reading> carried() {
      return test.subject().declared().filter(declared -> declared.simple().equals("Object"))
          .map(declared -> untyped.because("`" + tested + "` is cast back from `" + test.subject().text()
              + "`, which travels as `Object`"));
    }

    private Optional<Reading> self() {
      return Optional.of(method.because("`this` is asked which subtype it is"))
          .filter(reading -> test.subject().self());
    }

    private Optional<Reading> picked() {
      long kinds = test.alongside().stream().filter(family::holds).count();
      return test.pickedFrom().map(from -> kinds > 1
          ? visitor.because(kinds + " kinds of `" + family.root() + "` are told apart while walking `" + from + "`")
          : typed.because("it picks `" + tested + "` out of `" + from + "`"));
    }

    private Optional<Reading> lookedUp() {
      return test.subject().lookedUpBy().map(call -> typed.because("`" + tested + "` is fetched by a class with `"
          + call + "` and cast back"));
    }

    /** The value is held as a type that is not the project's: before anything else, it arrives as less than it is. */
    private Optional<Reading> wider() {
      return test.subject().declared().filter(declared -> !project.declares(declared))
          .map(declared -> typed.because("`" + tested + "` arrives as `" + declared.simple() + "`"));
    }

    private Optional<Reading> own() {
      return Optional.of(method.because("`" + tested + "` is asked about inside its own family, `" + family.root()
          + "`")).filter(reading -> family.holds(test.inside()) && !family.root().equals(tested));
    }

    /** An interface that is not a kind of what the value is: a role some of them play, or that outsiders play. */
    private Optional<Reading> played() {
      return clues.first(this::acrossFamilies, this::ofOutsiders)
          .filter(reading -> project.declared(tested).filter(Declared::isInterface).isPresent());
    }

    /** The value is declared as a type of the project that the tested one is neither above nor below. */
    private Optional<Reading> acrossFamilies() {
      return test.subject().declared().filter(project::declares).map(TypeName::simple)
          .filter(declared -> !project.below(declared).contains(tested) && !project.below(tested).contains(declared))
          .map(declared -> role.because("`" + tested + "` is not a kind of `" + declared
              + "`: a role some of them play"));
    }

    /** The tested type tops no family of its own: nobody here implements it, or those who do belong elsewhere. */
    private Optional<Reading> ofOutsiders() {
      Set<String> implementors = project.directlyBelow(tested);
      boolean elsewhere = implementors.stream()
          .anyMatch(implementor -> !project.family(implementor).root().equals(tested));
      String how = implementors.isEmpty() ? "nothing in this project implements `" + tested
          + "`: a role played from outside" : "the types that implement `" + tested
          + "` belong to other families: a role";
      return Optional.of(role.because(how))
          .filter(reading -> family.root().equals(tested) && (implementors.isEmpty() || elsewhere));
    }

    /** Nothing of the project is above the tested type: the test brings the value into the family, not across it. */
    private Optional<Reading> narrowed() {
      String how = family.subtypes() == 0 ? "`" + tested + "` has no family in this project"
          : "`" + tested + "` is the top of its family";
      return Optional.of(typed.because(how + ": what is tested arrives as less than it is"))
          .filter(reading -> family.root().equals(tested));
    }

    private Optional<Reading> visited() {
      return project.visitorsOf(family).stream().findFirst().map(accepted -> visitor.because(kind()
          + ", which already accepts `" + accepted + "`"));
    }

    private Optional<Reading> elsewhere() {
      String here = new Location(test.finding().file()).module();
      return project.declared(family.root()).map(Declared::module).filter(home -> !home.equals(here))
          .map(home -> visitor.because(kind() + ", in `" + home + "`, which cannot depend on `" + here + "`"));
    }

    private Optional<Reading> around() {
      return Optional.of(visitor.because(kind() + ", asked in " + asking + " members"))
          .filter(reading -> asking > 1);
    }

    private String kind() {
      return "`" + tested + "` is a kind of `" + family.root() + "` (" + family.subtypes() + " subtypes)";
    }
  }
}
