# cerco

Un plugin de Maven que hace cumplir, de forma determinística, las reglas de diseño que un proyecto Java decide
respetar: orientación a objetos pura (alta cohesión, bajo acoplamiento, polimorfismo en vez de condicionales sobre
tipos, encapsulamiento), sin excepciones silenciosas y con tamaños acotados. Se agrega al `pom.xml` y funciona sin
configurar nada: la primera corrida congela el proyecto tal como está, y desde entonces cualquier violación nueva
pone el build en rojo. La deuda congelada sólo puede bajar. Las excepciones existen, pero las firma un humano en una
terminal y vencen.

Todo el código es Java. No hay scripts.

## Instalar

```xml
<plugin>
  <groupId>com.fpetrola</groupId>
  <artifactId>cerco-maven-plugin</artifactId>
  <version>0.1.0</version>
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
archivo o método que excede el techo, y lo guarda bajo `.cerco/baseline/`, un archivo de texto por regla, legible en
un diff. Hay que **commitear `.cerco/`**: a partir de ahí lo que está anotado se tolera, lo nuevo no, y cuando una
violación congelada desaparece el plugin la borra de la línea de base solo. Nunca la agranda.

Para conectarlo a Claude Code, una vez por proyecto:

```
mvn com.fpetrola:cerco-maven-plugin:install-hooks
```

Eso escribe en `.claude/settings.json`, sin tocar lo que ya hubiera: un hook `PreToolUse` que niega los comandos de
shell que intentan escribir los archivos del cerco o apagarlo, un hook `PostToolUse` que corre el chequeo rápido
después de cada edición de un `.java`, un hook `Stop` que corre la auditoría completa antes de que el agente pueda
dar por terminado el trabajo, y reglas `deny` para que el editor del agente no pueda tocar `.cerco/` ni los hooks.
Cuando el cerco está rojo, el agente recibe el reporte como instrucción y no puede seguir hasta arreglarlo.

## Las reglas

| id | principio | qué detecta |
|---|---|---|
| `parseable` | el cerco ve todo | un archivo que el parser no puede leer |
| `no-package-cycles` | bajo acoplamiento | paquetes que se importan mutuamente (proyecto entero) |
| `no-mutable-static` | encapsulamiento | un campo `static` que no es `final` |
| `no-public-mutable-field` | encapsulamiento | un campo público de instancia que no es `final` |
| `no-instanceof` | polimorfismo | `instanceof`, y comparar `getClass()` con `==` |
| `no-switch` | polimorfismo | cualquier `switch`, sentencia o expresión |
| `no-static-method` | objetos antes que procedimientos | un método `static` no privado que no es `main` ni una fábrica (`of`, `from`, `parse`, `create`, `valueOf`, `copyOf`, o que devuelve su propio tipo) |
| `no-utility-class` | cohesión | un tipo cuyo nombre termina en Util, Utils, Helper, Helpers, Manager, Impl, Common, Misc o Tools |
| `no-swallowed-exception` | honestidad | un `catch` que no relanza ni usa la excepción |
| `no-repeated-statements` | abstracción | seis sentencias consecutivas que aparecen dos veces (proyecto entero) |
| `file-length` | cohesión | un archivo de más de 400 líneas; los congelados sólo pueden achicarse |
| `method-length` | cohesión | un método o constructor de más de 40 líneas; los congelados sólo pueden achicarse |

`mvn com.fpetrola:cerco-maven-plugin:explain` imprime cada regla con su principio, su remedio y cuánto de ella está
congelado.

Lo que se analiza son los *compile source roots* de cada módulo del reactor (`src/main/java`), no los tests ni los
módulos apagados. Se excluye lo que está bajo `target/`, `generated-sources/` o `generated/`, y cualquier tipo
anotado con `@Generated`.

## Las excepciones

Una excepción es una decisión humana, explícita y con fecha de vencimiento. Viven en `.cerco/exemptions.yaml`:

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

Hay un tope de 10 excepciones activas por proyecto. Una excepción vencida es rojo. Una excepción que ya no cubre
nada también es rojo, porque sólo serviría para esconder la próxima violación. Las excepciones por falso positivo
son una señal de que la regla, no el código, es lo que hay que arreglar.

El archivo no vale hasta que una persona lo firma:

```
mvn com.fpetrola:cerco-maven-plugin:approve
```

`approve` exige una terminal interactiva. Un agente corriendo en un shell sin terminal no puede ejecutarlo: le
contesta que eso es tarea de un humano. La firma, un hash del archivo de excepciones y de la configuración, queda en
`.cerco/approved`; si el archivo cambia, la firma deja de coincidir y ninguna excepción aplica hasta que alguien
vuelva a firmar. Lo mismo vale para la configuración del plugin: cualquier diferencia respecto de los valores por
defecto (techos, reglas apagadas, sufijos) es un aflojamiento y necesita firma.

Tres capas evitan que el agente se tiente con la salida fácil: las reglas `deny` de Claude Code le niegan el editor
sobre `.cerco/` y sobre los hooks; el `guard` le niega el shell para escribir esos archivos o para pasar
`-Dcerco.skip`; y si la línea de base aparece borrada pero está en el `HEAD` de git, el plugin se niega a recongelar
y pide restaurarla. Además, cada reporte en rojo le dice al agente, con todas las letras, que las excepciones no son
suyas.

## Goals

| goal | corre | qué hace |
|---|---|---|
| `check` | en `verify`, por módulo | las reglas por archivo sobre el módulo; congela todo el reactor si no hay línea de base |
| `audit` | hook `Stop`, CI, a mano | todo el reactor, todas las reglas, y detecta excepciones vencidas o vacías |
| `quick` | hook `PostToolUse` | sólo el archivo recién editado, reglas por archivo; a mano, los archivos que git ve cambiados |
| `approve` | un humano, en terminal | firma las excepciones y la configuración |
| `install-hooks` | una vez por proyecto | escribe los hooks y las reglas `deny` en `.claude/settings.json` |
| `explain` | a mano | las reglas, con su principio, su remedio y lo congelado |

Los reportes también quedan en `target/cerco/<alcance>.json`.

## Configuración

No hace falta. Si un proyecto necesita apartarse de los valores por defecto, va en el `<configuration>` del plugin y
requiere firma (`approve`): `fileCeiling` (400), `methodCeiling` (40), `utilitySuffixes`, `factoryPrefixes`,
`generated` (globs de código generado), `maxExemptions` (10), `includeTests` (false), `disabledRules`.

## Medido sobre zx-lift (38 módulos, 1577 archivos analizados, 52 MB de fuentes en el repo)

| corrida | tiempo | memoria |
|---|---|---|
| primera `audit`, congelando 70.050 entradas | 26 s | 768 MB de heap alcanzan |
| `audit` siguiente, verde | 16 s | |
| `check` de los 38 módulos, como en `verify` | 9,6 s | |
| `quick` desde el hook, Maven incluido | 1,7 s | |
| `guard` por cada comando de shell | 0,06 s | |

## Lo que la versión cero no tiene todavía

- Reglas sobre bytecode con tipos resueltos (ArchUnit): dirección de dependencias entre módulos, capas, `new` de
  concretas de otro módulo. Hoy los ciclos se ven por `import`, a nivel de paquete.
- Métricas de cohesión y acoplamiento (LCOM, CBO) con trinquete.
- Límites por hito (una tarea cerrada en un tablero) y presupuestos con cronograma de baja.
- Salida SARIF, pre-commit de git (sería un script, y acá no hay scripts: la muralla sin tope es la CI).
- El plugin se verifica a sí mismo con un test (`SelfCheckTest`) en vez de con su propio goal, para no depender de
  una versión anterior instalada.
