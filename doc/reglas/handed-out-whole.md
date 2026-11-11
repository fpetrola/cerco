# Ficha: `handed-out-whole` (candidata)

Estado: **adoptada en 0.15.0** por decisión de Fernando (2026-11-10).

## 1. Definición

Un método que no es privado y cuyo cuerpo entero es devolver un campo de instancia que es una colección o un mapa
(`List`, `Set`, `Map`, `Collection`, `Deque`, `Queue`). Un hallazgo por método.

Modelo que falta: `structure-walked-from-outside`. El objeto entrega su colección misma y los clientes la recorren y
la cambian; es donde empieza lo que ve `walked-from-outside`. Arreglo: las operaciones que los clientes necesitan, en
el dueño.

Origen: el cruce con los catálogos (*Leaky Encapsulation* de Suryanarayana; *Encapsulate Collection* de Fowler).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 1 | 0,06 |
| `machine/*` | 15 | 0,59 |
| `translation/*` | 24 | 0,03 |

Separa 10 veces.

## 3. Muestra: los 16 de `emulator` y `machine`, juzgados por Claude

| sitio | juicio |
|---|---|
| `OOZxConfiguration`: 11 getters (`getRecentFiles`, `getFavorites`, `getGameFolders`, `getOpenWindows`, `getSnapshots`…) y 2 de su `WindowState` | **verdaderos**: la configuración entrega sus listas y mapas y cada cliente los edita |
| `Machine.getMachineTypes()` | verdadero: es la lista que cuatro clientes recorrían hasta el arreglo de `Machine.model(String)` |
| `Backplane.wired()`, `Snapshots.notes()`, `Settings.Mirror.properties()` | verdaderos |
| `MultipleItemsInvoker.getList()` (`emulator`) | verdadero |

16 de 16.

## 4. Decisión

Cumple las tres condiciones. Adoptarla congela 40 hallazgos.
