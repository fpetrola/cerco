# Static-analysis tools for Java: design-level (OO modelling) rules and how they detect

Date of research: 2026-11-10. "Opened" = I read the official page or the source code in this session. Anything else is labelled UNVERIFIED. Sources cloned shallowly at HEAD of 2026-11-10 (sonar-java, codeql, intellij-community) and DesigniteJava master (last commit 2025-03-21).

## 1. DesigniteJava and JDeodorant (what exactly is detected, and how)

### Takeaway
DesigniteJava's open-source edition detects 17 design smells with plain metric/graph conditions over a JDT-resolved model; its two "missing model" smells are tiny: Missing Hierarchy = a single method that tests with instanceof two or more types outside the class's own ancestors; Unexploited Encapsulation = a single method that tests with instanceof two types of the same hierarchy. JDeodorant is the only tool that explicitly targets "Type/State Checking" (switch/if-chains on a type code or instanceof -> polymorphism or State/Strategy), but it is an Eclipse plug-in, not embeddable in a Maven build.

### Cited Findings
- Licence Apache-2.0, repo public, runs as `java -jar Designite.jar -i src -o out`, pom version 1.1.2, last commit on master 2025-03-21 (a merged PR); GitHub Releases section empty, jar distributed from the Designite site — [DesigniteJava repo](https://github.com/tushartushar/DesigniteJava) (opened README + cloned source). Maintenance: low but not dead (UNVERIFIED whether more recent activity exists on forks).
- Smell inventory (README): 17 design smells (Imperative, Multifaceted, Unnecessary, Unutilized Abstraction; Deficient, Unexploited Encapsulation; Broken, Cyclic-Dependent, Insufficient, Hub-like Modularization; Broken, Cyclic, Deep, Missing, Multipath, Rebellious, Wide Hierarchy) and 10 implementation smells (Abstract Function Call From Constructor, Complex Conditional, Complex Method, Empty Catch Clause, Long Identifier, Long Method, Long Parameter List, Long Statement, Magic Number, Missing Default). The README lists NO architecture smells and NO test smells for this edition — [README](https://github.com/tushartushar/DesigniteJava). (Architecture smells exist in the commercial Designite products: UNVERIFIED, not opened.)
- Exact detection conditions, read from source (`smells/designSmells/*SmellDetector.java`, `smells/ThresholdsDTO.java`), all over a model built with Eclipse JDT with resolved bindings (SM_Type, SM_Method, hierarchy graph, dependency graph):
  - Imperative Abstraction: exactly 1 public method AND that method has > 50 lines.
  - Multifaceted Abstraction: LCOM >= 0.8 AND fields >= 7 AND methods >= 7.
  - Unnecessary Abstraction: 0 methods AND fields <= 5.
  - Unutilized Abstraction: type (and, if it has supertypes, every supertype) has fan-in of 0 types.
  - Deficient Encapsulation: number of public fields > 0.
  - **Unexploited Encapsulation: some method of the class contains instanceof tests for two different types that lie in the same connected component of the project's hierarchy graph** (i.e. type-switching among siblings/ancestors instead of polymorphism).
  - Broken Modularization: 0 methods AND fields >= 5 (a pure data bag).
  - Cyclic-Dependent Modularization: type belongs to a strongly connected component of size > 1 in the type dependency graph.
  - Insufficient Modularization: public methods >= 20 OR methods >= 30 OR WMC >= 100.
  - Hub-like Modularization: fan-in >= 20 AND fan-out >= 20.
  - Broken Hierarchy: has supertypes and public methods, and for some supertype no public method of the type shares a NAME with a public/abstract/interface method of that supertype (name-only match).
  - Cyclic Hierarchy: a type is its own ancestor (recursive walk).
  - Deep Hierarchy: inheritance depth > 6. Wide Hierarchy: number of children > 10.
  - **Missing Hierarchy: some method of the class has instanceof tests for >= 2 types that are NOT among the class's own ancestors** (`INSTANCE_OF_TYPES_NOT_IN_HIERARCHY_THRESHOLD = 2`; i.e. the class dispatches on foreign types that should share a supertype).
  - Multipath Hierarchy: two supertypes where one is an ancestor of the other (diamond-ish redundant inheritance).
  - Rebellious Hierarchy: a method overrides a supertype method (same name and parameter types) and its body is empty (0 lines) or only a throw (1 line).
  - Implementation thresholds: complex conditional 3 operators, complex method CC 8, long identifier 30, long method 100 lines, long parameter list 5, long statement 120 chars.
  - Thresholds are configurable via a thresholds file (ThresholdsParser).
- JDeodorant: licence MIT, Eclipse plug-in, README states five smells: Feature Envy (Move Method), Type/State Checking (Type Checking -> Replace Conditional with Polymorphism; State Checking -> Replace Type Code with State/Strategy), Long Method (Extract Method), God Class (Extract Class), Duplicated Code (Extract Clone). Papers: Type/State Checking = Tsantalis & Chatzigeorgiou, JSS 2010 "Identification of refactoring opportunities introducing polymorphism"; Feature Envy = TSE 2009 (Move Method). Repo: 1,144 commits on master, no release shown — [JDeodorant repo](https://github.com/tsantalis/JDeodorant) (opened README).

### Inferences
- Both "Missing Hierarchy" and "Unexploited Encapsulation" are per-method set-of-instanceof-types tests; they do NOT look across methods or classes ("same family tested in several places" is not detected by Designite).
- Designite needs JDT binding resolution (type hierarchy, fan-in), so it cannot be ported as single-file AST rules; but the two instanceof smells need only: collect instanceof types per method + supertype closure, which a JavaParser rule with a symbol solver or an index of the project's `extends/implements` could replicate.
- JDeodorant's Type Checking conditions (JSS 2010 paper) were NOT opened by me; its exact algorithm (UNVERIFIED) is described in the paper as identifying if/else-if or switch chains over a type field/instanceof with the branches' state-dependent code.

### Gaps
- Exact JDeodorant Type Checking detection conditions and whether the project is still released: not verified (no release listed, only the repo page opened).
- Designite commercial architecture-smell definitions: not opened.

## 2. PMD (7.30.0-SNAPSHOT docs) and CPD

### Takeaway
PMD's design category is dominated by size/complexity/coupling counters; only LawOfDemeter, GodClass, DataClass, SwitchDensity, InstantiableUtilityClass and a few XPath rules touch modelling. All are single-project Java-API or XPath rules over the PMD AST with (optional) type resolution; PMD is Apache-2.0 and embeddable via Maven (maven-pmd-plugin / the PMD Java API).

### Cited Findings
- 45 design rules; detection "XPath" or "Java" class — [PMD design rules](https://pmd.github.io/pmd/pmd_rules_java_design.html) (opened). Relevant rules with default thresholds:
  - LawOfDemeter: trustRadius = 1; Java rule. Degree metric: `this` = 0; method parameters, objects created in the method and static variables = 1; each field access or getter call adds 1 to the degree of the expression it is applied to; transformation calls (`withFoo`) keep the same degree. An expression of degree exactly trustRadius+1 is reported, except when only returned or passed as argument; higher degrees are not reported (they depend on an already reported one). Documented as prone to false positives. Example: `b.getC().doIt()` on parameter `b`.
  - GodClass: high WMC, high ATFD, low TCC (Lanza & Marinescu 2006); page gives no numbers (Java rule `GodClassRule`). Numeric defaults (WMC >= 47, ATFD > 5, TCC < 1/3) are UNVERIFIED here (remembered from the PMD source, not opened).
  - DataClass: high NOPA+NOAM, low WOC, low WMC; no numbers on the page.
  - SwitchDensity: minimum = 10 (statements per label ratio).
  - TooManyFields (XPath): maxfields = 15 (non-final non-static fields). TooManyMethods (XPath): maxmethods = 10 (excluding getters/setters/is-methods).
  - CouplingBetweenObjects: threshold = 20 (unique attribute, local-variable and return types). ExcessiveImports: minimum = 30. ExcessivePublicCount: minimum = 45. ExcessiveParameterList: minimum = 10. NcssCount: method 60, class 1500. CyclomaticComplexity: method 10, class 80. NPathComplexity: 200. CognitiveComplexity: reportLevel 15. AvoidDeeplyNestedIfStmts: problemDepth 3.
  - LoosePackageCoupling: needs configured `packages`/`classes` (no default; inactive otherwise).
  - InstantiableUtilityClass (formerly UseUtilityClass, renamed in 7.27.0): classes with only static members that can be instantiated. UseObjectForClearerAPI: more than 3 String parameters; deprecated since 7.26.0, removed in 8.0.0. AbstractClassWithoutAnyMethod (XPath): abstract class with no methods. SingularField: private field always assigned before read -> could be local. SimplifyBooleanReturns: if/else returning boolean literals. SimplifyConditional: redundant null check with instanceof. ImmutableField, MutableStaticState (non-private non-final static), ClassWithOnlyPrivateConstructorsShouldBeFinal, FinalFieldCouldBeStatic, PublicMemberInNonPublicType, InvalidJavaBean.
- CPD: Karp-Rabin token matching; `--minimum-tokens` required (examples use 100, no default stated on the page); options `--ignore-identifiers`, `--ignore-literals`, `--ignore-annotations` all off by default; Java supported; runnable via Maven PMD Plugin goal `cpd-check`, CLI, Ant — [CPD docs](https://docs.pmd-code.org/latest/pmd_userdocs_cpd.html) (opened).

### Inferences
- PMD has no rule for instanceof chains in the design category (UNVERIFIED for other categories; grep of the design page shows none). SwitchDensity is the only switch-shape rule; it is a size ratio, not a type-code detector.
- Licence of PMD (BSD-style/Apache) not opened here: UNVERIFIED (commonly BSD-4-clause-like "BSD"). Embeddable in JVM builds is confirmed by the Maven goal named in the CPD docs.

### Gaps
- GodClass/DataClass numeric defaults (not on the page); PMD best-practices category not opened.

## 3. SonarJava (rule metadata from the sonar-java repository, HEAD 2026-11-10)

### Takeaway
Sonar's design-flavoured rules are counters and local-structure rules: coupling (S1200, S6539), size (S104, S138, S1448, S1820, S110), identical implementations/branches (S4144, S1871), duplicated literals (S1192), selector boolean parameters (S2301), Singleton (S6548). There is no instanceof-chain rule (only S6880 if-else chain -> switch and S1193 / S4201 / S6201 which are about instanceof mechanics), and no feature-envy rule.

### Cited Findings
- Source: rule metadata under `sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/*.json|html` and check classes in `java-checks/src/main/java/org/sonar/java/checks` of [sonar-java](https://github.com/SonarSource/sonar-java) (opened; cloned). Thresholds from `DEFAULT_*` constants in the check classes:
  - S107 Methods should not have too many parameters (7). S104 File too many lines (750 in the check; the metadata title says "too many lines of code"). S1448 Classes too many methods (35). S1820 Classes too many fields (title only; default value not extracted, UNVERIFIED). S110 Inheritance tree too deep (max depth 5). S1479 switch with too many cases (30). S1192 String literals duplicated (threshold 3 occurrences, minimal length 5). S3776 Cognitive Complexity (15). S1067 Expressions too complex (3 operators). S138 Methods too many lines (title; default UNVERIFIED). S134 nested control flow depth (title; default UNVERIFIED). S1188 anonymous classes lines (25 in `InnerClassTooManyLines`, which maps to S2972/S1188 family: mapping UNVERIFIED).
  - S1200 "Classes should not be coupled to too many other classes" and S6539 "Classes should not depend on an excessive number of classes (aka Monster Class)", tag `design`. The check `design/ClassCouplingCheck.java` has DEFAULT_MAX = 20; mapping to S1200 vs S6539 not verified. S6539 text: a class implementing too many functionalities not organised or cohesive.
  - S4144 Methods should not have identical implementations (tags confusing, duplicate). S1871 Two branches in a conditional structure should not have exactly the same implementation (tag design). S1192 tag design. S2301 Public methods should not contain selector arguments (tag design; a boolean parameter that picks one of two paths -> "separate methods should be written"). S1118 Utility classes should not have public constructors (design). S2440 Classes with only static methods should not be instantiated. S6548 The Singleton design pattern should be used with care (design). S3358 Ternary operators should not be nested. S1774 ternary operator should not be used (brain-overload). S2325 private/final methods that don't access instance data should be static. S3242 Method parameters should be declared with base types. S1319 Declarations should use Java collection interfaces. S2974 Classes without public constructors should be final (design). S5993 Constructors of abstract class should not be public (design). S9438 Private fields never reassigned should be final (design). S1694 An abstract class should have both abstract and concrete methods. S2094 Classes should not be empty. S6206 Records should be used instead of ordinary classes for immutable data. S6880 Use switch instead of if-else chain to compare a variable against multiple cases (java21). S2196 Switches should be used for sequences of simple String tests. S1301 switch should have at least 3 cases. S2386 / S1444 public static mutable fields. S2384 private mutable members stored/returned directly. S6201 pattern matching for instanceof. S1193 exception types tested with instanceof in catch. S3398 private methods called only by inner classes should be moved.
- Detection of S1871/S4144 is syntactic equivalence of trees (UNVERIFIED which algorithm: not opened); S1200/S6539 need type resolution (semantic model) since they count distinct referenced types.

### Inferences
- The Sonar licence (SSALv1 / LGPL history) was not checked: UNVERIFIED. The analyzer is not designed for embedding as a library by third parties.
- No Sonar Java rule found for: instanceof chains over a family, feature envy, data clumps, parallel collections, implicit state fields.

### Gaps
- Defaults for S1820, S138, S134 and exact rule-to-check mapping not extracted.

## 4. IntelliJ IDEA Java inspections (descriptions from intellij-community source)

### Takeaway
IntelliJ has the richest set of OO-design inspections among free tools: instanceof chains, instanceof this, concrete-class usage, class references subclass, feature envy, law of demeter, static method only used in one class, etc. Most need full PSI/type resolution and cross-file index; they run only inside the IDE (or headless IDE), not embeddable in a plain Maven build.

### Cited Findings
- Group counts on the Inspectopedia Java page: Abstraction issues 15, Class structure 31, Encapsulation 8, Inheritance issues 21, Class metrics 9, Method metrics 11, Dependency issues 6 — [Inspectopedia Java](https://www.jetbrains.com/help/inspectopedia/Java.html) (opened). Individual descriptions below come from `inspectionDescriptions/*.html` in [intellij-community](https://github.com/JetBrains/intellij-community) (opened).
- InstanceofChain ("Chain of 'instanceof' checks"): if-else chains where ALL conditions are instanceof or class-equality expressions; "failure in object-oriented design ... type-based dispatch should be done via polymorphic method calls".
- InstanceofThis: instanceof / `getClass() == X.class` applied to `this`; should be polymorphism.
- UseOfConcreteClass ("'instanceof' a concrete class" / "Cast to a concrete class" / variable of concrete class share this family): declarations using concrete classes rather than interfaces; library/system classes excluded; casts, instanceofs, locals reported by options. (Exact sub-inspection names for "Cast to a concrete class" not found in the description dir: UNVERIFIED.)
- ClassReferencesSubclass: a class contains references to one of its subclasses.
- FeatureEnvy: "a method calls methods on another class three or more times; calls to library classes, parent classes, contained or containing classes are not counted" (example: `job.beforeStart(); job.process(); job.afterProcessing();`).
- LawOfDemeter: "never call a method on an object received from another call". ChainedMethodCall: method call whose target is another call (quick-fix introduces a local).
- StaticMethodOnlyUsedInOneClass: static methods and fields used only from one other class; factory methods and anonymous-class uses ignored.
- TypeMayBeWeakened; DeclareCollectionAsInterface; OverlyStrongTypeCast; PublicMethodNotExposedInInterface; BooleanParameter ('public' method with boolean parameter, unless setter).
- ClassWithTooManyDependencies / ClassWithTooManyDependents / (Transitive variants) — project-level, "Only top-level classes are reported"; ClassCoupling (class references too many classes); MethodCoupling (method references too many classes, each counted once); ClassComplexity (sum of cyclomatic complexities); ClassInheritanceDepth; ClassNestingDepth; MethodCount; ParametersPerMethod; SwitchStatementWithTooManyBranches; SwitchStatementDensity (low label-to-statement ratio); NestedSwitchStatement; CyclicClassDependency, CyclicPackageDependency (batch "Inspect Code" only); Singleton ("complicate testing, may indicate lack of object-oriented design"); UtilityClassWithPublicConstructor; ExtendsUtilityClass; InstantiationOfUtilityClass; UtilityClassCanBeEnum; StaticInheritance (constants-only interface implementation); AbstractClassNeverImplemented; AbstractClassWithOnlyOneDirectInheritor; ClassMayBeInterface; NonFinalFieldInEnum.
- Not found as separate descriptions: "Message chain", "Overly coupled class" as named, "Field repeatedly accessed in method", "Data class" (grep of the description dirs I checked): UNVERIFIED/possibly different names or other modules.

### Inferences
- IntelliJ is the only tool found with an explicit "all branches of an if chain are instanceof" rule that also covers `getClass() ==`; CodeQL's counterpart counts only instanceof and requires > 5.

### Gaps
- Thresholds (e.g. FeatureEnvy "three calls"-only; class coupling default limits) other than the above were not extracted. Licence: Apache-2.0 for intellij-community (UNVERIFIED in this session); the inspections are not usable outside the IDE.

## 5. CodeQL Java standard queries (github/codeql HEAD 2026-11-10)

### Takeaway
CodeQL contains real graph-based "refactoring opportunity" queries (feature envy, inappropriate intimacy, hub classes) and a chained-instanceof query; all are declarative queries over a type-resolved database built by compiling the project. Not embeddable in a plain AST-only checker, and CodeQL's licence restricts use to open-source / GitHub-hosted contexts (UNVERIFIED, not opened).

### Cited Findings
- `java/chained-type-tests` ("Chain of 'instanceof' tests", precision high, recommendation): counts instanceof conditions along an if / else-if chain; reports when the count is > 5 on the chain head ("consider polymorphism or the visitor pattern") — source `Language Abuse/ChainedInstanceof.ql` in [github/codeql](https://github.com/github/codeql) (opened). Only counts if-chains, not switch.
- `java/feature-envy` (low precision): for method m and other class T: dependencyCount(m,T) = distinct members of T used by m (fields accessed + callables referenced); report when count > 2 * (members used from own class and enclosing types) AND count > 4; excludes interfaces, anonymous classes, initializers, ancestor/descendant pairs, nested types, tests, and requires T to already depend on every type that m depends on; also skips types used from many places — `Architecture/Refactoring Opportunities/FeatureEnvy.ql` (opened).
- `java/coupled-types` (Inappropriate Intimacy, low precision): two unrelated types in different compilation units, each with > 20 variable accesses plus method calls into the other (both directions) — `InappropriateIntimacy.ql` (opened).
- `java/hub-class` (low precision): source type with afferent coupling > 15 AND efferent source coupling > 15 — `HubClasses.ql` (opened).
- Also in `Architecture/Refactoring Opportunities`: DeeplyNestedClass. `Architecture/Dependencies`: `java/mutually-dependent-types`, unused Maven dependencies. `Metrics/RefTypes`: TEfferentCoupling, TAfferentCoupling, TInheritanceDepth, TLackOfCohesionCK / HS, TNumberOfFields, TNumberOfCallables, TResponse, TSpecialisationIndex, TSelfContainedness, TSizeOfAPI (metric queries, not alerts) (opened directory listing).
- Language Abuse: DubiousDowncastOfThis, DubiousTypeTestOfThis, CastThisToTypeParameter, UselessTypeTest, UselessUpcast (files listed; definitions not read). `java/internal-representation-exposure` (precision high; field of array/Collection/Map type returned or stored without copy) and `java/abstract-to-concrete-cast`, `java/constants-only-interface` (opened headers / listing).
- The query-help index page lacked feature-envy etc. (these live in the "Architecture" and "Metrics" folders, which appear not to be in the default query suites; UNVERIFIED whether they run by default).

### Inferences
- The feature-envy query is the only found tool implementing a counting definition of envy over member sets (distinct members used from another class > 2x own and > 4).

## 6. CodeScene Code Health

### Takeaway
CodeScene lists 16 smells; none has a published numeric threshold in the public docs (thresholds are in the help centre / configurable). Its MCP server gives AI agents deterministic Code Health feedback locally. The proprietary tool is not embeddable.

### Cited Findings
- Module smells: Low Cohesion (LCOM4), Brain Class (large module containing at least one Brain Method), Developer Congestion, Complex code by former contributors, Lines of Code. Function smells: Brain Method, DRY Violations (duplicated logic that changes together), Complex Method (cyclomatic), Primitive Obsession ("heavy use of built-in types ... often lacks a domain language that encapsulates validation and semantics of function arguments"), Large Method. Implementation smells: Nested Complexity, Bumpy Road (several logical chunks in one function), Complex Conditional (multiple logical operators in a branch), Large/Duplicated Assertion Blocks (tests). Config keys mentioned: `function_cyclomatic_complexity_warning`, `function_nesting_depth_warning`; rules customised via `.codescene/code-health-rules.json` — [Code Health docs](https://codescene.io/docs/guides/technical/code-health.html) (opened). Code Health is a 1-10 score — [enterprise docs](https://docs.enterprise.codescene.io/versions/4.4.13/guides/technical/code-health.html) (search snippet only).
- Not found on the docs page: String Heavy Function Arguments, Missing Arguments Abstractions, Constructor Over-Injection, Global Conditionals, Excess Function Arguments, Modularity (the page did not name them; some appear in other versions/languages of the help centre: UNVERIFIED).
- MCP server: local service exposing Code Health analysis to AI agents; AGENTS.md rules instruct agents to check before/after each refactoring; works without the paid ACE add-on; vendor claims such as "AI agents need code above 9.4" are vendor-reported with no methodology — [CodeScene MCP docs](https://codescene.io/docs/developer-tools/mcp/codescene-mcp-server.html) (search result only; page not opened).
- Change-coupling analyses: not researched (gap).

## 7. Other tools

### Takeaway
Everything else is either metrics/threshold counters, dependency-graph/cycle checks, or .NET (NDepend). None detects a missing model across several classes beyond what is above.

### Cited Findings
- ArchUnit 1.5.1: rules over imported bytecode (`ClassFileImporter`); GeneralCodingRules are only: no System.out/err, no generic exceptions, no java.util.logging, no JodaTime, no field injection; `slices().should().beFreeOfCycles()` (maxNumberToDetect 100); FreezingArchRule stores violations in a text ViolationStore (line numbers ignored by default; `freeze.refreeze` default false), later runs fail only on new violations; ArchitectureMetrics: Lakos CCD/ACD/NCCD, Martin Ca/Ce/I/A/D — [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html) (opened). Licence Apache-2.0 (UNVERIFIED, not opened). Embeddable: yes (JUnit library).
- NDepend (.NET, so not applicable to Java source; its Java sibling JArchitect is UNVERIFIED): default rule groups Object Oriented Design (Avoid interfaces too big, Base class should not use derivatives, Class shouldn't be too deep in inheritance tree, Class with no descendant should be sealed, A stateless class might be turned into a static type, Avoid the Singleton pattern, Avoid empty interfaces, ...), Code Smells (types/methods too big, too many fields/methods/parameters/overloads, Avoid types with poor cohesion), Design (Classes that are candidate to be turned into structures, Nested types should not be visible, ...) — [NDepend default rules](https://www.ndepend.com/default-rules/webframe.html) (opened first 100k chars). "Base class should not use derivatives" is a notable rule: base class referencing its subclasses (same idea as IntelliJ ClassReferencesSubclass).
- Checkstyle (checks index): Metrics ClassDataAbstractionCoupling (distinct classes instantiated), ClassFanOutComplexity, BooleanExpressionComplexity, NPathComplexity, JavaNCSS, CyclomaticComplexity; Design DesignForExtension, FinalClass, HideUtilityClassConstructor, InterfaceIsType, MutableException, ThrowsCount, InnerTypeLast, VisibilityModifier; Coding IllegalType, IllegalInstantiation, ReturnCount, NestedIfDepth, MagicNumber, DeclarationOrder; also ParameterNumber — [Checkstyle checks](https://checkstyle.org/checks.html) (opened). One-line definitions only; thresholds not extracted. Checkstyle is single-file AST (no type resolution) with LGPL (UNVERIFIED), embeddable via maven-checkstyle-plugin.
- Arcan: open-source (per the wiki/paper), analyses compiled Java, builds a dependency graph in a graph database, detects 3 architectural smells: Cycle Dependency, Unstable Dependency, Hub-Like Dependency (class-level, ignores classes that mostly call java.util); the wiki also lists God Component — [Arcan wiki](https://essere.disco.unimib.it/wiki/arcan/), [arXiv 2303.17862](https://arxiv.org/pdf/2303.17862) (search snippets; pages not opened).
- jPeek: bytecode (Javassist/ASM -> XML -> XSLT) cohesion metrics, default LCOM5, NHD, MMAC, SCOM, CAMC; Maven plugin under MIT; plugin last push December 2023 -> probably unmaintained; metrics return zero/NaN on small projects — [jPeek repo](https://github.com/cqfn/jpeek) (search snippets; not opened).
- Lanza-Marinescu detection strategies (Brain Method, Data Class, Feature Envy, God Class) are implemented by JSpIRIT (Eclipse), jQuality (JavaParser-based detector listing BrainMethod, DataClass, FeatureEnvy) and Organic; thresholds are from the 2006 book and questioned in later papers — [JSpIRIT](https://github.com/hcvazquez/JSpIRIT), [jquality](https://redirect.github.com/arturbosch/jquality) (search snippets only). Status of these: unverified, likely unmaintained.
- OpenRewrite: recipes found are instanceof mechanics only (InstanceOfPatternMatch, IfElseIfConstructToSwitch, ReplaceClassIsInstanceWithInstanceof, RemoveRedundantNullCheckBeforeInstanceof, assertion recipes); no recipe for switch-on-type -> polymorphism found — [OpenRewrite instanceof pattern match](https://docs.openrewrite.org/recipes/staticanalysis/instanceofpatternmatch) (search snippet). jSparrow, AutoRefactor, IntelliJ structural search, JArchitect, Sonargraph, Structure101, jQAssistant, Embold, Qulice, Error Prone design checks, iPlasma/inCode/JCodeOdor: NOT researched / UNVERIFIED.

### Inferences
- OpenRewrite/jSparrow-type tools transform mechanical idioms, not modelling defects; the absence of a "type code -> polymorphism" recipe suggests this refactoring is considered too semantic for automation (only JDeodorant claims it, for Eclipse).

## 8. Does any tool detect your seven cross-cutting patterns?

### Takeaway
Only fragments exist. No tool found detects, as a rule: parallel collections/maps keyed alike, field groups with a common name prefix (data clump), repeated call sequence on the same receiver across callers, boolean/enum field tested in many methods (implicit state), a discriminator method switched outside its class, or traversal of a structure from several clients.

### Cited Findings
- Same family of types tested with instanceof in several places: Designite Unexploited Encapsulation and Missing Hierarchy work per METHOD only (source read); CodeQL `java/chained-type-tests` per if-chain (> 5); IntelliJ InstanceofChain per chain. None aggregates across methods or classes.
- Structure traversed from outside (nested getter loops, Law of Demeter): PMD LawOfDemeter (degree > trustRadius), IntelliJ LawOfDemeter / ChainedMethodCall; per expression, no cross-client aggregation.
- Feature-envy-like (calls to one other class): IntelliJ FeatureEnvy (3+ calls to one class in a method), CodeQL `java/feature-envy` (> 4 distinct members and > 2x own), JDeodorant FeatureEnvy (Move Method), JSpIRIT/jQuality (Lanza ATFD-based).
- Discriminator method (kind()/getType()) switched outside its class: no tool found; closest are JDeodorant Type/State Checking (fields, not methods: UNVERIFIED) and Sonar S2301 (boolean selector parameters).
- Repeated call sequence on one receiver: IntelliJ FeatureEnvy example (`beforeStart(); process(); afterProcessing()`) is the nearest; PMD CPD / JDeodorant Duplicated Code find textual/structural clones, not "same receiver sequence" as a model concept.
- Implicit state (boolean/enum field tested across methods): JDeodorant State Checking claims it (State/Strategy) — not verified in detail; no rule found elsewhere.
- Primitive obsession / data clumps / string-heavy arguments: CodeScene Primitive Obsession (no thresholds public); PMD UseObjectForClearerAPI (> 3 String parameters; deprecated); Designite Long Parameter List (5), Broken Modularization (0 methods, >= 5 fields). Data clump detection (same parameters/fields recurring) not found in any opened tool.

### Gaps
- JDeodorant paper details, Embold, jQAssistant, Sonargraph/Structure101, Error Prone, jSparrow, AutoRefactor, structural-search templates, JArchitect: not researched in this pass (tool-call budget). CodeScene change-coupling not researched. Licences of Sonar, PMD, Checkstyle, CodeQL, IntelliJ not verified in this session.
