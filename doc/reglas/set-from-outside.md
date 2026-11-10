# Ficha: `set-from-outside` (candidata)

Estado: **candidata, sin decidir**. Casi todo lo que encuentra depende de la pregunta 3 de §11 del plan.

## 1. Definición

Un método que llama 3 o más setters distintos, declarados en el proyecto, sobre el mismo objeto que no es `this`.
La evidencia dice si 3 o más de los valores son getters de un mismo tercero (copia propiedad por propiedad). Un
hallazgo por método y receptor.

Modelo que falta: `behaviour-not-on-owner`, `concept-without-a-class`, o `knowledge-without-one-owner` cuando es una
copia.

## 2. Sondeo (oozx, reactor entero)

| grupo | hallazgos |
|---|---|
| `emulator` | 0 |
| `machine/*` | 8: 6 en `machine/spectrum`, 2 en `machine/devices/kit` |
| `translation/*` | 1 |

Separa.

## 3. Muestra

- `machine/spectrum`: `SnapshotLoader.setZ80State` (24 setters), `SnapshotSaver.extractZ80State` (28),
  `SnapshotZ80.loadFromStream` (25 sobre `z80` y 13 sobre `spectrum`), `SnapshotSaver.setupSnapshotWithState` y
  `getSnapshotAsBytes` (6 cada uno). Son traspasos entre los estados de snapshot (`Z80State`, `SpectrumState`) y la
  máquina. **Verdaderos si esos estados son un modelo paralelo**, que es la pregunta 3 del plan.
- `Desk.keep` (4 propiedades de `WindowState`): verdadero; el estado de una ventana se arma desde afuera.
- `MockEmulatorCore.setScreenOption` (4): verdadero, débil (un doble de prueba en código principal).

## 4. Decisión

Esperar la respuesta a la pregunta 3. Si los estados de snapshot son legítimos, conviene excluir los receptores que
son clases de datos del proyecto, y la regla queda con 2 hallazgos.

## 5. Lectura (0.15.0)

Si 3 o más de los valores son getters de un mismo tercero, lee `knowledge-without-one-owner` (una copia propiedad por
propiedad); si no, `behaviour-not-on-owner` (una sola cosa hecha con varios setters). En oozx `87e376742` (8 en
`machine`): 2 copias y 6 de la otra clase.
