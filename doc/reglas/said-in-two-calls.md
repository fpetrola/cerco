# Ficha: `said-in-two-calls` (candidata)

Estado: **candidata, no rinde todavía**.

## 1. Definición

Las mismas dos llamadas, una después de la otra, sobre un mismo objeto del proyecto, en 3 o más miembros. El objeto
tiene que estar declarado con un tipo del proyecto; no cuentan dos setters seguidos (eso es `set-from-outside`).
Un hallazgo por sitio. Generaliza `notified-by-caller`.

Modelo que falta: `protocol-by-convention`. Una operación del receptor que haga las dos.

Origen: el plan (§7.2, regla 11).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos |
|---|---|
| `emulator` | 3 |
| `machine/*` | 0 |
| `translation/*` | 0 |

Sin exigir el tipo del receptor daba 3 y 9, con 6 falsos (`recentFiles.remove(x); recentFiles.add(x)` sobre una
lista, `line.open(); line.start()` sobre una línea de audio de Java) y 3 que ya ve `set-from-outside`.

## 3. Muestra

`visitor.visitingTarget(); visitor.visitingTargetInstruction()` en el `accept` de `Dec16`, `Inc16` y
`TargetInstruction`: **verdadero**. Tres instrucciones repiten el mismo protocolo de visita.

## 4. Decisión

Un solo caso real: no cumple "3 o más casos". Lo que pierde son los receptores cuyo tipo no se ve en el archivo (un
campo de otro objeto, el resultado de una llamada). Para que rinda hace falta resolver esos tipos con el índice, que
es el paso siguiente de la infraestructura.

## 5. Con los tipos resueltos por el índice (0.15.0)

El tipo del receptor ya se resuelve a través del índice (`a.b()`, campos heredados). El resultado en oozx no cambió:
el único caso sigue siendo el par del visitor en `emulator`. La regla no rinde en este proyecto por falta de casos,
no por falta de tipos.

## 6. Segundo proyecto: oozx-plugins

6 hallazgos que son un solo caso (`steps.add(…); steps.repeat(…)` en tres métodos de `Sounds`), contado en los dos
órdenes. Sigue sin rendir.
