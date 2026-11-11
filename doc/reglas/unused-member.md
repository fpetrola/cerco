# Ficha: `unused-member` (candidata, mitad privada)

Estado: **candidata; hecha solo la mitad que no sale del archivo**.

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

## 4. Lo que no hace, y por qué

Los métodos públicos sin llamadores, que fueron la mayor parte de lo borrado. Dentro del reactor se pueden contar,
pero la API para plugins da falsos positivos: `savedBy` y `aboutNotDoingIt` los llama plugdesk, no el reactor. Verlos
exige leer jars de afuera (`~/.oozx/plugin-cache`, plugdesk), y eso choca con "cero configuración". Lo decide
Fernando.

## 5. Decisión

Pendiente de Fernando: adoptar la mitad privada, y si la mitad pública vale una configuración.
