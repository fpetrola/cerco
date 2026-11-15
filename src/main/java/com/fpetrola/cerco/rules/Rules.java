package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.surface.Reach;
import com.fpetrola.cerco.surface.Seal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The catalogue: every rule the gate knows, in the order it reports them, minus the ones a project switched off. */
public final class Rules {
  private final Settings settings;
  private final List<Rule> signedSurface;
  private final java.util.Optional<Path> baseline;

  /** The catalogue with nothing sealed: what a survey or a test measures. */
  public Rules(Settings settings) {
    this.settings = settings;
    this.signedSurface = List.of();
    this.baseline = java.util.Optional.empty();
  }

  /** The catalogue of a project at this root, its sealed surface included when its configuration seals one. */
  public Rules(Settings settings, Path root) {
    this.settings = settings;
    this.signedSurface = settings.sealedSurface().flatMap(Reach::from).stream()
        .flatMap(reach -> surface(root, reach).stream()).toList();
    this.baseline = java.util.Optional.of(root.resolve(".cerco").resolve("baseline"));
  }

  /** With the surface sealed, what a module offers is signed, and what a class offers is promised by interfaces. */
  private List<Rule> surface(Path root, Reach reach) {
    Seal seal = new Seal(root.resolve(".cerco").resolve("surface"));
    return List.of(new SealedSurface(seal, reach), new UnpromisedMember(seal, reach));
  }

  public List<Rule> all() {
    return every().stream().filter(rule -> !settings.disabledRules().contains(rule.id())).toList();
  }

  /** The rules a change to one file can only move findings of in that file: the ones a single edit is run on. */
  public List<Rule> perFile() {
    return all().stream().filter(Rule::perFile).toList();
  }

  /** The ids of the rules whose findings depend on the whole project. */
  public Set<String> projectWide() {
    return all().stream().filter(rule -> !rule.perFile()).map(Rule::id).collect(Collectors.toSet());
  }

  /** Every active rule in turn, in the order the gate reports them. */
  public void each(Consumer<Rule> told) {
    all().forEach(told);
  }

  /** The rules to measure: those named, every one when `all` is named, the candidates when none is. */
  public List<Rule> toSurvey(List<String> named) {
    return named.isEmpty() ? candidates() : Stream.concat(all().stream(), candidates().stream())
        .filter(rule -> named.contains(rule.id()) || named.contains("all")).toList();
  }

  public Set<String> ids() {
    return all().stream().map(Rule::id).collect(Collectors.toSet());
  }

  /** Every rule this cerco has, switched off or not, candidates included: a rule an autopsy asked for is owed no more. */
  public Set<String> known() {
    return Stream.concat(every().stream(), candidates().stream())
        .flatMap(rule -> Stream.concat(Stream.of(rule.id()), rule.alsoAnswers().stream())).collect(Collectors.toSet());
  }

  /**
   * Rules being measured before they gate anything: `mvn cerco:survey` runs them over the reactor, and they join the
   * catalogue only after their record (doc/reglas) shows they find real cases beyond the one that gave them birth.
   */
  public List<Rule> candidates() {
    return List.of(new ConstantArgument(), new StaticFilledByInstances(), new UnusedMember(),
        new HierarchyAskedFromOutside(), new SetFromOutside(),
        new UnpackedAtTheCall(), new SaidInTwoCalls(), new OneAndMany(), new ToldOneByOne(),
        new OverriddenToNothing(), new MiddleMan(), new DispatchByKey(), new WalkedByTheVisitor());
  }

  private List<Rule> every() {
    return Stream.of(first(), fromThePlanOfModelSmells(), signedSurface).flatMap(List::stream).toList();
  }

  /** The rules cerco started with and those its autopsies asked for. */
  private List<Rule> first() {
    return List.of(
        new Unparseable(),
        new PackageCycles(),
        new MutableStaticField(),
        new PublicMutableField(),
        new InstanceOfDispatch(),
        new TypeCodeDispatch(),
        new CastDispatch(),
        new TypeKeyedMap(),
        new SwitchDispatch(),
        new ConditionalChain(),
        new NonPrivateStaticMethod(settings.factoryPrefixes()),
        new UtilityClassName(settings.utilitySuffixes()),
        new ConstantHolder(),
        new SwallowedException(),
        new IgnoredResult(),
        new NullReturn(),
        new SuppressedWarning(),
        new RepeatedStatements(),
        new ClonedMethods(),
        new FlagDrivenFromOutside(),
        new AskThenTell(),
        new PropertyShuffle(),
        new NotifiedByCaller(),
        new BorrowedMode(),
        new RefilteredByType(),
        new KeyHoldsValue(),
        new EnumDecidedOutside(),
        new FileLength(settings.fileCeiling()),
        new MethodLength(settings.methodCeiling()));
  }

  /** The rules that were candidates of the plan of model smells, measured and then adopted. */
  private List<Rule> fromThePlanOfModelSmells() {
    return List.of(new FlagArgument(), new MapsKeyedAlike(), new NamedByText(), new HandedOutWhole(),
        new AbsenceAsState(), new ReachedThrough(), new WalkedFromOutside(), new EnumDecidedAcross(),
        new KeptTogether(), new DataWithoutBehaviour(), new StateAskedAcrossMethods(),
        new ConditionRepeated(), new VisitorHandedParts(), new AcceptedManyWays(),
        new PluralName(baseline), new KeptByIndex());
  }
}
