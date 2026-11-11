# Ficha: `condition-repeated` (candidata)

Estado: **candidata, no rinde todavía**.

## 1. Definición

La misma condición, escrita igual, en 2 o más miembros del proyecto, cuando está unida con `&&` o `||` y le pregunta
al menos dos cosas a otros objetos. Un hallazgo por sitio.

Modelo que falta: `behaviour-not-on-owner`. Una consulta con nombre en el objeto que tiene las respuestas.

Origen: propia de esta tanda: es "la misma pregunta desde varios lugares" (§3.1 del plan) leída al pie de la letra.

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 0 |
| `machine/*` | 2 |
| `translation/*` | 4 |

Con 3 miembros no encontraba nada.

## 3. Muestra

`line < 0 || line >= display.HEIGHT || into >= getTimings().frame()…` en `Spectrum.waitAt` y
`Spectrum.unattachedPort`: **verdadero**, "¿esto cae fuera de la imagen?" escrito dos veces.

## 4. Decisión

Un caso: no cumple. Exigir el mismo texto es demasiado estricto, porque dos lugares que hacen la misma pregunta casi
nunca usan los mismos nombres de variable. La forma que puede rendir compara la forma de la condición (los mismos
miembros pedidos, en el mismo orden) y no su texto.

## 5. Por forma y no por texto (0.15.0)

Ahora compara la forma: la condición con las variables a las que les pregunta escritas como `_`. En oozx no sumó
ningún caso (`emulator` 0, `machine` 2, `translation` 4): la regla no rinde aquí porque casi no hay condiciones
compuestas repetidas, no por cómo se comparan.

## 6. Segundo proyecto: oozx-plugins (0.15.0)

En oozx-plugins da **23** (0 en `emulator`). Muestra de 7, juzgada por Claude: 4 verdaderos (`owner != null &&
owner.keeper && owner.team != …` en dos métodos de `Match`; `entry.publishers == null || entry.publishers.isEmpty()`
en tres; un rango de `d.data` en `UpdFdc`) y 3 débiles, sobre objetos de librería (`evt.isAltDown() && …`,
`icon.getIconWidth() > 0 && …`).

Cambia la decisión: **propuesta para adoptar**, después de exigir que lo preguntado sea un objeto del proyecto, como
se hizo con `told-one-by-one`.
