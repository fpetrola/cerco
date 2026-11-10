# Ficha: `enum-decided-across` (candidata)

Estado: **candidata, propuesta para adoptar**, como reemplazo de `enum-decided-outside` (que mira un solo archivo).
Pendiente la revisión de Fernando.

## 1. Definición

Las constantes de un enum declarado en el proyecto, decididas en 2 o más miembros de cualquier archivo: `switch`
cuyas etiquetas son todas constantes de ese enum, o comparaciones `x == E.A` / `x != E.A` con la constante nombrada
por su tipo. Cuenta también el `switch` que un enum hace sobre sí mismo, el falso negativo documentado de JDeodorant.
Un hallazgo por sitio. Si un conjunto de etiquetas cabe en dos enums, va al primero por nombre.

Modelo que falta: `variation-as-data` (un campo, método o cuerpo por constante), o `state-without-an-object` si el
enum es un modo.

## 2. Sondeo (oozx `3278a4105`)

| grupo | hallazgos |
|---|---|
| `emulator` | 1 (`RegisterBank.get`, un `switch` sobre los 37 `RegisterName`) |
| `machine/*` | 24 (ui 17, host/input 7) |
| `translation/*` | 7 |

Separa.

## 3. Muestra (`machine` y `emulator`, por enum)

| enum | miembros | juicio de Claude |
|---|---|---|
| `AttachedFrame.Dock` | 8, todos `dock == Dock.FREE` en `AttachedFrame` | **verdadero**, y es deuda que dejó Claude al crear `Edge`: "¿está pegada?" es una pregunta de `Dock` |
| `Knob.Kind` | 4 (`KnobRows.controlFor`, `ScreenSettings.apply`) | verdadero, el caso conocido |
| `Tint.Shade`, `ShadowMask.Pattern` | 2 y 2 (`== NONE` en `isTransparent` y el `switch` de `apply`) | verdadero, débil: "NONE" es la ausencia del efecto |
| `InputKey` | 2 (`Input.useShiftedArrowKeys` con 4 comparaciones, `RecreatedLayout.pressed`) | débil: códigos de tecla comparados de a uno |
| `RegisterName` (emulator) | 2 | débil: el banco busca el registro por nombre; un `EnumMap` también lo haría, pero no es comportamiento de la constante |

Unos 20 verdaderos y 5 débiles de 25.

## 4. Decisión

Cumple las condiciones. Adoptarla como reemplazo de `enum-decided-outside` necesita la firma de una persona, porque
una regla que se va del catálogo es un problema que sólo `approve` acepta.
