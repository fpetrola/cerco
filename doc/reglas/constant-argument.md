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

Es el mismo límite que la mitad pública de `unused-member`: cerco solo ve el reactor. Desde 0.16.1 el hallazgo de un
método público o protegido lo dice ("it is public, and callers outside this project are not seen"), pero se sigue
marcando: limitarla a métodos privados o de paquete dejaría afuera el caso que le dio origen
(`BlipBuffer.readSamples` es público).

## 6. Decisión

Pendiente de Fernando, y atada a la misma pregunta que `unused-member`: o cerco lee a los consumidores de afuera del
reactor, o esta regla se limita a lo que no es público y pierde `BlipBuffer`. Así como está no se puede adoptar en un
proyecto que publica API. En `emulator` los 5 son constantes de la tabla de opcodes y de los tiempos.
