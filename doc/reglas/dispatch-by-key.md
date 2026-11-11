# Ficha: `dispatch-by-key` (candidata)

Estado: **candidata, no rinde**.

## 1. Definición

Una variable o un campo cuyo tipo es un mapa de una clave a una función (`Runnable`, `Supplier`, `Function`,
`Consumer`, `…Action`, `…Handler`): un `switch` guardado como dato. Un hallazgo por declaración.

Modelo que falta: `variation-as-data`. Comportamiento en el enum que son las claves, o un tipo comando.

Origen: idea 6 de CERCO.md; es el disfraz que `no-switch` nombra como "no es arreglo".

## 2. Sondeo (cerco 0.15.0)

oozx `87e376742`: `emulator` 0, `machine` 2, `translation` 2. oozx-plugins: 0.

## 3. Muestra

`KnobRows.refreshers` (`Map<Knob, Runnable>`) y `looks` en `quickMenu()` (`Map<String, Runnable>`): débiles, son
registros de qué refrescar, no despacho. Los dos de `translation` (`Map<Integer, AddressAction>`,
`Map<Register, …Handler>`) sí son tablas de despacho.

## 4. Decisión

4 casos en 800 mil líneas: no rinde. Sirve como red para cuando el agente esquive `no-switch` con un mapa.
