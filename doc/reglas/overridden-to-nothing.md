# Ficha: `overridden-to-nothing` (candidata)

Estado: **candidata, sin decidir**: precisa, no separa.

## 1. Definición

Un método con `@Override` cuyo cuerpo está vacío o solo tira una excepción, cuando lo que sobreescribe lo declara un
tipo del proyecto que está por encima (resuelto por el índice). Los callbacks vacíos de interfaces de librería
(`KeyListener.keyTyped`) no cuentan. Un hallazgo por método.

Modelo que falta: `hierarchy-without-is-a`, el modelo número doce, agregado para estos casos: un subtipo que hereda
lo que no hace. Arreglo: un supertipo más angosto para lo que todos hacen y un tipo propio para el resto.

Origen: *Refused Bequest* de Fowler; *Rebellious Hierarchy* de Suryanarayana; heurística de Riel ("una clase
derivada no sobreescribe con un método vacío").

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 6 |
| `machine/*` | 2 |
| `translation/*` | 9 |

No separa. Sin el índice daba 6 y 14: 9 eran callbacks de Swing o de oyentes con muchos métodos, y 3 un homónimo.

## 3. Muestra, juzgada por Claude

| sitio | juicio |
|---|---|
| `ReadOnlyMemoryImplementation`: `reset`, y agregar o quitar oyentes, vacíos (`emulator`) | **verdadero**: una memoria de solo lectura no es una `Memory` entera |
| `DummyInstruction.execute()` y `accept(…)` vacíos (`emulator`) | probable: es un objeto nulo; lo que falta es decirlo |
| `MockEmulatorCore.saveState` y `finishEmulation` vacíos | verdadero: un doble de prueba en código principal |

## 4. Decisión

8 de 8, pero más deuda en `emulator` que en `machine`. Cumple por "deuda que el dueño reconoce" si Fernando la
reconoce.
