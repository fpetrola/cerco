# Ficha: `flag-argument` (candidata)

Estado: **adoptada en 0.15.0** por decisión de Fernando (2026-11-10). Al adoptarla hubo que arreglar lo que encontró en el propio cerco (ver §13.5 del plan).

## 1. Definición

Un método que no es privado y bifurca por su propio parámetro booleano: el parámetro, solo o negado, es toda la
condición de un `if` o de un `?:`. No cuenta `setX(boolean)` con un solo parámetro: es una propiedad, y cuando se la
maneja desde afuera la ve `flag-driven-from-outside`. Un hallazgo por método y parámetro.

Modelo que falta: `variation-as-data`. Son dos métodos detrás de un nombre; o, si la bandera es el estado de algo,
decide el objeto que tiene ese estado.

Origen: el plan (§7.2, regla 10). Es el *flag argument* de Fowler y la S2301 de Sonar.

## 2. Sondeo (`mvn cerco:survey`, oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 1 | 0,06 |
| `machine/*` | 13 | 0,51 |
| `translation/*` | 2 | 0,00 |

Separa: 8 veces más en `machine`. Antes de excluir los setters daba 4 y 15.

## 3. Muestra: los 14 de `emulator` y `machine`, juzgados por Claude

| sitio | juicio |
|---|---|
| `BlipBuffer.clear(boolean entireBuffer)`, `Cpu.machineWasReset(boolean hard)`, `Backplane.attach(Wired[], boolean inFront)`, `Settings.Edits.over(List, boolean live)`, `ScreenLayout.showing(int, boolean colourPerLine)`, `BlipBuffer.readSamples(int[], int, boolean stereo)`, `SpeccyScreen.getScaledImage(..., boolean b)` | **verdaderos**: el llamador elige entre dos comportamientos |
| `OOZxConfiguration.takeIntoAccount(String, boolean taken)`, `PeripheralRegistry.activateType(Class, boolean active)`, `KeyedJoystick.push(Direction, boolean pushed)` | verdaderos: prender y apagar con un booleano |
| `AudioIn.hear(boolean wanted)`, `InstructionListing.showBytes(boolean shown)`, `MediaSlot.led(boolean on)` | débiles: son setters con otro nombre |
| `Flags.setFlag(Register, int, boolean set)` (`emulator`) | verdadero: poner y borrar una bandera del registro F |

11 verdaderos y 3 débiles, de 14.

## 4. Decisión

Cumple las tres condiciones (§8.1 del plan). Adoptarla congela 16 hallazgos.
