# Ficha: `reached-through` (candidata)

Estado: **adoptada en 0.15.0** por decisión de Fernando (2026-11-10).

## 1. Definición

Una llamada `a.b.m()` donde `a` es una variable (no `this`, no un tipo) y `b` es un campo declarado en el proyecto,
cuando el mismo camino `a.b` aparece en 2 o más miembros. No cuenta si el tipo que escribe la llamada declara `b`
(`other.b` en su propia clase), ni si `b` sólo está declarado dentro del mismo tipo de primer nivel (un record
anidado propio). Un hallazgo por sitio.

Es la Ley de Demeter entre lugares: PMD e IntelliJ la marcan expresión por expresión. Según la investigación del plan,
ninguna herramienta agrupa el mismo camino repetido.

Modelo que falta: `behaviour-not-on-owner` (`a` debería ofrecer la operación), `knowledge-without-one-owner` (los
llamadores sólo quieren `b`: dénles `b`), `concept-without-a-class` (`a` es una bolsa de partes).

## 2. Sondeo (oozx `a43d78f40`, el reactor entero en una pasada)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 6 | 0,36 |
| `machine/*` | 58 | alrededor de 2,5 (app 11,3; core 1,2) |
| `translation/*` | 184 | hasta 18,7 (bytecode, routines) |

Separa: 7 veces más en `machine` que en `emulator`.

## 3. Muestra (`emulator` y `machine`, por camino)

| camino | sitios | juicio de Claude |
|---|---|---|
| `speccy.loop`, `.machine`, `.processors`, `.roms`, `.session`, `.sound`, `.timer` en `SpeccyEmulatorCore` y `OOSpectrumLauncher` | 25 | **verdadero**: el core le habla a cada parte de `Speccy`; `Speccy` no esconde nada |
| `speccy.memory` en `LocalLibretroCore` | 4 | verdadero |
| `speccy.peripheralRegistry` en `Input`, `Snapshots` | 2 | verdadero |
| `state.clock` (campo público de `State`) en `emulator`, `spectrum`, `snapshot` | 8 | verdadero: el reloj es una parte de `State` usada desde afuera |
| `ula.contention` en `Machine` y `SpeccyPhaseProcessor` | 3 | verdadero |
| `display.border`, `machine.current` | 3 | probable |
| `machine.memory` en `Disassembly` | 2 | probable |
| `covering.memory` en `DecodedMemoryBus` | 3 | dudoso: parece un record de la misma familia del bus |
| `frame.line` en `MachineTimings` | 2 | dudoso: lectura de un valor |

Unos 57 verdaderos o probables y 5 dudosos, de 64.

## 4. Decisión

Cumple las tres condiciones. Adoptarla congela 248 hallazgos, la mayoría en `translation`.

## 5. Lectura (0.15.0)

En cada sitio mira si el miembro usa al todo para algo más que llegar a la parte. Si solo lo usa para eso, lee
`knowledge-without-one-owner` (denle la parte); si no, `behaviour-not-on-owner` (el todo ofrece la operación). En
oozx `87e376742`, `emulator` y `machine` (56 hallazgos): 33 solo quieren la parte, 23 usan al todo para más.
