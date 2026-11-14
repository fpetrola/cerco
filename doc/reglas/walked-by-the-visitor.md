# Ficha: `walked-by-the-visitor` (candidata)

Estado: **candidata, medida en un solo proyecto**.

## 1. Definición

Dentro de un método `visit*`, o de una clase que se llama `*Visitor` o implementa uno, una llamada `x.accept*(...)`
sobre un parámetro del método: el visitor elige qué partes del objeto visitado recorrer, y por eso sabe cómo está
armado. Un hallazgo por llamada.

Modelo que falta: `structure-walked-from-outside`. Un solo `accept` en el elemento, que se visita y hace aceptar al
visitor en sus partes; el visitor al que no le interesa una parte no hace nada al visitarla.

Origen: pedido de Fernando desde cerco-tdd (cerco 0.26.0), junto con `one-accept`, que es la otra mitad del mismo
problema.

## 2. Sondeo

cerco-tdd `9833df7`, después de migrar a un accept por elemento: 0 en `tui-api` (426 líneas), `tui-text` (1919) y
`tui-demo` (81). Antes de la migración habría marcado `window.acceptViews(this)`, `box.acceptRows(...)`,
`group.acceptOptions(...)`, `menu.acceptItems(this)` y `bar.acceptMenus(...)`: los cinco, casos reales.

## 3. Riesgo

Los visitors jerárquicos hechos a propósito (enter/leave, un visitor que poda) hacen la misma llamada. cerco-tdd no
tiene ninguno, así que el sondeo no dice nada sobre ellos.

## 4. Decisión

Pendiente. Medirla en oozx, o en otro proyecto con visitors propios, antes de sumarla al catálogo. En cerco-tdd
`one-accept` ya cubre lo que esta señal habría marcado: sin varios `accept*` no quedan partes para que el visitor
elija.
