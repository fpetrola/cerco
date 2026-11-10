# Ficha: `hierarchy-asked-from-outside` (candidata)

Estado: **candidata**. No cumple la condición de separar; su adopción depende de que Fernando reconozca como deuda lo
que encuentra en `emulator` (§4).

## 1. Definición

Una jerarquía del proyecto (un tipo con 2 o más subtipos, por nombre simple) cuyos tipos se testean con `instanceof`
o se castean desde 2 o más miembros fuera de la jerarquía. No cuentan los `equals` ni los tests hechos dentro de la
familia. Cada test se asigna a una sola familia, la más grande a la que pertenece el tipo testeado. Un hallazgo por
sitio, con la evidencia: tamaño de la familia, cuántos miembros y tipos la preguntan, y si la familia ya acepta un
visitor (`accept(XVisitor)`).

Modelo que falta: `dispatch-from-outside`. Una operación, si todos los sitios hacen lo mismo; un visitor, si hacen
cosas distintas o están en un módulo que la familia no ve. Si el tipo testeado es un rol, `variation-as-data`.

Implementada como `ProjectRule`, sin índice compartido. El índice de la fase 2 del plan se construye cuando una
segunda regla necesite los mismos hechos.

## 2. Sondeo (`mvn cerco:survey`, oozx `a43d78f40`, 2026-11-10)

| grupo | hallazgos | por 1000 líneas |
|---|---|---|
| `emulator` | 47 | 2,78 |
| `machine/*` | 2 (`machine/ui`) | 0 a 0,56 |
| `translation/*` | 21 | 0 a 3,93 |

**No separa**, y es esperable: la regla mide jerarquías preguntadas desde afuera, y `machine` casi no tiene
jerarquías (es lo que dijo la medición del plan: 0 a 9 tipos en jerarquías por mil líneas contra 26 en `emulator`).
Donde no hay modelo, no hay a quién preguntarle. Complementa a las reglas que miden ausencia; no las reemplaza.

## 3. Muestra

`emulator`, por familia:

- **`Instruction`** (91 subtipos, acepta `InstructionVisitor`), 14 sitios en 8 miembros de 7 tipos. Los visitors
  `Before`, `After`, `PhaseProcessor` y `ToStringInstructionVisitor` reciben `visitRepeatingInstruction` o
  `visitingConditionalInstruction` y adentro preguntan `instanceof Inir`, `Indr`, `Call`, `JP`, `Ret`. El visitor
  existe pero es más grueso que lo que sus clientes necesitan. `DefaultInstructionExecutor.execute` pregunta
  `instanceof RepeatingInstruction` y `MultiOpcodeFetcher.wrapInstructions` pregunta `Ld`. Juicio de Claude:
  **verdaderos**, por la regla de diseño de Fernando (el modelo se recorre solo, los clientes no preguntan).
- **`OpcodeReferenceBase`** (62 subtipos, sin visitor), 33 sitios en 21 miembros de 11 tipos. Preguntas como
  `PhaseProcessor.isIndirectHL`, `isIndexed`, `isImmediate`, `isLdSP`, `IndexerRegisterTableOpCodeGenerator.isHL`,
  `BIT.execute` y `LdOperation.execute` con `instanceof MemoryPlusRegister8BitReference`: son operaciones que le
  faltan a la referencia, o un visitor de referencias. **Verdaderos.** Dudosos: los casts de
  `InstructionCloner.clone` y `DefaultRegisterBankFactory.compose`, que pueden venir de `clone()` y de armar
  pares. Unos 6 de 33.

`machine/ui`: `ScreenSettings.settings` y `steps` preguntan `instanceof Scaler` sobre `ScreenEffect` (13
subtipos). **Verdadero.**

Veredicto de Claude sobre la muestra completa de `emulator` y `machine`: unos 43 verdaderos y 6 dudosos, de 49.

## 4. Decisión

Cumple 8 de 10 verdaderos y encuentra muchos más casos que el que la originó. No separa los grupos por tasa, pero el
plan admite la otra salida: "encuentra deuda que reconocés como tal". Adoptarla congela 47 hallazgos en `emulator`,
2 en `machine` y 21 en `translation`.

Pregunta para Fernando: los visitors de `emulator` que preguntan subtipos dentro de un `visit` (Before, After,
PhaseProcessor), ¿son deuda (al visitor le faltan métodos), o una decisión (el visitor es grueso a propósito para no
tener 91 métodos)?

## 5. Lectura y corrección (0.15.0)

**Corrección.** La muestra de §3 dice que `OpcodeReferenceBase` no tiene visitor. Es falso:
`OpcodeReferenceBase` declara `accept(InstructionVisitor)`, el mismo visitor de las instrucciones. La regla lo
informa bien desde que usa el índice del proyecto; el error era de la lectura a mano. Cambia el arreglo: no hay que
crear un visitor de referencias sino usar el que la familia ya acepta.

La regla lee siempre como visitor, y dice cuál acepta ya la familia. En oozx `87e376742`: los 48 de `emulator` son de
familias que ya aceptan `InstructionVisitor`; los 8 de `machine`, de familias sin visitor. Pasa a `RepeatedRule` y al
índice del proyecto, con los mismos hallazgos.
