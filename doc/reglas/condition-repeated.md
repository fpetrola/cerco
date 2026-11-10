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
