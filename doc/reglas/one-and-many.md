# Ficha: `one-and-many` (candidata)

Estado: **candidata, no rinde en oozx**.

## 1. Definición

Un tipo con un método que recibe una cosa y otro del mismo nombre que recibe una colección, un arreglo o varargs de
esa cosa. Un hallazgo por el método de muchos.

Modelo que falta: `structure-walked-from-outside`, por Composite: el grupo es una de las cosas y se recorre solo.

Origen: Kerievsky, *Replace One/Many Distinctions with Composite*.

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

`emulator` 0, `machine` 1, `translation` 1.

## 3. Muestra

`OOSpectrumConnector.sendData(double)` y `sendData(double[])`: débil, son datos crudos de un protocolo.

## 4. Decisión

No rinde aquí. Queda escrita porque es la única regla que apunta a Composite; hay que medirla en otro proyecto.
