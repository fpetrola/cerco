# Automatically detecting missing polymorphism, Visitor, State and other absent models in OO code

Method note: claims marked with a source were checked against a page or PDF actually opened during this research (several PDFs downloaded and text-extracted). Items marked UNVERIFIED come from memory or from search-result snippets that could not be opened. Research date 2026-11-10.

## 1. Tsantalis & Chatzigeorgiou (JSS 2010) and JDeodorant Type/State Checking

### Takeaway
The best-specified detector in the literature. It matches a switch or an if/else-if chain whose every branch compares the SAME variable (field, parameter, local, or a getter of a field) against named constants (static final / enum), or runs instanceof / getClass()==X.class on the SAME variable against subclasses of its declared type. It groups the candidates that share constants or a hierarchy, ranks groups by size, and reports moderate precision (43-57%), which rises to 94-100% once a single filter is added: average statements per branch >= 2.

### Cited Findings
- Paper: N. Tsantalis, A. Chatzigeorgiou, "Identification of refactoring opportunities introducing polymorphism", JSS 83(3):391-404, 2010, DOI 10.1016/j.jss.2009.09.017. Author PDF — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Candidate state variables**: SV = {x in IV_C ∪ P_m ∪ LV_m : type(x) = primitive ∨ type(x) = enum}, i.e. instance variables of class C, parameters and locals of method m. NC = the set of named constants of all system classes (static final fields) — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Switch rule (Replace Type Code with State/Strategy)**: (1) the switch expression is a variable v in SV, or a call to the getter of v when v is an instance variable; (2) every case label is a named constant in NC; (3) n > 1 cases, or n = 1 if a default exists — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **If/else-if rule (state checking)**: every if condition is, or contains, an equality infix expression with v (or its getter) on one side and a named constant on the other; v is common to all n conditions; n > 1, or n = 1 if there is a final else — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **RTTI rule (Replace Conditional with Polymorphism)**: an if/else-if chain (each if is the else of the previous one). The candidate Superclass Type Variables (STV) are variables in IV_C ∪ P_m ∪ LV_m whose type is a system class that other system classes inherit from. Each condition is, or contains, either `v instanceof T` with T a subclass of v's type, or `v.getClass() == T.class`. v must be common to all conditions, with n > 1 or n = 1 plus a final else. The output is v, the Identified Subclass Types (IST) with the code of each branch, and the inheritance tree — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **How it decides between the two refactorings**: by the kind of test. Equality against named constants of a primitive/enum variable gives a State/Strategy hierarchy (a new hierarchy, since the type code can change at runtime). instanceof/getClass on a variable of an existing hierarchy gives Replace Conditional with Polymorphism (moving the code into the existing subclasses). Both are under one "state-checking" umbrella — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Visitor explicitly left to the human**: RTTI conditionals can be removed either by Replace Conditional with Polymorphism or by Kerievsky's Move Accumulation to Visitor ("code is accumulated in a single Visitor class and double-dispatch is employed"). The Visitor "is required when the designer wants to avoid 'polluting' the subclasses with additional operations". The choice "depends on factors that cannot be automatically determined". The tool automates only the first option — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Additional Named Constants (ANC)**, the step that recovers the whole family. (a) Scan for assignments `v = CONST` with CONST in NC but not in the identified set INC: inside all methods of C if v is a field, inside m if v is local. (b) Merge fragments whose INC sets intersect: "if two state-checking code fragments operate on at least one common named constant ..., their state variables are conceptually related with all the named constants belonging to the union of their INC sets", so one State hierarchy should be created for both. Fig. 1 gives a greedy merge over INC sets sorted by descending size. The goal is to avoid "multiple inheritance hierarchies that constitute different concrete implementations of essentially the same state abstraction" — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Compound conditions**: an && expression is turned into a binary tree. The leaf that does the state check is chosen; when several leaves qualify, the one whose variable appears in the other ifs wins. The rest of the condition stays as a guard in the moved code. || is handled separately (Fig. 4). The ternary ?: is not considered — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Preconditions (disqualifiers)**: (1) the fragment assigns locals declared before it, or parameters, except a single such variable returned inside or after the fragment; (2) the fragment is inside a loop and contains break/continue; (3) it contains super calls; (4) a generated class name clashes with an existing one — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Ranking**: candidates are grouped by shared named constants (state checks) or by a common inheritance hierarchy (RTTI). Groups are sorted by size, "since the degree of polymorphism (i.e. the number of polymorphic methods added to a single inheritance hierarchy) ... will be higher". Ties are broken by average statements per branch. Within a group, candidates are sorted by the number of state-checking cases in each class, then by average statements per branch — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Evaluation (precision/recall against one independent expert, 12+ years of design experience)**. The expert was asked "Which are the conditional structures that should be replaced with an instance of State design pattern, or employ an RTTI mechanism that should be replaced with a polymorphic call?" — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
  - Violet 0.16: TO 7, TP 4, FP 4, FN 3; precision 50%, recall 57%.
  - IHM 0.1.1: TO 9, TP 9, FP 12, FN 0; precision 43%, recall 100%. With the filter: precision 100%, recall 78%.
  - Nutch 0.4: TO 17, TP 17, FP 13, FN 0; precision 57%, recall 100%. With the filter: precision 94%, recall 100%.
  - The filter excludes candidates whose average statements per branch is below 2, and "the precision of the technique is significantly improved".
  - (Table values were reconstructed from the extracted PDF text. The column alignment of the parenthesised values is my reading, so check against the PDF.)
- **False negatives**: conditionals that compare `this` with named constants (e.g. an enum switching on itself). The approach needs a variable, since the value of `this` cannot change. The expert would fix these with a subclass per constant (constant-specific behaviour) — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **What predicts expert agreement** (univariate logistic regression, Nutch, N = 30; all p < 0.01): (a) the number of polymorphic methods added to the same hierarchy (group size), B = 1.777, Nagelkerke R² 0.783; (b) the number of State subclasses created, R² 0.657; (c) the average statements moved per branch, B = 2.624, R² 0.712. The text says agreement rises with (a) and (c) and falls with (b). The extracted coefficient for (b) prints as 0.716 with no minus sign, which may be an extraction artefact — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- **Prior detectors cited in the paper**:
  - Van Emden & Moonen (2002) detected and visualised "instanceof" smells (a sequence of conditionals testing an object's type) and "typecast" smells.
  - Trifu & Reupke (2007) combined indicators into "explicit state checks": switch/if-else-if on an attribute whose NAME contains "state". These indicators "exhibit low precision when they are triggered individually or even simultaneously".
  - [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- JDeodorant implements this as "Replace Conditional Logic with Polymorphism (State-checking and Type-checking)" — [search summary of a JDeodorant retrospective, bibbase listing](https://bibbase.org/network/publication/tsantalis-chatzigeorgiou-identificationofrefactoringopportunitiesintroducingpolymorphism-2010)

### Inferences
- The two filters that rescue precision map directly onto the designer's "structural signal": (1) grouping, i.e. several conditionals over the same constant family or hierarchy; (2) non-trivial branches, at least 2 statements on average. A one-off `instanceof` with a 1-statement branch is exactly what the expert rejected.
- The INC-union merge is an operational definition of "several places testing the same family of types": build the connected components of conditionals linked by shared constants or a shared tested hierarchy, then fire when a component has at least k members.
- The `this`-switch false negative is the enum-switching-on-itself case, i.e. "kind() decided outside its class" seen from the inside. It needs its own rule: a switch on `this`, or on a field of the enum's own type, inside the enum.

### Gaps
- Per-candidate FP reasons beyond the statements-per-branch filter are not itemised in the paper.
- Only one expert and three small immature systems were used. No later independent replication of JDeodorant's type-checking precision was found.

## 2. Detectors for specific pattern opportunities (Strategy, Null Object, Template Method, State, Composite, Visitor, Factory...)

### Takeaway
After JSS 2010, the same Greek group (Giakoumakis, Zafeiris and others) built JDeodorant-integrated detectors for Strategy (IST 2012) and Null Object (IST 2015). Visitor introduction has been treated as a transformation problem (Mens & Tourwé, Kerievsky, Ajouli/Cohen, Hills et al.), never as a validated detector with reported precision. Ó Cinnéide judged automating Visitor introduction impractical.

### Cited Findings
- **Strategy**: Christopoulou, Giakoumakis, Zafeiris, Soukara, "Automated refactoring to the Strategy design pattern", IST 54(11):1202-1214, 2012, DOI 10.1016/j.infsof.2012.05.004. It works on a single Context class and its conditional statements, called "Strategy Selection Blocks" (SSBs). It offers "total replacement of conditional logic with method calls of appropriate concrete Strategy instances" for special cases. It is integrated into JDeodorant, and the relevance of its suggestions was verified by expert engineers — [researchr](https://researchr.org/publication/ChristopoulouGZS12); [record](https://www.bohrium.com/paper-details/automated-refactoring-to-the-strategy-design-pattern/813305827237560320-2498)
- **Null Object**: Gaitani, Zafeiris, Diamantidis, Giakoumakis, "Automated refactoring to the Null Object design pattern", IST 2015 — [reviewer listing](https://cs.reviewer.ly/app/publication/9e7207cb-d445-419c-a738-a31014c62919). The detection rule and its numbers could not be opened. UNVERIFIED recollection: it detects optional fields (fields that may be null) whose dereferences are guarded by `!= null` checks in several methods.
- **PARROT** (Kiel bachelor thesis, 2017): semi-automatic Null Object refactoring that finds candidates with a system dependence graph plus pattern matching — [thesis PDF](https://oceanrep.geomar.de/id/eprint/39922/1/tnr-thesis-final.pdf) (only the search snippet was read)
- **Ó Cinnéide & Nixon (ICSM 1999; PhD 2000)**:
  - A precursor is "a design structure that expresses the intent of a design pattern in a simple way, but that would not be regarded as an example of poor design". For Factory Method, the precursor is that the Creator creates an instance of Product.
  - Patterns are decomposed into minipatterns with behaviour-preserving minitransformations.
  - Facade, Mediator, Interpreter and Flyweight have precursors too inexact to drive a transformation; Decorator and Observer have only weak ones.
  - [Precursors page](https://csserver.ucd.ie/~meloc/thesis/Precursors.htm); [TCD tech report](https://publications.scss.tcd.ie/tech-reports/reports.99/TCD-CS-1999-18.pdf) (search summary)
- "Ó Cinnéide [OC00] give transformation to introduce several patterns but not the Visitor (he considers in [OC00] that automating the introduction of a visitor pattern is impractical)" — [Ajouli & Cohen, arXiv 1112.4271](https://arxiv.org/pdf/1112.4271)
- **Kerievsky's two Visitor recipes, as summarised by Ajouli & Cohen**: (1) like Mens & Tourwé, start from an operation spread as overriding methods in the hierarchy; (2) "external accumulation", which "applies to an operation defined outside of the class hierarchy by a switch on the type of an object with instanceof and type casts". Neither gives the inverse transformation — [arXiv 1112.4271](https://arxiv.org/pdf/1112.4271)
- **Composite ↔ Visitor duality**: "while the Composite pattern (as well as the Interpreter pattern and classic class hierarchies) provides modularity along subtypes and leaves operation definitions crosscut, the Visitor pattern provides modularity along operations". Ajouli & Cohen give a round trip of IntelliJ refactorings with computed preconditions, applied to JHotDraw — [arXiv 1112.4271](https://arxiv.org/abs/1112.4271)
- **Hills, Klint, van der Storm, Vinju**, "A case of visitor versus interpreter pattern", TOOLS 2011, pp. 228-243: an automated transformation of a Visitor-based program into an Interpreter-based one, with a few user interactions, specific to one program — [arXiv 1112.4271 related work](https://arxiv.org/pdf/1112.4271)
- **Jeon, Lee, Bae (APSEC 2002)**: "automatic inference of sequence of refactoring operations allowing to reach design pattern based versions of programs" — [arXiv 1112.4271](https://arxiv.org/pdf/1112.4271)
- **Briand, Labiche, Sauvé**, "Guiding the Application of Design Patterns Based on UML Models", ICSM 2006, pp. 234-243, DOI 10.1109/ICSM.2006.30: OCL detection rules arranged as a decision tree that flags UML design areas that are candidates for a GoF pattern; prototype tool; "encouraging" case study — [Simula page](https://www.simula.no/research/guiding-application-design-patterns-based-uml-models); [UL record](https://pure.ul.ie/en/publications/guiding-the-application-of-design-patterns-based-on-uml-models/). Whether the illustrated pattern is Visitor: UNVERIFIED (not confirmed from the full text).
- **Tourwé & Mens**, "Identifying Refactoring Opportunities Using Logic Meta Programming", CSMR 2003, pp. 91-100: bad smells expressed as logic queries (SOUL on Smalltalk), plus "cascaded" opportunities derived from the first ones — [typeset copy](https://typeset.io/pdf/identifying-refactoring-opportunities-using-logic-meta-4ipex50s99.pdf); [BibSonomy](https://bibsonomy.org/bibtex/2fae59103894ddc77ece51d6063f92790/kimmens). The Mens & Tourwé survey notes that introducing a Visitor "takes many primitive refactorings" — [survey PDF](https://www.eecs.yorku.ca/course_archive/2007-08/F/6431/Mens.pdf) (search summary)
- **ReInstancer** (ICSE 2022 demo; JSEP 2024) goes the opposite way from Visitor: it converts multi-branch instanceof chains into Java pattern-matching switch. ICSE: 7 applications, 4,404 instanceof, 2,060 casts removed, 141 multi-branch statements refactored. Journal: 20 projects, 3,558 instanceof and 228 multi-branch statements refactored, about 10.8 s per project, using class-hierarchy and control-flow analysis — [ICSE demo](https://conf.researchr.org/details/icse-2022/icse-2022-demo---demonstrations/43/ReInstancer-Automatically-Refactoring-for-Instanceof-Pattern-Matching); [JSEP abstract](https://www.ebsco.com/articles/computer-science/e701f655-326e-5cc0-82ea-4cb4a7205df0/reinstancer-an-automatic-refactoring-approach-for-instanceof-pattern-matching/)
- **LLMs**: on 180 real Java refactorings, ChatGPT found 28 opportunities and Gemini 7. Neither found any of the 20 Extract Class opportunities, and about 77% of the suggestions they did make were judged beneficial — [arXiv 2411.04444](https://arxiv.org/html/2411.04444v1). LLM design-pattern detection (recognising existing patterns, not missing ones): [arXiv 2502.18458](https://arxiv.org/abs/2502.18458v1); [Chalmers](https://research.chalmers.se/en/publication/545339)

### Inferences
- No published work found operationalises "this instanceof chain should be a Visitor" as a detector with measured precision. The closest is JSS 2010's RTTI rule plus its explicit statement that the Polymorphism-vs-Visitor choice is not automatable. A gate wanting Visitor as the default must supply the choice itself. A plausible rule: if the tested hierarchy is owned by the project and the operation belongs to the client's concept (not the subclass's), propose a Visitor; otherwise propose a polymorphic method.
- Recent work and tooling (ReInstancer, Java 21) push in the opposite direction, toward pattern-matching switch.

### Gaps
- No full text was opened for the Strategy, Null Object or Template Method (Zafeiris et al. 2017) papers, so their exact rules, thresholds and precision are missing.
- JIAD (Rajesh & Janakiram), and State, Composite, Decorator, Command or Factory opportunity detectors: no source opened.
- LLM "missing pattern" detectors: none found that target polymorphism or Visitor opportunities specifically.

## 3. Empirical studies of casts and type tests; sealed types and pattern matching vs Visitor

### Takeaway
In a manual study of 5,000 casts sampled from about 7,000 projects, Typecase (a cast guarded by instanceof, a class literal or a type tag) is by far the commonest pattern at 23.6%. The authors name Visitor or virtual dispatch as its alternatives but defer to pattern matching. Brian Goetz's data-oriented programming position treats sealed types plus pattern matching as superseding Visitor for closed data, while keeping OO for boundaries and complex entities.

### Cited Findings
- **OOPSLA 2019 headline numbers**: 7,559 projects analysed with QL on lgtm.com; 8.7% of methods contain at least one cast; 50% of the inspected casts are not guarded locally; 25 usage patterns — [OOPSLA page](https://2019.splashcon.org/details/splash-2019-oopsla/42/Casting-about-in-the-Dark); [author page](https://acuarica.github.io/publications/casting-about-in-the-dark)
- **The full categorisation** comes from the first author's PhD thesis, which contains the study (26 patterns, Table 4.2, a sample of 5,000 casts across 1,299 projects; 20.86% of them primitive conversions). "The categorization is subjective" — [thesis PDF, USI 2019](https://sonar.ch/documents/319069/files/2019INFO008.pdf):
  - **Guarded group**:
    - Typecase: "Cast guarded with instanceof, class literal, or application-specific tag"; 1,182 casts, 23.64%.
    - Equals: 247, 4.94%.
    - ParserStack: a heterogeneous stack; 29, 0.58%.
  - **API group**:
    - Stash: a heterogeneous collection element; 559, 11.18%. Variants: LookupById, StaticResource and Tag; a lookup by a constant id, tag or name into a `Collection<Object>` / `Map<K,Object>`.
    - Factory: 378, 7.56%.
    - KnownReturnType: 89, 1.78%.
    - Deserialization: 71, 1.42%.
    - NewDynamicInstance: 59, 1.18%.
    - Composite: 21, 0.42%.
  - **Covariance group**:
    - Family: 343, 6.86%. This is "a sort of family polymorphism": mutually dependent types derived together, where invariant parameter types force a downcast.
    - CovariantReturnType: 106, 2.12%.
    - FluentAPI: 23, 0.46%.
  - **Generics group**:
    - UseRawType: 335, 6.70%.
    - RemoveWildcard: 33.
    - CovariantGeneric: 10.
    - SelectTypeArgument: 9.
    - GenericArray: 5.
    - UnoccupiedTypeParameter: 1.
  - **Type-hacking group**:
    - SelectOverload: 97, 1.94%.
    - SoleSubclassImplementation: 57, 1.14%.
    - ImplicitIntersectionType: 45.
    - ReflectiveAccessibility: 26.
    - AccessSuperclassField: 4.
  - **Code smell group** (explicitly "regarded as code smells"):
    - Redundant: 117, 2.34%.
    - VariableSupertype: 64, 1.28%.
    - ObjectAsArray: 47, 0.94%.
- **Typecase details**: variants GuardByInstanceOf (881 in application/library code, the most used), GuardByTypeTag and GuardByClassLiteral; 1,050 of the 1,182 are in application code. "Often there is just one case and the default case ... performs a no-op or reports an error". "Alternatives to the Typecase pattern would be to use the visitor pattern or to use virtual dispatch on the match scrutinee. However, both of these alternatives might be difficult to implement when the scrutinee is defined in a library or in third-party code" — [thesis PDF](https://sonar.ch/documents/319069/files/2019INFO008.pdf)
- **Composite cast** (the pattern behind "code that knows the shape of a structure"): "typical of hierarchical data structures such as abstract syntax trees, document models, or UI layouts. Based on the grammar of the data structure, the types of adjacent objects in the structure can be known. The cast succeeds if the data structure is well-formed". Example: `(TableSectionBox) section.getPreviousSibling()` — [thesis PDF](https://sonar.ch/documents/319069/files/2019INFO008.pdf)
- The names "Selector" and "Tag" from the brief do not appear as top-level patterns in the thesis table: Tag is a Stash variant and GuardByTypeTag is a Typecase variant. The OOPSLA version reports 25 patterns against the thesis's 26, so names may differ slightly — [thesis PDF](https://sonar.ch/documents/319069/files/2019INFO008.pdf); [author page](https://acuarica.github.io/publications/casting-about-in-the-dark)
- **Goetz on data-oriented programming**:
  - "Before we had records and pattern matching, the standard approach to writing code like this was the visitor pattern".
  - "Pattern matching is clearly more concise than visitors, but it is also more flexible and powerful".
  - "Visitors require the domain to be built for visitation, and imposes strict constraints".
  - On exhaustiveness: "If we add more option types later, the compiler will remind us which switches need to be extended".
  - OO "is at its best when it is defining and defending boundaries"; "When we're modeling complex entities, OO techniques have a lot to offer us"; the two are "different tools for different granularities and situations".
  - [InfoQ, Data Oriented Programming in Java](https://www.infoq.com/articles/data-oriented-programming-java/)

### Inferences
- Only Typecase (23.6%) and arguably Stash-Tag and Family are design-gap patterns that a "missing model" gate cares about. Equals, Factory, Deserialization, generics and type hacks are legitimate or language-forced. A gate that flags every cast hits the legitimate ~75% too, which is a direct quantification of the "too blunt" complaint.
- "Often there is just one case and the default" means a single-branch instanceof is the modal Typecase. JSS 2010 found such small cases were mostly rejected by the expert. So: count families, not instances.
- Under Goetz's framing, the Visitor-vs-pattern-matching decision depends on whether the hierarchy is a closed data carrier (sealed records, operations added freely) or an entity or boundary (behaviour belongs inside). A rule keyed on "sealed interface of records" versus "class with behaviour/state" can respect both positions.

### Gaps
- Instanceof-prevalence studies other than this one were not found.
- No academic paper directly comparing Visitor vs sealed + switch on reuse of traversal was found. The JEP texts were not opened.

## 4. Visitor, internal iteration, Demeter / adaptive programming, and "same navigation path in several clients"

### Takeaway
The Demeter line (Lieberherr: DJ, DemeterJ, traversal strategies) is the theoretical home of "code should not know the shape of a structure it does not own". It enforces this by generating traversals from a class graph, not by detecting duplication. No published detector was found that clusters identical navigation paths across clients.

### Cited Findings
- DJ (Orleans & Lieberherr, Reflection 2001): a pure-Java library where traversal strategies are built and interpreted at run time over a class graph computed by reflection. It separates where to go (the strategy), what to do (the adaptive visitor) and the class graph — [DJ page](https://www2.ccs.neu.edu/research/demeter/biblio/DJ-reflection.html); [CACM](https://cacm.acm.org/?p=5047)
- Strict LoD adherence scatters many small forwarding methods. Adaptive programming with traversal strategies and visitors is presented as the way out, keeping structure and behaviour loosely coupled — [DJ page](https://www2.ccs.neu.edu/research/demeter/biblio/DJ-reflection.html)
- Lieberherr et al., TOPLAS 2004 on traversal strategies: key terms are structure-shy software and class graphs versus object graphs — [p370-lieberherr.pdf](https://scg.unibe.ch/assets/download/mm/Resources/p370-lieberherr.pdf) (search summary)
- Missing-call detection by majority rule (Monperrus & Mezini, TOSEM 2013): it finds method calls that most similar code contains but one site lacks, revealing missing calls, code smells and API misuse — [arXiv 1306.0762](https://arxiv.org/abs/1306.0762). The precision figure is not in the abstract.

### Inferences
- An implementable "navigation duplication" detector:
  1. For each method, extract the chains of getter or field accesses rooted at a parameter, field or local, e.g. `a.getB().getC().getItems()`.
  2. Normalise each chain to its type path (`A→B→C→List<Item>`), resolved with the symbol solver.
  3. Index the paths project-wide and fire when one path of length 2 or more appears in at least N distinct client classes that do not own A.
  4. Propose a method on A (Hide Delegate) or an internal iterator / Visitor on A when the chain ends in a collection that is iterated.

  This combines Message Chains, Inappropriate Intimacy and Demeter's class-graph view. It is my synthesis; no paper validates it.
- Monperrus's majority rule could be inverted for "repeated call sequences on the same receiver" (a missing method on the receiver): mine the frequent call sequences per receiver type across clients.

### Gaps
- No source was found for a detector of "traversal duplication", "Encapsulate Collection opportunity" or "representation exposure" with reported precision. The Walkabout / reflective visitor (Palsberg & Jay 1998) is UNVERIFIED here (not opened).
- Kerievsky's "Move Accumulation to Visitor" and "Replace Implicit Tree with Composite" were not opened directly. They are only known via the citations in JSS 2010 and Ajouli/Cohen.

## 5. Law of Demeter checkers

### Takeaway
PMD's LawOfDemeter uses a "degree of foreignness" dataflow and a long list of exemptions: builders, factories, static calls, String/primitive values, java.util containers, Stream, and "transformation" calls whose return type is related to the receiver. It still warns that the rule is FP-prone. Guo et al. 2011 found LoD-violation counts correlate with bugs about as strongly as CBO, WMC and RFC.

### Cited Findings
- **PMD degree computation**:
  - `this` has degree 0.
  - Parameters, locally created objects and statics have degree 1.
  - A field access or getter call adds 1 to its receiver's degree.
  - A "transformation" call such as `withFoo()` keeps the receiver's degree.
  - A variable takes the maximum degree over its reaching definitions.
  - Collection elements have the container's degree.
  - With trustRadius N (default 1): degree N+1 is reported unless the value is only returned or passed as an argument; degree above N+1 is not re-reported.
  - The page warns the rule is "prone to false positives" and that raising trustRadius reduces them.
  - [PMD design rules](https://pmd.github.io/pmd/pmd_rules_java_design.html)
- **PMD exemptions, read from the source** ([LawOfDemeterRule.java](https://github.com/pmd/pmd/blob/main/pmd-java/src/main/java/net/sourceforge/pmd/lang/java/rule/design/LawOfDemeterRule.java)):
  - A call is treated as ACCESSIBLE (degree 1) when overload resolution failed, the method is static ("taken to be construction methods"), the call is on this, the qualifier type ends with "Factory" or is named "factory", the qualifier type ends with "Builder", or the call is "pure data".
  - Pure data means String, StringBuilder or StringBuffer, a primitive or boxed primitive, a value that is null-checked, or a value that is the operand of instanceof.
  - The result keeps the qualifier's degree (no increment) when the declaring type is a "pure data container" (any `java.util` type, Stream, Class, w3c NodeList/NamedNodeMap, arrays), when the call is not a getter, or when the return type is related to the qualifier type (`TypeOps.areRelated`, i.e. fluent APIs).
  - Values that escape as an argument, return value or throw are not reported.
- **Guo, Würsch, Giger, Gall**, "An Empirical Validation of the Benefits of Adhering to the Law of Demeter", WCRE 2011, pp. 239-243 ([PDF](https://khoury.northeastern.edu/home/lieber/LoD/LoD-2011-Zurich.pdf)):
  - They use the class form, since "Only the latter is designed to be able to statically enforced". A method M of C may send messages only to instance variables of C, M's argument classes, classes instantiated in M, and direct fields of C.
  - Strong LoD excludes inherited instance variables from the friend set; weak LoD includes them (C_fw = C_this ∪ C_fields ∪ C_parameters ∪ C_ov ∪ C_iv).
  - Five Eclipse sub-projects, an Eclipse plugin, AST visitor counts per compilation unit.
  - Spearman ρ with bug counts: jdt.core SVLoD 0.67 and WVLoD 0.59 (3rd and 5th of all measures); compare 0.70 and 0.62 (behind LOC). They "do not differentiate themselves" from CBO, WMC and RFC.
  - PCA plus logistic bug-proneness models raised AUC from LOC-only: jdt.core 0.8311→0.8546, pde 0.6638→0.7152, jface 0.8546→0.8590.

### Inferences
- PMD's exemption list is a ready-made FP-avoidance spec. The most useful item is the `areRelated(receiverType, returnType)` test for fluent and builder chains, together with the pure-data container rule.
- The LoD evidence is correlational and of similar strength to plain size and coupling metrics. It supports LoD as a weak signal, not as a high-precision gate.

### Gaps
- The IntelliJ "Law of Demeter" inspection was not opened. UNVERIFIED: IntelliJ has a "Chained method calls" / "Method call chain" style inspection rather than a strict LoD one.

## 6. Implicit state machines, flags, temporal coupling

### Takeaway
JDeodorant's state-checking rule (Section 1) is the only rigorously evaluated detector found for "behaviour driven by an enum or int field tested across methods": several switch / if-chains on the same field with shared constants get merged into one State hierarchy.

### Cited Findings
- JSS 2010's ANC step scans all methods of C for `v = CONST` assignments and merges conditionals with overlapping constant sets. Kerievsky's "Replace State-Altering Conditionals with State" applies "if conditional logic controls the state transitions of an object" — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- Trifu & Reupke's name-based "explicit state checks" (a variable name containing "state") had low precision — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)

### Inferences
- Rule for flags tested across many methods: a field of type boolean or enum that is (a) read in a condition in at least k methods of the class and (b) assigned a constant in at least one other method. This is JSS 2010's state variable criterion widened to booleans.

### Gaps
- Typestate and API-protocol mining, temporary-field detection, and detection of sequential coupling: no sources opened in this session.

## 7. Type codes and simulated polymorphism in Designite

### Takeaway
DesigniteJava's two type-test smells are crude per-method rules over instanceof only. They ignore switch, equality on constants and getClass, and have no grouping.

### Cited Findings
- **Missing Hierarchy**: fires if any method of class T contains instanceof checks against at least 2 types that are NOT ancestors of T (`INSTANCE_OF_TYPES_NOT_IN_HIERARCHY_THRESHOLD = 2`, `setDifference(listOfInstanceOfTypes, allAncestors).size() >= 2`) — [HierarchySmellDetector.java](https://github.com/tushartushar/DesigniteJava/blob/master/src/Designite/smells/designSmells/HierarchySmellDetector.java)
- **Unexploited Encapsulation**: fires if any method has instanceof checks on two different types in the same hierarchy (same connected component of the project hierarchy graph) — [EncapsulationSmellDetector.java](https://github.com/tushartushar/DesigniteJava/blob/master/src/Designite/smells/designSmells/EncapsulationSmellDetector.java)
- **Deficient Encapsulation**: any public field (`getNumOfPublicFields() > 0`) — [EncapsulationSmellDetector.java](https://github.com/tushartushar/DesigniteJava/blob/master/src/Designite/smells/designSmells/EncapsulationSmellDetector.java)
- **Other hierarchy smells in the same file**:
  - Rebellious Hierarchy: an override with an empty body or only a throw.
  - Broken Hierarchy: a subtype with public methods that overrides none of its supertype's methods.
  - Multipath, Cyclic, Deep and Wide Hierarchy, the last two by configured thresholds.
  - [HierarchySmellDetector.java](https://github.com/tushartushar/DesigniteJava/blob/master/src/Designite/smells/designSmells/HierarchySmellDetector.java)

### Inferences
- Unexploited Encapsulation is a one-method version of JSS 2010's RTTI rule without the "same variable" and branch-size conditions. Expect higher false positives: equals(), visitor fallbacks, and a guarded cast on two unrelated variables.

### Gaps
- No published precision for these two Designite smells was found.

## 8. Lanza & Marinescu detection strategies

### Takeaway
Detection strategies are AND/OR compositions of metric filters with named thresholds. Verified formulas: God Class (and PMD's exact numbers), Feature Envy, Data Class (PMD's), Shotgun Surgery, God/Brain Method. Precision is modest and tools disagree on which entities are smelly.

### Cited Findings
- **God Class**: ATFD > FEW ∧ WMC ≥ VERY HIGH ∧ TCC < ONE THIRD — [UFMG lecture, Figueiredo](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf). PMD's values are WMC ≥ 47, ATFD > 5, TCC < 1/3 — [GodClassRule.java](https://github.com/pmd/pmd/blob/main/pmd-java/src/main/java/net/sourceforge/pmd/lang/java/rule/design/GodClassRule.java)
- **Data Class** in PMD: WOC < 1/3 and (NOPA + NOAM > 3 ∧ WMC < 31, or NOPA + NOAM > 5 ∧ WMC < 47) — [DataClassRule.java](https://github.com/pmd/pmd/blob/main/pmd-java/src/main/java/net/sourceforge/pmd/lang/java/rule/design/DataClassRule.java)
- **Feature Envy**: ATFD > FEW ∧ LAA < ONE THIRD ∧ FDP ≤ FEW — [UFMG lecture](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf); [SMAD paper, arXiv 1903.01899](https://arxiv.org/pdf/1903.01899)
- **God (Brain) Method**: LOC > HIGH(class)/2 ∧ CYCLO ≥ HIGH ∧ NOAV > MANY — [UFMG lecture](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf). UNVERIFIED: the book's Brain Method also adds MAXNESTING ≥ SEVERAL.
- **Shotgun Surgery**: CM > MANY ∧ CC > MANY, where CM and CC count incoming calling methods and classes outside the inheritance tree — [UFMG lecture](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf)
- **Refused Parent Bequest**: (NProtM > FEW ∧ BUR < ONE THIRD, or BOvR < ONE THIRD) ∧ child not small (AMW > AVG, or WMC > AVG, ∧ NOM > AVG) — [UFMG lecture](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf). The full formula was partly truncated in the extraction.
- **UNVERIFIED (from memory)**:
  - Intensive Coupling: (CINT > SHORT MEMORY CAP ∧ CDISP < HALF) ∨ (CINT > FEW ∧ CDISP < QUARTER), ∧ MAXNESTING > SHALLOW.
  - Dispersed Coupling: CINT > SHORT MEMORY CAP ∧ CDISP ≥ HALF ∧ MAXNESTING > SHALLOW.
  - Tradition Breaker: NAS ≥ AVG NOM ∧ PNAS ≥ TWO THIRDS, plus a heavy child, plus a parent that is not trivial.
  - Brain Class: contains Brain Methods plus WMC/TCC conditions.
  - Typical values: FEW = 2-5, SHORT MEMORY CAP = 7-8, ONE THIRD = 0.33.
- **Precision in practice**:
  - SMAD (an ML ensemble over InCode/DECOR-style detectors): God Class precision 45%, recall 51%. Feature Envy precision 40% (ASCI 48%), recall 80% — [arXiv 1903.01899](https://arxiv.org/pdf/1903.01899)
  - Tool comparisons (inFusion, JDeodorant, PMD, JSpIRIT): overall agreement 83-98%, but mostly on non-smelly entities; agreement on smell occurrences between pairs of tools 0.38%-64.56%; inFusion has the highest precision and lowest recall for God Class and Feature Envy; JDeodorant has the lowest precision for God Class and God Method; thresholds change detected counts greatly — [JSERD 2017 article (search summary)](https://link.springer.com/article/10.1186/s40411-017-0041-1); [distantreader copy](https://distantreader.org/stacks/journals-ojs/jserd/jserd-433.htm)

### Inferences
- Metric-threshold strategies are not high-precision gates (40-50% precision). They fit as advisory signals. JSS-style structural rules with grouping reach about 94-100% on small systems.

### Gaps
- The book itself was not accessed, so the Brain Class, Intensive/Dispersed Coupling and Tradition Breaker formulas remain unverified.

## 9. History-based detection (HIST, change coupling)

### Takeaway
HIST detects five smells from co-change data, with precision 72-86% and recall 58-100% on 20 projects. Divergent Change, Shotgun Surgery and Parallel Inheritance are essentially only visible in history. This requires mining the version history, which a per-edit agent gate does not have.

### Cited Findings
- Palomba et al., "Mining Version Histories for Detecting Code Smells", TSE 41(5):462-489, 2015, DOI 10.1109/TSE.2014.2372760 ([core.ac.uk record](https://core.ac.uk/works/29747834); [IRIS](https://www.iris.unisa.it/handle/11386/4590057)):
  - Five smells: Divergent Change, Shotgun Surgery, Parallel Inheritance, Blob, Feature Envy.
  - Precision 72-86%, recall 58-100% across 20 projects; the earlier conference version reported precision 61-80% and recall 61-100%.
  - Strongly complementary to snapshot-based tools.
  - 12 developers of four projects recognised more than 75% of HIST's instances as real problems.
  - (search summaries)

### Inferences
- The co-change rule for Parallel Inheritance (two hierarchies that get subclasses added in the same commits) is the history twin of the Family cast pattern and of a missing Visitor / double-dispatch axis.

### Gaps
- The full PDF could not be downloaded (HTML or 403), so the per-smell rules, support/confidence thresholds and cost are missing. Gall's, D'Ambros's and Tornhill's change-coupling work was not opened.

## 10. Which detectors are reported to have high precision

### Takeaway
Only one detector found reports gate-level precision: JDeodorant's type/state checking with the "average statements per branch ≥ 2" filter (94-100%, on small samples). Metric strategies sit around 40-60%, HIST at 72-86% (needs history), and LoD is correlational.

### Cited Findings
- JSS 2010 precision: 43-57% unfiltered, 94-100% filtered — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)
- God Class / Feature Envy ensembles: 40-48% — [arXiv 1903.01899](https://arxiv.org/pdf/1903.01899)
- HIST: 72-86% — [IRIS](https://www.iris.unisa.it/handle/11386/4590057)
- The PMD LoD rule self-describes as FP-prone — [PMD](https://pmd.github.io/pmd/pmd_rules_java_design.html)
- Name-based state indicators: low precision even in combination — [JSS_2010.pdf](https://users.encs.concordia.ca/~nikolaos/publications/JSS_2010.pdf)

### Inferences
- For a blocking gate, the evidence favours structural rules that require all of the following:
  - the same tested variable across every branch;
  - an owned hierarchy or a project-defined constant family;
  - non-trivial branches (≥ 2 statements on average);
  - and above all REPETITION, i.e. at least 2 conditionals in the same constant/hierarchy group (group size was the strongest predictor of expert agreement, R² 0.78).

  Single occurrences should be advisory only.

### Gaps
- No study evaluates any of these detectors as a blocking gate for LLM-written code. All precision figures come from human-expert baselines on small legacy systems.
