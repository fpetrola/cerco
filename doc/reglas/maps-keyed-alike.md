# Ficha: `maps-keyed-alike` (candidata)

Estado: **candidata, propuesta para adoptar**. Pendiente la revisión de Fernando.

## 1. Definición

Un tipo con 2 o más campos mapa (`Map`, `HashMap`, `EnumMap`…) cuyo primer argumento de tipo es el mismo. Un hallazgo
por tipo y tipo de clave.

Modelo que falta: `concept-without-a-class`. Lo que se sabe de una clave está repartido entre los mapas: una clase
para eso, en un solo mapa; o campos de la clave, si la clave es un tipo del proyecto.

Origen: la medición del plan (§1.3 y §7.2, regla 8).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 0 |
| `machine/*` | 5 |
| `translation/*` | 15 |

Separa: 0 en el código de referencia.

## 3. Muestra: los 5 de `machine`, juzgados por Claude

| sitio | juicio |
|---|---|
| `OOZxConfiguration`: 4 mapas por `String` (`screenDefaults`, `keptScreenProfiles`, `snapshots`, `snapshotHistory`) | **verdadero**, el caso conocido |
| `RomFiles`: 3 mapas por `String` (`files`, `sources`, `sets`) | verdadero: lo que se sabe de una ROM por su nombre |
| `Joystick`: 2 mapas por `JoystickType` (`lastRead`, `kinds`) | verdadero: la clave es un tipo del proyecto, que podría tenerlos |
| `RecreatedLayout`: `presses` y `releases` por `Integer` | probable: apretar y soltar de una misma tecla |
| `SwingKeyboard`: `unicodeKeysymsHash` y `keysyms` por `Integer` | **dudoso**: dos tablas cuyas claves son enteros de dominios distintos |

4 verdaderos o probables y 1 dudoso, de 5. El dudoso muestra el límite: con claves `String` o `Integer`, que dos mapas
compartan el tipo de la clave puede ser coincidencia (ahí lo que falta es el tipo de la clave).

## 4. Decisión

Cumple: más de 3 casos reales y separa. La muestra de `machine` es chica (5); conviene mirar los 15 de `translation`
antes de adoptarla.
