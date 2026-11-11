# Plan: smells de modelo

Una dimensión nueva para cerco: pasar de prohibir construcciones a detectar **la huella de un modelo que falta**, y
decirle al agente cuál es ese modelo. Escrito el 2026-11-10 sobre cerco 0.13.0 (29 reglas).

Lo acompañan:

- el informe de la investigación: [`reports/Smells que delatan modelos ausentes.md`](reports/Smells%20que%20delatan%20modelos%20ausentes.md);
- las notas con fuentes, una por tema, en `research_notes/Smells que delatan modelos ausentes/`;
- la medición sobre oozx, con el código de las sondas y su salida cruda, en la subcarpeta `sonda/` de esas notas.

---

## 0. En una página

1. **Lo que cerco prohíbe hoy no distingue el código bien modelado del mal modelado.** Medido en oozx: `emulator`
   tiene más `instanceof` por línea que `machine`, y la misma cantidad de `switch`, casts y cadenas de `if`. Lo que
   los separa, por 5 a 50 veces, es otra cosa: si hay un modelo polimórfico que sabe recorrerse (visitor), y cuánto
   se interroga a los objetos desde afuera (`null`, campos ajenos, setters, recorridos de colecciones ajenas, prosa
   dentro de los métodos). §1.
2. **Esos smells existen en la literatura, pero casi nadie los detecta como vos los ves.** Hay catálogos que mapean
   smell → modelo que falta → transformación (Kerievsky, Suryanarayana, Riel, Demeyer). Hay detectores de la versión
   chica, dentro de un método (Designite, JDeodorant, CodeQL, IntelliJ). **Ninguna herramienta detecta lo que pasa
   entre varios lugares**: la misma familia de tipos testeada en varios sitios, una estructura recorrida desde varios
   clientes, un discriminador decidido fuera de su clase. Y ninguna le dice al que lee qué modelo falta. §2.
3. **La idea del plan son tres cambios**, en este orden:
   - **diagnóstico**: cada regla dice qué modelos suelen faltar detrás de ese smell, qué pregunta los distingue, la
     transformación típica y qué *no* es un arreglo. §4 y §5;
   - **evidencia del modelo**: cerco arma un índice del proyecto (tipos, jerarquías, quién le pregunta qué a quién) y
     lo usa para decir cosas como "`OpcodeReference` tiene 8 subtipos y se testea desde 9 archivos". §6;
   - **reglas de repetición**: la señal precisa no es *una* pregunta desde afuera sino *la misma* pregunta desde
     varios lugares. Eso es un método, un visitor o un objeto que falta. §7.
4. **Ninguna regla se construye sin medirla antes.** Las seis reglas que salieron de autopsias detectaron casi sólo
   su propio caso. Desde ahora una regla nace como candidata, se corre sobre los proyectos de referencia, se revisa
   una muestra a mano y recién ahí entra al cerco. §8.
5. **Las autopsias pasan a etiquetar qué modelo faltó**, con un vocabulario cerrado de once. Con eso cerco aprende
   de tu proyecto: "en oozx, `no-switch` fue cuatro veces *variación como dato*". §5.3.

Orden de trabajo en §9. Preguntas que necesito que decidas vos, en §11.

---

## 1. Lo que se midió en oozx

Usé tu frase como verdad de referencia: *"emulator es el que mejor está diseñado, los otros varios los hiciste vos"*.
Un indicador sirve si su tasa separa `emulator` de `machine/*`. Dos sondas en Java sobre JavaParser, sin tipos.
Todas las tasas son por 1000 líneas de código.

### 1.1 Lo que no separa

| por 1000 líneas | emulator | core | app | ui | spectrum | kit |
|---|---|---|---|---|---|---|
| `instanceof` | 4,33 | 1,14 | 8,45 | 4,47 | 0,52 | 4,86 |
| cast a tipo referencia | 2,62 | 0,00 | 4,23 | 4,47 | 0,00 | 9,73 |
| `switch` | 1,21 | 0,57 | 0,53 | 1,49 | 4,20 | 5,84 |
| cadena `else if` de 3+ ramas | 0,40 | 0,38 | 1,06 | 0,00 | 0,00 | 0,00 |

Tampoco separan: getters sobre objetos del proyecto (32 en emulator, 18 en core), métodos con 4+ parámetros,
primitivos con nombre repetido (`int address` aparece 50 veces en cada grupo), ni la duplicación tipo 2 medida
en ventanas de 3 sentencias (2,82 contra 2,57).

### 1.2 Lo que sí separa

| por 1000 líneas | emulator | core | app | ui | spectrum | kit |
|---|---|---|---|---|---|---|
| **presencia de modelo** | | | | | | |
| métodos `accept` / `visit*` | 37,59 | 2,08 | 0 | 0 | 0 | 0 |
| tipos que extienden o implementan | 26,10 | 7,96 | 2,64 | 9,44 | 1,05 | 15,56 |
| líneas promedio por método (no es tasa) | 3,9 | 4,9 | 8,7 | 8,3 | 8,2 | 5,0 |
| **interrogar desde afuera** | | | | | | |
| comparación con `null` | 1,81 | 15,34 | 46,49 | 17,89 | 9,97 | 34,05 |
| campo de otro objeto del proyecto (`x.y`) | 7,05 | 35,99 | 54,41 | 24,85 | 7,35 | 25,29 |
| llamada a través de un campo ajeno (`a.b.m()`) | 0,8 | 5,5 | 20,1 | 1,0 | 1,6 | 3,9 |
| setter de un objeto del proyecto | 2,52 | 0,76 | 10,04 | 1,49 | 84,99 | 13,62 |
| for-each o stream sobre el getter de otro | 0,40 | 3,97 | 15,32 | 4,47 | 1,04 | 2,92 |
| **narrar en vez de modelar** | | | | | | |
| comentario de línea dentro de un método | 1,51 | 19,51 | 45,96 | 52,19 | 34,10 | 5,84 |
| método con 8+ variables locales | 0,10 | 1,14 | 1,58 | 4,97 | 0,52 | 0 |
| método estático no privado | 2,32 | 9,09 | 6,34 | 18,89 | 12,07 | 1,95 |
| **valores sin tipo** | | | | | | |
| literal de texto repetido 3+ veces en un archivo | 0 | 3,22 | 3,70 | 42,74 | 6,30 | 33,07 |
| método que bifurca por su parámetro booleano | 0,40 | 1,70 | 1,06 | 1,99 | 0 | 2,92 |

### 1.3 Estructuras contadas

Las que piden **repetición** o **propiedad** son las más limpias: dan cero en `emulator`.

| estructura | emulator | machine | ejemplos |
|---|---|---|---|
| campo comparado con `null` en 2+ métodos de su clase | 0 | 14 | `AttachedFrame.machineWindow` (5 métodos), `JavaSoundDevice.line` (4) |
| método que llama 3+ setters del mismo objeto del proyecto | 0 | 9 | `SnapshotLoader.setZ80State` (32), `SnapshotZ80.loadFromStream` (28) |
| clase de datos (3+ campos, 80 %+ accesores) | 0 | 10 | `Z80State` (31 campos), `SpectrumState` (22), `WindowState` (17) |
| clase con 2+ mapas de la misma clave | 0 | 4 | `OOZxConfiguration` (4 mapas por `String`), `RomFiles` (3) |
| método con 3+ comentarios de línea adentro | 0 | 42 | `SnapshotZ80.loadFromStream` (38), `EmulatorModule.configure` (27) |
| getter recorrido desde 3+ archivos | 0 | 2 | `found()` desde 6, `getMachineTypes()` desde 3 |
| camino `a.b.m()` (sitios) | 8 | 76 | `device.peripheral` 11, `speccy.machine` 9, `speccy.loop` 7 |

Y una que encuentra deuda **en emulator**: jerarquías del proyecto cuyos subtipos se preguntan con `instanceof`.

| jerarquía | subtipos | archivos que la testean |
|---|---|---|
| `OpcodeReference` | 8 | 9 |
| `Register` | 4 | 6 |
| `ImmutableOpcodeReference` | 3 | 3 |
| `AbstractInstruction`, `RepeatingInstruction`, `Instruction`, `ConditionalInstruction`, `JumpInstruction` | 15, 8, 5, 5, 4 | 2 cada una |
| `Scaler`, `ScreenEffect` (machine/ui) | 7, 5 | 1 (`ScreenSettings`) |

`emulator` ya tiene `InstructionVisitor` con 103 `accept(`, y aun así sus instrucciones se testean desde dos
archivos y `OpcodeReference` desde nueve. Es exactamente tu ejemplo: no alcanza con contar `instanceof`, hay que ver
**qué jerarquía** se pregunta y **desde cuántos lugares**.

### 1.4 Límites de esta medición

- Un solo proyecto; la verdad de referencia es tu opinión sobre quién escribió qué.
- Sin tipos: un método cuenta como "del proyecto" si algún tipo del proyecto declara ese nombre.
- `app`, `ui` y `kit` son escritorio Swing, que impone setters y `null`. `core` y `spectrum` no lo son y muestran
  las mismas diferencias, más chicas.
- Los estados de snapshot de `spectrum` (`Z80State`, `SpectrumState`…) parecen heredados de JSpeccy, no escritos
  por el agente. Explican casi todos los setters de ese módulo.
- No revisé a mano cada caso. Falta medir precisión por muestreo antes de convertir cada indicador en regla (§8).

---

## 2. Qué existe: papers y herramientas

El detalle y las fuentes están en el informe. Acá, lo que cambia el diseño de cerco.

### 2.1 Catálogos: sí existen, y traen la relación smell → modelo → transformación

| fuente | qué aporta |
|---|---|
| Kerievsky, *Refactoring to Patterns* (2004) | 27 refactorings hacia patrones, cada uno con una frase de problema casi operativa: *"un método acumula información de clases heterogéneas"* → Visitor; *"formás un árbol implícito con una representación primitiva"* → Composite. Agrega smells propios: Conditional Complexity, Indecent Exposure, Solution Sprawl, Combinatorial Explosion, Oddball Solution. |
| Ganesh, Sharma, Suryanarayana (JOT 2013 y el libro de 2014) | Smells de diseño clasificados por principio violado. Los que nombran un modelo ausente: **Missing Abstraction**, **Missing Hierarchy** (condicionales sobre un "tag"), **Unexploited Hierarchy** (tests de tipo sobre una jerarquía que ya existe; en el libro, Unexploited Encapsulation), **Imperative Abstraction** (una operación convertida en clase), **Broken Modularization** (datos y métodos que van juntos, separados). |
| Riel, *Object-Oriented Design Heuristics* (1996) | Heurísticas chequeables: el análisis de casos sobre el tipo de un objeto o sobre el valor de un atributo es casi siempre un error; clases con nombre de verbo; clases con muchos accesores; override vacío. |
| Demeyer, Ducasse, Nierstrasz, *Object-Oriented Reengineering Patterns*, cap. 10 | El procedimiento de decisión publicado más cercano: *Transform Conditionals to Polymorphism*, que separa los tests sobre el propio tipo de los tests que un cliente le hace a un proveedor. |
| Jerzyk y Madeyski (2023) | Catálogo de 56 smells con los más nuevos: Flag Argument, Status Variable, Special Case, Tramp Data, Null Check, Boolean Blindness, Imperative Loops. |

De cruzar esas fuentes sale una **tabla de decisión** que es la base del diagnóstico (§5): *qué testea la condición
y qué hacen las ramas* decide el patrón.

| la condición testea… | …y las ramas… | modelo que falta |
|---|---|---|
| el tipo de un objeto (`instanceof`, cast) | hacen la misma operación de distinta forma | jerarquía polimórfica; **Visitor** si se acumula sobre una estructura |
| un atributo fijo de por vida (código de tipo, enum) | difieren en comportamiento | subclases, o comportamiento en el enum |
| un atributo que las ramas reasignan | cambian el modo | State |
| un parámetro selector | eligen entre cálculos | Strategy, o dos métodos |
| una clave de un pedido | eligen acciones | Command |
| `null` | saltean o hacen lo "vacío" | Null Object |
| uno contra muchos | repiten la lógica para el caso plural | Composite |
| un texto o entero contra constantes | interpretan el valor | una clase o enum para ese valor |

### 2.2 Detectores: existen para la versión chica, con precisión baja salvo que se filtre

| herramienta | qué detecta | cómo | precisión reportada |
|---|---|---|---|
| JDeodorant (Tsantalis y Chatzigeorgiou, 2010) | Type Checking y State Checking | `switch` o cadena `if` donde todas las ramas comparan la misma variable contra constantes con nombre, o la testean con `instanceof`, y el tipo es una clase del proyecto con subclases | 43 a 57 % contra un experto. Sube a **94 a 100 %** exigiendo 2+ sentencias por rama. El acuerdo del experto crece con la cantidad de condicionales del mismo grupo. Es una sola evaluación: un experto, tres sistemas chicos |
| DesigniteJava | Missing Hierarchy, Unexploited Encapsulation | un método que testea con `instanceof` 2+ tipos | sin dato publicado. Mira un método, no cruza métodos ni archivos |
| CodeQL | `chained-type-tests`, `feature-envy` | más de 5 `instanceof` en una cadena; un método que usa más de 4 miembros de otra clase y más del doble que de la suya | sin dato |
| IntelliJ | InstanceofChain, FeatureEnvy, LawOfDemeter, ClassReferencesSubclass | por método, dentro del IDE | sin dato |
| PMD | LawOfDemeter, GodClass, DataClass | distancia a `this` con exenciones (builders, fluidas, colecciones); métricas | su propia doc avisa de falsos positivos |
| detectores por métricas (God Class, Feature Envy de Lanza y Marinescu) | — | umbrales sobre WMC, ATFD, TCC, LAA | **40 a 48 %**, y las herramientas no coinciden entre sí |

Dos consecuencias para cerco:

- **La repetición es el filtro de precisión.** El único detector con precisión de portón es el de JDeodorant con el
  filtro de ramas no triviales, y su acuerdo con el experto sube con la cantidad de condicionales del mismo grupo.
  Coincide con lo que da la medición de §1.3.
- **Las métricas solas no sirven de portón** (40 a 48 %). Sirven como presupuesto con trinquete, que es otra cosa:
  no acusan, sólo impiden empeorar.

### 2.3 Lo que no detecta nadie

No encontré ninguna herramienta ni paper que detecte, como regla:

- la misma familia de tipos testeada con `instanceof` en varios lugares;
- una estructura recorrida desde varios clientes;
- un discriminador (`kind()`, `getType()`) decidido fuera de su clase en varios archivos;
- mapas paralelos con la misma clave; campos con el mismo prefijo;
- la misma secuencia de llamadas sobre un receptor repetida en varios llamadores;
- "esto debería ser un Visitor", con precisión medida. Tsantalis dice explícitamente que elegir entre Visitor y
  polimorfismo *"no se puede determinar automáticamente"*.

O sea: el nivel que vos describís **no tiene implementación para embeber**. Lo reutilizable son las definiciones
(las condiciones de JDeodorant, Designite y CodeQL, y las exenciones de la Ley de Demeter de PMD). Y ninguna
herramienta le explica al lector qué modelo falta.

Una advertencia honesta sobre Visitor: la corriente principal de Java va para el otro lado. Goetz sostiene que los
tipos sellados con pattern matching reemplazan al Visitor para datos planos, y hay herramientas que reescriben
cadenas de `instanceof` a `switch` con patrones. Tu postura es una decisión de diseño, no el consenso. Lo que se
pierde con el `switch` de patrones es justo lo que vos valorás: que el modelo sepa recorrerse y que ese recorrido
se reuse. Cerco ya prohíbe el `switch`, así que la decisión está tomada; conviene que el diagnóstico la diga.

### 2.4 Agentes LLM

- **Los agentes casi no refactorizan a nivel de diseño.** Sobre 15.451 refactorings de agentes en Java (preprint),
  los más frecuentes son cambiar un tipo o renombrar un parámetro; la duplicación fue el motivo en el 1,1 % de los
  casos contra el 13,7 % en humanos, y la mediana de smells de diseño no cambió.
- **Decirle al modelo qué refactoring corresponde y dónde lo cambia todo.** Sin ayuda, los modelos encontraron el
  15,6 % de las oportunidades; diciéndoles la categoría esperada y el lugar, el 86,7 %. Es la evidencia indirecta
  más fuerte a favor del diagnóstico en el mensaje.
- **Pasar la herramienta no es arreglar.** En CORE el 76,8 % de los archivos Java pasaban los chequeos, pero hizo
  falta un segundo modelo que filtrara arreglos que un humano no aceptaría.
- **De a 3 a 5 hallazgos por ronda** rinde más que de a uno, y la mejora se aplana a las 3 o 4 rondas.
- **Hueco**: nadie midió cómo cambia el arreglo según lo que dice el mensaje del chequeo, ni agentes que esquiven
  linters (sí está documentado que esquivan tests). Cerco puede medirlo en casa (§8.3).

---

## 3. La idea

### 3.1 De construcciones prohibidas a preguntas repetidas

Las seis reglas que salieron de autopsias (`property-shuffle`, `notified-by-caller`, `borrowed-mode`,
`refiltered-by-type`, `key-holds-value`, `enum-decided-outside`) tienen todas la misma forma de fondo: **el que
llama hace el trabajo del dueño**. Cada una describe un caso sintáctico, y por eso encuentra poco.

La generalización es un solo principio, con siete caras:

> **La misma pregunta, hecha al mismo objeto desde varios lugares, es algo que le falta a ese objeto.**

| se repite… | falta… |
|---|---|
| la misma familia de tipos testeada | un despacho: método polimórfico o **Visitor** |
| el mismo recorrido de la colección de otro | que la estructura se recorra sola |
| la misma decisión sobre las constantes de un enum o un discriminador | comportamiento en el enum o en los subtipos |
| el mismo camino `a.b` | que `a` ofrezca la operación, o que me den `b` |
| la misma secuencia de llamadas sobre un receptor | una operación completa en el receptor |
| el mismo `null` preguntado | modelar la ausencia |
| la misma clave en varios mapas, el mismo prefijo en varios campos | un objeto |

Una pregunta desde afuera puede ser legítima. La misma pregunta desde tres lugares, no. Eso da precisión (§2.2) y
además da el arreglo: los N sitios son las N implementaciones de lo que falta.

### 3.2 Propiedad: de quién es lo que se pregunta

`button.setText(...)` no es un smell: el botón es de Swing. `z80.setRegA(...)` sí: el Z80 es nuestro. Sin tipos,
cerco puede saberlo igual con un **vocabulario propio**: los nombres de tipos, métodos y campos que el proyecto
declara. En la medición ese filtro separó los setters de `app` en 10 propios y 23 de librería.

### 3.3 Diagnóstico, no sólo remedio

Hoy un hallazgo dice el principio y qué hacer. Falta el paso del medio, que es el que hiciste vos a mano en cada
ciclo: **qué modelo falta**. Para un mismo smell hay dos o tres candidatos y una pregunta que los distingue. Cerco
puede escribir esa pregunta siempre, y contestarla él mismo cuando tiene la evidencia.

---

## 4. Los modelos que faltan: un vocabulario cerrado

Once. Cada regla apunta a uno o más; cada autopsia elige uno.

| id | qué falta | cómo se ve en el código | transformación típica |
|---|---|---|---|
| `behaviour-not-on-owner` | el objeto que tiene los datos no tiene el comportamiento | getters y setters llamados desde afuera, `a.b.m()`, estáticos que reciben el objeto | mover el método al dueño; el dueño ofrece la operación |
| `variation-as-data` | una variante está codificada como valor | `switch`, cadenas `if`, enum decidido afuera, parámetro booleano | subtipos, comportamiento en el enum, Strategy, dos métodos |
| `dispatch-from-outside` | la jerarquía existe, pero la operación vive afuera | `instanceof` y casts sobre la misma familia, mapas por `Class` | método en el tipo común; **Visitor** si hay 2+ operaciones o el cliente es de otro módulo |
| `structure-walked-from-outside` | la estructura no sabe recorrerse | for-each sobre getters ajenos, bucles anidados, recursión externa, cadenas de getters | iterador interno, consulta con nombre, Composite más Visitor |
| `concept-without-a-class` | datos que viajan juntos sin tipo | mapas paralelos, prefijos de campo, listas largas de parámetros, argumentos desempaquetados | Extract Class, objeto parámetro, pasar el objeto entero |
| `value-without-a-type` | un primitivo o texto con rol de dominio | literales repetidos, comparación con texto, la misma aritmética en varios lugares | objeto valor con su aritmética; enum para conjuntos cerrados |
| `state-without-an-object` | un modo o una fase guardados en flags o en `null` | campo testeado en varios métodos, campo `null` hasta que "llega" | State, Null Object, dos clases, colaborador obligatorio |
| `shape-repeated` | la misma forma con una parte que varía | clones tipo 2 y 3, métodos clonados | nombrar lo fijo; lo que varía es parámetro, estrategia o subtipo |
| `knowledge-without-one-owner` | el mismo dato o la misma decisión en varios lugares | registros que duplican lo que el objeto sabe, constantes repetidas, clases de datos paralelas a un modelo | un dueño; los demás le preguntan |
| `procedure-instead-of-model` | un procedimiento narrado | métodos largos con comentarios de pasos, muchas locales, anidamiento, clases `Manager` | Compose Method; si los pasos comparten estado, un objeto con nombre de concepto |
| `protocol-by-convention` | una colaboración que depende de que el llamador haga las cosas en orden | setter y aviso, `open()` y `start()`, guardar y restaurar | el dueño lo hace entero: execute-around, Observer adentro, Builder |

Van en inglés porque son ids que cerco imprime y que las autopsias escriben.

---

## 5. Diagnóstico en los mensajes

### 5.1 Qué dice una regla

Cada `Rule` suma dos cosas a `principle`, `description` y `remedy`:

- **hipótesis**: lista corta de `(modelo que falta, cuándo, transformación)`;
- **no es arreglo**: los disfraces conocidos del mismo smell. Es lo que hoy aprendemos caro, cuando el agente
  cumple la regla por la letra.

Y cada hallazgo puede traer **evidencia**: una o dos líneas calculadas con el índice del proyecto (§6), y la
**lectura** cuando la evidencia alcanza para elegir una hipótesis.

Así se vería (los mensajes de cerco son en inglés):

```
[no-instanceof] 4 new
  principle: polymorphism: the object decides, the caller does not inspect it
  probably missing (the evidence under each finding says which; name it in the autopsy):
    dispatch-from-outside          the hierarchy lacks this operation        -> a method on the common type;
                                                                               a Visitor when 2+ operations test it
    variation-as-data              the test stands for a role or capability  -> a method every element answers
    structure-walked-from-outside  you are filtering someone's mixed list    -> its owner hands the typed view
  not a fix: getClass(), a kind() enum plus switch, isX() per subtype, a Map keyed by Class, a sealed switch:
             the same test, moved
  in this project so far: dispatch-from-outside 2, structure-walked-from-outside 1
  ZXSpectrumDesktopApp.java:412  `window instanceof Opens` in opensFor(Component)
     evidence: Opens is tested in 4 methods of this file (opensFor, openFor, playingInto, playInto);
               3 types implement it; none declares what these methods decide
     reading:  the same family asked in 4 places is one missing operation -> dispatch-from-outside
```

Reglas de redacción, por lo que dice §2.4:

- las hipótesis se imprimen una vez por regla, no por hallazgo;
- a lo sumo 5 hallazgos por regla en la corrida rápida, los de más evidencia primero;
- la evidencia nombra clases y métodos concretos: "decile dónde" es lo que más rinde.

### 5.2 Mapa de las reglas actuales

Lo que se escribiría en cada regla. La columna del medio es la pregunta que distingue.

| regla | qué pregunta decide | modelo que falta | no es arreglo |
|---|---|---|---|
| `no-instanceof`, `no-cast`, `no-type-keyed-map` | ¿la jerarquía es del proyecto y se testea en 2+ lugares? ¿se filtra una colección ajena? ¿es una interfaz de rol? ¿el tipo es de librería? | `dispatch-from-outside`; `structure-walked-from-outside`; `variation-as-data`; adaptar en el borde, una vez | `getClass()`, `kind()` más `switch`, `isX()`, mapa por `Class` |
| `no-switch`, `no-conditional-chain`, `no-type-code`, `enum-decided-outside` | la tabla de §2.1: ¿qué testea la condición y qué hacen las ramas? | `variation-as-data`; `state-without-an-object` si las ramas reasignan; `value-without-a-type` si son textos o enteros | mapa de lambdas por la misma constante, cadena de `Optional.or`, partir el `switch` en dos |
| `ask-then-tell`, `flag-driven-from-outside`, `property-shuffle` | ¿el dato y la decisión son del mismo objeto? | `behaviour-not-on-owner`; `state-without-an-object` | un `isX()` que envuelve el getter; un setter con otro nombre |
| `notified-by-caller`, `borrowed-mode` | ¿quién garantiza que las dos cosas pasen juntas? | `protocol-by-convention` | un método privado en el llamador que hace las dos |
| `refiltered-by-type` | ¿de quién es la colección? | `structure-walked-from-outside` | un método estático que filtra |
| `key-holds-value` | ¿el valor ya lo sabe la clave? | `knowledge-without-one-owner` | otro mapa |
| `no-cloned-methods`, `no-repeated-statements` | ¿qué difiere entre las copias, y dónde están? | `shape-repeated`: literal → parámetro; llamada → estrategia o Template Method; tipo → supertipo. Misma clase → método; hermanas → subir; sin relación → objeto nuevo | una clase utilitaria estática |
| `method-length`, `file-length` | ¿hay comentarios de pasos? ¿8+ locales? ¿interroga 4+ veces al mismo objeto? ¿arma algo por setters? | `procedure-instead-of-model`; `behaviour-not-on-owner`; `concept-without-a-class` | `parte1()` y `parte2()` privados que comparten todo; mover a un `Helper` |
| `no-static-method`, `no-utility-class`, `no-constant-holder` | ¿de quién es el primer parámetro? ¿quién es dueño de la constante? | `behaviour-not-on-owner`; `value-without-a-type` | un singleton |
| `no-null-return` | ¿la ausencia es un caso del dominio? | `variation-as-data`; `state-without-an-object` | `Optional` desenvuelto con `get()`; un centinela mágico |
| `no-public-mutable-field`, `no-mutable-static` | ¿quién cambia el estado? | `behaviour-not-on-owner` | getter y setter triviales |

La regla de clones gana además evidencia nueva: **qué difiere entre las copias**. La literatura dice que el 94 % de
los clones son tipo 2 o 3, y que la diferencia decide la abstracción (Tsantalis). Hoy cerco sólo ve copias
idénticas.

### 5.3 Las autopsias etiquetan

La sección de causa de la autopsia suma una línea obligatoria:

```
model: dispatch-from-outside
```

`FormCheck` valida que sea uno de los once. Con eso:

- cerco cuenta, por regla, qué modelo faltó de verdad en este proyecto, y lo imprime en el diagnóstico;
- la sección "dónde más aparece" la puede pre-llenar cerco con los otros sitios de la misma clave;
- con el tiempo, el mapa smell → modelo deja de ser el de los libros y pasa a ser **el de tu proyecto**.

Las 11 autopsias ya archivadas en oozx se etiquetan a mano una vez.

---

## 6. Infraestructura

Lo que hay que construir antes de las reglas. Todo en Java, sin dependencias nuevas, sin configurar nada.

### 6.1 Hechos y modelo

Hoy cada regla recorre el AST por su cuenta, y las de proyecto guardan su propio estado. Lo nuevo:

- **`Extractor`**: una pasada por archivo que produce `FileFacts`, una lista compacta de hechos con su sitio
  (archivo, línea, miembro). No guarda AST, así que la memoria sigue acotada (los 768 MB de zx-lift).
- **`Model`**: el índice del proyecto armado con todos los `FileFacts`.

| hecho | contenido | para qué |
|---|---|---|
| `type` | nombre, clase/interfaz/enum/record, supertipos, campos, métodos, constantes | jerarquías, vocabulario propio, clases de datos |
| `ask` | sitio, receptor y su tipo declarado si se ve en el archivo, miembro, forma (getter, setter, campo, llamada), contexto (condición, iterable, argumento, sentencia) | preguntas repetidas, `a.b.m()`, setters desde afuera |
| `test` | sitio, tipo testeado, forma (`instanceof`, cast, `getClass`, patrón) | jerarquía preguntada desde afuera |
| `decide` | sitio, selector, constantes, sentencias por rama | discriminador decidido afuera |
| `walk` | sitio, dueño y miembro recorrido, anidamiento, si recurre, forma del cuerpo | estructura recorrida desde afuera |
| `sequence` | sitio, receptor, par de llamadas consecutivas | protocolo por convención |
| `absence` | sitio, qué se compara con `null` (campo, parámetro, resultado) | ausencia como estado |

Los hechos son objetos que saben entrar al modelo: el `Model` los recibe con un visitor. Cerco tiene que cumplir
sus propias reglas (`SelfCheckTest`), así que no hay otra forma, y es coherente con lo que predica.

**Tipos sin symbol solver.** Se mantiene la decisión de CERCO.md §6. El tipo del receptor sale de la declaración
visible en el archivo (parámetro, campo, local con tipo escrito). Lo que no se resuelve (`var`, resultado de una
llamada) queda como desconocido, y **una regla sólo es portón sobre lo resuelto**. Si las fichas de §8 muestran que
los homónimos arruinan la precisión, el paso siguiente es leer `target/classes` (bytecode, tipos exactos), no el
symbol solver.

### 6.2 Reglas de modelo, también en la corrida rápida

Una forma nueva de regla, `ModelRule`: no ve AST, concluye sobre el `Model`. Ventajas: se prueban con hechos
escritos a mano, y **pueden correr en `quick`**.

Hoy las reglas de proyecto esperan a `audit` porque costarían el proyecto entero por edición (CERCO.md §6). Con los
hechos guardados por digest en `target/cerco/facts`, una edición sólo re-extrae el archivo tocado y vuelve a
concluir sobre el índice. Sin caché (después de un `clean`), `quick` las saltea y lo anota, como hace hoy con las
reglas sin baseline.

Es un cambio a una decisión escrita. Va a §11 como pregunta, y se mide antes: `quick` hoy tarda 1,7 s en zx-lift.

Los hallazgos son **uno por sitio**, como en `no-cloned-methods`. Así un sitio nuevo de una pregunta ya congelada es
rojo, y la baseline la ve crecer.

### 6.3 Comentarios

Cerco hoy lee estructura, no prosa, y el digest ignora comentarios. La medida de narración (§7, regla 7) necesita
ver los comentarios de línea dentro de un método. Se pueden leer de los tokens sin cambiar el parser ni el digest.

Efecto colateral bueno: si el agente "arregla" borrando los comentarios, el digest del archivo no cambia y el
testigo (§3.11 de CERCO.md) ya lo marca como hallazgo que se fue sin que cambie el código. El atajo más barato queda
cerrado sin escribir nada.

### 6.4 La sonda: `mvn cerco:survey`

Las dos sondas de esta investigación son código suelto. Pasan a ser un goal:

- corre las reglas adoptadas y las **candidatas** sobre el reactor, sin tocar la baseline;
- imprime por módulo la tasa por 1000 líneas, las claves más repetidas y una muestra de sitios `archivo:línea`;
- imprime el **perfil de modelo** de cada módulo (§7.4).

Cubre también la idea 8 de CERCO.md (la baseline como tabla por módulo y regla).

---

## 7. Las reglas

Cada una con lo que rindió en la medición. **Todas son candidatas hasta pasar por §8.**

### 7.1 Primera tanda: las que más separan

| # | id | detecta | modelo que falta | emulator | machine | forma |
|---|---|---|---|---|---|---|
| 1 | `reached-through` | una llamada a través de un campo de otro objeto del proyecto: `a.b.m()` | `behaviour-not-on-owner` | 8 | 76 | archivo + vocabulario |
| 2 | `absence-as-state` | un campo comparado con `null` en 2+ métodos de su clase | `state-without-an-object` | 0 | 14 | archivo |
| 3 | `set-from-outside` | un método que llama 3+ setters del mismo objeto del proyecto | `concept-without-a-class`, `behaviour-not-on-owner` | 0 | 9 | archivo + vocabulario |
| 4 | `hierarchy-asked-from-outside` | una jerarquía del proyecto (2+ subtipos) testeada en 2+ miembros fuera de ella | `dispatch-from-outside` | 8 jerarquías | 1 a 3 | modelo |
| 5 | `enum-decided-outside` (se ensancha) | las constantes de un enum, o un discriminador del proyecto, decididos en 2+ lugares de **cualquier** archivo | `variation-as-data` | 1 grupo | 2 grupos y `kind()` | modelo |
| 6 | `walked-from-outside` | una colección de un tipo del proyecto recorrida desde 3+ miembros ajenos | `structure-walked-from-outside` | 0 | 2+ | modelo |
| 7 | `narration` | comentarios de línea dentro de un método, con techo 2 | `procedure-instead-of-model` | 0 | 42 | presupuesto |

Qué le dice cada una al agente:

1. **`reached-through`**. Evidencia: cuántos miembros de `a` usa el llamador además de `b`, y cuántos otros sitios
   usan el mismo camino. Si sólo usa `a.b`: *"te dieron la máquina y sólo hablás con su loop: pedí el loop"*. Si el
   camino se repite (`speccy.machine` en 9 sitios): *"`speccy` no esconde nada: o ofrece la operación, o el
   colaborador real es `machine`"*. Exenciones, tomadas de PMD: `this.b`, constantes, builders y cadenas fluidas.
   Es la versión entre varios lugares de la Ley de Demeter, que según el informe nadie detecta.
2. **`absence-as-state`**. El objeto tiene dos vidas, con y sin ese campo. Tres salidas, y la pregunta que elige:
   ¿siempre está después de construir? → obligatorio en el constructor. ¿Es un colaborador opcional? → Null Object.
   ¿El objeto tiene fases? → dos clases o State. No es arreglo: `Optional` con `ifPresent` en los mismos lugares,
   ni un `hasX()` que envuelve la comparación.
3. **`set-from-outside`**. Alguien arma al objeto desde afuera. Evidencia: cuántos argumentos son getters de un
   mismo tercero. Si lo son (`a.setX(b.getX())` tres veces o más): *"son dos bolsas de propiedades y un tercero que
   traspasa: o uno sabe tomar del otro, o son la misma estructura"*. El ejemplo bueno ya está en tu código:
   `State.setRegisters` recorre `RegisterName` en una línea, donde `SnapshotLoader.setZ80State` llama 32 setters.
4. **`hierarchy-asked-from-outside`**. Ver §7.3.
5. **`enum-decided-outside`**. Mantiene el id y pasa de un archivo a todo el proyecto. Agrupa por enum (los
   conjuntos de constantes que se solapan son la misma decisión, como hace JDeodorant) y por discriminador
   (`kind()`). Toma el filtro que da precisión: ramas de 2+ sentencias, o 2+ sitios. Suma el caso que JDeodorant
   no ve: el enum que hace `switch` sobre sí mismo. Casos que ya conocemos: `KnobRows` y `ScreenSettings` sobre
   `kind()`; el modelo de máquina decidido en `spectrum` y en `core`. En `emulator` encuentra dos `switch` sobre
   `RegisterName`.
6. **`walked-from-outside`**. Evidencia: el dueño, los sitios, y si dos cuerpos de bucle tienen la misma forma:
   *"el mismo bucle está escrito N veces: esa es la operación que falta"*. `refiltered-by-type` queda como caso
   particular. No es arreglo: devolver una copia inmutable o un `Stream`; la forma sigue expuesta.
7. **`narration`**. Cada comentario dentro de un método es el nombre de algo que falta: un paso, y si los pasos
   comparten tres o más locales, un objeto. Como presupuesto con trinquete no acusa lo viejo. Choca con tu regla
   de que un algoritmo complejo puede explicarse: ver §11.

### 7.2 Segunda tanda

| # | id | detecta | modelo que falta | emulator | machine |
|---|---|---|---|---|---|
| 8 | `maps-keyed-alike` | 2+ campos mapa con la misma clave en una clase | `concept-without-a-class` | 0 | 4 |
| 9 | `named-by-text` | un literal repetido 3+ veces en un archivo, o comparado con `equals` en 2+ lugares | `value-without-a-type` | 0 | ~150 apariciones, casi todas en `ui` y `kit` |
| 10 | `flag-argument` | un método no privado que bifurca por su propio parámetro booleano | `variation-as-data` | 4 | ~20 |
| 11 | `said-in-two-calls` | el mismo par de llamadas seguidas sobre un receptor, en 3+ miembros | `protocol-by-convention` | 1 | 2 |
| 12 | `state-asked-across-methods` | un campo mutable testeado en condiciones de 3+ métodos de su clase | `state-without-an-object` | 1 | 4 |
| 13 | `data-without-behaviour` | una clase de datos, junto con el método ajeno que la interroga o la arma | `behaviour-not-on-owner`, `knowledge-without-one-owner` | 0 | 10 |
| 14 | `unpacked-at-the-call` | una llamada que pasa 2+ getters del mismo objeto | `concept-without-a-class` | 1 | 15 |
| 15 | diferencia entre clones | evidencia nueva para las reglas de repetición: qué token difiere | `shape-repeated` | — | — |
| 16 | despacho encubierto | `Map<String, lambda>`, cadenas de `Optional.or` (idea 6 de CERCO.md) | `variation-as-data` | sin medir | sin medir |

`said-in-two-calls` generaliza `notified-by-caller`. `data-without-behaviour` es la idea 7 de CERCO.md con la
medida que faltaba definir: no acusa a un record por ser datos, acusa al **par** datos más interrogador. Para
`named-by-text` y `flag-argument` ya hay definiciones en uso: Sonar S1192 (literal de 5+ caracteres repetido 3+
veces) y S2301 (parámetro booleano selector), y el FlagArgument de Fowler con sus excepciones.

Presupuestos con trinquete para lo que separa pero no tiene forma de regla: comparaciones con `null` por archivo,
locales por método (techo 7), anidamiento (techo 3). Son la idea 3 de CERCO.md.

**Descartadas por la medición**: primitivos con nombre repetido y listas de 4+ parámetros no separan los grupos
(`int address` está igual en `emulator`). Los prefijos de campo aparecen también en `RegisterBank`. Quedan sólo
como evidencia dentro de otros diagnósticos.

### 7.3 Visitor y double dispatch en particular

Tu pregunta era si alcanza con ver que hay `instanceof`. No alcanza, y la medición lo muestra. Las señales de que
falta un Visitor, de más a menos fuerte:

| señal | cómo la ve cerco | estado |
|---|---|---|
| una jerarquía del proyecto testeada desde 2+ lugares | hechos `test` agrupados por jerarquía | regla 4 |
| la jerarquía **ya tiene** `accept` y aun así la testean | el `Model` sabe que el tipo declara `accept(XVisitor)` | evidencia de la regla 4 |
| el cliente está en un módulo que la jerarquía no conoce | módulo Maven del sitio contra el de la jerarquía | evidencia de la regla 4 |
| se acumula mientras se recorre una estructura testeando tipos | un bucle con ramas `instanceof` que agregan a un colector | evidencia de `no-instanceof` (es *Move Accumulation to Visitor* de Kerievsky) |
| una estructura recorrida desde 3+ clientes | regla 6 | regla 6 |
| un recorrido recursivo escrito fuera de la estructura | método que itera los hijos de su parámetro y se llama a sí mismo | evidencia de la regla 6 |
| un discriminador más cast | hechos `decide` y `test` en el mismo miembro | evidencia de la regla 5 |

La regla 4 decide entre método y Visitor con dos datos que cerco puede calcular:

- **cuántas operaciones distintas** hay afuera. Una, y es del concepto del subtipo → método polimórfico en el tipo
  común. Dos o más, o es del concepto del cliente → Visitor: los N sitios pasan a ser N visitors, y la próxima
  operación no cuesta un test.
- **si la operación puede entrar a la jerarquía**. Si el sitio está en `machine/ui` y la jerarquía en `emulator`, que
  no conoce a `ui`, el método no puede mudarse: sólo queda el Visitor.

Mensaje cuando ya existe el visitor: *"`AbstractInstruction` already accepts `InstructionVisitor` (103
implementations); `DefaultInstructionExecutor` tests 3 of its subtypes: write the visitor"*.

El costo del Visitor (agregar un subtipo obliga a tocar todos los visitors) el diagnóstico lo dice cuando la
jerarquía tiene pocas operaciones afuera y muchos subtipos nuevos en la historia de git. No es para la primera
versión.

### 7.4 Perfil de modelo: lo positivo

Las reglas dicen qué sobra. La medición mostró que lo que más separa es lo que **está**: visitors, tipos en
jerarquías, métodos cortos. `survey` imprime ese perfil por módulo:

```
module         types/kloc  in-hierarchy  accept+visit/kloc  lines/method  asks/kloc  null/kloc
emulator            31.8          82%               37.6           3.9       10.4        1.8
machine/core        23.5          34%                2.1           4.9       42.3       15.3
machine/app          3.7          71%                0.0           8.7       84.6       46.5
```

Los números son los de la medición de §1. `asks` suma campos ajenos, setters de objetos del proyecto y llamadas a
través de un campo.

No es portón. Es el espejo para ver si el ciclo arreglo → autopsia → regla mueve el modelo, que es lo que
preguntaste la vez pasada y no te pude contestar con números.

---

## 8. Protocolo: medir antes de construir

Las seis reglas de autopsia encontraron, fuera de su propio caso: 1 hallazgo real, 1 que era mi propio arreglo a
medias, 2 falsos positivos y 0 las otras tres. Para que no vuelva a pasar:

### 8.1 El camino de una regla

1. **Candidata.** Se escribe como regla, pero vive en `Rules.candidates()`: el portón no la corre.
2. **Sondeo.** `mvn cerco:survey` sobre los proyectos de referencia: oozx (con `emulator` contra `machine`), cerco
   mismo y zx-lift.
3. **Muestra.** Hasta 10 hallazgos revisados a mano: ¿hay de verdad un modelo que falta? Los primeros los revisás
   vos; yo propongo la clasificación.
4. **Ficha** en `doc/reglas/<id>.md`: definición, tabla por módulo, la muestra con su veredicto, decisión.
5. **Decisión**, con tres condiciones para entrar:
   - encuentra **3 o más casos reales** además del que la inspiró;
   - **8 de 10** de la muestra son verdaderos;
   - su tasa **separa** el código de referencia (3 veces o más), o encuentra deuda que reconocés como tal.
6. Si no pasa: se generaliza y se vuelve a sondear, o se descarta, y la ficha queda como constancia.

Las autopsias siguen proponiendo reglas, pero lo que deben es **una candidata con su ficha**, no una regla
adoptada. Si la ficha dice que no rinde, la deuda se salda con la ficha (y la firma de una persona, como hoy con
`declinedRules`).

### 8.2 Dos estados, no tres

Pensé en un nivel "aviso" para reglas de precisión media. No lo propongo: la baseline ya hace que adoptar una regla
no duela, y un aviso que no frena es prosa, que es lo que cerco vino a reemplazar. Una regla o es portón o no
existe para el agente.

### 8.3 Medir si el diagnóstico sirve

Es el hueco que la literatura no cubre, y cerco lo puede medir con lo que ya guarda:

- **arreglos por la letra**: autopsias `none:`, hallazgos que se van sin cambio de digest, hallazgos que reaparecen
  bajo otra regla en el mismo miembro;
- **rondas por hallazgo**: cuántas corridas rojas pasan entre que aparece y se resuelve;
- comparar los ciclos de antes y después del diagnóstico. Son pocos casos; va a ser una observación, no una
  estadística, y lo voy a decir así.

---

## 9. Orden de trabajo

Cada fase termina aplicada en oozx con el ciclo de siempre: audit en verde, arreglo, autopsia.

| fase | versión | qué | hecho cuando |
|---|---|---|---|
| 0. Medir | 0.14.0 | goal `survey`, reglas candidatas, las dos sondas portadas a Java dentro de cerco, formato de ficha | `mvn cerco:survey` en oozx reproduce las tablas de §1 |
| 1. Diagnóstico | 0.15.0 | los once modelos, hipótesis y "no es arreglo" en las 29 reglas, reporte nuevo, línea `model:` en la autopsia y conteo por proyecto | las 11 autopsias de oozx etiquetadas; el reporte rojo de un caso conocido muestra el diagnóstico |
| 2. Hechos y modelo | 0.16.0 | `Extractor`, `FileFacts`, `Model`, caché, `ModelRule`, evidencia en `no-instanceof` y `no-switch` | `quick` sigue cerca de 1,7 s en zx-lift y la corrida completa entra en 768 MB |
| 3. Primera tanda | 0.17 a 0.23 | las reglas 1 a 7, una por ciclo: candidata, sondeo, ficha, adopción, arreglar el hallazgo más claro en oozx | cada regla con su ficha y un arreglo real que la use |
| 4. Segunda tanda | después | reglas 8 a 16 y presupuestos, en el orden que diga el sondeo | ídem |
| 5. Más allá | — | co-cambio en git (lo que cambia junto sin dependerse; HIST reporta 72 a 86 % de precisión, dato de segunda mano); clones estructurales; tipos por bytecode si las fichas lo piden | — |

La fase 1 no detecta nada nuevo y es la que más rápido cambia lo que el agente lee. Por eso va antes que el índice.

Si querés ver una regla nueva funcionando antes de la infraestructura, las reglas 2 (`absence-as-state`) y 7
(`narration`) no necesitan índice: son de un solo archivo y se pueden adelantar a la fase 1.

---

## 10. Riesgos

| riesgo | qué se hace |
|---|---|
| **Homónimos sin tipos** (`all()`, `get()`, `values()` de dueños distintos se mezclan) | portón sólo sobre receptores con tipo resuelto; la ficha mide cuántos quedan afuera; si son muchos, bytecode |
| **Falsos positivos que empujan al agente a contorsiones** | el umbral de 8 sobre 10; "no es arreglo" en cada regla; el testigo ya detecta arreglos sin cambio de código |
| **Sobreajuste a oozx** | zx-lift y cerco como segundo y tercer proyecto; ver §11 por un cuarto de afuera |
| **Estilo impuesto por Swing** | vocabulario propio: lo que no declara el proyecto no cuenta |
| **Primitivos a propósito** (el núcleo rápido se deriva del modelo OO) | `value-without-a-type` no acusa `int address`: medido, no separa |
| **Visitor de más** | la regla 4 pide 2+ operaciones afuera o un límite de módulo; con una sola, método |
| **Reporte demasiado largo** | hipótesis una vez por regla; 5 hallazgos por regla en `quick` |
| **La baseline crece al adoptar** | es el trinquete de siempre: se congela y sólo baja |
| **Costo en `quick`** | medir en la fase 2 antes de decidir; sin caché se saltean, como hoy |

Lo que **no** se hace: detección con un LLM dentro del portón. Cerco es determinístico; el LLM es el que lee el
diagnóstico.

---

## 11. Lo que necesito que decidas

1. **Reglas de modelo en `quick`** con caché de hechos. Cambia una decisión escrita en CERCO.md §6. A favor: es lo
   que frena al agente mientras edita y no al final. En contra: más complejidad y un caché que puede quedar viejo.
   Recomiendo que sí, midiendo primero.
2. **Comentarios como medida** (`narration`, techo 2 por método). Cambia "las reglas leen estructura, no prosa". Tu
   CLAUDE.md permite comentar un algoritmo complejo: ¿ese caso va con javadoc en el método, o con excepción firmada?
3. **¿Los estados de snapshot son legítimos?** `Z80State`, `SpectrumState` y `MemoryState` son la deuda más grande
   que encuentran las reglas 3 y 13. Tu decisión anterior fue que lo guardado no conoce formatos y que el traspaso
   es de los mappings. ¿Esas clases son ese diseño, o son un modelo paralelo a `State` y `RegisterBank` heredado de
   JSpeccy? De eso depende que la regla 13 sea correcta ahí.
4. **¿Las reglas corren también sobre `emulator`?** La regla 4 le encuentra deuda real (`OpcodeReference` desde 9
   archivos). Se puede congelar o ir arreglando.
5. **Umbrales de arranque**: 2 métodos para `null`, 3 setters, 3 clientes para un recorrido, 2 lugares para una
   jerarquía. Los puse por lo que dio la medición; la ficha de cada regla los ajusta.
6. **Un proyecto de referencia de afuera.** Uno diseñado con patrones (JHotDraw es el clásico de la literatura)
   serviría para no ajustar todo a tu estilo. No es necesario para arrancar.
7. **Por dónde empiezo.** Propongo la fase 0 y 1 juntas (medir y diagnóstico), con las reglas 2 y 7 adelantadas.

---

## 12. Estado al 2026-11-10, noche

Hecho, en la rama `modelos-ausentes` (cerco 0.14.0, instalado en `~/.m2`, sin publicar):

- **Fase 0**: goal `survey` (una pasada por el reactor, hallazgos por módulo, `-Dcerco.rules`, `-Dcerco.sample`) y
  reglas candidatas (`Rules.candidates()`), que no frenan y cuentan como conocidas para las autopsias.
- **Fase 1**: `MissingModel` (el vocabulario de once), `Diagnosis` e `Hypothesis` en 25 reglas, `DecisionRule` para
  la familia de decisiones, línea `model:` obligatoria en la autopsia, conteo por regla en el reporte. Las 11
  autopsias de oozx etiquetadas.
- **Fase 2**, sin el índice compartido: las reglas de proyecto que lo necesitaban usan `Vocabulary` (los nombres que
  el proyecto declara) o `IndexingRule` (declaraciones por nombre y sitios). El índice con caché para `quick` sigue
  pendiente: depende de la pregunta 1 de §11.
- **Fase 3**, seis candidatas con su ficha en `doc/reglas/`: `absence-as-state`, `hierarchy-asked-from-outside`,
  `set-from-outside`, `reached-through`, `walked-from-outside`, `enum-decided-across`. Ninguna adoptada: esperan la
  revisión de Fernando.

Hallazgos de las candidatas en oozx, después de los arreglos de esta tanda:

| grupo | absence | hierarchy | set | reached | walked | enum |
|---|---|---|---|---|---|---|
| emulator | 0 | 48 | 0 | 6 | 0 | 1 |
| machine | 12 | 8 | 8 | 49 | 19 | 22 |
| translation | 12 | 268 | 1 | 184 | 12 | 7 |

Arreglos en oozx guiados por las candidatas: `WaysOfDrawing` (despacho doble de los efectos que llegan),
`Speccy.select/reset/changeSpeed`, `Machine.model(String)/modelNames()`, `Knob.Kind` con comportamiento y el visitor
`Knob.Controls`.

Pendiente y anotado: `AttachedFrame` pregunta `dock == Dock.FREE` en 8 métodos (FREE debería ser el objeto nulo de
`Dock`; no se tocó sin poder probar el docking en el escritorio real); los reproductores encontrados con
`instanceof Opens` (API binaria de plugins); los estados de snapshot (pregunta 3); `narration` (pregunta 2).

---

## 13. Estado al 2026-11-10, cerco 0.15.0 (rama `lectura-e-indice`)

Hecho sobre `modelos-ausentes`, en un worktree aparte (`coding/cerco-lectura`) y con otra versión, para no tocar el
jar 0.14.0 que usan los hooks de la prueba en oozx. Instalado en `~/.m2`, sin publicar ni mergear.

### 13.1 Cada hallazgo elige su modelo (la lectura)

Un `Finding` puede llevar una lectura (`Reading`): de las hipótesis de su regla, cuál señala el código de ese lugar,
con qué evidencia y con qué transformación. Es consejo y no entra en la clave. El reporte la imprime bajo cada
hallazgo (`missing: … because …` y `do: …`), el JSON la trae, y `survey` la suma por módulo.

Qué mira cada familia:

| regla | lo que decide la lectura |
|---|---|
| `no-instanceof`, `no-cast` | de quién es el tipo (del proyecto o de librería, por los imports); su familia; si la familia ya acepta un visitor; si vive en un módulo que no puede depender del que pregunta; cuántos miembros la preguntan; si se elige de una colección, se busca por su clase o llega declarado como un tipo ajeno; si es una interfaz que no es de la familia del valor (rol); si viaja como `Object` |
| `no-switch`, `no-conditional-chain`, `no-type-code`, `enum-decided-outside` | con qué se compara (constantes de un enum del proyecto, textos, números, constantes sueltas, tipos, `null`) y qué hacen las ramas (devuelven un valor, reasignan un campo que testean, hacen trabajo distinto); a quién se le preguntó |
| `no-static-method` | si recibe un objeto del proyecto, o solo valores planos |
| `no-null-return` | si es el caso de no encontrar, o un campo que todavía no llegó |
| `no-cloned-methods` | si las copias están en una familia o en tipos sin nada en común |
| `method-length` | si llena un objeto con setters, le pregunta mucho a uno, o narra pasos |
| `no-mutable-static`, `flag-driven-from-outside` | quién puede cambiarlo |
| las de una sola hipótesis | esa hipótesis |
| las seis candidatas de §12 | lo que cada una ya juntaba (ver su ficha, §5) |

No leen, a propósito: `equals`, variables de tipo, excepciones, `null` y lambdas con cast, cadenas de condiciones sin
relación, y las reglas de honestidad (`no-ignored-result`, `no-swallowed-exception`, `no-suppressed-warning`).
Tampoco leen todavía `file-length`, `no-repeated-statements`, `ask-then-tell`, `property-shuffle` y `borrowed-mode`.

Medido en oozx `87e376742` con las 35 reglas de 0.14.0: los 10.619 hallazgos son los mismos, línea por línea. El
`audit` de 0.15.0 da verde sobre la línea de base congelada con 0.14.0 (9.972 congelados, 0 nuevos, 0 resueltos).

| grupo | hallazgos | con lectura |
|---|---|---|
| `emulator` | 285 | 246 (86 %) |
| `machine/*` | 551 | 428 (78 %) |
| `translation/*` | 9.783 | 4.607 |

Qué modelo falta, por grupo (hallazgos que leen como cada uno):

| modelo | emulator | machine |
|---|---|---|
| `dispatch-from-outside` | 109 | 16 |
| `behaviour-not-on-owner` | 35 | 126 |
| `value-without-a-type` | 21 | 70 |
| `variation-as-data` | 11 | 62 |
| `knowledge-without-one-owner` | 5 | 44 |
| `concept-without-a-class` | 33 | 37 |
| `state-without-an-object` | 2 | 26 |
| `structure-walked-from-outside` | 2 | 23 |
| `procedure-instead-of-model` | 3 | 16 |
| `shape-repeated` | 25 | 7 |
| `protocol-by-convention` | 0 | 1 |
| sin lectura | 39 | 123 |

La deuda de `emulator` es casi toda de una clase: familias que ya aceptan un visitor y aun así se preguntan desde
afuera. La de `machine` está repartida, y lo que más le falta es comportamiento en el dueño del dato.

La muestra de lecturas la juzgó Claude, no Fernando. Los defectos que encontró en su propia primera versión, ya
corregidos: homónimos (`Timer`, `Frame`) tomados por tipos del proyecto; excepciones; `(Tipo) null`; valores que viajan
como `Object`; un `switch` que reasigna una variable local leído como estado; un enum elegido mal cuando dos tienen
las mismas constantes. Quedan sin medir la precisión de la lectura con un segundo juez.

### 13.2 El índice del proyecto

`Project` (paquete `project`) aprende de cada archivo, antes que las reglas lo vean, los tipos que declara: nombre
simple y completo, supertipos, campos con su tipo, métodos, constantes, tipo de primer nivel y archivo (de ahí el
módulo). Contesta familias, visitors aceptados, quién declara un método, qué enum tiene unas constantes. Las reglas
lo reciben en `inspection(Project)` y le preguntan al concluir.

- `TypeName` e `Imports` dicen de quién es un nombre de tipo: escrito completo, importado por nombre, del mismo
  paquete o de un paquete importado entero.
- `Names` dice con qué tipo se declaró un valor dentro de su archivo (parámetro, local, patrón, campo).
- `Recall` guarda lo aprendido por una corrida completa en `target/cerco/project.json` (1 MB en oozx) y `quick`
  parte de ahí. Solo alimenta la lectura: ningún veredicto depende de lo recordado. `quick` tarda lo mismo que
  antes (unos 0,7 s dentro de cerco).

Esto contesta en parte la pregunta 1 de §11: el índice ya llega a `quick`, pero solo como consejo. Que las reglas de
proyecto *frenen* en `quick` sigue sin hacerse y sigue siendo decisión de Fernando.

Bases nuevas, las tres nacidas de clones que cerco marcó en su propio código: `SiteRule` (un sitio por hallazgo,
leído al final), `RepeatedRule` (lo mismo en varios miembros) y `PlacesRule`.

Pendiente de la infraestructura: `Vocabulary` e `IndexingRule` siguen vivos en cuatro candidatas, que arman su
propio índice por nombre; resolver el tipo de `a.b()` y de un campo heredado a través del índice, que es lo que
pierden `said-in-two-calls`, `unpacked-at-the-call` y `walked-from-outside` (los homónimos `all()`).

### 13.3 Siete candidatas más

| candidata | emulator | machine | muestra (juicio de Claude) | propuesta |
|---|---|---|---|---|
| `flag-argument` | 1 | 13 | 11 verdaderos y 3 débiles de 14 | adoptar |
| `maps-keyed-alike` | 0 | 5 | 4 de 5 | adoptar, mirando antes los 15 de `translation` |
| `named-by-text` | 4 | 18 | 22 de 22 | adoptar |
| `state-asked-across-methods` | 1 | 2 | 3 de 3 | no separa: bajar el umbral o dejarla como evidencia |
| `unpacked-at-the-call` | 6 | 7 | 12 de 13 | no separa: depende de si los clonadores de `emulator` son deuda |
| `said-in-two-calls` | 3 | 0 | 1 caso real | no rinde hasta resolver tipos con el índice |
| `condition-repeated` | 0 | 2 | 1 caso real | no rinde: comparar la forma, no el texto |

Con las seis de §12 son trece candidatas con ficha en `doc/reglas/`. Ninguna está adoptada.

### 13.4 Lo que sigue sin hacer, y por qué

- **Adoptar candidatas.** Es de Fernando: el protocolo (§8.1) pide que las primeras muestras las juzgue él.
- **Congelar por pregunta y no por miembro, y la frontera.** Son decisiones de diseño de cerco que quedaron
  planteadas y sin tomar.
- **`narration`** (comentarios dentro de un método): pregunta 2 de §11.
- **`data-without-behaviour`** y el modelo paralelo de los estados de snapshot: pregunta 3 de §11.
- **El resto del catálogo** de §7.2 y de lo que se cruzó con los catálogos: diferencia entre clones, despacho
  encubierto, grupos de datos que viajan juntos, modelo paralelo, mismos literales en dos archivos, jerarquías de más.
- **Un segundo proyecto de referencia.** Todo está medido sobre oozx.

### 13.5 Adoptadas: `flag-argument`, `maps-keyed-alike` y `named-by-text`

Fernando las adoptó el 2026-11-10. El catálogo pasa a 32 reglas y quedan diez candidatas. En un proyecto con línea de
base, la primera corrida completa con 0.15.0 congela lo que encuentran (en oozx `87e376742`: 16, 20 y 27 hallazgos).

El autochequeo no tiene línea de base, así que cerco tuvo que cumplirlas. Encontraron 15 cosas en su propio código:

| hallazgo | arreglo |
|---|---|
| `Project` con tres mapas por nombre de tipo | un `Named` por nombre: los tipos declarados con él y los que tiene debajo |
| `Runs` con tres mapas | un `Run` por regla (inspección o medida); los digests, aparte, en `Digests` |
| `PackageGraph` con `edges`, `index` y `low` por paquete | un `Node` por paquete, que sabe cuándo se lo alcanzó y a cuál vuelve |
| `PackageCycles.Graph` con dos mapas por paquete | un `Seen` por paquete |
| `ConsoleReport` con las reglas y sus conteos por id | un `Known` por regla |
| `Witness.record(digests, full)` | `recordAll` y `recordSome` |
| `Approval.of(…, configIsDefault, …)` | recibe el digest de la configuración solo cuando hay uno que firmar |
| `"equals"`, `"getClass"` y `"cast"` comparados en ocho lugares de las reglas | `ObjectProtocol`, que es el único que los nombra |

Después de esos arreglos, las 35 reglas anteriores siguen dando en oozx los mismos 10.619 hallazgos.

### 13.6 Dos candidatas más

| candidata | emulator | machine | muestra (juicio de Claude) | propuesta |
|---|---|---|---|---|
| `handed-out-whole` | 1 | 15 | 16 de 16 | adoptar |
| `parallel-model` | 8 | 16 | no encontró el modelo paralelo; encontró las partes de la máquina guardadas sueltas en cinco clases | rehacerla como grupo de colaboradores que viaja junto |

Quedan doce candidatas con ficha.

### 13.7 Segunda parte de la tanda (misma fecha)

- **Adoptada `handed-out-whole`** (decisión de Fernando): 33 reglas. En el propio cerco encontró cinco colecciones
  entregadas tal cual, arregladas dándole a cada dueño la operación que se le pedía (`Digests`, `Signed`).
- **`kept-together`** reemplaza a `parallel-model`: un grupo de campos que guardan tres o más tipos. 2 en `emulator`,
  7 en `machine` (las partes de la máquina).
- **El índice sigue cadenas** (`Chain`, `Project.typeOf`): `a.b()`, campos heredados, `Tipo.estatico()`. Con eso
  `walked-from-outside` cuenta por dueño y quedó en 9 de 9.
- **Patrones que faltaban.** Command es una lectura nueva de las decisiones: textos que eligen cada uno una acción
  (3 en `machine`: los `setGeneralOption(String, Object)`). Observer y Composite son dos candidatas,
  `told-one-by-one` (1 y 2) y `one-and-many` (0 y 1), que en oozx encuentran poco.
- **Un modelo número doce: `hierarchy-without-is-a`.** El vocabulario de §4 nombraba lo que falta; no tenía cómo
  nombrar un modelo de más o mal armado. Lo usan `overridden-to-nothing` (6 y 2) y `middle-man` (0 y 2). Las
  autopsias lo aceptan como cualquier otro id.
- **`data-without-behaviour`**: el par clase de datos más quien la interroga. 0 en `emulator`, 12 en `machine`.

Estado del catálogo: 33 reglas adoptadas y 16 candidatas con ficha. Las que propongo adoptar y esperan a Fernando:
`absence-as-state`, `reached-through`, `walked-from-outside`, `enum-decided-across` (de la primera tanda),
`kept-together` y `data-without-behaviour`.

