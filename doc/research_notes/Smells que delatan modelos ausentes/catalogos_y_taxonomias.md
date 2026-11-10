# Catalogues and taxonomies that map a smell to a missing abstraction and its repair

Scope: published smell catalogues that go from a code structure to the concept, class, hierarchy or pattern that is missing, and on to the refactoring that repairs it. Java and class-based OO only. "Verified" means read in a source opened during this session. Anything else is labelled UNVERIFIED (recalled from training, not re-read).

Sources actually opened:
- Industrial Logic, "Smells to Refactorings Quick Reference Guide" (Jan 2006 PDF, combines Fowler 1999 and Kerievsky 2004 with page numbers). Read via pdftotext: https://www.industriallogic.com/img/blog/2005/09/smellstorefactorings.pdf
- Industrial Logic's Refactoring to Patterns catalogue: the index plus all 27 refactoring pages (Problem and Solution text). https://www.industriallogic.com/xp/refactoring/catalog.html and https://www.industriallogic.com/refactoring-to-patterns/catalog/<name>.html
- Ganesh, Sharma, Suryanarayana, "Towards a Principle-based Classification of Structural Design Smells", JOT 12(2) 2013, full text: https://www.jot.fm/issues/issue_2013_06/article1.pdf
- Jerzyk & Madeyski's online catalogue (56 smells). Read from its source repo (front-matter fields `refactors`, `known_as`, `history` and the body text): https://luzkan.github.io/smells/ and https://github.com/Luzkan/smells
- Riel's 60 heuristics, sequential numbering, from a list page (secondary transcription): http://www.vincehuston.org/ood/oo_design_heuristics.html
- Figueiredo (UFMG) lecture slides that restate Lanza & Marinescu's detection strategies (secondary): https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf
- William Durand's summary of Object Calisthenics (secondary): https://williamdurand.fr/2013/06/03/object-calisthenics/
- Secondary pages for the 2014 book's smell names: https://pmateti.github.io/Courses/7140/Lectures/OODesign/design-smells.html (says it is based wholly on Wikipedia "Design smell")

---

## Q1. Kerievsky, Refactoring to Patterns (2004): smell → refactoring table, and the trigger for each refactoring that introduces a model

### Takeaway
Kerievsky adds five smells to Fowler's list: Conditional Complexity, Indecent Exposure, Solution Sprawl, Combinatorial Explosion and Oddball Solution. Each catalogue entry is a "Problem" sentence, and that sentence is a ready-made detection criterion. The ones that introduce a model are:
- "accumulates information from heterogeneous classes" → Visitor
- "implicitly form a tree ... using a primitive representation, such as a String" → Composite
- "methods on a class combine elements of an implicit language" → Interpreter
- "conditional logic is used to dispatch requests" → Command
- "conditional expressions that control an object's state transitions" → State
- "conditional logic controls which of several variants of a calculation" → Strategy
- "a field's type (String or int) fails to protect it" → a class for the type code
- "null logic duplicated" → Null Object
- "processes single and multiple objects using separate pieces of code" → Composite
- "subclasses hard-coded to notify a single instance" → Observer

### Cited Findings

**Industrial Logic quick reference.** Columns are smell, description and refactorings. F = Fowler 1999 page, K = Kerievsky 2004 page. Source: [Industrial Logic Smells to Refactorings PDF](https://www.industriallogic.com/img/blog/2005/09/smellstorefactorings.pdf). The two-column layout makes a few row boundaries ambiguous; those are marked (?).

| Smell | Description (quoted or paraphrased from the card) | Refactorings listed |
|---|---|---|
| Alternative Classes with Different Interfaces [F85, K43] | "interfaces of two classes are different and yet the classes are quite similar ... refactor the classes to make them share a common interface" | Unify Interfaces with Adapter [K247], Rename Method, Move Method |
| Combinatorial Explosion [K45] | "A subtle form of duplication ... numerous pieces of code do the same thing using different combinations of data or behavior" | Replace Implicit Language with Interpreter [K269] |
| Comments (Deodorant) [F87] | refactor so the comment becomes superfluous | Rename Method, Extract Method, Introduce Assertion |
| Conditional Complexity [K41] | conditional logic "rarely ages well ... becomes complicated and expansive" | Introduce Null Object [F260, K301], Move Embellishment to Decorator [K144], Replace Conditional Logic with Strategy [K129], Replace State-Altering Conditionals with State [K166] |
| Data Class [F86] | "fields, getting and setting methods ... and nothing else ... manipulated in far too much detail by other classes" | Move Method, Encapsulate Field, Encapsulate Collection |
| Data Clumps [F81] | "Bunches of data that hang around together really ought to be made into their own object." Deletion test: "if you did this [deleted one value], would the others make any sense? ... an object that's dying to be born" | Extract Class, Preserve Whole Object, Introduce Parameter Object |
| Divergent Change [F79] | one class "commonly changed in different ways for different reasons" | Extract Class |
| Duplicated Code [F76, K39] | "explicit ... identical code, ... subtle duplication exists in structures or processing steps that are outwardly different, yet essentially the same" | Chain Constructors [K340], Extract Composite [K214], Extract Method, Extract Class, Form Template Method [F345, K205], Introduce Null Object, Introduce Polymorphic Creation with Factory Method [K88], Pull Up Method, Pull Up Field, Replace One/Many Distinctions with Composite [K224], Substitute Algorithm, Unify Interfaces with Adapter [K247] |
| Feature Envy [F80] | "Data and behavior that acts on that data belong together" | Extract Method, Move Method, Move Field |
| Freeloader / Lazy Class [F83, K43] | "isn't doing enough to pay for itself" | Collapse Hierarchy, Inline Class, Inline Singleton [K114] |
| Inappropriate Intimacy [F85] | classes "delving into each others' private parts" | Move Method, Move Field, Change Bidirectional Association to Unidirectional, Extract Class, Hide Delegate, Replace Inheritance with Delegation |
| Incomplete Library Class [F86] | — | Introduce Foreign Method, Introduce Local Extension |
| Indecent Exposure [K42] | "lack of ... information hiding [Parnas] ... methods or classes that ought not to be visible to clients are publicly visible" | Encapsulate Classes with Factory [K80] |
| Large Class [F78, K44] | "too many instance variables usually indicates that a class is trying to do too much" | Extract Class, Extract Subclass, Extract Interface, Replace Data Value with Object, Replace Conditional Dispatcher with Command [K191], Replace Implicit Language with Interpreter [K269], Replace State-Altering Conditionals with State [K166] |
| Long Method [F76, K40] | short methods share logic and explain code | Extract Method, Compose Method [K123], Introduce Parameter Object, Move Accumulation to Collecting Parameter [K313], Move Accumulation to Visitor [K320], Decompose Conditional, Preserve Whole Object, Replace Conditional Dispatcher with Command [K191], Replace Conditional Logic with Strategy [K129], Replace Method with Method Object, Replace Temp with Query |
| Long Parameter List [F78] | — | Replace Parameter with Method, Introduce Parameter Object, Preserve Whole Object |
| Message Chains [F84] | "long sequence of method calls or temporary variables to get some data" | Hide Delegate, Extract Method, Move Method |
| Middle Man [F85] | objects "simply passing messages on" | Remove Middle Man, Inline Method, Replace Delegation with Inheritance |
| Oddball Solution [K45] | "a problem is solved one way throughout a system and the same problem is solved another way ... usually indicates subtly duplicated code" | Unify Interfaces with Adapter [K247] |
| Parallel Inheritance Hierarchies [F83] | "special case of Shotgun Surgery – every time you make a subclass of one class, you have to make a subclass of another" | Move Method, Move Field |
| Primitive Obsession [F81, K41] | "typically occurs when you haven't yet seen how a higher-level abstraction can clarify or simplify your code"; "once you create a class, you'll often discover how other code in a system belongs in that class" | Replace Data Value with Object, Encapsulate Composite with Builder [K96], Introduce Parameter Object, Extract Class, Move Embellishment to Decorator [K144], Replace Conditional Logic with Strategy [K129], Replace Implicit Language with Interpreter [K269], Replace Implicit Tree with Composite [K178], Replace State-Altering Conditionals with State [K166], Replace Type Code with Class [F218, K286], Replace Type Code with State/Strategy [F227], Replace Type Code with Subclasses [F223], Replace Array with Object |
| Refused Bequest [F87] | "inheriting code you don't want" | Push Down Field, Push Down Method, Replace Inheritance with Delegation |
| Shotgun Surgery [F80] | "change lots of pieces of code in different places simply to add a new or extended piece of behavior" | Move Method, Move Field, Inline Class |
| Solution Sprawl [K43] | "code and/or data used in performing a responsibility becomes sprawled across numerous classes ... quickly adding a feature ... without ... consolidating the design" | Move Creation Knowledge to Factory [K68] |
| Speculative Generality [F83] | generic or abstract code not needed today | Collapse Hierarchy, Rename Method, Remove Parameter, Inline Class |
| Switch Statement [F82, K44] | "the same switch statement (or 'if…else if…else if' statement) is duplicated across a system ... reveals a lack of object-orientation" | Move Accumulation to Visitor [K320] (?), Replace Conditional Dispatcher with Command [K191], Replace Conditional with Polymorphism [F255], Replace Type Code with Subclasses, Replace Type Code with State/Strategy, Replace Parameter with Explicit Methods, Introduce Null Object |
| Temporary Field [F84] | "fields that don't seem to be needed all the time" | Extract Class, Introduce Null Object |

**The 27 Refactoring to Patterns entries, Problem → Solution, as written on Industrial Logic's catalogue pages** (e.g. [implicitTreeWithComposite](https://www.industriallogic.com/refactoring-to-patterns/catalog/implicitTreeWithComposite.html); all pages linked from the [catalogue index](https://www.industriallogic.com/xp/refactoring/catalog.html)):

| Refactoring | Problem (trigger, quoted) | Solution / missing model |
|---|---|---|
| Move Accumulation to Visitor | "A method accumulates information from heterogeneous classes." | "Move the accumulation task to a Visitor that can visit each class to accumulate the information." The missing model is double dispatch over a heterogeneous structure. |
| Move Accumulation to Collecting Parameter | "You have a single bulky method that accumulates information to a local variable." | "Accumulate results to a Collecting Parameter that gets passed to extracted methods." |
| Replace Implicit Tree with Composite | "You implicitly form a tree structure, using a primitive representation, such as a String." | "Replace your primitive representation with a Composite." |
| Replace Implicit Language with Interpreter | "Numerous methods on a class combine elements of an implicit language." | "Define classes for elements of the implicit language so that instances may be combined to form interpretable expressions." |
| Replace Conditional Dispatcher with Command | "Conditional logic is used to dispatch requests and execute actions." | "Create a Command for each action. Store the Commands in a collection and replace the conditional logic with code to fetch and execute Commands." |
| Replace State-Altering Conditionals with State | "The conditional expressions that control an object's state transitions are complex." | "Replace the conditionals with State classes that handle specific states and transitions between them." |
| Replace Conditional Logic with Strategy | "Conditional logic in a method controls which of several variants of a calculation are executed." | "Create a Strategy for each variant and make the method delegate the calculation to a Strategy instance." |
| Replace Type Code with Class | "A field's type (e.g. a String or int) fails to protect it from unsafe assignments and invalid equality comparisons." | "Constrain the assignments and equality comparisons by making the type of the field a class." |
| Introduce Null Object | "Logic for dealing with a null field or variable is duplicated throughout your code." | "Replace the null logic with a Null Object – an object that provides the appropriate null behavior." |
| Form Template Method | "Two methods in subclasses perform similar steps in the same order, yet the steps are different." | "Generalize the methods by extracting their steps into methods with identical signatures, then pull up the generalized methods to form a Template Method." |
| Extract Composite | "Subclasses in a hierarchy implement the same Composite." | "Extract a superclass that implements the Composite." |
| Replace One/Many Distinctions with Composite | "A class processes single and multiple objects using separate pieces of code." | "Use a Composite to produce one piece of code capable of handling single or multiple objects." |
| Move Embellishment to Decorator | "Code provides an embellishment to a class' core responsibility." | "Move the embellishment code to a Decorator." |
| Replace Hard-Coded Notifications with Observer | "Subclasses are hard-coded to notify a single instance of another class." | "Remove the subclasses by making their superclass capable of notifying one or more instances of any class that implements an Observer interface." |
| Unify Interfaces with Adapter | "Clients interact with two classes, one of which has a preferred interface." | "Unify the interfaces with an Adapter." |
| Extract Adapter | "One class adapts multiple versions of a component, library, API or other entity." | "Extract an Adapter for a single version ..." |
| Unify Interfaces | "You need a superclass and/or interface to have the same interface as a subclass." | Add the missing public methods to the superclass with null behaviour |
| Encapsulate Composite with Builder | "Building a Composite is repetitive, complicated or error-prone." | "Simplify the build by letting a Builder handle the details." |
| Move Creation Knowledge to Factory | "Data and code used to instantiate a class is sprawled across numerous classes." | "Move the creation knowledge into a single Factory class." |
| Encapsulate Classes with Factory | "Clients directly instantiate classes that reside in one package and implement a common interface." | "Make the class constructors non-public and let clients create instances of them using a Factory." |
| Introduce Polymorphic Creation with Factory Method | "Classes in a hierarchy implement a method similarly except for an object creation step." | "Make a single superclass version of the method that calls a Factory Method to handle the instantiation." |
| Replace Constructors with Creation Methods | "Constructors on a class make it hard to decide which constructor to call during development." | intention-revealing Creation Methods |
| Chain Constructors | "You have Constructors that contain duplicate code." | "Chain the constructors together to obtain the least duplicate code." |
| Compose Method | "You can't rapidly understand a method's logic." | "Transform the logic into a small number of intention-revealing steps at the same level of detail." |
| Extract Parameter | "A method or constructor assigns a field to a locally instantiated value." | assign the field from a client-supplied parameter |
| Inline Singleton | "Code needs access to an object but doesn't need a global point of access to it." | move the Singleton's features to the class that holds the object; delete the Singleton |
| Limit Instantiation with Singleton | "Your code creates multiple instances of an object and that uses too much memory or slows system performance." | Singleton |

### Inferences
- The Problem sentences can be turned almost directly into AST criteria:
  - Visitor: a method that branches by `instanceof` or type over the elements of one collection or structure, and accumulates.
  - Composite: string concatenation or StringBuilder that writes nested open/close tags; or two code paths, one for a single X and one for `List<X>`.
  - Interpreter: several methods whose names combine terms (e.g. `belowPriceAndColor`, `byColorAndBelowSize`). Kerievsky's own example of Combinatorial Explosion is product finders; that example is UNVERIFIED in this session.
  - Command: an if/switch on a request name, string or key, where each branch runs an action.
  - State: a field that is both tested and reassigned inside the same conditionals.
  - Strategy: a branch that selects one of several formulas.
  - Class for the type code: a String or int field compared with `==`/`equals` against constants.
- Several of these overlap with the checker's existing ban level (switch/type dispatch). The new information is the target pattern, picked by what the branches do:
  - branches run actions → Command
  - branches compute variants of one value → Strategy
  - branches change the object's own state field → State
  - branches accumulate over heterogeneous elements → Visitor
  - branches add optional behaviour around a core → Decorator
  - branches test for null → Null Object

### Gaps
- The full Motivation sections of the book, with the longer smell descriptions and the "Benefits and Liabilities" boxes, are not online. Industrial Logic's pages show only Problem and Solution, so the motivation text is not verified.
- The PDF layout leaves the row for Move Accumulation to Visitor ambiguous (Speculative Generality or Switch Statement). Recall says Switch Statement in Kerievsky's own smell table (UNVERIFIED).

---

## Q2. Suryanarayana, Samarthyam & Sharma (2014), and Ganesh, Sharma & Suryanarayana (JOT 2013): principle-based design smells

### Takeaway
The 2013 JOT paper is the precursor. It catalogues 31 structural smells under four principles: Abstraction, Encapsulation, Modularity, Hierarchy. For each smell it gives a definition and an alias map to about 210 smells in the earlier literature, which makes it a Rosetta stone across catalogues. The 2014 book trims the list to 25 and renames the smells, but the full text of the book was not accessible. Two smells point straight at a missing model:
- Missing Hierarchy (JOT): "conditional logic on embedded features ... ('tagging' indicates an unexploited is-a relationship)".
- Unexploited Hierarchy, renamed Unexploited Encapsulation in the book: "explicit condition checks instead of leveraging the polymorphism inherent in the hierarchy".

### Cited Findings (JOT 2013; definitions quoted from [Ganesh et al. 2013](https://www.jot.fm/issues/issue_2013_06/article1.pdf))
Abstraction:
- **Incomplete Abstraction**: "an abstraction does not support a responsibility completely", or "a responsibility is split among two or more abstractions". Variant: "solution sprawl" [Kerievsky].
- **Passive Abstraction**: "a class is used as a holder for data, without any methods operating on it". Aliases: data class, record, C-like structs, no-command classes. Variant: data clumps.
- **Delegating Abstraction**: exists "only for passing messages". Alias: middle man.
- **Imperative Abstraction**: "an operation is turned into a class abstraction". Detection criteria quoted: "(a) the class name containing only a verb (or start with a verb), (b) having only one method with mostly the same name as the class, (c) the class shares no inheritance relationships." Aliases: operation class, method turned into class, single-routine classes.
- **Trivial Abstraction**: lazy class, freeloader.
- **Unutilized Abstraction**: unreferenced concrete classes, and orphan interfaces or abstract classes with no subtypes.
- **Overlooked Abstraction**: "clients refer to concrete types instead of their abstract base types", either because no base type exists or because clients bypass it. Aliases: interface bypass, short-circuited abstraction.
- **Duplicate Abstraction**: two or more similar abstractions "that share commonalities that have not been captured", by identical name, identical public interface or identical implementation. Alias: alternative classes with different interfaces.
- **Large Abstraction** (god class, blob); **Dense Abstraction** (spaghetti code); **Controlling Abstraction** (brain class, controller class).
- **Multifaceted Abstraction**: more than one responsibility. Aliases: divergent change, Swiss army knife.
- **Envious Abstraction**: feature envy, message chains.

Encapsulation:
- **Deficient Encapsulation**: either *lenient* (accessibility more permissive than required) or *vulnerable* (implementation details exposed). Alias: indecent exposure.
- **Unrestrained Encapsulation**: depends on global state.
- **Violated Encapsulation**: inappropriate intimacy.

Modularity:
- **Weakened Modularity**, cyclic form.
- **Weakened Modularity**, central form: god class, dispersed coupling, intensive coupling.

Hierarchy:
- **Folded Hierarchy**: "a suitable supertype could be identified ... but is currently missing", or data, behaviour or interface duplicated across abstractions "that could be factored out to a suitable supertype". Aliases: missing abstract class, orphan sibling method/attribute.
- **Missing Hierarchy**: "an abstraction uses conditional logic on embedded features to determine behavior (such 'tagging' indicates an unexploited is-a relationship)". Aliases: "Tag class" [Bloch 2008], missing inheritance, embedded features.
- **Unexploited Hierarchy**: "explicit condition checks instead of leveraging the polymorphism inherent in the hierarchy ... includes the case where explicit type checks are performed". Aliases: simulated polymorphism, improper use of switch statement, case analysis, instanceof checks, self type checks, type queries.
- **Complex Hierarchy**: wide, deep, tangled or skewed.
- **Convenience Hierarchy**: supertypes with many concrete methods that subtypes never override, i.e. inheritance used for reuse.
- **Rebellious Hierarchy**: the subtype rejects the supertype's methods (rebellious interface) or violates its contract (rebellious implementation). Alias: refused bequest.
- **Broken Hierarchy**: no conceptual is-a. Aliases: "object or variable turned into a subclass", "tradition breaker" [Lanza & Marinescu].
- **Cyclic Hierarchy**: a supertype refers to a subtype. Alias: base class depends on derived classes.
- **Polygonal Hierarchy**: diamond inheritance.
- **Unnecessary Hierarchy**: at most one concrete subtype. Aliases: taxomania, speculative generality.
- **Degraded Hierarchy**: concrete root, abstract leaves.
- **Intimate Hierarchy**: subtype accesses the supertype's state.
- **Parallel Hierarchy**.

2014 book names, secondary sources only ([pmateti course page derived from Wikipedia](https://pmateti.github.io/Courses/7140/Lectures/OODesign/design-smells.html)):
- **Missing Abstraction**: "clumps of data or encoded strings are used instead of creating an abstraction". Aliases: primitive obsession, data clumps.
- Multifaceted Abstraction; Duplicate Abstraction.
- **Deficient Encapsulation**.
- **Unexploited Encapsulation**: client code uses explicit type checks (chained if-else or switch on type) instead of the variation already in the hierarchy. Alias: simulated polymorphism.
- **Broken Modularization**: data or methods that belong together are split across abstractions. Alias: data class.
- **Insufficient Modularization** (God class, fat interface); **Cyclically-dependent Modularization**.
- **Unfactored Hierarchy**: sibling duplication.
- **Broken Hierarchy**; **Cyclic Hierarchy**.

Remaining 2014 names: Imperative, Incomplete, Unnecessary and Unutilized Abstraction; Leaky and Missing Encapsulation; Hub-like Modularization; Missing, Unnecessary, Wide, Speculative, Deep, Rebellious and Multipath Hierarchy. Their book definitions are UNVERIFIED here. Recall:
- **Leaky Encapsulation**: the public interface exposes implementation details, e.g. returning a mutable internal collection.
- **Missing Encapsulation**: a variation is not encapsulated, so it is scattered over the hierarchy or the clients. Example: classes like `EncryptDES` and `EncryptAES`, or each client deciding the variant. Suggested refactoring: encapsulate the variation with Strategy or Bridge.
- **Missing Hierarchy (2014)**: conditional logic on "type codes"; refactoring Replace Conditional with Polymorphism / extract hierarchy.
- **Unfactored Hierarchy**: refactoring Pull Up.

### Inferences
- The JOT alias lists are the most useful artefact for unifying names across catalogues. For example, Missing Hierarchy, Tag class, embedded features and Switch Statement are one concept, and the paper shows that.
- The Imperative Abstraction criteria (verb name, one method named like the class, no hierarchy) are mechanically checkable. They point to a missing owner class, which is also Riel heuristic 20.

### Gaps
- The book's per-smell "Rationale / Potential causes / Example / Suggested refactoring" sections are behind O'Reilly and were not opened. The definitions and refactorings of the remaining 14 smells are UNVERIFIED. The author slides on Slideshare (https://www.slideshare.net/sgganesh/refactoring-for-design-smells) were not opened.

---

## Q3. Fowler, Refactoring 2nd ed. (2018): the smell list and its refactorings

### Takeaway
The 2nd edition list, recalled: Mysterious Name, Duplicated Code, Long Function, Long Parameter List, Global Data, Mutable Data, Divergent Change, Shotgun Surgery, Feature Envy, Data Clumps, Primitive Obsession, Repeated Switches, Loops, Lazy Element, Speculative Generality, Temporary Field, Message Chains, Middle Man, Insider Trading, Large Class, Alternative Classes with Different Interfaces, Data Class, Refused Bequest, Comments. The list itself is UNVERIFIED against the book. The refactorings below come from the online catalogue's mapping, which cites Fowler 2018 and is a secondary source.

### Cited Findings ([Jerzyk & Madeyski online catalogue](https://github.com/Luzkan/smells), `refactors` field)
- **Repeated Switches**: an alias of Conditional Complexity. Refactorings: guard clause, extract conditional, "Replace with Polymorphism", Strategy, Null Object. The catalogue's text: one switch "usually executes code scattered around the code base and should usually be replaced with a polymorphism solution".
- **Primitive Obsession**: Replace Data Value with Object, Extract Class, Introduce Parameter Object, Replace Array with Object, Replace Type Code with Class, Replace Type Code/Conditional Logic with State/Strategy, Move Embellishment to Decorator.
- **Data Clump**: Extract Class, Introduce Parameter Object.
- **Feature Envy**: Move Method, Move Field, Extract Method.
- **Message Chain**: Hide Delegate, Extract Method, Move Method.
- **Middle Man**: Remove Middle Man, Inline Method, Replace Delegation with Inheritance, Replace Superclass with Delegate.
- **Insider Trading**: Move Method, Move Field, Encapsulate Field, Replace Inheritance with Delegation, Change Bidirectional Association to Unidirectional.
- **Temporary Field**: Introduce Null Object, Extract Class, Move Function.
- **Loops** (catalogued as Imperative Loops): Replace Loop with Pipeline.
- **Mutable Data**: Remove Setting Method, Separate Query from Modifier, Change Reference to Value, Replace Derived Variable with Query, Encapsulate Variable, Combine Methods into Class.
- **Global Data**: Encapsulate Variable.
- **Alternative Classes with Different Interfaces**: Move Method.
- **Data Class** (catalogued as Fate over Action, aka Data Class): Move Method, Extract Method, Freeze Variables. Text: "If a class has only the first part [data], it is an indicator that there could be a functionality tied to these variables, but it is missing or placed somewhere else."
- **Refused Bequest**: Extract Subclass, Push Down Field/Method, Replace Inheritance with Delegation, Replace Superclass/Subclass with Delegate.
- **Shotgun Surgery**: Extract Method, Combine Functions into Class, Combine Functions into Transform, Split Phase, Move Method and Move Field, Inline Function/Class.
- **Divergent Change**: Extract Superclass, Extract Subclass, Extract Class, Extract Function, Move Function.
- **Speculative Generality**: Collapse Hierarchy, Inline Function, Inline Class, Rename Method.
- **Lazy Element**: Inline Class, Inline Function, Collapse Hierarchy.

UNVERIFIED (2nd ed. text from recall):
- Repeated Switches is defined as the *same* conditional switching logic appearing in multiple places, so that adding a clause means finding every switch. The fix is Replace Conditional with Polymorphism.
- Primitive Obsession → Replace Primitive with Object, Replace Type Code with Subclasses, Replace Conditional with Polymorphism, Extract Class, Introduce Parameter Object.
- Data Class → Encapsulate Record, Remove Setting Method, Move Function, Extract Function, Split Phase.
- Alternative Classes → Change Function Declaration, Move Function, Extract Superclass.
- Temporary Field → Extract Class, Move Function, Introduce Special Case.
- Global Data → Encapsulate Variable.
- Insider Trading → Move Function/Field, Hide Delegate, Replace Subclass/Superclass with Delegate.

### Inferences
- Fowler 2018 renames "Switch Statements" to "Repeated Switches" (recall). That makes the smell "the same case analysis in two or more places", which is exactly the checker's "same enum constants decided in two places" rule. The missing model is a polymorphic type: an enum with constant-specific methods, or a hierarchy.

### Gaps
- The book's text was not opened. refactoring.com/catalog lists refactorings, not smells.

---

## Q4. Wake 2004, Mäntylä 2003, Jerzyk & Madeyski 2023: newer and less-known entries

### Takeaway
Jerzyk & Madeyski's 2023 catalogue has 56 smells in 9 obstruction groups: Bloaters, Change Preventers, Couplers, Data Dealers, Dispensables, Functional Abusers, Lexical Abusers, Obfuscators, OO Abusers, plus Other. It extends Mäntylä's five groups. Several entries name a missing model outright:
- Type Embedded in Name: the potential class `Date` is embedded in the name of a variable such as `current_date`.
- Boolean Blindness: "Introduce New Type".
- Fate over Action: missing behaviour on a data holder.
- Null Check: Null Object or Optional.
- Special Case: polymorphism or Null Object.
- Combinatorial Explosion: Wake's reading, near-identical code differing by quantities of data or objects.

### Cited Findings (all from [the catalogue repo](https://github.com/Luzkan/smells), per-smell pages at https://luzkan.github.io/smells/<slug>)

| Smell (group) | Origin per catalogue | Definition (catalogue) | Refactorings |
|---|---|---|---|
| Special Case (Change Preventers), aka Complex Conditional | Wake 2004, Refactoring Workbook | "a complex `if` statement and/or value checking before doing the actual work" | Consolidate Conditional Expression, Replace Conditional with Polymorphism, Introduce Null Object, Replace Exception with Test |
| Status Variable (Obfuscators) | Jerzyk 2023 | "mutable primitives that are initialized before an operation to store some information based on the process and are later used as a switch for some action" | Replace with Built-In, Extract Method, Remove Status Variables |
| Tramp Data (Data Dealers) | McConnell, Code Complete 1993; Steve Smith 2013 | data passed through a long chain of methods to reach its destination; "an indicator of Dubious Abstraction" | Extract Method, Hide Delegate, Move Method |
| Flag Argument (Change Preventers) | Martin 2008; Fowler | "function argument that tells the function to carry out a different operation depending on its value" | Remove Flag Argument, Extract Method |
| Boolean Blindness (Lexical Abusers) | Jerzyk 2023 (Haskell community origin) | a function operating on booleans "destroys the information about what boolean represents" | Introduce New Type |
| Imperative Loops (Functional Abusers), aka Loops | Fowler 2018 | loops replaceable by pipelines | Replace Loop with Pipeline, Replace Loop with built-in |
| Hidden Dependencies (Data Dealers) | — | "methods are silently resolving dependencies, hiding that behavior from the caller" | Inject Dependencies |
| Required Setup or Teardown Code (Bloaters) | Steve Smith 2013 | several lines needed before or after using a class or method | Replace Constructor with Factory Method, Introduce Parameter Object |
| Oddball Solution (Bloaters), aka Inconsistent Solution | Kerievsky 2005 | similar problem solved differently in different places; subtle duplication | Unify Interfaces with Adapter |
| Conditional Complexity (OO Abusers), aka Repeated Switching, Switch Statement | Fowler 1999/2018, Smith | see Q3 | guard clause, extract conditional, polymorphism, Strategy, Null Object |
| Null Check (Bloaters) | Wake 2004 | "special objects could be created that implement the missing-event behavior" | Introduce Null Object, Introduce Maybe, Introduce Optional |
| Base Class depends on Subclass (OO Abusers) | Martin 2008 | child classes should be deployable independently from the parent | (none listed) |
| Inappropriate Static (OO Abusers), aka Static Cling | Martin 2008 | the test is whether the method should behave polymorphically: `Math.max` is fine; the catalogue gives an `Hour`… counter-example | Inject Dependencies |
| Fate over Action (Couplers), aka Data Class | Jerzyk 2023, after Fowler | behaviour tied to the data "is missing or placed somewhere else" | Move Method, Extract Method, Freeze Variables |
| Dubious Abstraction (Change Preventers), aka Inconsistent Abstraction Levels, Code at Wrong Level of Abstraction | Jerzyk 2023, after Martin and Smith | class interfaces should provide a constant abstraction level | Extract Superclass, Extract Subclass, Extract Class |
| Afraid To Fail (Couplers) | Jerzyk 2023 | code that avoids surfacing or handling failure | Move Method |
| Type Embedded in Name (Couplers) | Wake 2004 | "variable has an explicit type prefix or suffix, it can strongly signal that it should be just a class of its own" | Extract Class, Rename Method, Rename Variable |
| Combinatorial Explosion (Bloaters) | Wake 2004 | "a lot of code does almost the same thing ... blocks that differ from each other only by the quantities of data or objects used in them" | Replace Inheritance with Delegation, Tease Apart Inheritance |
| Parallel Inheritance Hierarchies (Change Preventers) | Fowler 1999 | "to create a subclass for a class, one finds that he has to make a subclass for another class" | Move Method, Move Field, Create Partial, Fold Hierarchy into One |

### Inferences
- Wake's Combinatorial Explosion, as the catalogue reads it, points to a class-hierarchy explosion: subclasses per combination, fixed by delegation or Bridge. Kerievsky's reading points to query methods per combination of criteria, fixed by Interpreter (Q1). The two readings name different missing models for the same smell name.
- Status Variable plus Flag Argument plus Boolean Blindness are three views of one missing model: a value without a type, or an implicit state, to be repaired with an enum or type, State, or a split into separate methods.

### Gaps
- Mäntylä's 2003 taxonomy paper and Wake's book were not opened directly. The group names come from the catalogue's ancestry, and the "Mäntylä" attribution is from recall.
- The catalogue does not give a missing model as a field. The mapping above is read from its text.

---

## Q5. Riel, Object-Oriented Design Heuristics (1996): checkable heuristics that point at a missing model

### Takeaway
The transcription used numbers the 60 heuristics 1–60; the book numbers them by chapter, e.g. 5.12. The following are mechanically checkable and model-revealing, with the wording verified at [vincehuston.org Riel list](http://www.vincehuston.org/ood/oo_design_heuristics.html):
- 46/47: case analysis on type or on an attribute value → polymorphism or hierarchy.
- 51: NOP override is illegal.
- 20: operation turned into a class, verb name.
- 14: many accessors → data and behaviour not together.
- 13: god-class name words.
- 11: roles versus classes.
- 48: dynamic semantics modelled by inheritance → State.
- 49: objects turned into subclasses.
- 52: optional containment modelled with inheritance.
- 44/45: common data → contained class; common data and behaviour → common base.
- 26: containment implies uses.
- 34/35: a class should not know its container; siblings in the same container should not use each other.
- 27: methods use most data members → cohesion.

### Cited Findings (quoted, sequential numbering of the transcription)
- 9 "Keep related data and behavior in one place." 10 "Spin off nonrelated information into another class (that is, non-communicating behavior)."
- 11 "Be sure the abstractions that you model are classes and not simply the roles objects play."
- 13 "Do not create god classes ... Be very suspicious of a class whose name contains DRIVER, MANAGER, SYSTEM, SUBSYSTEM, etc." (the transcription has "MANGER").
- 14 "Beware of classes that have many accessor methods defined in their interface. Having many implies that related data and behavior are not being kept in one place."
- 15 "Beware of classes that have too much non-communicating behavior, that is, methods that operate on a proper subset of the data members of a class."
- 20 "Do not turn an operation into a class. Be suspicious of any class whose name is a verb or is derived from a verb, especially those which have only one piece of meaningful behavior. Ask if that piece of meaningful behavior needs to be migrated to some existing or undiscovered class."
- 21 Agent classes are often irrelevant in design.
- 26 "If a class contains objects of another class, then the containing class should be sending messages to the contained objects".
- 27 "Most of the methods defined in a class should be using most of the data members most of the time."
- 30–33 semantic constraints belong in the class definition, or in the constructor; volatile constraint information goes in a central third-party object.
- 34 "A class must know what it contains, but should never know who contains it." 35 objects in the same containing class "should not have uses relationship between them".
- 37 base classes should not know their derived classes. 38 no protected data. 42 "All base classes must be abstract classes."
- 44 "If two or more classes share only common data (no common behavior), then that common data should be placed in a class that will be contained by each sharing class."
- 45 "If two or more classes have common data and behavior ... inherit from a common base class".
- 46 "Explicit case analysis on the type of an object is usually an error. The designer should use polymorphism in most of these cases."
- 47 "Explicit case analysis on the value of an attribute is often an error. The class should be decomposed into an inheritance hierarchy, where each value of the attribute is transformed into a derived class."
- 48 "Do not model the dynamic semantics of a class through the use of an inheritance relationship ... will lead to a toggling of types at run time."
- 49 "Do not turn objects of a class into derived classes of the class. Be very suspicious of any derived class for which there is only one instance."
- 50 run-time class creation means you need objects of a new generalised class.
- 51 "It should be illegal for a derived class to override a base class method with a NOP method".
- 52 "Do not confuse optional containment with the need for inheritance ... will lead to a proliferation of classes."
- 58 do not use global data or functions for bookkeeping on the objects of a class.

### Inferences
- Book numbering, from recall and UNVERIFIED: 46 = 5.12, 47 = 5.13, 48 = 5.14, 49 = 5.15, 51 = 5.17, 20 = 3.9, 14 = 3.3. Cite the book numbers only after checking.
- 47 together with 48 gives a decision procedure. Case analysis on an attribute value means subclasses when the value is fixed for the object's life. When the value changes at run time, it means a State object, because inheritance would toggle types. This is the State versus subclass choice.

### Gaps
- Book chapter numbering and the per-heuristic discussion were not verified.

---

## Q6. Object Calisthenics, Elegant Objects, Tell-Don't-Ask, Law of Demeter, Effective Java

### Takeaway
Object Calisthenics is the clearest rule-to-forced-model list:
- wrap all primitives and strings → value objects
- first-class collections → a class per collection
- no else → polymorphism, Null Object, State, Strategy
- one dot per line → Demeter
- at most two instance variables → small cohesive classes
- no getters or setters → tell-don't-ask

Effective Java's Item 23 "Prefer class hierarchies to tagged classes" (3rd ed.) is the same smell as Missing Hierarchy, under the "Tag class" alias in JOT 2013. Item 37 "Use EnumMap instead of ordinal indexing" is verified as well.

### Cited Findings
- Bay's nine rules (ThoughtWorks Anthology), summarised at [Durand 2013](https://williamdurand.fr/2013/06/03/object-calisthenics/):
  1. one level of indentation per method → Extract Method
  2. no `else` → early return or polymorphism (Null Object, State, Strategy)
  3. wrap all primitives and strings → value types (e.g. `Money`, `Hour`)
  4. first-class collections: "a class holding a collection holds no other fields", and collection behaviour lives there
  5. one dot per line → Law of Demeter
  6. don't abbreviate; a hard-to-name class signals mixed responsibilities
  7. keep entities small (about 50 lines per class, under 10 files per package)
  8. no more than two instance variables
  9. no getters, setters or properties → tell-don't-ask
- Effective Java 3rd ed.: Item 23 "Prefer class hierarchies to tagged classes" and Item 37 "Use EnumMap instead of ordinal indexing". Both are confirmed in the table of contents per [O'Reilly listing](https://oreilly.com/library/view/effective-java/9780134686097) and [Cisco Press sample chapter](https://www.ciscopress.com/content/images/9780134685991/samplepages/9780134685991_CH05.pdf). Item 34 is "Use enums instead of int constants" and Item 35 "Use instance fields instead of ordinals", per search snippets.
- Bloch's "tag class" is cited by JOT 2013 as an alias of Missing Hierarchy ([Ganesh et al.](https://www.jot.fm/issues/issue_2013_06/article1.pdf)).

UNVERIFIED:
- Item 34 recommends constant-specific method implementations and the "strategy enum" pattern instead of a `switch` on `this`.
- Item 65 "Prefer interfaces to reflection".
- Item 22 "Use interfaces only to define types" (constant interface antipattern).
- Item 20 "Prefer interfaces to abstract classes".
- Elegant Objects (Bugayenko 2016): no `-er` names, no static methods or utility classes, no getters or setters, no null, small immutable objects, no type checks or casting, constructors with one primary constructor. None of these were opened this session.
- Tell-Don't-Ask (Pragmatic Programmers / Fowler bliki) and the Law of Demeter (Lieberherr 1987–89) were not opened.

### Inferences
- Calisthenics rules 3 and 4 name a model the checker can demand. A `List<X>` field alongside other fields, with filtering logic in the owner, means a missing first-class collection. A `String` or `int` parameter with validation or formatting logic around it means a missing value type.

### Gaps
- No primary text was opened for Bay's essay, Bugayenko's book or Bloch's item bodies.

---

## Q7. AntiPatterns (Brown et al. 1998) and the DECOR rule cards (Moha et al., TSE 2010)

### Takeaway
DECOR (IEEE TSE 36(1):20–36, 2010) specifies four antipatterns from 15 underlying code smells: Blob, Functional Decomposition, Spaghetti Code and Swiss Army Knife. The full rule cards were not retrieved.

### Cited Findings
- DECOR and its DETEX detector specify Blob, Functional Decomposition, Spaghetti Code and Swiss Army Knife "along with their 15 underlying code smells", with detection algorithms generated from rule cards. Validated on Xerces 2.7.0 and 11 systems ([IEEE Xplore](https://ieeexplore.ieee.org/abstract/document/5196681/); the search snippet summarised the abstract).
- JOT 2013 maps Blob to Large or Multifaceted Abstraction, Spaghetti Code to Dense Abstraction, and Swiss Army Knife to Multifaceted Abstraction ([Ganesh et al.](https://www.jot.fm/issues/issue_2013_06/article1.pdf)).

UNVERIFIED, from recall of Moha et al.:
- **Blob**: a large controller class (LargeClass and LowCohesion, with method or class names containing Process, Control, Manage, System…) associated with several Data Classes (accessor-only). Brown's refactored solution is to move behaviour to the data classes and break up the controller.
- **Functional Decomposition**: a main class whose name suggests a procedure (Make, Create, Exec…), little use of inheritance or polymorphism, many private fields, few methods, associated with classes that each have one method (Imperative Abstraction).
- **Spaghetti Code**: long methods without parameters, global variables, procedural names, no inheritance or polymorphism.
- **Swiss Army Knife**: a class implementing many interfaces (high number of interfaces).
- **Poltergeists** (Brown): short-lived, stateless classes that only invoke methods of other classes. The repair is to remove them and move their behaviour into the longer-lived classes.

### Gaps
- The rule-card text and thresholds were not opened; the full PDF is behind IEEE. A typeset.io copy exists: https://typeset.io/pdf/decor-a-method-for-the-specification-and-detection-of-code-4jig9eh1ay.pdf (not opened).

---

## Q8. Evans, Domain-Driven Design (2003), ch. 9 "Making Implicit Concepts Explicit"

### Takeaway
The chapter's signals of a hidden concept are: listen to language, scrutinize awkwardness, contemplate contradictions, read the book, try again. Its explicit models for less obvious concepts are explicit constraints, processes as domain objects, and Specification.

### Cited Findings
- The book index lists "Scrutinize Awkwardness" (pp. 210–216), "Contemplate Contradictions" (216–217), "Explicit Constraints" (220–222), "Processes as Domain Objects" (222–223) and "SPECIFICATION" (223 ff.) ([Evans index, Cisco Press](https://www.ciscopress.com/content/images/0321125215/index/evansindex.pdf)), per search snippet.
- Constraints: "a lot of design and requirements conversation revolves around the constraints, but in the implementation, they are hidden away in procedural code". The remedy is to extract them into an explicit object or set of objects ([herbertograca summary](https://herbertograca.com/2015/12/02/ddd-9-making-implicit-concepts-explicit/), secondary, via snippet).
- Specification is "a predicate that determines if an object does or does not satisfy some criteria" (secondary summaries in search results).

UNVERIFIED: Evans' warning signs for a constraint hidden in a method body are that evaluating it needs data that does not otherwise fit the object's definition, that related rules appear in multiple objects, and that conversation keeps returning to it while the code buries it. Specification is prescribed for validation, selection (querying) and building-to-order.

### Gaps
- No primary text was opened; the summaries are secondary. The PLoP 2002 excerpt (https://Hillside.net/plop/plop2002/final/DomainDrivenDesign_Excerpt_PLoP2002.pdf) was not opened.

---

## Q9. Lanza & Marinescu, Object-Oriented Metrics in Practice (2006): disharmony co-occurrences

### Takeaway
Detection strategies are metric conjunctions, and their thresholds are verified via the course slides. The co-occurrence reading — God Class with Data Class satellites, read as moving behaviour to the data — is recalled, not verified.

### Cited Findings ([Figueiredo slides restating L&M](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf))
- **God Class**: ATFD > Few AND WMC ≥ Very High AND TCC < One Third. Reading: "performs too much work, lacks internal relationships between its methods, accesses a lot of data from other classes". Book §5.3.
- **God (Brain) Method**: LOC > High (class/2) AND CYCLO ≥ High AND NOAV > Many. Book §5.6.
- **Feature Envy**: ATFD > Few AND LAA < One Third AND FDP ≤ Few, meaning the foreign attributes belong to very few classes. Book §5.4.
- **Shotgun Surgery**: CM > Many AND CC > Many. Book §6.5.
- **Refused Parent Bequest**: (NProtM > Few AND BUR < One Third) OR BOvR < One Third, AND the child is not small (AMW > Avg OR WMC ≥ Avg, AND NOM > Avg). Book §7.3.
- JOT 2013 maps L&M names as follows: brain class → Controlling Abstraction; dispersed and intensive coupling → Weakened Modularity (central); tradition breaker → Broken Hierarchy; access to foreign data → Violated Encapsulation ([Ganesh et al.](https://www.jot.fm/issues/issue_2013_06/article1.pdf)).

UNVERIFIED:
- L&M's "Data Class" strategy: WOC < One Third AND public attributes plus accessors above a threshold.
- Their correlation web: God Class co-occurs with Data Class through Feature Envy (methods of the God Class envy the Data Class). The recommended redesign is to move the envious methods into the Data Class, which repairs both.
- Intensive Coupling (many calls to few classes) → extract or move the collaborating logic. Dispersed Coupling → introduce a mediating abstraction.
- Tradition Breaker (a subclass that ignores the parent's interface and adds a lot of new services) → the hierarchy is wrong; use delegation.

### Gaps
- The book's "disharmony correlation" chapter and its recovery strategies were not opened.

---

## Q10. Published work that frames smells as symptoms of a missing abstraction and gives a smell → pattern decision procedure

### Takeaway
The explicit smell → pattern maps are:
- Kerievsky's smell table and quick reference (verified, Q1).
- The JOT 2013 principle-based classification. Its naming scheme puts the violated principle in the name ("Missing Hierarchy", "Overlooked Abstraction"), so each smell names the missing construct (verified).
- Demeyer, Ducasse & Nierstrasz, *Object-Oriented Reengineering Patterns* (2002/2008), chapter 10 "Transform Conditionals to Polymorphism". This is a pattern language that chooses between Transform Self Type Checks, Transform Client Type Checks, Factor Out State, Factor Out Strategy, Introduce Null Object and Transform Conditionals into Registration. The section list is verified at [LibreTexts OORP ch.10](https://eng.libretexts.org/Bookshelves/Computer_Science/Programming_and_Computation_Fundamentals/Book%3A_Object-Oriented_Reengineering_Patterns_(Demeyer_Ducasse_and_Nierstrasz)/10%3A_Transform_Conditionals_to_Polymorphism); the decision guidance itself was not opened.

### Cited Findings
- Kerievsky's Primitive Obsession note says the smell "typically occurs when you haven't yet seen how a higher-level abstraction can clarify or simplify your code" ([Industrial Logic PDF](https://www.industriallogic.com/img/blog/2005/09/smellstorefactorings.pdf)). This is the clearest published "smell = missing abstraction" statement found.
- Fowler's Data Clumps deletion test: "an object that's dying to be born" (same PDF).
- JOT 2013 Missing Hierarchy: "'tagging' indicates an unexploited is-a relationship". Folded Hierarchy: "a suitable supertype ... is currently missing" ([Ganesh et al.](https://www.jot.fm/issues/issue_2013_06/article1.pdf)).
- The OORP search snippet: the conditional is replaced "with a call to a new hook method", and the reader should "look for applications of the methods isMemberOf: and isKindOf:" ([LibreTexts 10.2 Transform Self Type Checks](https://eng.libretexts.org/Bookshelves/Computer_Science/Programming_and_Computation_Fundamentals/Book%3A_Object-Oriented_Reengineering_Patterns_(Demeyer_Ducasse_and_Nierstrasz)/10%3A_Transform_Conditionals_to_Polymorphism/10.02%3A_Transform_Self_Type_Checks)).

UNVERIFIED, recall of OORP ch.10's decision guidance:
- conditional on the type of `self` or of a field → Transform Self Type Checks (subclasses)
- conditional on the type of an argument or provider, in clients → Transform Client Type Checks (move the method to the provider hierarchy)
- conditional on a state that changes → Factor Out State
- conditional selecting an algorithm → Factor Out Strategy
- conditional on null → Introduce Null Object
- conditional on a key to choose a tool or plugin → Transform Conditionals into Registration

### Inferences
Combining the verified sources gives a compact decision procedure, keyed on *what the branch condition tests* and *what the branches do*. Each row is backed by Kerievsky's Problem sentence, a Riel heuristic or a JOT definition.

| Condition tests… | Branches… | Missing model | Transformation |
|---|---|---|---|
| the runtime type of `this`, a field or an argument (`instanceof`, `getClass`, kind tag) | behave differently | polymorphic hierarchy | Replace Conditional with Polymorphism (Riel 46, JOT Unexploited Hierarchy) |
| an attribute value fixed for the object's life (tag or type code) | behave differently | subclass per value | Replace Type Code with Subclasses (Riel 47, JOT Missing Hierarchy, EJ Item 23) |
| an attribute that the branches themselves reassign | transition | State | Replace State-Altering Conditionals with State (K166, Riel 48) |
| a selector (parameter, configuration) | compute variants of one result | Strategy | Replace Conditional Logic with Strategy (K129) |
| a request name or key | execute actions | Command, plus a registry | Replace Conditional Dispatcher with Command (K191) |
| element types while iterating a heterogeneous structure | accumulate | Visitor / double dispatch | Move Accumulation to Visitor (K320) |
| null | default behaviour | Null Object / Special Case | Introduce Null Object (K301) |
| single versus many | the same operation | Composite | Replace One/Many Distinctions with Composite (K224) |
| an optional feature flag | add behaviour around a core | Decorator | Move Embellishment to Decorator (K144) |
| (no condition) the primitive string being built is nested | — | Composite tree | Replace Implicit Tree with Composite (K178) |
| (no condition) methods named by combinations of criteria | — | Interpreter / Specification | Replace Implicit Language with Interpreter (K269); Evans Specification |
| (no condition) a String or int field compared to constants | — | value type / enum class | Replace Type Code with Class (K286) |

### Gaps
- No 2020–2026 work was found that publishes a smell → missing-model decision table beyond these. Jerzyk & Madeyski 2023 is the most recent catalogue opened, and it records refactorings but no "missing model" field. Not searched: LLM-specific design-smell studies from 2024–2026.
