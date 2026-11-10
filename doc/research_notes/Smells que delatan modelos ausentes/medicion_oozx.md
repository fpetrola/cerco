# Medición propia: qué estructuras separan el módulo bien diseñado de los escritos por el agente

Fuente: no es bibliografía, es una medición hecha en esta misma investigación (2026-11-10) sobre el proyecto donde
se prueba el chequeador. El dueño del proyecto dice que el módulo `emulator` es el mejor diseñado (lo modeló él) y
que los módulos `machine/*` los escribió casi todos un agente LLM y son los que tienen más problemas de modelado.
Esa diferencia de autoría se usa como verdad de referencia: un indicador sirve si su tasa separa los dos grupos.

Método: dos sondas en Java sobre JavaParser 3.26.3, sin resolución de tipos (sólo nombres), en
`sonda/Probe.java` y `sonda/Probe2.java`; salidas crudas en `sonda/salida-sonda-1.txt` y `sonda/salida-sonda-2.txt`.
Todas las tasas son por 1000 líneas de código (sin líneas en blanco ni de comentario). Tamaños: emulator 9.924
líneas de código en 229 archivos; machine/core 5.279 en 99; machine/app 1.893 en 6; machine/ui 2.012 en 19;
machine/spectrum 1.906 en 14; machine/devices/kit 1.028 en 20.

## 1. Lo que el chequeador prohíbe hoy NO separa los dos grupos

| por 1000 líneas | emulator | core | app | ui | spectrum | kit |
|---|---|---|---|---|---|---|
| `instanceof` | 4,33 | 1,14 | 8,45 | 4,47 | 0,52 | 4,86 |
| cast a tipo referencia | 2,62 | 0,00 | 4,23 | 4,47 | 0,00 | 9,73 |
| `switch` | 1,21 | 0,57 | 0,53 | 1,49 | 4,20 | 5,84 |
| cadena `else if` de 3+ ramas | 0,40 | 0,38 | 1,06 | 0,00 | 0,00 | 0,00 |

El módulo bien diseñado tiene MÁS `instanceof` por línea que el promedio de machine (4,33 contra 2,78). Contar
construcciones prohibidas no distingue código bien modelado de código mal modelado.

Tampoco separan: getters llamados sobre objetos del proyecto (32 emulator; 18 core; 55 app; 32 ui), métodos con 4 o
más parámetros (7,05 contra 5,69), parámetros primitivos cuyo (tipo, nombre) se repite en 8+ métodos (38,7 contra
31,0: `int address` aparece 50 veces en cada grupo), ventanas de 3 sentencias con la misma forma tipo 2 compartidas
por 2+ métodos (2,82 contra 2,57).

## 2. Lo que sí separa: presencia del modelo

| por 1000 líneas | emulator | core | app | ui | spectrum | kit |
|---|---|---|---|---|---|---|
| métodos `accept` / `visit*` | 37,59 | 2,08 | 0 | 0 | 0 | 0 |
| tipos que extienden o implementan algo | 26,10 | 7,96 | 2,64 | 9,44 | 1,05 | 15,56 |
| tipos declarados | 31,84 | 23,49 | 3,70 | 13,92 | 6,30 | 22,37 |
| líneas promedio por método (no es tasa) | 3,9 | 4,9 | 8,7 | 8,3 | 8,2 | 5,0 |

emulator tiene una interfaz `InstructionVisitor` y 103 implementaciones de `accept(`; machine/app, ui, spectrum y
kit, ninguna. Es la diferencia más grande de toda la medición (50 veces).

## 3. Lo que sí separa: interrogar desde afuera, y narrar

| por 1000 líneas | emulator | core | app | ui | spectrum | kit |
|---|---|---|---|---|---|---|
| comparación con `null` | 1,81 | 15,34 | 46,49 | 17,89 | 9,97 | 34,05 |
| campo de otro objeto leído o escrito (`x.y`, campo declarado en el proyecto) | 7,05 | 35,99 | 54,41 | 24,85 | 7,35 | 25,29 |
| llamada a través de un campo ajeno (`a.b.m()`) | 0,8 | 5,5 | 20,1 | 1,0 | 1,6 | 3,9 |
| setter de un objeto del proyecto llamado desde afuera | 2,52 | 0,76 | 10,04 | 1,49 | 84,99 | 13,62 |
| for-each o stream sobre el getter de otro objeto | 0,40 | 3,97 | 15,32 | 4,47 | 1,04 | 2,92 |
| cadena de getters (`x.getA().getB()`) | 0,81 | 3,03 | 6,87 | 1,99 | 1,05 | 2,92 |
| comentario de línea dentro de un método | 1,51 | 19,51 | 45,96 | 52,19 | 34,10 | 5,84 |
| método con 3+ comentarios de línea adentro | 0 | 1,89 | 6,87 | 4,47 | 2,10 | 0,97 |
| método estático no privado | 2,32 | 9,09 | 6,34 | 18,89 | 12,07 | 1,95 |
| literal de texto repetido 3+ veces en un archivo | 0 | 3,22 | 3,70 | 42,74 | 6,30 | 33,07 |
| texto comparado con un literal | 0,40 | 0,76 | 2,64 | 0 | 0 | 20,43 |
| método que bifurca por su propio parámetro booleano | 0,40 | 1,70 | 1,06 | 1,99 | 0 | 2,92 |
| método con 8+ variables locales | 0,10 | 1,14 | 1,58 | 4,97 | 0,52 | 0 |
| campo de tipo mapa | 0,30 | 2,46 | 1,58 | 0,99 | 0 | 0 |

## 4. Estructuras contadas (no tasas)

- Campo comparado con `null` en 2 o más métodos de su clase: 0 en emulator, 14 en machine (por ejemplo
  `AttachedFrame.machineWindow` en 5 métodos, `JavaSoundDevice.line` en 4).
- Método que llama 3 o más setters de un mismo objeto del proyecto: 0 en emulator, 9 en machine
  (`SnapshotLoader.setZ80State` llama 32, `SnapshotZ80.loadFromStream` 28, `SnapshotSaver.extractZ80State` 28).
- Clase de datos (3+ campos y 80 % o más de sus métodos son accesores): 0 en emulator, 10 en machine (`Z80State`
  con 31 campos, `SpectrumState` 22, `WindowState` 17, `MemoryState` 10).
- Clase con 2+ campos mapa con el mismo tipo de clave: 0 en emulator, 4 en machine (`OOZxConfiguration` tiene 4
  mapas con clave `String`, `RomFiles` 3).
- Campos con el mismo prefijo de nombre (3+): `Z80State` tiene 22 campos `reg*`; emulator también tiene 2 clases
  así (`RegisterBank` con 11 campos `register*`).
- Getter de otro objeto recorrido desde 3+ archivos: `found()` desde 6, `getMachineTypes()` desde 3.
- Caminos `a.b.m()` más repetidos en machine/core y machine/app (67 sitios, 25 caminos): `device.peripheral` 11,
  `speccy.machine` 9, `speccy.loop` 7, `speccy.roms` 5, `speccy.session` 4. En emulator: `state.clock` 6.
- Jerarquías del proyecto con 3+ subtipos cuyos subtipos se preguntan con `instanceof`: en emulator,
  `OpcodeReference` (8 subtipos) desde 9 archivos, `Register` (4) desde 6, `ImmutableOpcodeReference` (3) desde 3,
  `AbstractInstruction` (15), `RepeatingInstruction` (8), `Instruction` (5), `ConditionalInstruction` (5) y
  `JumpInstruction` (4) desde 2 cada una; en machine, `Scaler` (7) y `ScreenEffect` (5) desde un archivo,
  `EmulatorCommand` (15) desde uno. O sea: el módulo bien diseñado, que ya tiene visitor para sus instrucciones,
  conserva además una deuda propia de despacho desde afuera sobre otra jerarquía.

## 5. Lectura

1. Las construcciones que hoy se prohíben una por una aparecen con la misma frecuencia en el código bueno y en el
   malo. Lo que cambia entre uno y otro es (a) si existe un modelo polimórfico que sabe recorrerse y (b) cuánto se
   interroga a los objetos desde afuera: `null`, campos ajenos, setters, recorridos de colecciones ajenas, y prosa
   dentro de los métodos.
2. Los indicadores más limpios son los que piden REPETICIÓN o PROPIEDAD: el mismo campo preguntado por `null` en
   varios métodos, varios setters del mismo objeto en un método, la misma jerarquía del proyecto testeada desde
   varios archivos. Esos dan 0 en el módulo bien diseñado, salvo el último, que encuentra una deuda real.

## 6. Límites de la medición

- Un solo proyecto y una sola comparación; la "verdad" es la opinión del dueño sobre quién escribió qué.
- Sin tipos: un método cuenta como "del proyecto" si algún tipo del proyecto declara ese nombre. Homónimos de
  librería se cuelan (`getID()`, `values()`).
- Dominio distinto: machine/app, ui y kit son escritorio Swing, que impone setters y `null`; emulator es un núcleo
  de CPU. machine/core y machine/spectrum, que no son interfaz gráfica, muestran las mismas diferencias, pero
  menores.
- No se revisó a mano cada caso: falta medir precisión por muestreo antes de convertir un indicador en regla.
