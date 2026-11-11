# Ficha: `parallel-model` (candidata)

Estado: **candidata, sin decidir**: no encontró lo que buscaba y encontró otra cosa.

## 1. Definición

Dos tipos del proyecto, de familias distintas, con 4 o más campos del mismo nombre y tipo. Un hallazgo por tipo y
compañero (cada par aparece dos veces).

Modelo que se buscaba: `knowledge-without-one-owner`, lo mismo modelado dos veces (los estados de snapshot contra
`State`).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 8 |
| `machine/*` | 16 |
| `translation/*` | 10 |

No separa.

## 3. Muestra, juzgada por Claude

- **No encontró** `Z80State` contra `State`: no comparten nombres de campo.
- `machine`: `Speccy`, `Machine`, `Spectrum`, `Snapshots` y `LocalLibretroCore` guardan cada uno los mismos
  colaboradores (`banks`, `cpu`, `display`, `memory`, `scheduler`, `sound`, `timer`; entre 4 y 9 en común). **Es un
  hallazgo real, pero de otro modelo**: las partes de la máquina viajan juntas y cinco clases las guardan sueltas
  (`concept-without-a-class`). Coincide con lo que muestran `reached-through` y `unpacked-at-the-call` sobre `Speccy`.
- `Display` y `Painting` (4 campos): probable.
- `emulator`: `DefaultInstructionFactory` contra las instrucciones que fabrica (guarda los registros que les pasa):
  débil. `UnrolledRegisterBank` contra `SpectrumApplication` (13 a 16 registros con el mismo nombre): probable.

## 4. Decisión

Como está no sirve para lo que se pensó. Lo que detecta bien es un grupo de colaboradores que viaja junto: conviene
rehacerla con ese nombre y ese modelo (campos compartidos por 3 o más tipos, un hallazgo por grupo y no por par), y
buscar el modelo paralelo por otro lado: el código que copia campo a campo, que ya ve `set-from-outside`.
