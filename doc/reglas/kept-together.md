# Ficha: `kept-together` (candidata)

Estado: **candidata, propuesta para adoptar con una reserva** (§4). Reemplaza a `parallel-model`, que miraba pares y
no encontró lo que buscaba.

## 1. Definición

Un tipo que guarda 3 o más campos (mismo nombre y mismo tipo) que también guardan otros 2 tipos de familias
distintas. De los grupos posibles se informa el más grande. Un hallazgo por tipo.

Modelo que falta: `concept-without-a-class`. Cosas que viajan juntas sin un tipo propio: un tipo para el grupo, que
cada uno guarda entero.

Origen: el *data clump* de Fowler entre campos (definición de Zhang: los mismos campos en 2 o más clases).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 2 | 0,12 |
| `machine/*` | 7 | 0,27 |
| `translation/*` | 8 | 0,01 |

Separa 2 veces.

## 3. Muestra: los 9, juzgados por Claude

| sitio | juicio |
|---|---|
| `Speccy` guarda `banks, cpu, display, memory, scheduler, sound, timer`, igual que `Machine` y `Spectrum` | **verdadero**: las partes de la máquina, que también muestran `reached-through` y `unpacked-at-the-call` |
| `Machine`, `ContendedWiring`, `Display`, `Spectrum`, `Snapshots`, `LocalLibretroCore`: 3 o 4 de esas mismas partes cada uno | verdaderos: el mismo grupo visto desde cada clase que lo guarda |
| `MemoryPlusRegister8BitReference`, con `ReadMemoryReference` y `WriteMemoryReference`: `address, memory, value` (`emulator`) | probable |
| `UnrolledRegisterBank`, con `JSW2b` y `SpectrumApplication`: 13 registros con el mismo nombre (`emulator`) | probable: es código que imita lo generado |

7 verdaderos y 2 probables, de 9. En realidad son tres casos (las partes de la máquina, las referencias a memoria,
los registros), vistos desde nueve clases.

## 4. Decisión

Cumple justo: 3 casos reales. La reserva: que un hallazgo por clase del mismo grupo sea lo que se quiere congelar, o
uno por grupo.
