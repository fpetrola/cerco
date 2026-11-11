# Ficha: `static-filled-by-instances` (candidata)

Estado: **candidata, precisa y con pocos casos**.

## 1. Definición

Un campo estático que cambia un constructor o un método de instancia de su mismo tipo: una asignación (`f = …`,
`f[i] = …`, `Tipo.f = …`), un `++`/`--`, o una llamada que modifica (`add`, `put`, `remove`, `clear`, …). No cuentan
los nombres que el miembro declara como parámetro o variable local. Un hallazgo por miembro y campo. Corre por
archivo.

Modelo que falta: `knowledge-without-one-owner`. Un campo de la instancia; o, cuando de verdad es uno para todas, un
objeto que lo tiene y que se le pasa a cada instancia.

Origen: autopsia de oozx del 2026-11-11 (`SwingKeyboard.unicodeKeysymsHash`, que cada ventana llenaba y el `end()` de
cualquiera vaciaba para todas, commit `2e6b7495b`). `no-mutable-static` lo tenía como deuda genérica congelada; y un
`static final Map` ni siquiera lo marca.

## 2. Sondeo (cerco 0.16.0)

oozx `87e376742`: `emulator` 1, `machine` 5, `translation` 1. oozx-plugins: 1.

## 3. Muestra

Los 8 leídos. `SwingKeyboard.unicodeKeysymsHash` (constructor y `end()`): el bug que le dio origen.
`SpectrumApplication.io` (lo pisa cada constructor), `AttachedFrame.resettling` (bandera estática que prende un
método de instancia), `ScreenSettings.kept`, `OOSpectrumConnector.remoteData`, `RzxFrame.numbersInUse`: ciertos.
`CachedTableAluOperation.TABLE_CACHE` es un caché compartido a propósito: cumple la definición y es el caso
discutible.

## 4. Decisión

Pendiente de Fernando. La otra sesión propone que frene aunque haya deuda congelada, porque suele ser un bug y no
un olor: eso es una decisión de trinquete (hoy toda regla nueva congela lo que encuentra) y no se implementó.
