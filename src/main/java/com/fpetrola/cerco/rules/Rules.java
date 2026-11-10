package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Settings;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The catalogue: every rule the gate knows, in the order it reports them, minus the ones a project switched off. */
public final class Rules {
  private final Settings settings;

  public Rules(Settings settings) {
    this.settings = settings;
  }

  public List<Rule> all() {
    return every().stream().filter(rule -> !settings.disabledRules().contains(rule.id())).toList();
  }

  public Set<String> ids() {
    return all().stream().map(Rule::id).collect(Collectors.toSet());
  }

  /** Every rule this cerco has, switched off or not, candidates included: a rule an autopsy asked for is owed no more. */
  public Set<String> known() {
    return Stream.concat(every().stream(), candidates().stream()).map(Rule::id).collect(Collectors.toSet());
  }

  /**
   * Rules being measured before they gate anything: `mvn cerco:survey` runs them over the reactor, and they join the
   * catalogue only after their record (doc/reglas) shows they find real cases beyond the one that gave them birth.
   */
  public List<Rule> candidates() {
    return List.of(new AbsenceAsState(), new HierarchyAskedFromOutside(), new SetFromOutside(), new ReachedThrough(),
        new WalkedFromOutside(), new EnumDecidedAcross(), new FlagArgument(), new StateAskedAcrossMethods(),
        new MapsKeyedAlike(), new UnpackedAtTheCall(), new NamedByText(), new SaidInTwoCalls(),
        new ConditionRepeated());
  }

  private List<Rule> every() {
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
}
