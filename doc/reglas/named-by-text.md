# Ficha: `named-by-text` (candidata)

Estado: **adoptada en 0.15.0** por decisión de Fernando (2026-11-10). Al adoptarla hubo que arreglar lo que encontró en el propio cerco (ver §13.5 del plan).

## 1. Definición

El mismo texto comparado (operando de `equals` o `equalsIgnoreCase`, o etiqueta de un `case`) en 2 o más miembros del
proyecto. El texto tiene al menos 2 caracteres y alguna letra. Un hallazgo por sitio.

Modelo que falta: `value-without-a-type`. Un caso del dominio que solo tiene una forma de escribirse: un enum o un
tipo para él, dueño de lo que deciden las comparaciones.

Origen: el plan (§7.2, regla 9), acotado. La versión medida al principio (literal repetido 3 veces en un archivo) daba
unas 150 apariciones, casi todas etiquetas de interfaz; esta solo mira comparaciones.

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 4 | 0,24 |
| `machine/*` | 18 | 0,70 |
| `translation/*` | 5 | 0,01 |

Separa 3 veces, y lo que encuentra en `emulator` es deuda.

## 3. Muestra: los 22 de `emulator` y `machine`, juzgados por Claude

| textos | dónde | juicio |
|---|---|---|
| `"turbo"`, `"model"`, `"romSet"`, `"processor"` | `EmulatorCore` (4 miembros cada uno), `DefaultsCore`, `MockEmulatorCore`, `SpeccyEmulatorCore` | **verdaderos**: las opciones de la máquina nombradas por texto en cada lugar que las lee o las pone |
| `"tstates"` | `LocalLibretroCore`, al leer y al escribir un registro | verdadero |
| `"BC"`, `"DE"`, `"SP"` | `MemptrUpdater`, `IndirectMemory16BitReference` (`emulator`) | **verdaderos**: registros comparados por su nombre cuando existe `RegisterName` |

22 verdaderos de 22.

## 4. Decisión

Cumple las tres condiciones. Adoptarla congela 27 hallazgos.
