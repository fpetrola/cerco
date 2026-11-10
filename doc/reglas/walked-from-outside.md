# Ficha: `walked-from-outside` (candidata)

Estado: **candidata, propuesta para adoptar**. Pendiente la revisión de Fernando.

## 1. Definición

Lo que entrega un método del proyecto sin argumentos (`x.items()`, también a través de `.values()`, `.keySet()` o
`.entrySet()`) recorrido con for-each, `stream`, `forEach`, `iterator` o `removeIf` desde 3 o más miembros de tipos
de primer nivel que no declaran ese método. Un hallazgo por sitio. Generaliza `refiltered-by-type`, que veía una sola
forma en un solo archivo.

Modelo que falta: `structure-walked-from-outside` (una consulta con nombre, o un iterador interno en el dueño), o
`dispatch-from-outside` si cada recorrido trata distinto a cada tipo de elemento.

## 2. Sondeo (oozx `e346c72b6`, el reactor entero en una pasada)

| grupo | hallazgos |
|---|---|
| `emulator` | 0 |
| `machine/*` | 23 (app 20, kit 2, ui 1) |
| `translation/*` | 12 |

Separa.

## 3. Muestra (`machine`, por método entregado)

| entregado | miembros que lo recorren | juicio de Claude |
|---|---|---|
| `WhatIsPluggedIn.equipment()` | 7, en `ZXSpectrumDesktopApp` (`openFor`, `opensFor`, `offers`…) | **verdadero**: buscan el equipo que abre un archivo; falta la consulta en el dueño |
| `WhatIsPluggedIn.tools()` | 6, en `ZXSpectrumDesktopApp` y `EmulatorInternalFrame` | verdadero |
| `WhatIsPluggedIn.deskWindows()` | 3 | verdadero |
| `Machine.getMachineTypes()` | 4, en `SpeccyEmulatorCore`, `DefaultsCore`, `OOSpectrumLauncher` | verdadero: buscan un tipo por nombre, y listan nombres |
| `all()` de `Scalers`, `DirtyCells` y `Processors` | 3 | **falso**: tres dueños con el mismo nombre se suman sin tipos |

20 verdaderos y 3 falsos, de 23.

## 4. Decisión

Cumple las condiciones. Para bajar los homónimos se puede exigir que el método lo declare un solo tipo del proyecto
(`all()` lo declaran tres), a costa de no ver los casos reales con nombres comunes. Adoptarla congela 35 hallazgos.

## 5. Lectura (0.15.0)

Un `stream` sobre lo entregado lee como consulta que falta en el dueño; un bucle, como recorrido que el dueño debería
hacer solo. En oozx `87e376742` (12 en `machine`): 5 consultas y 7 recorridos. Después del arreglo de
`WhatIsPluggedIn` en la rama `cerco`, `equipment()` bajó de 7 miembros a 3.
