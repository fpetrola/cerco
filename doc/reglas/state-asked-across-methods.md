# Ficha: `state-asked-across-methods` (candidata)

Estado: **candidata, sin decidir**: precisa, pero encuentra poco y no separa.

## 1. Definición

Un campo de instancia que su propia clase testea en 3 o más métodos, como bandera (solo, negado, unido con `&&` o
`||`) o comparado con una constante con nombre. No cuentan los nombres que el método declara como parámetro o local.
Un hallazgo por campo.

Modelo que falta, según quién lo cambia: si algún método lo asigna, `state-without-an-object` (un objeto por estado);
si solo se fija al construir, `variation-as-data` (una subclase o una estrategia por valor: la *tag class*).

Origen: el plan (§7.2, regla 12). Es el *state checking* de JDeodorant extendido a booleanos.

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 1 |
| `machine/*` | 2 |
| `translation/*` | 6 |

No separa por tasa.

## 3. Muestra: los 3 de `emulator` y `machine`, juzgados por Claude

| sitio | lectura de cerco | juicio |
|---|---|---|
| `ObservableRegister.listening` (4 métodos) | estado | verdadero: el registro espiado escucha o no, y cada operación pregunta |
| `AttachedFrame.compact` (3 métodos) | estado | verdadero |
| `Edge.flat` (5 métodos: `across`, `along`, `laid`, `run`, `thickness`) | un tipo fijo al construir | **verdadero**, y es deuda que dejó Claude al crear `Edge`: horizontal y vertical son dos clases de borde |

3 verdaderos de 3.

## 4. Decisión

No cumple la condición de separar, y la muestra es chica. Dos salidas: bajar el umbral a 2 métodos y volver a
sondear, o dejarla como evidencia de `enum-decided-across` y `absence-as-state`.
