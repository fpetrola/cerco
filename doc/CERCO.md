# cerco: intención, conceptos, arquitectura y decisiones

Este documento es para quien sigue el desarrollo de cerco, persona o agente. El `README.md` dice cómo usarlo; acá
está por qué es como es, cómo está armado por dentro, qué se decidió y por qué, qué no sabe hacer y qué falta. Cuando
algo de acá no coincida con el código, manda el código: corregí este documento.

Estado al escribirlo: versión 0.2.0, commit `ee5db5e`, 62 tests, 21 reglas, ~100 archivos Java chicos.

---

## 1. Intención

### El problema

`CLAUDE.md`, las guías de arquitectura y los lineamientos de diseño son prosa. Un agente de IA los sigue
**probabilísticamente**: casi siempre, hasta que no. Y cuando no, nadie se entera hasta que el daño ya está en el
código, repetido en diez lugares.

### La respuesta

Convertir los lineamientos de diseño en **chequeos determinísticos** que corren cada vez que el agente toca código,
lo frenan y le dicen qué está mal, qué principio rompe y qué hacer en su lugar. No es un linter de estilo: los
lineamientos son de diseño orientado a objetos purista:

- polimorfismo en vez de condicionales sobre tipos (sin `instanceof`, `switch`, casts, cadenas de `if`, códigos de
  tipo, mapas indexados por clase);
- encapsulamiento (sin estado estático mutable, sin campos públicos mutables);
- objetos antes que procedimientos (sin métodos estáticos públicos salvo `main` y fábricas, sin clases utilitarias);
- alta cohesión y bajo acoplamiento (sin ciclos de paquetes, tamaños acotados de archivo y método);
- honestidad (sin excepciones tragadas, sin `return null`, sin `@SuppressWarnings`, sin resultados ignorados);
- abstracción (sin bloques ni métodos clonados).

### Tres restricciones de Fernando, no negociables

1. **Todo el código de la herramienta es Java.** Cero scripts: ni `sh`, ni Python, ni hooks de git en shell. Por
   eso los hooks de Claude Code invocan una clase Java (`HookMain`), y por eso no hay pre-commit.
2. **Extremadamente fácil de integrar.** Se agrega el plugin al `pom.xml` y funciona con todas las reglas sin
   configurar nada del proyecto. Configurar es para casos muy específicos, y además cuesta una firma humana.
3. **Las excepciones no pueden ser una tentación para la IA.** O son manuales del desarrollador, o existe un
   mecanismo por el que el agente entiende que no puede abusarlas. Cerco hace las dos cosas: las excepciones
   necesitan una firma en una terminal interactiva (que un agente no tiene), y cada reporte rojo le dice al agente,
   con todas las letras, que no son suyas.

---

## 2. Cerco en una página

```
            primera corrida                     cada corrida siguiente
   proyecto ───────────────► .cerco/baseline    hallazgos de hoy ──┐
   tal cual   (congela todo)  (un .txt por regla)                   ▼
                                                 ┌─ en la baseline ──► tolerado (frozen)
                                                 ├─ ya no aparece ───► se borra solo (solved)
                                                 ├─ cubierto por una excepción firmada y vigente ─► exempted
                                                 └─ lo demás ────────► NUEVO = ROJO
```

- La baseline **sólo achica**. Nada la agranda salvo la adopción de una regla nueva (ver §3.5).
- El agente se encuentra con el cerco en tres momentos: antes de cada comando de shell (`guard`), después de cada
  edición o comando que escribe (`quick`), y antes de terminar (`audit`, hook `Stop`). El build (`check` en
  `verify`) y la CI son las otras dos murallas.
- Rojo en un hook = exit 2 con el reporte en stderr, que Claude Code le muestra al agente como instrucción.

---

## 3. Conceptos

### 3.1 Regla

Un principio de diseño traducido a algo medible sobre el AST. El parser es JavaParser 3.26 en nivel `JAVA_21`, sin
comentarios (`setAttributeComments(false)`: las reglas leen estructura, no prosa). Sin resolución de tipos.

Cada regla (`rules/Rule`) declara `id`, `principle`, `description` y `remedy`: el reporte no sólo dice qué está mal,
dice qué hacer. Hay tres formas:

| forma | clase base | corre en | ejemplo |
|---|---|---|---|
| por archivo | `FileRule` | todas las corridas, incluido `quick` | `no-switch` |
| de proyecto entero | `ProjectRule<S>` (estado `S` acumulado archivo por archivo, `conclude` al final) | sólo corridas completas | `no-package-cycles`, `no-cloned-methods`, `no-repeated-statements`, `no-ignored-result` |
| de presupuesto | `BudgetRule` + `Measuring` | todas | `file-length`, `method-length` |

`Rule.perFile()` vale `true` por defecto; `ProjectRule` lo fija en `false`. La regla no recibe la lista de archivos:
recibe una `Inspection` con estado que el motor alimenta un archivo por vez, en orden de nombre. Eso es lo que
permite que un proyecto de 52 MB entre en 768 MB de heap (§5.3).

El catálogo es `rules/Rules`, en el orden en que se reportan, menos las que el proyecto apagó (`disabledRules`).

Umbrales fijos en el código (no configurables hoy): `no-conditional-chain` desde 3 ramas, `no-repeated-statements`
con ventana de 6 sentencias, `no-cloned-methods` desde 3 tipos (ignora `equals`/`hashCode`/`toString`). Los techos
de tamaño (400 líneas por archivo, 40 por método) sí son configurables, con firma.

### 3.2 Hallazgo y clave

Un `Finding` tiene `rule`, `file` (ruta relativa a la raíz), `line`, `key` y `detail`. La línea sirve al humano y al
agente; **la clave no lleva línea**. Su forma es

```
ruta/relativa/Archivo.java|com.paquete.Tipo.metodo(Tipos)|qué
```

(el prefijo de archivo lo pone `FileRule.finding`; el miembro, `Enclosing.of`; el `qué` lo arma cada regla, p.ej.
`switch(kind)`). Así, un refactor que mueve líneas no genera hallazgos nuevos falsos. `baseline/Key.file()` recupera
el archivo de una clave: de eso dependen el alcance, la poda de archivos borrados y la liberación por excepción.

### 3.3 Línea de base (baseline) y trinquete

La primera corrida congela el proyecto tal como está bajo `.cerco/baseline/`:

- `<regla>.txt`: una línea por clave, `cuenta<TAB>clave`, ordenadas. Legible en un diff.
- `<regla>.ceilings`: para las reglas de presupuesto, `techo<TAB>clave` de lo que ya excedía el techo.
- `frozen`: la fecha del congelamiento y debajo la lista de reglas congeladas.

La baseline es un **multiset** (`baseline/Multiset`): tres violaciones idénticas congeladas y cuatro encontradas = una
nueva, la última por línea (`Reconciler`). Cuando una entrada deja de aparecer se borra sola (`solved`). Un archivo de
regla que queda vacío se elimina.

**Techos**: un método congelado con 120 líneas tiene techo propio de 120. Si baja a 90, el techo pasa a 90; si baja a
40 o menos, la entrada desaparece; si sube a 121, es rojo (`CeilingReconciler`). El techo sólo baja.

**Autopsias** (0.3.0): lo que sale de la baseline en una corrida deja anotada una autopsia por regla
(`autopsy/Autopsies`). Ver 3.10.

### 3.4 Alcance (Scope)

`engine/Scope(label, files, full)`:

- **Completo** (`audit`; `check` de un proyecto de un solo módulo): todo el reactor. Corren también las reglas de
  proyecto, se adoptan reglas nuevas, se podan las entradas de archivos que ya no existen (cuentan como `solved`),
  se liberan las entradas cubiertas por excepciones firmadas, y una excepción que no cubre nada es rojo.
- **Parcial** (`check` de un módulo, `quick`): sólo reglas por archivo. Las entradas de la baseline fuera del alcance
  se conservan intactas: una corrida parcial no puede distinguir "borrado" de "fuera de alcance".

### 3.5 Adopción de reglas nuevas

Una regla que llega a un proyecto ya congelado (no figura en `frozen`) se congela sola en la siguiente corrida
**completa**, con una nota en el reporte, y se agrega a `frozen` conservando la fecha (`Engine.adopt`,
`BaselineStore.extendFrozen`). Las parciales la saltan con nota, para no tomar un módulo por el proyecto. Así se
agregan reglas a un proyecto en marcha sin ponerlo en rojo de golpe: 0.2.0 adoptó nueve reglas en zx-lift
(15.848 hallazgos) en un `audit` de 34 s.

### 3.6 Excepciones

Una excepción es una **decisión humana, explícita y con límite**, en `.cerco/exemptions.yaml` (SnakeYAML a beans,
`ExemptionsFile`). Cada entrada:

- `rule`: un id conocido;
- `target`: glob sobre la ruta relativa a la raíz (`PathMatcher` de `glob:`);
- `reason`: al menos 20 caracteres;
- `category`: `false-positive`, `migration` o `decision`;
- **exactamente un límite** (`Limit`, interfaz sellada): `until` (fecha), `untilVersion` (versión del proyecto,
  comparada con `VersionOrder`) o `budget` (máximo de hallazgos cubiertos).

Tope: 10 (`maxExemptions`). Vencida, o pasada de presupuesto = rojo. En corrida completa, una excepción que no cubre
nada = rojo: sólo serviría para esconder la próxima violación. `explain` imprime un archivo de ejemplo válido
(`ExemptionsFile.EXAMPLE`), porque un agente tuvo que leer el bytecode para adivinar el formato.

Una excepción cubre sólo hallazgos **nuevos**… y desde `ee5db5e`, también lo congelado: en corrida completa y con
firma válida, las entradas de la baseline que una excepción cubre **salen** de la baseline y vuelven como hallazgos
cubiertos por ella (`engine/Signed.releases`, `Engine.Kept`). La deuda tolerada en silencio pasa a ser decisión
explícita con motivo y vencimiento. Ojo: si después la excepción vence, la corrida es roja por el *problema*
"exemption … expired", pero los hallazgos siguen listados como exceptuados (`Exempting` los cubre igual) y no vuelven
a la baseline; como no hay hallazgos *nuevos*, el hook lo trata como rojo humano (§3.8) y deja pasar, y sólo el
build y la CI quedan rojos. Es coherente con "renovar o quitar es decisión humana", pero conviene saberlo.

### 3.7 Firma (`approve`)

Ninguna excepción aplica sin firma. `mvn cerco:approve`:

1. exige una terminal interactiva (`hooks/Terminal`: `System.console()` y, en Java 22+, `Console.isTerminal()` por
   reflexión, porque desde 22 `console()` responde aunque los flujos sean pipes; el plugin compila con release 17);
2. valida el YAML y lista lo que se va a firmar;
3. pide que se escriba exactamente `yes`;
4. escribe `.cerco/approved` (properties): SHA-256 del YAML, SHA-256 de la configuración si no es la de defecto,
   quién (`GIT_AUTHOR_NAME` o `user.name`) y cuándo.

Si el YAML cambia, el hash no coincide y **ninguna** excepción aplica hasta que alguien vuelva a firmar.

**Configuración también firmada**: `model/Settings` tiene `DEFAULTS` y una `fingerprint()` canónica. Cualquier
diferencia (techos, sufijos, prefijos de fábrica, globs de generado, tope de excepciones, `includeTests`,
`disabledRules`) es un aflojamiento y es rojo hasta que un humano la firme.

### 3.8 Rojo humano

Si no hay hallazgos nuevos y lo único rojo es algo que sólo una persona puede resolver (excepciones sin firma,
configuración sin firma, una excepción vencida), bajo `-Dcerco.hook=true` el hook avisa y **deja terminar**: no hay
código que arreglar y `.cerco/` no es del agente. El build sigue rojo. Nació de un `Stop` que entraba en bucle
diciéndole "Fix the code" a un agente que no tenía código que arreglar.

### 3.9 Código generado y alcance de fuentes

Se analizan los *compile source roots* de cada módulo del reactor (más los de test si `includeTests`). Se excluye lo
que cae en los globs de `generated` (`**/target/**`, `**/generated-sources/**`, `**/generated/**`) y todo archivo con
algún tipo anotado con una anotación cuyo nombre termina en `Generated` (`engine/Generated`).

---

### 3.10 Autopsia

Primera regla nacida de una autopsia: `flag-driven-from-outside` (0.4.0), de la autopsia de oozx
`2026-11-10-no-instanceof`.


La idea de Fernando: los hallazgos son síntomas de modelo faltante, y arreglarlos para cumplir la letra de la regla
deja la causa en su lugar. Cada arreglo tiene que terminar en un análisis de la causa, y ese análisis es la fuente de
las reglas nuevas. Para que no dependa de la buena voluntad del agente, es determinista:

- `Engine.reconcile` calcula qué claves salieron de la baseline en esta corrida (`Solved`): las que bajaron de cuenta,
  las de archivos borrados en una corrida completa, y las de techos que desaparecieron. No cuentan las liberadas por
  una excepción firmada ni los techos que sólo bajan.
- `Ledger` escribe la deuda en `.cerco/autopsies/<fecha>-<regla>.owed` (la regla y las claves). Está bajo `.cerco/`,
  así que el `deny` del editor y el `guard` del shell ya la protegen. Una regla con autopsia abierta suma las claves
  nuevas a esa misma.
- `Catalog` escribe el formulario en `autopsies/pending/<id>.md` desde `Template`, con la sección `Symptom` regenerada
  por cerco en cada corrida. Si falta, lo vuelve a escribir.
- `FormCheck` valida: causa de 150 caracteres que no repita una archivada, dos lugares `ruta:línea` que existan
  (`References`) con uno fuera de lo arreglado, otros lugares o `none`, principio, y regla candidata (`rule:`,
  `none:` o `same-as:` de una archivada). Lo que está dentro de comentarios HTML es la guía de la plantilla y no cuenta.
- Completo, se mueve a `autopsies/` y la deuda se borra. Incompleto, es un `Owed` en el `Outcome`: rojo, y a
  diferencia del rojo humano el hook no lo deja pasar, porque es tarea del agente.
- `Backlog` y el goal `autopsies` ordenan las reglas propuestas por cuántas autopsias las piden.

- Proponer no alcanza (0.5.0): `Backlog.unbuilt` compara las reglas que piden las autopsias archivadas con
  `Rules.known()` (todas, apagadas o no) y cada una que falta es un `Owed` más, con id `rule-<regla>`: rojo del
  agente, como una autopsia pendiente. El mensaje dice dónde está el código de cerco (`cerco-source.properties`,
  filtrado por Maven con `${project.basedir}` al compilar) y el camino: regla y tests en una rama, tests de cerco
  verdes, versión nueva, `mvn install`, versión nueva en el pom del proyecto; la regla llega y congela sus hallazgos
  como cualquier otra (§3.5). Sólo una persona la descarta: `declinedRules` en la configuración, que como todo
  apartamiento de los defaults vale sólo con firma.

Riesgo conocido: si cada arreglo cuesta una autopsia, el agente puede preferir no arreglar deuda vieja. Lo mitigan
una autopsia por regla (no por hallazgo) y `same-as:`. La configuración `autopsies` (true) la apaga, con firma.

### 3.11 Testigo: aflojar una regla no es arreglar el código

Desde que el agente vigilado escribe el código de cerco (§3.10), podría aflojar una regla en vez de arreglar su
código. `engine/Witness` guarda, junto a la baseline:

- `digests`: `digest<TAB>archivo` del código de cada archivo leído (SHA-256 del árbol impreso por JavaParser, sin
  comentarios ni formato; del texto si no parsea). Lo calcula `Parser`, en paralelo, y viaja en `ParsedFile`;
- `self`: `project`, el digest de todos los archivos en la última corrida completa, y `defaults`, el de
  `Settings.DEFAULTS.fingerprint()` del cerco que guardó la baseline.

Lo que sale de la baseline (`Solved`) pasa por `Witness.fixedInCode`: una clave de regla por archivo cuyo archivo
tiene el mismo digest que la última vez, o una de regla de proyecto sin que ningún archivo haya cambiado desde la
última completa (las reglas de proyecto dependen de otros archivos, por eso no alcanza el del suyo), se anota en
`loosened` y no pide autopsia. Son problemas de rojo humano (§3.8), junto con una regla de `frozen` que el cerco no
conoce y unos defaults con otro digest. `approve` los muestra y, con el `yes`, `Witness.accept` los acepta: saca las
reglas idas de `frozen` con sus baselines, registra los defaults y vacía `loosened`.

Límite: el digest es del código, no del comportamiento; un cambio trivial del archivo (renombrar una variable) junto
con la regla aflojada pasa. Atrapa el caso grueso: una regla que deja de ver hallazgos en archivos que nadie tocó.

## 4. Integración con Claude Code

### 4.1 Qué escribe `install-hooks`

`InstallHooksMojo` mezcla en `.claude/settings.json`, sin romper lo que haya e idempotente (cada hook lleva un
marcador, `HookSpec.marker`; `ClaudeSettings` reemplaza el propio y respeta los ajenos):

| evento | matcher | comando | timeout |
|---|---|---|---|
| `PreToolUse` | `Bash` | `java -cp <jar> com.fpetrola.cerco.hooks.HookMain guard` | 20 s |
| `PostToolUse` | `Edit\|Write\|MultiEdit\|Bash` | `... HookMain quick --mvn <maven.home>/bin/mvn` | 240 s |
| `Stop` | — | `... HookMain audit --mvn ...` | 900 s |

y reglas `deny`: `Edit(.cerco/**)`, `Edit(.claude/settings.json)`, `Bash(*-Dcerco.skip*)`.

El `java` es el de la JVM que corrió Maven y el jar es el del plugin en `~/.m2`: **rutas absolutas de esta máquina**.

### 4.2 `HookMain`

Punto de entrada sin Maven y sin dependencias: por eso existe el paquete `json` (un parser JSON mínimo propio) en vez
de usar Jackson, que sí usan `ClaudeSettings` y `JsonReport` dentro de Maven. El guard corre con `java -cp jar`, sin
el classpath del plugin.

- `guard`: decide en la misma JVM (~60 ms) y, si niega, imprime el JSON de `permissionDecision: deny` (`Decision`).
- `quick`: si fue un `Edit`/`Write` de algo que no es `.java`, sale en 0. Si fue `Bash`, lee el comando con
  `ShellCommand` y sólo si **escribe algo** lanza Maven; un comando que sólo lee cuesta 60 ms.
- `audit`: lanza Maven.
- Maven se lanza como `mvn -q -o -B -f <root>/pom.xml -Dcerco.hook=true com.fpetrola:cerco-maven-plugin:<goal>`
  con `MAVEN_OPTS=-Xmx1g` y el JSON del hook por stdin (`MavenRun`). Exit distinto de 0 → `MavenOutput` limpia el
  ruido de Maven/JVM y el hook sale con **2** y el reporte en stderr.
- JSON ilegible en la entrada → deja pasar (exit 0) con un aviso: un hook roto no debe trabar al agente.
- La raíz es `CLAUDE_PROJECT_DIR`.

### 4.3 `quick`: qué archivos mira

`QuickMojo`: si el hook nombra un archivo (`tool_input.file_path`), ese. Si no (comando de shell, o una persona en
una terminal), los `.java` que git ve modificados, cambiados, agregados o no rastreados (`GitTree`, que le pregunta al `git` de la línea de comandos: JGit 6 no entendía los worktrees enlazados).
En ambos casos se intersecan con las fuentes del reactor, así que un `.java` fuera de los source roots no se mira.

Esto nació en 0.2.0: la sesión de cerco-demo escribió casi todo por heredocs y Python, y el `quick` de 0.1.0, que
sólo miraba `Edit|Write`, nunca corrió.

### 4.4 El guard lee el shell como un shell

`ShellLexer` tokeniza (comillas, escapes, redirecciones, separadores `;` `&&` `||` `|` y saltos de línea; cada tipo de
token sabe aplicarse a sí mismo al `SimpleCommandBuilder`: polimorfismo, no `switch`), y `ShellCommand` lo agrupa en
`SimpleCommand`s. Se niega sólo una **escritura real** a `.cerco` o `.claude/settings.json`:

- comandos que escriben (`rm`, `mv`, `cp`, `tee`, `truncate`, `install`, `rsync`, `ln`, `chmod`, `chown`, `touch`,
  `dd`, `shred`, `unlink`, `rmdir`, `mkdir`, `patch`), redirección a esos paths, `sed -i` / `perl -i`;
- intérpretes (`python`, `ruby`, `node`, `perl`, `php`, `sh`, `bash`, `zsh`, `eval`, `jq`, `yq`, `sqlite3`) cuya línea
  los menciona;
- `git checkout/restore/rm/clean/stash/reset/update-index/mv/apply/revert/switch` sobre ellos;
- `find … -delete/-exec/-execdir/-ok/-okdir`, `xargs` con un escritor después de un comando que los nombró;
- con prefijos `sudo`, `command`, `exec`, `nohup`, `time` o asignaciones `VAR=…` delante, igual;
- cualquier escritura después de un `cd` adentro.

Leer, listar, diffear (`cat`, `git diff`, `ls`, `2>/dev/null`, `>&2`) pasa. Es una aproximación de shell, no un
shell: alcanza para distinguir escribir de mencionar. Aparte, `-Dcerco.skip` se niega **siempre**, por simple
coincidencia de texto (aunque aparezca en un `grep`): es el interruptor del cerco.

### 4.5 Anti-borrado

Si `.cerco/baseline/frozen` está en el `HEAD` de git pero no en disco (`GitTree`), `check`, `audit` y `quick` se
niegan a recongelar y piden `git checkout -- .cerco/baseline`. Sin esto, borrar la baseline sería la forma más corta
de "arreglar" todo.

### 4.6 Las murallas

1. **Hooks**: rápidas y en el momento; evitables sólo editando `settings.json`, que está denegado.
2. **Build**: `check` en `verify`, por módulo.
3. **CI**: `audit` sin tope de tiempo. Es la muralla que no se puede saltear. Pre-commit no: sería un script.

---

## 5. Arquitectura

### 5.1 Paquetes (`com.fpetrola.cerco`)

| paquete | qué tiene |
|---|---|
| `model` | `SourceFile` (ruta absoluta + nombre relativo a la raíz), `ParsedFile`, `Finding`, `Measure`, `Settings` |
| `rules` | `Rule`, `Inspection`, `FileRule`, `ProjectRule<S>`, `BudgetRule`/`Measuring`, `Enclosing` (nombres de miembros para las claves), `PackageGraph`, las 21 reglas, `Rules` |
| `engine` | `Parser`, `Generated`, `Sources` (recorrido + globs), `Feed`, `Runs` (una `Inspection` por regla), `Tally`, `Scope`, `Signed`, `Witness` (digests de código y de cerco, §3.11), `Outcome`, `Engine` |
| `baseline` | `BaselineStore`, `Multiset`, `Key`, `Reconciler`, `CeilingReconciler`, `Reconciliation` |
| `exemptions` | `ExemptionsFile` (+ `ExemptionsDocument`/`ExemptionEntry`, los beans de YAML), `Exemption`, `Limit`, `VersionOrder`, `Context`, `Verdict`, `Exempting`, `Applied`, `Loaded`, `Approval`, `Digest` |
| `report` | `ConsoleReport` (lo que lee el agente), `JsonReport` (`target/cerco/<label>.json`) |
| `hooks` | `HookMain`, `HookInput`, `Guard`, `Decision`, `ShellCommand`/`ShellLexer`/`SimpleCommand`, `MavenRun`, `MavenOutput`, `Terminal`, `StdinText` |
| `json` | parser JSON mínimo para `HookMain` |
| `maven` | los mojos (`AbstractCercoMojo`, `CheckMojo`, `AuditMojo`, `QuickMojo`, `ApproveMojo`, `InstallHooksMojo`, `ExplainMojo`), `ClaudeSettings`, `HookSpec`, `GitTree`, `RootLocator` |

Dependencias de runtime: JavaParser, Jackson, SnakeYAML. Maven es `provided`; `git` se usa como comando.

### 5.2 Una corrida (`Engine.run`)

1. Elige las reglas: todas si el alcance es completo, sólo las `perFile` si no.
2. Si hay baseline, `adopt`: reglas sin congelar se adoptan (completo) o se saltan con nota (parcial).
3. `Feed` parsea y entrega cada archivo a cada `Inspection` (`Runs`).
4. Carga excepciones y firma (`Signed`): problemas de YAML, firma ausente o vieja, configuración sin firmar.
5. Por regla: `Kept` decide qué entradas de la baseline siguen (fuera de alcance, o en alcance y no liberadas),
   cuáles son de archivos desaparecidos (sólo en completo); `Reconciler`/`CeilingReconciler` cruzan con los
   hallazgos y la baseline se reescribe si cambió.
6. Si la firma es válida, `Exempting` aplica las excepciones a lo nuevo y las juzga (vencida, vacía).
7. `Outcome`: nuevos, exceptuados, resueltos, congelados, notas, problemas, milisegundos. Verde = sin nuevos y sin
   problemas.

`freeze` es lo mismo sin reconciliar: todo lo encontrado pasa a la baseline. Lo dispara `check`/`audit` si no hay
baseline, siempre sobre el **reactor entero** aunque el goal sea de un módulo.

`AbstractCercoMojo.report` escribe el JSON, imprime el reporte y falla el build si es rojo; bajo `cerco.hook` y sin
hallazgos nuevos, avisa y pasa (§3.8). `RootLocator` ubica la raíz: el ancestro más cercano con `.cerco/`, o el
directorio desde donde se lanzó Maven.

### 5.3 Memoria

`Feed` parsea en paralelo por chunks acotados en bytes (768 KB o 16 archivos, lo que llegue primero; un gigante
generado viaja solo), y entrega en orden de nombre de a uno. Cada regla ve un árbol y lo olvida; las de proyecto
guardan sólo lo que necesitan en su estado `S`. Por eso los 52 MB de zx-lift entran en 768 MB de heap.

### 5.4 El reporte

`ConsoleReport` está escrito para que el agente actúe: titular (`cerco RED: N new finding(s) in <alcance> (archivos,
frozen, solved, exempted, problems, ms)`), luego por regla el principio, el remedio (`do:`) y hasta 25 hallazgos con
`archivo:línea`, después problemas y notas, y un cierre que distingue "arreglá el código; las excepciones no son
tuyas" de "esto es de un humano, avisale y seguí".

### 5.5 Auto-aplicación

`SelfCheckTest` pasa las fuentes del plugin por sus 21 reglas, con defaults y sin baseline: cerco no tiene deuda
congelada propia. Toda regla nueva se aplica primero a cerco. Ya obligó a quitar casts (`ClaudeSettings`,
`Terminal`), una cadena de `if`, métodos clonados (de ahí `ProjectRule<S>`) y un `boolean` ignorado. Es la mejor
evidencia de que las reglas se pueden vivir. Se hace con un test y no con el goal propio para no depender de una
versión anterior instalada.

---

## 6. Decisiones y por qué

- **Trinquete en vez de todo o nada.** Un proyecto con 70 mil violaciones (zx-lift) tiene que poder adoptar el cerco
  hoy. Lo que importa es que la deuda no crezca y que baje.
- **Claves sin número de línea, multiset.** Estabilidad ante refactors. El precio: dos violaciones idénticas en el
  mismo método son intercambiables (si se arregla una y aparece otra igual, no se ve). Aceptado.
- **Reglas de proyecto fuera del `quick`.** Costarían el proyecto entero por edición. Las atrapan `audit` en el
  `Stop` y la CI.
- **Excepciones humanas por construcción, no por prosa.** La terminal es el mecanismo; la frase en el reporte es
  sólo refuerzo. Las tres capas (deny del editor, guard del shell, anti-borrado) cierran los atajos.
- **Aflojar la configuración también se firma.** Si no, `disabledRules` en el pom sería la excepción sin firma.
- **Una excepción que no cubre nada es rojo.** Sólo serviría para esconder la próxima violación.
- **Excepciones liberan lo congelado** (`ee5db5e`). Si no, una excepción sobre deuda congelada nunca cubría nada y
  era rojo; y la deuda tolerada en silencio no se volvía decisión explícita.
- **Sin resolución de tipos (sin symbol solver).** Más rápido y sin classpath, pero ciego a tipos:
  `no-ignored-result` resuelve por nombre y aridad dentro del proyecto y excluye nombres que toda colección tiene
  (`add`, `remove`…); `no-type-code` y `no-type-keyed-map` miran texto de tipos y nombres. Reglas con tipos reales
  quedan para ArchUnit sobre bytecode.
- **`includeTests` falso por defecto.** Los tests pueden castear y usar `instanceof` en aserciones. Cambiarlo se firma.
- **`HookMain` sin Maven para el guard.** 60 ms por comando de shell es tolerable; 1,7 s no lo sería.
- **Sin pre-commit.** Sería un script (restricción 1). La CI es la muralla sin tope.
- **Todo en el plugin.** Los hooks los escribe el propio plugin para que integrar sea un comando, no una receta.

### El experimento de cerco-demo

Una sesión de Claude, con 0.1.0 instalado y el pedido de construir un framework ("vista") en
`/home/fernando/detodo/coding/cerco-demo`, leyó las reglas del jar y **diseñó contra ellas**: cumplió la letra, no el
espíritu. Nunca pasó a rojo y dejó `Element.class.cast(x)`, `Type.class::cast`, `getNodeType() == ELEMENT_NODE`, una
escalera de `if/else if`, registros `Map<Class, Handler>`, clases de sólo constantes con un sufijo no listado
(`Marks`), el mismo método de una línea en cinco tipos `Slot`, `return null` en un visitor `Void`,
`@SuppressWarnings`, y un `boolean` descartado (`WebAgenda.followAction`) que escondía un bug real. Cada rodeo se
volvió una regla de 0.2.0; sobre una copia limpia de ese proyecto dan 50 hallazgos, todos del propio informe de la
sesión.

La lección: **un agente que conoce las reglas busca la forma más cercana que no las rompe.** Cada regla nueva hay que
pensarla contra el rodeo que va a provocar.

---

## 7. Historia

| commit | versión | qué |
|---|---|---|
| `17cb080` | 0.1.0 | 12 reglas, baseline con trinquete, excepciones firmadas, hooks, anti-borrado, 51 tests |
| `5929bb5` | 0.2.0 | 9 reglas nuevas del experimento, `quick` tras Bash que escribe, guard con lexer de shell, adopción de reglas, `ProjectRule<S>`, 60 tests |
| `c6489b1` | 0.2.0 | la corrida completa poda entradas de archivos borrados; `explain` imprime un YAML de ejemplo; 61 tests |
| `ee5db5e` | 0.2.0 | las excepciones firmadas liberan lo congelado; el rojo sólo humano no bloquea el `Stop`; 62 tests |
| `9b0e641` | 0.3.0 | autopsias; 69 tests |
| `2e821c8` | 0.4.0 | `flag-driven-from-outside`, la primera regla nacida de una autopsia; 70 tests |
| `9a7bafe` | 0.4.1 | `GitTree` (git de línea de comandos, entiende worktrees) en vez de JGit; 72 tests |
| | 0.5.0 | regla propuesta = deuda hasta que exista; testigo de código y de cerco; 76 tests |

Los mensajes de commit son largos y cuentan el porqué de cada cambio: `git log` es lectura recomendada.

---

## 8. Medido sobre zx-lift

38 módulos, 1577 archivos analizados, 52 MB de fuentes.

| corrida | tiempo |
|---|---|
| primera `audit`, congelando 70.050 entradas | 26 s, en 768 MB de heap |
| `audit` siguiente, verde | 16 s |
| `check` de los 38 módulos | 9,6 s |
| `quick` desde el hook, Maven incluido | 1,7 s |
| `guard`, o `quick` tras un comando que no escribe | 0,06 s |
| `audit` de 0.2.0 adoptando 9 reglas (15.848 hallazgos más) | 34 s |

11.566 métodos clonados y ~51 mil repeticiones de zx-lift son código generado de `oop/zx-game-run`: ahí conviene
configurar `<generated>` y firmar.

---

## 9. Límites conocidos

- **Ceguera de tipos.** Sin symbol solver, `no-ignored-result` puede confundir métodos homónimos de igual aridad, y
  no ve un resultado ignorado de una API externa. `no-package-cycles` ve ciclos por `import`, a nivel de paquete,
  no de módulo ni de capa.
- **Formas indistinguibles.** Una cadena de responsabilidad legítima (una lista de `Marks` recorrida) y un
  `Optional.or().or()` encadenado son iguales a los ojos de `no-conditional-chain`. Un `Map<String, X>` indexado por
  tag (despacho por nombre) no tiene regla.
- **Multiset sin líneas**: dos violaciones idénticas en el mismo miembro se confunden.
- **Corridas parciales no podan** entradas de archivos borrados ni liberan por excepciones: eso espera a la completa.
- **Generado por anotación**: cualquier tipo del archivo anotado con algo que termine en `Generated` excluye el
  archivo entero.
- **El guard es una aproximación de shell**: no expande variables, subshells ni alias. Un intérprete que escribe un
  path construido en tiempo de ejecución sin nombrarlo pasa; lo protegen igual el anti-borrado (para la baseline),
  la firma (para excepciones y configuración) y la CI.
- **`-Dcerco.skip` se niega por texto**, también en comandos que sólo lo buscan (o un heredoc que lo cita).
- **`sed -i` se niega si la línea menciona `.cerco`**, aunque el archivo editado sea otro (pasó al cambiar en
  `AbstractCercoMojo.java` un literal `".cerco/baseline/frozen"`): el guard mira la línea, no a qué archivo escribe
  `sed`. Rodeo legítimo: el Edit del agente.
- **Hooks con rutas absolutas** (el `java` de sdkman, el jar en `~/.m2`): sirven sólo en esta máquina hasta publicar.
- **`approve` exige terminal real**: no se puede firmar desde un IDE.
- **Umbrales fijos** de cadena (3), repetición (6) y clonado (3) no son configurables.

---

## 10. Estado de las instalaciones

- **cerco 0.2.0** instalado en `~/.m2` (también queda 0.1.0).
- **zx-lift**: el pom con el plugin, `.cerco/` (~13 MB) y los hooks, **sin commitear** allá, por decisión de Fernando.
- **cerco-demo** (`/home/fernando/detodo/coding/cerco-demo`): el refactor de vista, `exemptions.yaml`, `approved` y la
  baseline vaciada, **sin commitear**.

---

## 11. Pendiente e ideas

1. **Publicar** en un repositorio Maven (GitHub Packages o interno) para que CI y otras máquinas lo resuelvan, y que
   los hooks no dependan de rutas absolutas.
2. **ArchUnit sobre bytecode** con tipos resueltos: dirección de dependencias entre módulos y capas, `new` de
   concretas de otro módulo, Ley de Demeter.
3. **Métricas con trinquete**: LCOM, CBO, fan-out por clase (encajan como `BudgetRule`).
4. **Límites por hito** (una tarea cerrada en un tablero) y presupuestos con cronograma de baja.
5. **SARIF** para IDEs y GitHub.
6. **Más despacho encubierto**: `Map<String, lambda>`, `Optional.or()` encadenados, `EnumMap` como `switch`,
   reflexión (`Class.forName`, `getMethod`).
7. **Getters/setters que exponen estado y clases anémicas**: definir la medida con cuidado para que los records
   legítimos no sean rojo.
8. **Goal `status`**: la baseline como tabla por módulo y regla, con tendencia en el tiempo.
9. **`approve` desde un IDE** sin abrir la puerta al agente.
10. ~~**`quick` que muestre lo resuelto**~~: hecho en 0.3.0 por las autopsias, que listan qué se resolvió.
12. ~~**Que una regla propuesta por autopsias no pueda quedar olvidada**~~: hecho en 0.5.0 (§3.10, §3.11).
11. **Exigir arreglos de la baseline al tocar un archivo** que tiene hallazgos congelados, para contrarrestar el
    incentivo de no arreglar deuda vieja que crean las autopsias.

---

## 12. Cómo trabajar en cerco

- `mvn install` compila, corre los tests (incluido `SelfCheckTest`) e instala en `~/.m2`. Los proyectos que lo usan
  toman la versión instalada; los hooks apuntan al jar de esa versión.
- **Agregar una regla**:
  1. una clase en `rules/` que extienda `FileRule` (si un cambio en un archivo sólo puede mover hallazgos de ese
     archivo) o `ProjectRule<S>`; `principle`, `description` y `remedy` escritos para que el agente sepa qué hacer;
  2. una clave estable y sin línea: `Enclosing.of(nodo)` + `|` + qué;
  3. registrarla en `Rules.all()` en su lugar;
  4. tests en `rules/` con `Snippets` (casos que deben dar rojo y, sobre todo, los que no);
  5. `SelfCheckTest` tiene que seguir verde: si cerco no puede vivir con la regla, la regla está mal o cerco tiene
     que cambiar;
  6. pensar el rodeo que va a provocar (§6, experimento);
  7. agregarla a la tabla del `README.md`. Los proyectos la adoptan solos en su siguiente `audit`.
- **Cambiar el formato de la baseline** rompe proyectos ya congelados: el encabezado dice `cerco baseline v1`.
- **Cambiar la forma de una clave** convierte toda la deuda congelada de esa regla en "resuelta + nueva": evitarlo, o
  tratarlo como una regla nueva.
