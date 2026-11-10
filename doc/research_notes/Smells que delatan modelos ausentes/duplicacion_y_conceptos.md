# Repetition of code, structure and concept, and values without a type: detection and the missing abstraction they point to

Scope note: research budget was limited (about 25 tool calls). Everything under "Cited Findings" was read in a source opened during this session (several PDFs were downloaded and read with pdftotext). Items recalled from prior knowledge and not re-opened are marked UNVERIFIED and kept in Inferences or Gaps.

## 1. Clone taxonomy and the mapping from the KIND OF DIFFERENCE to the abstraction to introduce

### Takeaway
The strongest operational mapping comes from Tsantalis, Mazinanian and Krishnan (TSE 2015): clones are unified by mapping their statements (PDG-based), and every difference is classified and checked against 8 preconditions. Location decides the target (same class -> private method; sibling subclasses -> pull up / new intermediate class; same signatures but different callees -> Template Method; unrelated classes with no instance state -> static utility). Most detected clones are NOT refactorable by plain Extract Method (only ~14% of Type-2/3 per the authors' slides); the precondition violations that block them (unmapped statements, differences in void calls) are exactly the ones that lambdas (ICSE 2017) or Template Method overcome.

### Cited Findings
- TSE 2015 approach: "examines if the differences present between the clones can be safely parameterized without causing any side-effects"; studied over a million clone pairs from four detectors (CCFinder, Deckard, CloneDR, NiCad) in nine open-source projects. Findings: production-code clones more refactorable than test clones; clones in close locations (same method, type or file) more refactorable than distant ones (same hierarchy, unrelated types); Type-1 more refactorable than other types; small clones more refactorable than large ones — [Tsantalis et al., TSE 2015, author PDF](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- The 8 preconditions (verbatim from the paper): P1 parameterising differences must not break control, data, anti and output dependencies; P2 "Matched variables having different subclass types should call only methods that are declared in the common superclass or are being overridden in the respective subclasses" (i.e. a type difference is unifiable only through a common supertype); P3 differing fields can be parameterised only if not modified; P4 differing method calls can be parameterised only if they do not return void; P5 unmapped statements must be movable before/after the mapped ones without breaking dependencies; P6 at most one returned variable of the same type; P7 no conditional return statements; P8 mapped break/continue must come with their mapped loops — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- Location -> refactoring mapping in the same paper: same class -> Extract Method into a private method; "Extract and pull up method is applied when the clone fragments are located in methods that belong to different subclasses of the same superclass" (protected method in the superclass, or in a new intermediate class if the superclass has more subclasses; identical fields/methods accessed by the clones are pulled up too); "Introduce template method" when commonly-accessed methods are not clones but "have an identical signature and the same return type" -> abstract method in the superclass; "Introduce utility method" when clones are in unrelated classes and "do not access any instance variables or methods" -> static method — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- Percentage of refactorable clone pairs by detector: CloneDR 33.9%, CCFinder 19.9%, Deckard 11.2%, NiCad 6.2%; method-level clone pairs: NiCad lowest at 7.7%, CloneDR highest at 79.8%. CloneDR and Deckard were run at similarity 0.95. On JFreeChart, 1,091 refactorable pairs out of 2,306 Deckard method-level pairs; all 610 test-covered refactorable pairs were refactored without breaking tests — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- Authors' own conclusion: violations "related to unmapped statements or differences in method calls with a void return type could be possibly surpassed by applying the Template Method design pattern or by introducing Lambda expressions. Our empirical results show that the aforementioned precondition violations are frequent" — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- Clone refactoring with lambda expressions (ICSE 2017, pp. 60-70): 46.7K Type-2/Type-3 clones from 9 projects analysed, 12.6K refactored as lambda-parameterisable, zero compilation errors, one test failure; Template Method was an alternative in only about one third of the lambda cases; about 94% of the 1M+ clones were Type-2 or Type-3 and only 14% of those were safely refactorable without lambdas; tool is a JDeodorant Eclipse plug-in — [author slides](https://www.slideshare.net/slideshow/clone-refactoring-with-lambda-expressions/76431047) (abstract-level confirmation: [BibSLEIGH entry](https://bibtex.github.io/ICSE-2017-TsantalisMR.html))
- A 2025 follow-up ports lambda-based clone refactoring to C# — [arXiv 2512.21511](https://arxiv.org/pdf/2512.21511)

### Inferences
- Rule design that follows directly: for each clone pair the checker already finds, compute the diff kind and emit the model: (a) only literals/local names differ -> "parameterise" (missing parameter / value); (b) differing callees with same signature in sibling classes -> Template Method / abstract hook; (c) differing callees in one class or void calls -> Strategy / functional parameter (lambda); (d) differing types with calls only to supertype methods -> common supertype or generics; (e) location: same class / siblings / unrelated-with-state -> Extract Method / Pull Up or Extract Superclass / Extract Class + delegate. Location is cheap to compute with a project index.
- The "Introduce utility method" outcome (static method) is banned in this checker; for unrelated classes the checker should recommend Extract Class (a collaborator both delegate to) instead — this is a design choice, not in the paper.
- Clone Type 1-4 taxonomy (Type 1 identical modulo whitespace/comments, Type 2 renamed identifiers/literals/types, Type 3 added/removed statements, Type 4 semantically equivalent) is standard (Roy & Cordy survey) — UNVERIFIED in this session.

### Gaps
- Krishnan & Tsantalis "Unification and refactoring of clones" (CSMR-WCRE 2014) and Higo et al.'s Aries (metrics on clone sets, e.g. DCH = distance in class hierarchy, to choose Pull Up vs Extract Method) were not opened — UNVERIFIED.
- Bulychev & Minea anti-unification (Clone Digger) and Baxter et al. 1998 CloneDR (AST hashing into buckets, similarity = 2S/(2S+L+R)) — recalled, UNVERIFIED.
- Which precondition is violated most often (exact distribution) is in the TSE paper's tables but was not extracted.

## 2. Higher-level / structural clones and conceptual duplication

### Takeaway
Basit & Jarzabek's Clone Miner treats repetition above the method as a data-mining problem: detect small simple clones first (lower token threshold than usual), then find sets of clone classes that co-occur in several files with Frequent Itemset Mining; recurring sets are "structural clones" (similar files, similar groups of classes). Their target abstraction is a generic design (meta-level XVCL in their work; in Java terms a common superclass, generic class or framework of collaborating classes).

### Cited Findings
- Clone Miner: "Clone Miner takes a bottom-up approach"; uses "the Frequent Itemset Mining (FIM) technique" over files as transactions and clone classes as items; output is a clone pattern with a support count = number of files containing it; then clusters highly cloned files; "similar files form basic structural clones"; "Structural clones: patterns of inter-related classes emerging from the simple clone data"; minimum clone length: "In CCFinder, the default value is set to 30 tokens, but in Clone Miner, it is advised to set a smaller threshold value to facilitate the detection of structural clones. Several small clones may form a bigger gapped clone" — [Basit & Jarzabek, FSE 2005 PDF](https://plg.uwaterloo.ca/~migod/846/papers/jarzabek-fse05-CloneMiner.pdf)
- Their goal is to "unify clone classes with generic design solutions" via XVCL meta-structures; motivating industrial example: a partner could not design a generic implementation for groups of similar classes across GUI, Service and other layers of domain entities (i.e. parallel layered classes per entity) — [FSE 2005 PDF](https://plg.uwaterloo.ca/~migod/846/papers/jarzabek-fse05-CloneMiner.pdf)
- TSE 2009 journal version "A Data Mining Approach for Detecting Higher-Level Clones in Software" presents clone detection as a data mining problem at various levels — [ResearchGate](https://www.researchgate.net/publication/220069338_A_Data_Mining_Approach_for_Detecting_Higher-Level_Clones_in_Software); related: "A case for structural clones" (IWSC 2009) — [academia.edu](https://www.academia.edu/74979464/A_case_for_structural_clones); Jarzabek's 2020 keynote "Software Similarity Patterns and Clones: A Curse or Blessing?" — [SciTePress](https://www.scitepress.org/PublishedPapers/2020/98200/98200.pdf)

### Inferences
- With a project index, the checker can reproduce the FIM idea cheaply: treat each class (or file) as a transaction whose items are its clone-class IDs (from the existing method-clone rule) plus "concept tokens" (switch case sets, field-type sets). Two or more classes sharing the same itemset of size >= 2 are a structural clone -> missing common superclass / generic class. Parallel Inheritance Hierarchies are the special case where the shared items are name stems across two hierarchies (Fowler: every subclass of A needs a subclass of B) — the detector design is my inference.
- Repeated conditionals: the same `switch`/if-chain case set over the same discriminator appearing in N >= 2 places is the classic "Replace Conditional with Polymorphism" trigger (Fowler's Switch Statements / Repeated Switches smell). Pengő & Gál's SFP-SCU (section 4) is a published, operational precursor: count uses of static-final primitive constants as case labels across the project.

### Gaps
- Marcus & Maletic, "Identification of high-level concept clones in source code" (ASE 2001; LSI over identifiers/comments to find concept clones such as several list implementations) — not opened, UNVERIFIED.
- No source opened that gives a published detector of Parallel Inheritance Hierarchies with precision figures.

## 3. State-of-practice clone detectors (JVM build usable), algorithms and defaults

### Takeaway
PMD CPD (Karp-Rabin over tokens, min tokens required, literal/identifier normalisation opt-in) is the only one that runs natively in a Maven build and has options that turn Type-1 into Type-2 detection; everything else either is token/line based (jscpd, Simian, Sonar) or research grade (NiCad, SourcererCC, Deckard, iClones).

### Cited Findings
- PMD CPD: rewritten "to use the Karp-Rabin string matching algorithm"; `--minimum-tokens` is required; "By default, CPD finds identical duplicates"; `--ignore-literals`, `--ignore-identifiers` (Java, C++) and `--ignore-annotations` are disabled by default; runs in Maven through the `cpd-check` goal of the Maven PMD plugin — [PMD CPD docs](https://pmd.github.io/pmd/pmd_userdocs_cpd.html)
- CCFinder default minimum clone length 30 tokens — [Clone Miner paper](https://plg.uwaterloo.ca/~migod/846/papers/jarzabek-fse05-CloneMiner.pdf)
- Default minimum clone mass in CloneDR is six AST nodes, which yields many single-statement clones — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- NiCad detects a large share of method-level clones (60% of its clones are method-level) while CCFinder only 5.3% — [TSE 2015](https://users.encs.concordia.ca/~nikolaos/publications/TSE_2015.pdf)
- CodeScene: "CodeScene detects duplicated logic that is actually changed together in predictable patterns" (duplication weighted by change coupling, not raw similarity) — [CodeScene Code Health docs](https://codescene.io/docs/guides/technical/code-health.html)

### Inferences
- UNVERIFIED defaults (recalled, not opened): Maven PMD plugin `minimumTokens` default 100; jscpd `min-tokens` 50, `min-lines` 5 (Rabin-Karp over tokens); Simian default threshold 6 lines; SonarQube Java duplication = at least 10 successive duplicated statements; NiCad: pretty-printed blocks, 10-2500 lines, 30% UPI dissimilarity threshold (Type-3); SourcererCC: bag-of-tokens with 70% overlap threshold and sub-block filtering; Deckard: AST characteristic vectors clustered with LSH; iClones: incremental suffix-tree; IntelliJ "Duplicated code fragment": AST-based with anonymisation of variables/fields/methods/literals and a cost threshold.
- For the checker, CodeScene's idea (duplication that co-changes) is a reminder that raw similarity alone over-reports; but a deterministic gate can't use history, so the diff-kind classification of section 1 is the better filter.

### Gaps
- Exact current defaults of jscpd, Simian, Sonar and IntelliJ not verified in this session.

## 4. Primitive obsession made operational

### Takeaway
Pengő & Gál (ICSOFT 2018) give the only fully operational, Java-implemented metric set found: relative (not absolute) primitive-parameter ratios at class and project level (LPE/GPE/HPE), Method Parameter Clones (same (type,name) primitive parameter >= 3 times in a class, rule of three) and SFP-SCU (static final primitive constants used as switch case labels = type codes). They state no absolute threshold is possible and there is no benchmark; validation was manual.

### Cited Findings
- PrimitiveTypes = boolean, byte, short, int, long, char, float, double, String (W+ variants add wrappers). LPE true for method Mc when class-wide primitive-parameter ratio < Mc's primitive-parameter ratio; GPE same against the whole project; HPE = LPE AND GPE; only methods with >= 1 parameter; "Skip Ones" excludes methods with < 2 parameters (Skip Setters was too noisy) — [Pengő & Gál, ICSOFT 2018](https://www.scitepress.org/Papers/2018/69188/pdf/index.html)
- MPC: for each class, build (type, name) pairs of primitive parameters and count each pair that appears at least three times ("The Rule of Three") — [ICSOFT 2018](https://www.scitepress.org/Papers/2018/69188/pdf/index.html)
- SFP: class-level static final upper-case-named primitive; SFP-SCU counts uses of SFP as switch case labels across the project, on the idea that type codes "most probably will appear in branching structures" — [ICSOFT 2018](https://www.scitepress.org/Papers/2018/69188/pdf/index.html)
- Results on log4j / joda-time / commons-math: reported methods "less than 8% of the total number of methods in average"; MPC flagged 6 / 50 / 90 classes (max 11 in log4j's logMF/logSF); including wrappers changed almost nothing; "impossible to find an absolute threshold"; "there is no benchmark for Primitive Obsession detection manual validation was performed"; enums excluded; tool built on OpenStaticAnalyzer — [ICSOFT 2018](https://www.scitepress.org/Papers/2018/69188/pdf/index.html)
- Follow-up: "Examining the Bug Prediction Capabilities of Primitive Obsession Metrics" (ICCSA 2021) — [Springer](https://link.springer.com/chapter/10.1007/978-3-030-87007-2_14); earlier short version at CSCS 2018 — [Acta Szeged](http://acta.bibl.u-szeged.hu/61785/1/cscs_2018_147-150.pdf); author's PhD booklet — [Szeged](https://doktori.bibl.u-szeged.hu/id/eprint/11376/2/pengoe_booklet_FINAL.pdf)
- CodeScene defines Primitive Obsession as "Code that uses a high degree of built-in, primitives such as integers, strings, floats" that "often lacks a domain language that encapsulates the validation and semantics of function arguments"; no numeric threshold on that page — [CodeScene Code Health](https://codescene.io/docs/guides/technical/code-health.html)
- Fowler's FlagArgument: an argument that "tells the function to carry out a different operation depending on its value"; transformation: separate methods (`regularBook`, `premiumBook` instead of `book(Customer, boolean)`); exceptions: keep a private flagged helper if splitting duplicates a tangled implementation; derive the behaviour from the object (e.g. customer status) instead of passing it; boolean setters fed by a UI checkbox — [Fowler bliki](https://martinfowler.com/bliki/FlagArgument.html)

### Inferences
- Deterministic rules derivable: (a) MPC exactly as defined, generalised to the project index (same (type,name) primitive parameter in >= 3 methods anywhere -> missing value object named after the parameter); (b) SFP-SCU generalised to "constant used as case label or in == comparisons in >= 2 places" -> missing enum / polymorphic type; (c) boolean parameter that is tested by an `if` in the callee and passed as a literal by callers -> FlagArgument (two methods, or the decision belongs to an object — the second Fowler exception maps to "move the decision to the object that owns the state"); (d) Fowler's "derived flags" exception is itself a smell detector: caller computes `x.isPremium()` and passes it -> Feature Envy on the flag.
- "Whole Value"/CHECKS (Cunningham), Evans' Value Object, Fowler's Quantity/Money/Range patterns are the target models; Range is the recommended model for a recurring (start,end)/(min,max) parameter pair — UNVERIFIED, not opened.

### Gaps
- CodeScene's "String Heavy Function Arguments" and "Missing Arguments Abstractions" definitions and thresholds: docs page for code smells returned 404; not found elsewhere. Recalled (UNVERIFIED): String Heavy flags modules where a large share (around 39%?) of function arguments are strings; Missing Arguments Abstractions flags modules where many functions take many arguments, suggesting argument objects.
- Empirical studies of boolean parameters / "boolean blindness" detectors: none found in budget.
- c2 wiki StringlyTyped / ParallelArrays pages not opened.

## 5. Data clumps, parallel collections and tramp data

### Takeaway
The precise Zhang et al. (2008) definition, as restated by Baumgartner et al. (2023), is: same fields (name, type, visibility) shared by two or more classes, or same parameters (name, type) shared by two or more method declarations, order-independent, excluding methods related by overriding; the threshold is stated as "more than three". Baumgartner's group implemented it live in IntelliJ (median < 0.5 s on ArgoUML) and later as an LLM (ChatGPT) detection-and-refactoring pipeline with occasional compile errors.

### Cited Findings
- Fields instance: "More than three data fields are shared in two or more classes"; "The data fields have the same signatures, consisting of names, types and visibility"; order-independent. Parameters instance: "More than three input parameters are shared in two or more method declarations", same signatures, order-independent, and "The same inheritance hierarchy and method signature should not be present in these methods" (overrides excluded) — [Baumgartner, Adleh, Pulvermüller, ENASE 2023](https://www.scitepress.org/Papers/2023/117275/117275.pdf), citing Zhang et al. 2008 and Hall et al. 2014
- The live IntelliJ plug-in detects field-field, parameter-parameter and parameter-field clumps with generated suggestions and semi-automatic refactoring (Extract Class, then replace parameters, "at least two fields have been replaced"); median < 0.5 s for ArgoUML — [ENASE 2023](https://www.scitepress.org/Papers/2023/117275/117275.pdf)
- LLM pipeline: "AI-Driven Refactoring: A Pipeline for Identifying and Correcting Data Clumps in Git Repositories" (Electronics 13(9):1644, 2024) uses ChatGPT with a human in the loop — [MDPI](https://MDPI.com/2079-9292/13/9/1644/review_report); extended as "The Scalable Detection and Resolution of Data Clumps Using a Modular Pipeline with ChatGPT" (Software 4(1):3, 2025), which reports accurate identification especially where semantic understanding is needed, but occasional compiler errors and high computational cost — [MDPI Software](https://mdpi.com/2674-113X/4/1/3)
- Reek (Ruby) states the smell as "the same two or three items frequently appear together in classes and parameter lists" — [Reek Data-Clump docs](https://codeclimate.com/github/troessner/reek/docs/Data-Clump.md/source)

### Inferences
- Note the threshold disagreement: Zhang via Baumgartner says "more than three"; Reek says "two or three"; Fowler's informal test (delete one, do the others still make sense?) has no number. The checker can use >= 3 with name+type matching and use the project index to count occurrences.
- Parallel collections (several `Map<K, …>` fields with the same key type in one class, or the same index variable used into two lists/arrays in one loop) are the field-level data clump in disguise: the missing class is "the value of K" with the map values as its fields, and the maps collapse into one `Map<K, NewClass>` or into a field of K itself. No published detector for this was found; it is a straightforward AST rule (same key type across >= 2 map fields; `a.get(i)` and `b.get(i)` with the same `i`).
- Tramp data (a parameter passed through methods that only forward it) maps to "the object that needs it should hold it" (constructor injection / move the method); no source opened.

### Gaps
- The original Zhang, Baddoo, Wernick, Hall 2008 paper was not found online in this session; its wording is known only through Baumgartner's restatement.
- Long Parameter List thresholds (PMD ExcessiveParameterList default 10; Checkstyle ParameterNumber default 7) — UNVERIFIED.

## 6. Identifier evidence of a hidden concept; linguistic antipatterns; -er/Manager/Util names

### Takeaway
Arnaoudova et al.'s linguistic antipatterns (CSMR 2013, extended in EMSE 2016) are a catalogue of mismatches between a name and its signature/type/comment (e.g. "Get" that does more than access, "Is" not returning boolean, "Says one but contains many"); their LAPD detector for Java reports 72% average precision, with the "opposite meaning" cases below 10%. Name-prefix/suffix grouping as an Extract Class signal, and class-name suffixes (Manager/Helper/Util) as procedural signals, were not verified in this budget.

### Cited Findings
- CSMR 2013 catalogue entries (families A-F): A "Get" - more than an accessor; "Is" returns more than a Boolean; "Set" method returns; Expecting but not getting a single instance; B Not implemented condition; Validation method does not confirm; "Get" method does not return; Not answered question; Transform method does not return; Expecting but not getting a collection; C Method name and return type are opposite; Method signature and comment are opposite; D Says one but contains many; Name suggests Boolean but type does not; E Says many but contains one; F Attribute name and type are opposite; Attribute signature and comment are opposite — [Arnaoudova et al., CSMR 2013 PDF](https://assets.ptidej.net/Publications/Documents/CSMR13d.doc.pdf)
- "LAPD has an average precision of 72%"; the two "...and comment are opposite" detectors are below 10% "due to the difficulty of capturing opposite meaning" — [CSMR 2013 PDF](https://assets.ptidej.net/Publications/Documents/CSMR13d.doc.pdf)
- EMSE extension: "Linguistic antipatterns: what they are and how developers perceive them", EMSE 21(1):104-158, 2016 — [Springer](https://link.springer.com/article/10.1007/s10664-014-9350-8)
- Identifier appraisal tooling exists (IDEAL, 2021) — [arXiv 2107.08344](https://arxiv.org/pdf/2107.08344)

### Inferences
- Many LA checks are deterministic over signatures alone (no NLP): "is*/has*" not returning boolean, "get*" returning void, plural name with scalar type and vice versa. They do not point to a missing model by themselves, but "Says many but contains one" / a plural-named `String` often marks a stringly typed collection (missing List/Set of a value type).
- Shared prefix/suffix rule (inference, matching Fowler's Extract Class wording "common prefixes or suffixes for a subset of the data"): fields `startX, startY, endX, endY` or methods `orderTotal, orderTax` -> group by stem, >= 2 fields + methods using them together -> Extract Class named after the stem. Bavota/De Lucia/Oliveto and JDeodorant Extract Class use structural + semantic cohesion to split classes — UNVERIFIED in this session.

### Gaps
- Empirical study of class-name suffixes (Manager, Helper, Util, -er) as procedural/anaemic signals: none found in budget.
- EMSE 2016 final catalogue size and its developer-perception figures not extracted.

## 7. Micro patterns, nano-patterns and other structural vocabularies

### Takeaway
Gil & Maman (OOPSLA 2005) defined 27 machine-recognisable class-level micro patterns over ~70,000 Java classes covering about 75% of them; some are considered anti-patterns relative to OO design (e.g. data-only or function-only classes). Later work studied them as stability and agile-quality indicators. A purpose-built catalogue of "anti-structures" with transformations was not found.

### Cited Findings
- Micro patterns: 27 patterns over Java classes/interfaces, corpus ~70,000 classes, ~75% matched — [ACM DL](https://dl.acm.org/doi/10.1145/1094811.1094819); [Semantic Scholar](https://www.semanticscholar.org/paper/Micro-patterns-in-Java-code-Gil-Maman/1d45968bbe9c4b2f5c1fafa0cfde999fd4fcee20)
- Some micro patterns are treated as anti-micro-patterns conflicting with OO design; studied as stability indicators — [ResearchGate, "Could Micro Patterns be Used as Software Stability Indicators?"](https://www.researchgate.net/publication/271384895_Could_Micro_Patterns_be_Used_as_Software_Stability_Indicators); agile study — [Springer, "Micro Patterns in Agile Software"](https://link.springer.com/chapter/10.1007/978-3-642-38314-4_15)
- Java nano-patterns (method-level) — [ACM DL](https://dl.acm.org/doi/10.1145/1900008.1900089)
- Micro patterns re-applied to Solidity (2025) — [arXiv 2505.01282](https://arxiv.org/html/2505.01282)

### Inferences
- UNVERIFIED pattern list (recalled): Degenerate: Designator, Taxonomy, Joiner, Pool, Function Pointer, Function Object, Cobol Like; Containment: Stateless, Common State, Immutable, Restricted Creation, Sampler, Box, Compound Box, Canopy, Record, Data Manager, Sink; Inheritance: Outline, Trait, State Machine, Pure Type, Augmented Type, Pseudo Class, Implementor, Overrider, Extender. The ones a design gate would treat as "anti-structures": Cobol Like (one static method, static fields = procedure), Pool (only static final fields = constant bag -> enum/type), Record (public fields, no methods = data class), Data Manager (only getters/setters), Stateless/Common State (utility class), Pseudo Class (abstract class that should be interface). Each has a canonical transformation (move behaviour to data; constants -> enum with behaviour; utility -> instance collaborator).
- Kim, Pan & Whitehead (MSR 2006) and Destefanis et al. related some micro patterns to fault-proneness — UNVERIFIED which ones.
- Elemental design patterns (Smith & Stotts), MARPLE micro-structures (Arcelli Fontana), idiom mining (Allamanis & Sutton FSE 2014) — not opened, UNVERIFIED.

### Gaps
- Full Gil & Maman definitions and which patterns correlate with defects were not read (ACM paywall).
- No source found that defines an "anti micro pattern" vocabulary paired with transformations; this appears to be an open niche.

## 8. Recognisable structures of inexperienced programmers

### Takeaway
De Ruvo et al. (ACE 2018) define "semantic style" and 16 indicators found in ~19,000 submissions from 900+ novices; half the students showed two or more, and they persist into fourth-year students; most concern faulty if/else constructs. Later work turned these into conditional-statement anti-patterns. An OO-specific novice catalogue (parallel arrays, flags-as-state, static everything) was not found in a verifiable source.

### Cited Findings
- "Understanding semantic style by analysing student code" (ACE '18): defines semantic style, 16 indicators "may be manifestations of poor knowledge of some programming concepts", almost 19,000 samples from over 900 novice students; half the students had two or more indicators; also present in fourth-year students — [ACM DL](https://dl.acm.org/doi/10.1145/3160489.3160500)
- Follow-ups on students' conditional anti-patterns (2024) — [arXiv 2410.18989](https://arxiv.org/html/2410.18989v1); resource for novices refactoring conditionals — [ACM](https://dx.doi.org/10.1145/3502718.3524810); "Novice Use of the Java Programming Language" (TOCE) — [ACM DL](https://dl.acm.org/doi/10.1145/3551393); Code Style Model for educators — [ACM DL](https://dl.acm.org/doi/10.1145/3716861)

### Inferences
- The De Ruvo indicators are mostly intra-method (redundant if/else, returning boolean via if, etc.) — UNVERIFIED individual list. They are cheap AST rules but point to "simplicity", not to a missing model.
- For OO-model defects, the novice structures listed in the brief (state in flags, parallel arrays, giant controller, getters/setters everywhere, static everything, string-encoded data) are covered better by the rules of sections 4, 5 and 7 than by the education literature found.

### Gaps
- Keuning, Heeren & Jeuring (ITiCSE 2017, code quality issues in student programs, PMD-based) and Effenberger & Pelánek (2022) not opened — UNVERIFIED.
- No catalogue of OO-specific novice anti-patterns found.

## 9. Anaemic domain model / data class + god class pairs

### Takeaway
Not researched within the budget beyond what micro patterns (Record, Data Manager) and data clumps already give.

### Cited Findings
- CodeScene measures Low Cohesion with LCOM4 ("the module/class has multiple unrelated responsibilities") — [CodeScene Code Health](https://codescene.io/docs/guides/technical/code-health.html)

### Inferences
- Pair rule (inference): a class that is mostly getters/setters (Data Manager/Record micro pattern) whose getters are called mostly from one other class that has many methods taking it as parameter -> behaviour belongs on the data class (Move Method; Fowler's AnemicDomainModel). Detection with a project index: for each data class D, count call sites of D's accessors per caller class; one dominant caller = Feature Envy pair. The Marinescu/Lanza detection strategies (Data Class: WOC < 1/3, NOAP+NOAM > threshold; God Class: ATFD > few, WMC very high, TCC < 1/3) are the standard thresholds — UNVERIFIED.

### Gaps
- Precision of Data Class / God Class detection strategies and anaemic-model detectors not verified; Fowler's AnemicDomainModel bliki not opened.
