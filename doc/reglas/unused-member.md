# Ficha: `unused-member` (candidata)

Estado: **candidata; la mitad privada es precisa, la pública todavía es ruidosa** (§6).

## 1. Definición

Un campo privado que nadie lee (se escribe con `=` y nada más, o ni eso) o un método privado que nadie llama ni pasa
como referencia. Lo privado solo se puede usar desde su archivo, así que el archivo alcanza para decirlo. No mira
miembros con anotaciones (inyección, serialización) ni los ganchos de serialización (`readObject`,
`serialVersionUID`, …), ni un campo cuyo inicializador es una llamada a un método (desde 0.16.1, ver §3). Un método que solo se llama a sí mismo cuenta como sin llamadores. Corre por archivo.

Sin modelo que falte: es una regla de honestidad, como `no-ignored-result`. Remedio: borrarlo junto con el código
que solo lo alimentaba.

Origen: arreglos de oozx del 2026-11-11 (12 métodos y 11 campos borrados, unas 280 líneas; commits `ec3a5afa4`,
`daf0b2fc0`, `3141cc248`, `3412016a1`).

## 2. Sondeo (cerco 0.16.0)

oozx `87e376742`: `emulator` 6, `machine` 9, `translation` 17 (1 en código generado). oozx-plugins: 15.

## 3. Muestra

7 verificados contra el fuente en oozx, todos ciertos: `DefaultInstructionFetcher.prefetchPC` (se asigna en un lugar
y no se lee), `DefaultInstructionFactory.c`, `MemptrUpdater.memory`, `SpeccyScreen.screenBuffer`,
`OOZxConfiguration.CONFIG_FILE` (constante sin uso), `VerifyRangeHandler.getOwnRange()`,
`Virtual8BitsRegister.isComposed`. En plugins, `MiniZXKeyboard.joystickToSJS1/2` y dos constantes sin uso.

Falso positivo encontrado al usarla (oozx, 2026-11-11): `ZXSpectrumDesktopApp.whileItChanges`,
`private final AutoCloseable whileItChanges = Plugins.managing()…`. Nadie lo lee, pero lo que importa es el registro
que hace el inicializador; el campo solo lo retiene. Desde 0.16.1 un campo inicializado con una llamada a un método
no se marca (uno inicializado con `new` sí). Los otros ocho que marcó en `machine` eran ciertos y se borraron (oozx
`41228a9b9`).

## 4. Lo que no hacía en 0.16 (resuelto en §5 y §6)

Los métodos públicos sin llamadores, que fueron la mayor parte de lo borrado. Dentro del reactor se pueden contar,
pero la API para plugins da falsos positivos: `savedBy` y `aboutNotDoingIt` los llama plugdesk, no el reactor. Verlos
exige leer jars de afuera (`~/.oozx/plugin-cache`, plugdesk), y eso choca con "cero configuración". Lo decide
Fernando.

## 5. Decisión de Fernando (2026-11-11): cerco no lee consumidores de afuera

Ni `~/.m2`, ni jars, ni otros repos. El uso que hacen los plugins se escribe como tests en el propio proyecto, y
cerco cuenta esos tests como llamadores. Muerto quiere decir que nadie lo nombra en main ni en test.

Desde 0.17.0 las reglas pueden oír los tests (`Inspection.hear`): se leen solo para saber quién llama a qué, ningún
hallazgo sale de un archivo de test e `includeTests` sigue en false para el resto. Desde 0.17.1 los oyen también
`audit`, el `check` de un proyecto de un módulo y el congelado; el digesto del proyecto los incluye, así que un test
nuevo que hace vivo un método cuenta como cambio de código y no como regla aflojada. `quick` y el `check` de un
módulo no corren reglas de todo el proyecto, así que no los necesitan.

## 6. La mitad pública (0.17.0)

Un método que no es privado, con cuerpo y sin anotaciones (`@Override` lo saca), cuyo nombre nadie llama ni pasa
como referencia en main ni en test. Se compara por nombre solo, sin aridad ni tipo: es conservador. El hallazgo
termina con "if a consumer outside this project uses it, write a test that uses it the same way".

| proyecto | privados | públicos |
|---|---|---|
| oozx `87e376742` | 31 | 1.231 (790 en juegos generados) |
| oozx-plugins | 15 | 195 |

Muestra de 25 de los 441 de oozx fuera del código generado. Hay de tres clases: muertos de verdad
(`Z80State.setRegHLx`, `RegistersBase.triggerNMI`, `Routine.getInstructions`); API que usan los plugins
(`Desk.machines()`, `Cpu.stopTellingAboutInterrupts`), que con esta decisión piden su test; y falsos positivos por
reflexión, que un test no arregla: los getters y setters de `OOZxConfiguration` que lee el serializador de JSON, y los
`retro_*` de `LocalLibretroCore` que llama JNA. 237 de los 441 son `get`/`set`/`is`. Los métodos de los juegos
generados se llaman por dirección, no por nombre.

Desde 0.17.2 no marca un `get`/`set`/`is` de un tipo que un serializador lee por reflexión. Sin symbol solver: un
tipo que lleva anotaciones `Json…` (en sí o en un miembro), sus anidados, los tipos escritos en sus campos
(argumentos genéricos incluidos), los de los campos de esos, y los supertipos de todos. Lo propuso la sesión que
arregla `machine` después de ver `OOZxConfiguration.setGameFolders`, `Favorite.setGameId`,
`SnapshotHistoryEntry.setLoadedTime` e `InputSetup.Keys.getDown` (este último sin anotaciones en su archivo: se llega
por un campo). En oozx los hallazgos de `OOZxConfiguration` e `InputSetup` bajan de 78 a 12, ninguno accesor; la
mitad pública fuera del código generado, de 441 a 374.

Queda como falso positivo conocido lo que llama el lado nativo por nombre: `OOSpectrumConnector.sendData` y
`GetTStatesHistory.setTstatesUpdates` en `machine/bridge` (libretro/JNA). Un test no los hace vivos.

Para que rinda falta decidir qué hacer con lo que llama el código nativo (propiedades de tipos que se serializan,
callbacks nativos) y con el código generado.

## 7. Decisión

Pendiente de Fernando: adoptar la mitad privada tal como está; la pública necesita primero el criterio de §6.
