# Ficha: `constant-argument` (candidata)

Estado: **candidata, rinde en los dos proyectos; no separa `emulator`**.

## 1. Definición

Un parámetro de un método con cuerpo para el que todas las llamadas del proyecto (2 o más) pasan el mismo literal.
Las llamadas se juntan por nombre y cantidad de argumentos, sin resolver tipos. Un método que le pasa su propio
parámetro a otro del mismo nombre y aridad no cuenta como llamada: así se sigue la cadena `BlipSynth.readSamples` →
`BlipBuffer.readSamples`. No mira métodos con `@Override`, con varargs, ni los que se pasan como referencia
(`this::m`). Un hallazgo por parámetro, en la declaración.

Modelo que falta: `variation-as-data`, leído al revés: la variación está solo en la firma. Remedio: borrar el
parámetro y la rama que nadie toma. No son dos métodos, porque al segundo no lo llama nadie.

Origen: autopsias de oozx del 2026-11-11 (`BlipBuffer.readSamples(..., stereo)` y `clear(entireBuffer)`, commit
`b65e8dd25`). `flag-argument` lo veía, pero su remedio (dividir en dos métodos) era el equivocado.

## 2. Sondeo (cerco 0.16.0)

| proyecto | hallazgos |
|---|---|
| oozx `87e376742` `emulator` | 5 |
| oozx `machine/*` | 7 |
| oozx `translation/*` | 1 |
| oozx-plugins | 15 |

## 3. Muestra

Leídos los 13 de oozx y 9 de plugins, todos ciertos: `BlipBuffer.readSamples` y `BlipSynth.readSamples` (`true` como
`stereo`), `OpcodeTargets.iRRn` (10 llamadas, siempre `2`), `PhaseProcessorBase.ifJumped` (9, siempre `5`),
`Scalers.resize` (`"scaled"` y `true`), `Knob.number` (`0.05` como `step`), `Disk.blank` (`2` lados, `80` cilindros),
`Disk.wroteTrack` (`false` como `preindex`). `MemoryState.setIF1RomPaged(true)` es un setter que solo prende: cierto,
y el remedio ahí es un método sin parámetro.

## 4. Límites

- Solo ve los fuentes de `src/main/java` del reactor: un parámetro que los tests o un plugin de afuera varían sale
  igual. `BlipBuffer.clear(entireBuffer)` no sale porque en main tiene una sola llamada.
- Dos métodos distintos con el mismo nombre y aridad comparten las llamadas: si uno no tiene llamadas propias, hereda
  las del otro.

## 5. Falsos positivos al usarla (oozx, 2026-11-11)

`Knob.number(..., step)`: las cinco llamadas del reactor pasan `0.05`, pero el método es público y lo llaman también
oozx-plugins (`TubeCurve`, `MouseInternalFrame`) y los jars publicados. `SpeccyEmulatorCore.setGeneralOption("turbo")`
es API para plugins: mismo caso. Aciertos en `machine`: `Scalers.resize` (borrado en oozx `df45987c4`) y
`MemoryState.setIF1RomPaged` (cierto; es código de snapshots y espera otra decisión).

Es el mismo límite que la mitad pública de `unused-member`: cerco solo ve el proyecto. Decisión de Fernando
(2026-11-11): cerco no lee consumidores de afuera; el uso de los plugins se escribe como tests del proyecto y esos
tests cuentan como llamadores. Desde 0.17.0 la regla oye las llamadas de `src/test` (solo en `survey`, ver la ficha
de `unused-member` §5), y el hallazgo de un método público o protegido termina con "if a consumer outside this
project uses it, write a test that uses it the same way". Con los tests oídos: oozx 17, oozx-plugins 19 (suben
porque un método con una sola llamada en main y otra igual en un test ahora llega a dos).

## 6. Decisión

Pendiente de Fernando. Desde 0.17.1 el motor oye los tests como `survey`; falta que estén escritos los tests que
imitan a los consumidores conocidos. En `emulator` los 5 son constantes de la tabla de opcodes y de los tiempos.
