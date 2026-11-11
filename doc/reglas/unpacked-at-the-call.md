# Ficha: `unpacked-at-the-call` (candidata)

Estado: **candidata, sin decidir**: precisa, no separa.

## 1. Definición

Una llamada (o un `new`) que recibe 3 o más partes de un mismo objeto del proyecto, pedidas en la lista de
argumentos: `f(a.x(), a.y(), a.z())` o `f(a.x, a.y, a.z)`. El objeto tiene que estar declarado, en su archivo, con un
tipo del proyecto, y cada parte ser un campo suyo o un getter. Un hallazgo por llamada y objeto.

Modelo que falta: `behaviour-not-on-owner`. Se le pasa el objeto entero, o la operación es un método de ese objeto.

Origen: el plan (§7.2, regla 14).

## 2. Sondeo (oozx `87e376742`, cerco 0.15.0)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 6 | 0,36 |
| `machine/*` | 7 | 0,27 |
| `translation/*` | 0 | 0 |

No separa. Sin saber de quién es cada tipo daba 8 y 13, con 7 falsos: objetos de librería (`BufferedImage`,
`MouseEvent`, `Rectangle`) y fábricas (`select(i.RLCA(), i.RRCA(), …)`).

## 3. Muestra: los 13, juzgados por Claude

| sitio | juicio |
|---|---|
| `new LocalLibretroCore(speccy.scheduler, speccy.display, speccy.machine, speccy.cpu, …)` (6 partes) | **verdadero**: el mismo `Speccy` que `reached-through` muestra como bolsa de partes |
| `open(chosen.file(), chosen.source(), chosen.entry())` | verdadero: `Opens.open` debería recibir lo elegido |
| `setBounds(state.getX(), state.getY(), state.getWidth(), state.getHeight())` (2 sitios) | verdadero: el estado de una ventana no sabe dar su rectángulo |
| `paintRow(first.y, first.x, first.colour)`, `new TStateUpdate(kv.key, kv.value, kv.pc)` | verdaderos |
| `new Rectangle(main.getX(), main.getY(), main.getWidth(), main.getHeight())` | débil: `getBounds()` ya existe |
| `emulator`: 5 `new X(x.getA(), x.getB(), x.getC())` en los clonadores | verdaderos: copiar un objeto pidiéndole sus partes; cada tipo podría copiarse solo |
| `contend(contention.times(), contention.tstates(), contention.kind())` | verdadero |

12 verdaderos y 1 débil, de 13.

## 4. Decisión

Precisa, pero no separa: `emulator` clona sus referencias pidiéndoles las partes. Si Fernando reconoce eso como deuda,
cumple por la otra salida del plan.

## 5. Con los tipos resueltos por el índice (0.15.0)

6 en `emulator` y 8 en `machine` (uno más: el objeto era un campo heredado).
