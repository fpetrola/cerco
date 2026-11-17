# cerco

Un plugin de Maven que hace cumplir, de forma determinística, las reglas de diseño que un proyecto Java decide
respetar: orientación a objetos pura (alta cohesión, bajo acoplamiento, polimorfismo en vez de condicionales sobre
tipos, encapsulamiento), sin excepciones silenciosas y con tamaños acotados. Se agrega al `pom.xml` y funciona sin
configurar nada: la primera corrida congela el proyecto tal como está, y desde entonces cualquier violación nueva
pone el build en rojo. La deuda congelada sólo puede bajar. Las excepciones existen, pero las firma un humano en una
terminal y vencen.

Todo el código es Java. No hay scripts.

## Instalar

Se publica en [JitPack](https://jitpack.io/#fpetrola/cerco): cada tag es una versión.

```xml
<pluginRepositories>
  <pluginRepository>
    <id>jitpack</id>
    <url>https://jitpack.io</url>
  </pluginRepository>
</pluginRepositories>
```

```xml
<plugin>
  <groupId>com.github.fpetrola</groupId>
  <artifactId>cerco</artifactId>
  <version>0.34.1</version>
  <executions>
    <execution>
      <goals>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Eso es todo. En un proyecto multi-módulo va en el pom raíz y corre en cada módulo durante `verify`.

La primera vez que corre, no hay línea de base: el plugin recorre todo el reactor, anota cada violación y cada
archivo o método que excede el techo, y lo guarda bajo `.cerco/sealed/baseline/`, un archivo de texto por regla, legible en
un diff. Hay que **commitear `.cerco/`**. Todo lo de cerco vive ahí: en `.cerco/sealed/` lo que sólo escriben el cerco y una persona (línea de base, excepciones, firma, sello, autopsias debidas), protegido por el guard; al lado, lo que escribe el agente (`.cerco/autopsies/`, `.cerco/surface/pending/proposal.md`). Un proyecto con la disposición anterior a 0.34 se muda solo en la primera corrida, sin tocar el contenido, y la firma sigue valiendo: a partir de ahí lo que está anotado se tolera, lo nuevo no, y cuando una
violación congelada desaparece el plugin la borra de la línea de base solo (si es que no se mudó: un hallazgo congelado cuyo archivo se movió sigue congelado bajo la ruta nueva, y no cuenta como arreglado ni pide autopsia; un archivo que deja el proyecto, por ejemplo porque se fue a otro repositorio, no pide autopsia por lo que se lleva, ni por los hallazgos de reglas de proyecto que se quedan sin su otra mitad; un ciclo de paquetes congelado que pierde paquetes sigue congelado), también cuando desaparece el archivo
entero (eso lo ve una corrida completa, no el `quick` de un archivo). Nunca la agranda.

Para conectarlo a Claude Code, una vez por proyecto:

```
mvn com.github.fpetrola:cerco:install-hooks
```

Eso escribe en `.claude/settings.json`, sin tocar lo que ya hubiera: un hook `PreToolUse` que niega los comandos de
shell que intentan escribir los archivos del cerco o apagarlo, un hook `PostToolUse` que corre el chequeo rápido
después de cada edición de un `.java` **y después de cada comando de shell que escribe algo** (un heredoc, un
`sed -i`, un script de Python: ahí el alcance son los `.java` que git ve cambiados), un hook `Stop` que corre la
auditoría completa antes de que el agente pueda dar por terminado el trabajo, y reglas `deny` para que el editor del
agente no pueda tocar `.cerco/sealed/` ni los hooks. Cuando el cerco está rojo, el agente recibe el reporte como instrucción
y no puede seguir hasta arreglarlo. Si lo único rojo es algo que sólo una persona puede resolver (un archivo de
excepciones sin firma, una configuración sin firma), el hook lo informa y deja terminar: no hay código que arreglar
y `.cerco/sealed/` no es del agente; el build, en cambio, sigue en rojo hasta que alguien firme. Un comando de shell que sólo lee (`cat`, `git diff`, `ls`, `find`, `2>/dev/null`)
pasa, aunque nombre los archivos del cerco: el guard lee la línea como el shell, comando simple por comando simple, y
decide por la escritura real, no por la mención.

## Las reglas

| id | principio | qué detecta |
|---|---|---|
| `parseable` | el cerco ve todo | un archivo que el parser no puede leer |
| `no-package-cycles` | bajo acoplamiento | paquetes que se importan mutuamente (proyecto entero) |
| `no-mutable-static` | encapsulamiento | un campo `static` que no es `final` |
| `no-public-mutable-field` | encapsulamiento | un campo público de instancia que no es `final` |
| `no-instanceof` | polimorfismo | `instanceof`, y comparar `getClass()` con `==` |
| `no-type-code` | polimorfismo | `getClass()` para algo que no sea un nombre en un mensaje; un código de tipo (`getNodeType()`, `getKind()`, `getTagName()`, un nombre de clase) comparado con `==` o `equals` |
| `no-cast` | polimorfismo | un cast a un tipo de referencia, `Tipo.class.cast(x)` o `Tipo.class::cast` |
| `no-type-keyed-map` | polimorfismo | un `Map` cuya clave es una `Class`: un registro de manejadores por tipo, un service locator |
| `no-switch` | polimorfismo | cualquier `switch`, sentencia o expresión |
| `no-conditional-chain` | polimorfismo | una cadena `if` / `else if` de tres o más ramas |
| `no-static-method` | objetos antes que procedimientos | un método `static` no privado que no es `main` ni una fábrica (`of`, `from`, `parse`, `create`, `valueOf`, `copyOf`, o que devuelve su propio tipo) |
| `no-utility-class` | cohesión | un tipo cuyo nombre termina en Util, Utils, Helper, Helpers, Manager, Impl, Common, Misc o Tools |
| `no-constant-holder` | cohesión | una clase o interfaz con campos y sin métodos, todos `static final`: una clase utilitaria con otro nombre |
| `no-swallowed-exception` | honestidad | un `catch` que no relanza ni usa la excepción |
| `no-ignored-result` | honestidad | una sentencia que llama a un método propio que devuelve `boolean` u `Optional` y tira el resultado (proyecto entero; los nombres que toda colección también tiene, como `add` o `remove`, no cuentan) |
| `no-null-return` | honestidad | `return null` |
| `no-suppressed-warning` | honestidad | cualquier `@SuppressWarnings` |
| `no-repeated-statements` | abstracción | seis sentencias consecutivas que aparecen dos veces (proyecto entero) |
| `no-cloned-methods` | abstracción | el mismo método, nombre y cuerpo, en tres o más tipos (proyecto entero) |
| `flag-driven-from-outside` | un solo dueño | otra clase llama con `true` o `false` al setter pelado de un campo `boolean` (`s.setPending(false)`): un protocolo de estados manejado desde afuera (proyecto entero) |
| `ask-then-tell` | decí, no preguntes | un `if` le pregunta a otro objeto por una propiedad (`s.isHalted()`) y en una rama se la cambia (`s.setHalted(false)`): la transición la decide el que llama, y cada uno tiene su copia |
| `property-shuffle` | decí, no preguntes | a una propiedad de otro objeto se le pone otra propiedad del mismo objeto (`s.setIff1(s.isIff2())`): el que llama mueve estado adentro de un objeto que no es suyo |
| `notified-by-caller` | un cambio y su aviso son una operación del dueño | un setter sobre otro objeto y en la sentencia siguiente el que llama avisa (`loop.setPaused(p); notifyPaused(p);`): los otros que hacen el mismo cambio no avisan |
| `borrowed-mode` | decí, no preguntes | el modo de otro objeto se guarda en una local, se cambia y se restaura (`boolean was = r.isListening(); r.setListening(false); r.write(v); r.setListening(was);`): falta esa operación en el objeto |
| `refiltered-by-type` | un contenedor guarda sus partes por tipo | dos o más loops sobre la misma colección (`desktop.getAllFrames()`) prueban cada elemento por el mismo tipo: la clase deriva cada vez qué elementos son de ese tipo en vez de guardarlos |
| `key-holds-value` | un solo dueño por hecho | una clave construida con un valor se guarda junto a ese valor (`core = new Core(speccy); desk.registerMachine(core, speccy)`): el mapa copia un vínculo que la clave ya tiene |
| `enum-decided-outside` | polimorfismo en vez de preguntar el tipo | las mismas constantes de un enum se deciden en dos o más lugares del archivo (switches con los mismos `case`, o `x == E.A \|\| x == E.B`): lo que se decide es comportamiento de las constantes |
| `file-length` | cohesión | un archivo de más de 400 líneas; los congelados sólo pueden achicarse |
| `method-length` | cohesión | un método o constructor de más de 40 líneas; los congelados sólo pueden achicarse |

`mvn com.github.fpetrola:cerco:explain` imprime cada regla con su principio, su remedio, los modelos que su smell
suele esconder y cuánto de ella está congelado.

Cada regla de diseño trae un **diagnóstico**: los modelos que suelen faltar detrás de su smell (de un vocabulario
cerrado de once: `behaviour-not-on-owner`, `variation-as-data`, `dispatch-from-outside`, …), la pregunta que los
distingue, la transformación habitual y los disfraces que no son un arreglo. El reporte rojo lo imprime bajo cada
regla, y si las autopsias del proyecto ya nombraron modelos para esa regla, dice cuáles. La autopsia nombra el suyo
con una línea `model: <id>` en la causa.

Desde 0.15.0 cada hallazgo además **elige** una de esas hipótesis, cuando el código del lugar alcanza para
decidir, y la imprime debajo: `missing: <modelo>, because <evidencia>` y `do: <transformación>`. Para eso cerco
mantiene un índice de lo que el proyecto declara (tipos, familias, visitors, módulos); una edición suelta lee con lo
que aprendió la última corrida completa (`target/cerco/project.json`). La lectura es consejo: no entra en la clave
del hallazgo ni cambia ningún veredicto.

Las reglas **candidatas** (hoy doce, cada una con su ficha en `doc/reglas/`) no frenan: se miden con `survey` y entran al catálogo cuando su
ficha en `doc/reglas/` muestra que encuentran casos reales más allá del que las originó. `mvn help:describe -Dplugin=com.github.fpetrola:cerco -Ddetail` describe los parámetros.

Las nueve reglas que no son de polimorfismo clásico ni de tamaño nacieron de un experimento: un agente al que se le
pidió un framework nuevo sobre un proyecto con el cerco 0.1.0 instalado leyó las reglas del bytecode y diseñó contra
ellas de antemano. No pasó nunca a rojo, y dejó casts como `Element.class.cast(x)`, un `getNodeType() ==` en vez de
`instanceof`, una cadena de `if` en vez de un `switch`, clases de sólo constantes con un sufijo que no estaba en la
lista, cinco clases con el mismo método de una línea, y un `boolean` descartado que escondió un bug real. Cada una
de esas formas tiene ahora su regla.

`flag-driven-from-outside` es la primera regla que salió de una autopsia (ver más abajo): al sacar un `instanceof EI`
de oozx, la autopsia encontró que el flag `pendingEI` vivía en `State`, el bucle de la CPU lo apagaba, los snapshots
lo copiaban y nada lo prendía nunca, así que la emulación de la sombra del EI no existía. Medida sobre oozx: 20
hallazgos, unos dos tercios protocolos reales (HALT e interrupciones repartidos entre `State`, `OOZ80`, `Halt` y
`UncontendedTiming`) y el resto carga de datos desde un snapshot.

**Cuando una regla nueva llega a un proyecto que ya tiene línea de base**, la siguiente corrida completa (`audit`, o
el `check` de un proyecto de un módulo) congela sus hallazgos actuales y lo anota en el reporte; las corridas
parciales (`quick`, `check` de un módulo) la saltan hasta entonces, con una nota, para no tomar un módulo por el
proyecto. El archivo `.cerco/sealed/baseline/frozen` lleva la fecha y la lista de reglas congeladas.

cerco no lee nada de afuera del proyecto: ni jars, ni `~/.m2`, ni otros repos. Cuando una regla necesita saber quién
usa algo (`unused-member`, `constant-argument`), cuenta main y los tests: el uso que hace un consumidor de afuera se
escribe como un test que lo usa igual. Los tests solo se oyen como llamadores; ningún hallazgo sale de ellos.

Lo que se analiza son los *compile source roots* de cada módulo del reactor (`src/main/java`), no los tests ni los
módulos apagados. Se excluye lo que está bajo `target/`, `generated-sources/` o `generated/`, y cualquier tipo
anotado con `@Generated`.

Un archivo que pasa a ser generado (se le pone `@Generated`) no cuenta como arreglado: sus hallazgos congelados salen
de la línea de base como una regla aflojada, que acepta una persona con `mvn cerco:approve` (desde 0.17.3). Así el
código que escribe una herramienta, como los juegos que traduce oozx, deja de juzgarse con una sola firma y sin
autopsias, y anotar `@Generated` una clase escrita a mano no es una salida barata. La herramienta es la que tiene que
escribir la anotación: lo que genera la próxima vez ya llega marcado.

## Las excepciones

Una excepción es una decisión humana, explícita y con fecha de vencimiento. Viven en `.cerco/sealed/exemptions.yaml`:

```yaml
exemptions:
  - rule: no-switch
    target: pipeline/zx-opcodes/**
    reason: el decodificador Z80 es la frontera de parseo y un switch sobre opcodes es la tabla misma
    category: decision
    until: 2027-03-31
  - rule: no-instanceof
    target: oop/zx-transform/**
    reason: los nodos de JavaParser se despachan por tipo hasta que lleguen los visitantes, tarea T-77
    category: migration
    budget: 12
```

Cada entrada necesita una regla conocida, un `target` (glob sobre rutas relativas a la raíz), un `reason` de al
menos 20 caracteres, una `category` (`false-positive`, `migration` o `decision`) y **exactamente un límite**:

- `until`: una fecha; ese día la excepción muere y la regla vuelve a aplicar.
- `untilVersion`: una versión del proyecto; al llegar, muere.
- `budget`: cuántos hallazgos puede cubrir como máximo; uno más y es rojo.

`mvn com.github.fpetrola:cerco:explain` imprime un archivo de ejemplo completo con estas reglas.

Una excepción firmada también alcanza a lo que ya estaba congelado: en la siguiente corrida completa, los hallazgos
de la línea de base que cubre salen de ella y pasan a exceptuados. Así la deuda tolerada se convierte en decisión
explícita, con motivo y vencimiento, y la línea de base se achica por eso.

Hay un tope de 10 excepciones activas por proyecto. Una excepción vencida es rojo. Una excepción que ya no cubre
nada también es rojo, porque sólo serviría para esconder la próxima violación. Las excepciones por falso positivo
son una señal de que la regla, no el código, es lo que hay que arreglar.

El archivo no vale hasta que una persona lo firma:

```
mvn com.github.fpetrola:cerco:approve
```

`approve` exige una terminal interactiva. Un agente corriendo en un shell sin terminal no puede ejecutarlo: le
contesta que eso es tarea de un humano. La firma, un hash del archivo de excepciones y de la configuración, queda en
`.cerco/sealed/approved`; si el archivo cambia, la firma deja de coincidir y ninguna excepción aplica hasta que alguien
vuelva a firmar. Lo mismo vale para la configuración del plugin: cualquier diferencia respecto de los valores por
defecto (techos, reglas apagadas, sufijos) es un aflojamiento y necesita firma.

Tres capas evitan que el agente se tiente con la salida fácil: las reglas `deny` de Claude Code le niegan el editor
sobre `.cerco/sealed/` y sobre los hooks; el `guard` le niega el shell para escribir esos archivos (`rm`, `mv`, `tee`,
redirecciones, `sed -i`, intérpretes, `git checkout`, `find -delete`, `xargs rm`, o cualquier escritura tras un `cd`
adentro) o para pasar `-Dcerco.skip`; y si la línea de base aparece borrada pero está en el `HEAD` de git, el plugin
se niega a recongelar y pide restaurarla. Además, cada reporte en rojo le dice al agente, con todas las letras, que
las excepciones no son suyas.

## Las autopsias

Arreglar un hallazgo hace desaparecer el síntoma, no explica la causa. Casi siempre la causa es un pedazo de modelo
que faltaba, y ese mismo hueco aparece en otros lugares como olores que parecen no tener relación. Por eso, cada vez
que una corrida saca entradas de la línea de base, cerco deja anotada una **autopsia** pendiente por regla:

- la deuda queda en `.cerco/sealed/autopsies/<id>.owed`, donde el agente no puede escribir;
- el formulario a completar queda en `.cerco/autopsies/pending/<id>.md`, con el síntoma ya escrito: qué hallazgos se fueron.

Mientras haya una autopsia pendiente o incompleta, cerco está en rojo: lo dice el `quick` después de cada edición, lo
bloquea el hook `Stop` y falla `verify`. Si se borra el formulario, vuelve a aparecer. Un segundo arreglo de la misma
regla se suma a la autopsia abierta en vez de abrir otra.

cerco no puede juzgar el análisis, pero sí obligar a que exista y a que mire alrededor del arreglo:

| sección | qué exige |
|---|---|
| `Cause` | qué faltaba en el modelo, en al menos 150 caracteres, sin copiar la causa de otra autopsia archivada |
| `Context` | al menos dos lugares leídos (`ruta:línea`, relativos a la raíz, que existan), uno fuera de lo arreglado |
| `Same cause elsewhere` | otros lugares (`ruta:línea`) con la misma causa, o `none` y cómo se buscó |
| `Principle` | el principio de diseño en juego |
| `Candidate rule` | `rule: <id>` de una regla **nueva**, con qué detectaría y un ejemplo `ruta:línea`; o `covered-by: <id>` de una regla que ya existe y alcanzó; o `same-as: <id>` de una autopsia archivada; o `none:` y por qué ninguna regla podría |

Cuando el formulario está completo, la siguiente corrida lo mueve a `.cerco/autopsies/` y salda la deuda. El catálogo va a
git: es el registro de por qué el código tenía la forma que tenía. `mvn com.github.fpetrola:cerco:autopsies`
lista las reglas candidatas ordenadas por cuántas autopsias las piden (`same-as` cuenta para la regla de la autopsia
que nombra): una regla que vuelve desde arreglos que no tienen nada que ver es la próxima regla de cerco.

No dispara autopsia el techo de tamaño que baja sin salir del límite ni lo que libera una excepción firmada. Un
archivo borrado sí: el código muerto también merece saber por qué existía.

Proponer no alcanza: una regla que pide una autopsia archivada (`rule:` o `same-as:`) y que el cerco que corre no
tiene queda como deuda del agente, roja como una autopsia pendiente, con la ruta del código de cerco (embebida en el
jar al compilar) y la autopsia como especificación. Se cierra sola cuando llega una versión de cerco que tiene una
regla con ese id, o cuando una persona la descarta en `<declinedRules>` y firma.

`rule:` propone una regla que cerco no tiene (desde 0.16.0). La regla que encontró el síntoma no es una propuesta, y
ninguna otra regla adoptada tampoco: el formulario las rechaza. Cuando la regla que ya existe alcanzó, se dice con
`covered-by: <id>`, que se archiva sola y pide tres cosas: que la regla esté en el catálogo del cerco que corre, que
haya marcado ese código antes del arreglo (es la de la autopsia, o tiene hallazgos congelados en los archivos
arreglados) y al menos 150 caracteres que digan por qué lo que la regla dijo ya llevaba al modelo que faltaba.

`none:` no es la salida barata: una autopsia que dice que ninguna regla podría atrapar su caso no se archiva sola,
espera a que una persona la lea y firme con `mvn cerco:approve` (el hook la deja pasar, el build no). Espera una sola
a la vez: mientras tanto, toda otra autopsia propone una regla. Y `same-as:` tiene que nombrar una autopsia que pida
una regla.

## Lo que sólo una persona acepta

Como el agente puede escribir el código de cerco, aflojar una regla no puede pasar por arreglar el código. Junto a la
línea de base, cerco guarda el digest del código de cada archivo (del árbol sin comentarios ni formato), el del
proyecto entero en la última corrida completa y el de sus propios valores por defecto. Es rojo humano, que sólo
acepta `approve`:

- un hallazgo congelado que se va mientras su código sigue igual (por archivo, el digest del archivo; para las reglas
  de proyecto, el del proyecto): cambió la regla, no el código. No pide autopsia; queda en `.cerco/sealed/baseline/loosened`;
- una regla congelada que el cerco que corre ya no tiene;
- valores por defecto distintos de aquellos con que se guardó la línea de base.

## Goals

| goal | corre | qué hace |
|---|---|---|
| `check` | en `verify`, por módulo | las reglas por archivo sobre el módulo; congela todo el reactor si no hay línea de base |
| `audit` | hook `Stop`, CI, a mano | todo el reactor, todas las reglas, y detecta excepciones vencidas o vacías |
| `quick` | hook `PostToolUse` | el archivo recién editado, o tras un comando de shell que escribe, los `.java` que git ve cambiados; reglas por archivo |
| `approve` | un humano, en terminal | firma las excepciones y la configuración, y acepta reglas que se fueron, defaults cambiados y hallazgos que una regla dejó de ver |
| `install-hooks` | una vez por proyecto | escribe los hooks y las reglas `deny` en `.claude/settings.json` |
| `explain` | a mano | las reglas, con su principio, su remedio y lo congelado |
| `autopsies` | a mano | las reglas que proponen las autopsias archivadas, las más pedidas primero |
| `survey` | a mano | mide reglas por módulo sin tocar la línea de base: las candidatas, las de `-Dcerco.rules=a,b`, o todas con `-Dcerco.rules=all`; imprime también cuántos hallazgos de cada módulo leen como cada modelo que falta, y la superficie de cada módulo (tipos y miembros en los alcances `interfaces`, `public` y `all`, qué parte declara una interfaz, cuántas firmas dicen `String`, `Object`, `boolean` o una función, y cuántos miembros nadie nombra); cuántos hallazgos, cuántos por mil líneas y una muestra para juzgar a mano (`-Dcerco.sample=N`, 10 por defecto) |

Los reportes también quedan en `target/cerco/<alcance>.json`.

## Configuración

No hace falta. Si un proyecto necesita apartarse de los valores por defecto, va en el `<configuration>` del plugin y
requiere firma (`approve`): `fileCeiling` (400), `methodCeiling` (40), `utilitySuffixes`, `factoryPrefixes`,
`generated` (globs de código generado), `maxExemptions` (10), `includeTests` (false), `disabledRules`,
`autopsies` (true), `declinedRules` (reglas propuestas por autopsias que una persona decidió no construir), `sealedSurface` (ninguno: `interfaces`, `promises` (interfaces de cualquier apertura y los constructores y métodos estáticos públicos de clases públicas), `public` o `all` sella la superficie de cada módulo en `.cerco/surface/<módulo>.yaml`, agrupada por tipo (los `.txt` de antes de 0.23 se leen y pasan a YAML en la siguiente corrida completa); todo miembro que otros pueden usar y una persona no firmó es un hallazgo que no se congela ni se exceptúa; la salida es quitarlo o completar `.cerco/surface/pending/proposal.md`, que cerco escribe con lo que crece y que `approve` firma, agrandando el sello y archivándola en `.cerco/surface/signed/NNNN.md`, donde el agente no puede editarla; la propuesta está completa sólo si los tests de Contract nombran cada miembro que crece. Un proyecto sin código queda sellado vacío en su primera corrida completa. Con el sello prendido corre también `unpromised-member`: un método, componente de record o campo no privado de una clase que ninguna interfaz de su jerarquía declara, o un constructor público o método estático que el sello no tiene; lo que el código ya ofrecía cuando llegó el sello se congela como cualquier regla nueva y sólo puede bajar, lo que crece después es rojo).

## Medido sobre zx-lift (38 módulos, 1577 archivos analizados, 52 MB de fuentes en el repo)

| corrida | tiempo | memoria |
|---|---|---|
| primera `audit`, congelando 70.050 entradas | 26 s | 768 MB de heap alcanzan |
| `audit` siguiente, verde | 16 s | |
| `check` de los 38 módulos, como en `verify` | 9,6 s | |
| `quick` desde el hook, Maven incluido | 1,7 s | |
| `quick` tras un comando de shell que no escribe | 0,06 s | sin Maven |
| `guard` por cada comando de shell | 0,06 s | |
| `audit` de 0.2.0 adoptando nueve reglas nuevas (15.848 hallazgos más) | 34 s | |

## Lo que la versión cero no tiene todavía

- Reglas sobre bytecode con tipos resueltos (ArchUnit): dirección de dependencias entre módulos, capas, `new` de
  concretas de otro módulo. Hoy los ciclos se ven por `import`, a nivel de paquete.
- Métricas de cohesión y acoplamiento (LCOM, CBO) con trinquete.
- Límites por hito (una tarea cerrada en un tablero) y presupuestos con cronograma de baja.
- Salida SARIF, pre-commit de git (sería un script, y acá no hay scripts: la muralla sin tope es la CI).
- El plugin se verifica a sí mismo con un test (`SelfCheckTest`) en vez de con su propio goal, para no depender de
  una versión anterior instalada.
