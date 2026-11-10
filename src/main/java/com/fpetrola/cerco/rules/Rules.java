package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Settings;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The catalogue: every rule the gate knows, in the order it reports them, minus the ones a project switched off. */
public final class Rules {
  private final Settings settings;

  public Rules(Settings settings) {
    this.settings = settings;
  }

  public List<Rule> all() {
    List<Rule> every = List.of(
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
        new FileLength(settings.fileCeiling()),
        new MethodLength(settings.methodCeiling()));
    return every.stream().filter(rule -> !settings.disabledRules().contains(rule.id())).toList();
  }

  public Set<String> ids() {
    return all().stream().map(Rule::id).collect(Collectors.toSet());
  }
}
