# Ficha: `absence-as-state` (candidata)

Estado: **candidata**, propuesta para adoptar. Falta la revisión de Fernando de la muestra (§3).

## 1. Definición

Un campo de instancia comparado con `null` (`==`, `!=`, `Objects.isNull/nonNull`, `Optional.ofNullable`) en 2 o
más métodos de su clase. `this.campo` siempre cuenta; un nombre suelto sólo si el método no declara un parámetro o
local con ese nombre. Un hallazgo por campo, en su declaración.

Modelo que falta: `state-without-an-object`. El objeto vive dos vidas (con y sin el campo) y cada método pregunta en
cuál está. Salidas: argumento obligatorio del constructor; Null Object; un objeto por fase.

Origen: la medición del plan (`plan-smells-de-modelo.md` §1.3), no una autopsia.

## 2. Sondeo (`mvn cerco:survey`, oozx `94dbed88b`, 2026-11-10)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` (diseñado a mano) | 0 | 0 |
| `machine/*` (escrito mayormente por un agente) | 12 | 0,2 a 5,4 por módulo |
| `translation/*` | 12 | 0 a 2,2 |

Separa: 0 en el código de referencia.

## 3. Muestra: los 12 de `machine`, juzgados por Claude

| sitio | métodos | ¿modelo que falta? |
|---|---|---|
| `AttachedFrame.machineWindow` | 5 | sí: pegada contra suelta son dos fases de la ventana |
| `JavaSoundDevice.line` | 4 | sí: abierta y cerrada; cada método pregunta |
| `UlaPortHandler.speaker` | 2 | sí: parlante enchufado o no, un Null Object |
| `AudioIn.monitor` | 2 | sí: monitoreo opcional, un Null Object |
| `AudioIn.line` | 2 | sí: fases abierta y cerrada |
| `SnapshotZ80.fIn`, `fOut` | 2 y 2 | sí, distinto: el stream es de una operación y vive en un campo; va como local con try-with-resources |
| `SpectrumMemory.beside` | 2 | sí: colaborador opcional con un valor por defecto (`shown`) |
| `MediaSlot.flip`, `protect` | 2 y 2 | sí: ranuras de una clase que da vuelta y otra que no; variación como dato |
| `RomNotShippedDialog.window` | 2 | sí, débil: el diálogo mostrándose o no |
| `BlipBuffer.buffer` | 2 | débil: reserva perezosa en código portado |

Veredicto de Claude: 10 verdaderos, 2 débiles, 0 falsos.

## 4. Decisión

Cumple las tres condiciones del plan (§8.1): más de 3 casos reales, 8 de 10 o más verdaderos, y separa el código de
referencia. Propuesta: adoptarla en 0.15.0 si Fernando confirma la muestra. Al adoptarla congela 12 hallazgos en
`machine` y 12 en `translation`.

## 5. Lectura (0.15.0)

La regla elige entre sus tres hipótesis mirando cómo llega el campo: si algún método lo vuelve a `null`, son fases;
si lo da un método después de construir, llega tarde; si solo se da al construir, es opcional. En oozx `87e376742`
(10 hallazgos en `machine`): 4 leen como fases y 6 como llegada tardía. Límite conocido: `SnapshotZ80.fIn` y `fOut`
leen como llegada tardía, cuando lo que pasa es que un stream de una sola operación vive en un campo.
