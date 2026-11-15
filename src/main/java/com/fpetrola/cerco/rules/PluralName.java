package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A type named in plural that holds or hands out many of something: Windows, Keys, MenuTitles. The name says the
 * concept in singular is missing; the type ends up keeping a collection by index, and the element, with its own
 * behaviour, never becomes an object. The name alone is not enough: it has to keep many (a collection or an array),
 * hand them out, take a position (an int), or be an enum, whose every value is one thing.
 */
public final class PluralName extends FoundInPlaceRule {
  private final IntType intType = new IntType();
  private final java.util.Optional<java.nio.file.Path> frozen;

  /** The rule with what the baseline in this directory (.cerco/baseline) froze of it, if a project has one. */
  public PluralName(java.util.Optional<java.nio.file.Path> baseline) {
    this.frozen = baseline.map(directory -> directory.resolve("plural-name.txt"));
  }

  /** The rule with nothing frozen: what a survey or a test measures. */
  public PluralName() {
    this(java.util.Optional.empty());
  }

  /**
   * A frozen plural name is settled by its name, not by its signals: one that lost them (an int, a collection) while
   * keeping the plural stays owed. That is what an autopsy asked for as frozen-name-kept.
   */
  @Override
  public java.util.Set<String> alsoAnswers() {
    return java.util.Set.of("frozen-name-kept");
  }
  private static final Pattern LAST_WORD = Pattern.compile("[A-Z][a-z0-9]*$");
  private static final Pattern MANY = Pattern.compile(
      "(.*\\[\\]|(java\\.util\\.)?(List|Set|Map|Collection|Iterable|Stream|Deque|Queue|SortedMap|SortedSet|"
          + "NavigableMap|NavigableSet)(<.*>)?)");
  private static final Set<String> SINGULAR = Set.of("Status", "Canvas", "Alias", "Bias", "Atlas", "Lens", "Bus",
      "News", "Series", "Species", "Means", "Physics", "Always", "Its", "Was", "Has", "Does", "Is", "Thus", "Plus",
      "Minus", "Focus", "Corpus", "Radius", "Census", "Chaos", "Gas", "Yes", "Analysis", "Axis", "Basis", "Ellipsis");

  @Override
  public String id() {
    return "plural-name";
  }

  @Override
  public String principle() {
    return "each thing of the domain is an object: a name in plural says the one it is made of is missing";
  }

  @Override
  public String description() {
    return "a class, interface, enum or record named in plural that keeps many, hands them out, takes a position, "
        + "or is an enum: the element in singular has no type, so the collection is run by index from outside it";
  }

  @Override
  public String remedy() {
    return "find the concept in singular (a title on the bar, a window, a key) and make it an object with its own "
        + "behaviour; the collection walks its elements (a visitor) instead of answering by index; name the whole by "
        + "its role (bar, stack, group, style) or, for an enum, in singular: each value is one. Renaming without "
        + "that redesign is not a fix";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("the same type renamed to a role or a singular while its methods still go and come with an "
        + "int: the name changed, the missing object did not appear",
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "methods take or answer a position, and parallel lists keep the parts of each element",
            "one object per element, which knows what the index used to look up"));
  }

  @Override
  protected List<Finding> sites(ParsedFile file, CompilationUnit unit) {
    List<Finding> found = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      String name = type.getNameAsString();
      if (plural(name)) {
        List<String> signals = signals(type);
        if (signals.isEmpty() && frozenKeys().contains(file.name() + "|" + name)) {
          signals.add("was frozen so: it left no baseline while the name stays");
        }
        if (!signals.isEmpty()) {
          found.add(finding(file, type, name, name + " is named in plural and " + String.join(", ", signals)
              + ": which one, in singular, is missing as an object? Redesign it (an object per element, walked by "
              + "the collection; a name for the role of the whole; an enum in singular); a rename alone is no fix"));
        }
      }
    }
    return found;
  }

  /** The keys of this rule the baseline froze: lines `count<TAB>key`. */
  private Set<String> frozenKeys() {
    return frozen.filter(java.nio.file.Files::isRegularFile).map(file -> {
      try {
        return java.nio.file.Files.readAllLines(file).stream().filter(line -> line.contains("\t"))
            .map(line -> line.substring(line.indexOf('\t') + 1)).collect(java.util.stream.Collectors.toSet());
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException("could not read " + file, e);
      }
    }).orElse(Set.of());
  }

  /** Whether the last word of the name is in plural. */
  boolean plural(String name) {
    java.util.regex.Matcher last = LAST_WORD.matcher(name);
    if (!last.find()) {
      return false;
    }
    String word = last.group();
    return word.length() > 2 && word.endsWith("s") && !word.matches(".*(ss|us|is)") && !SINGULAR.contains(word);
  }

  /** What shows the type keeps many: an enum's values, collections it keeps or hands out, a position it takes. */
  private List<String> signals(TypeDeclaration<?> type) {
    List<String> signals = new ArrayList<>();
    if (type.isEnumDeclaration()) {
      signals.add("is an enum, whose every value is one");
    }
    if (type.getFields().stream().map(FieldDeclaration::getElementType).anyMatch(this::many)
        || type.getFields().stream().anyMatch(field -> field.getVariables().stream()
            .anyMatch(variable -> many(variable.getType())))) {
      signals.add("keeps a collection");
    }
    List<MethodDeclaration> open = type.getMethods().stream().filter(method -> !method.isPrivate()
        && !method.isAnnotationPresent("Override")).toList();
    if (open.stream().anyMatch(method -> many(method.getType()))) {
      signals.add("hands one out");
    }
    long positions = open.stream().filter(method -> method.getParameters().stream()
        .anyMatch(parameter -> intType.writes(parameter.getType()))).count();
    if (positions > 0) {
      signals.add(positions + " method(s) take an int");
    }
    return signals;
  }

  private boolean many(Type type) {
    return MANY.matcher(type.asString()).matches();
  }
}
