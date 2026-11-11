# Ficha: `told-one-by-one` (candidata)

Estado: **candidata, sin decidir**: casos reales, pero pocos.

## 1. Definición

3 o más sentencias seguidas que hacen la misma llamada (mismo nombre y cantidad de argumentos) sobre objetos
distintos. Un hallazgo por tirada.

Modelo que falta: `protocol-by-convention`. Los que tienen que enterarse son un grupo que nadie guarda: oyentes que
se registran y a los que se avisa de una vez (Observer), o partes de una cosa que pasa el mensaje (Composite).

Origen: Kerievsky, *Replace Hard-Coded Notifications with Observer*.

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 1 |
| `machine/*` | 2 |
| `translation/*` | 8 |

No separa.

## 3. Muestra, juzgada por Claude

| sitio | juicio |
|---|---|
| `Speccy.accept(PartVisitor)`: 7 partes reciben `accept(visitor)` una por una | **verdadero**: las partes de la máquina no son una colección; es el mismo `Speccy` de `kept-together` |
| `OOZ80.reset()`: 3 partes reciben `reset()` (`emulator`) | verdadero |
| `MediaSlot.show(String)`: 4 botones reciben `setEnabled(…)` | débil: son componentes de Swing |

## 4. Decisión

2 verdaderos y 1 débil: no llega a 3 casos reales fuera de `translation`. Mirar los 8 de `translation` antes de decidir.

## 5. Segundo proyecto, y solo objetos del proyecto (0.15.0)

En oozx-plugins daba 30, casi todos filas de botones de Swing (`addActionListener`, `setEnabled`). Ahora la regla
exige que el primer receptor sea de un tipo del proyecto (resuelto por el índice): quedan 1 en `emulator`, 1 en
`machine` (`Speccy.accept`) y 0 en oozx-plugins. Precisa, y casi sin casos: no rinde.
