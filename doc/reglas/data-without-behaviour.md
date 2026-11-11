# Ficha: `data-without-behaviour` (candidata)

Estado: **candidata, propuesta para adoptar**, sujeta a la pregunta 3 de §11 del plan (si los estados de snapshot
son un diseño legítimo).

## 1. Definición

Un miembro que llama 3 o más getters distintos (sin argumentos) sobre un mismo objeto, cuando el tipo de ese objeto,
resuelto por el índice, es del proyecto y solo guarda datos: 3 o más campos y todos sus métodos son accesores. No
cuenta la propia clase de datos. Un hallazgo por miembro y objeto.

No acusa a la clase de datos sola, que puede ser un record legítimo: acusa al **par** datos más quien hace el trabajo.

Modelo que falta: `behaviour-not-on-owner`.

Origen: el plan (§7.2, regla 13; idea 7 de CERCO.md).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 0 |
| `machine/*` | 12 |
| `translation/*` | 5 |

Separa: 0 en el código de referencia.

## 3. Muestra: los 12 de `machine`, juzgados por Claude

| quién pregunta | a quién | juicio |
|---|---|---|
| `SnapshotZ80` (3 métodos; uno pide 10 cosas), `SnapshotLoader`, `Snapshots` (3 métodos) | `SpectrumState` | verdaderos si esos estados son un modelo paralelo (pregunta 3) |
| `Desk.bringBack` (9 cosas) | `WindowState` | **verdadero**: el estado de una ventana no sabe restaurarla |
| `ZXSpectrumDesktopApp.open(Chosen, boolean)` | `Chosen` | verdadero: lo mismo que marca `unpacked-at-the-call` |

## 4. Decisión

Cumple las tres condiciones. Depende de la pregunta 3 solo para los ocho de snapshot.
