# Ficha: `middle-man` (candidata)

Estado: **candidata, no rinde en oozx**.

## 1. Definición

Una clase con 3 o más métodos que no hacen más que pasar la llamada a un mismo campo u objeto. Un hallazgo por clase.

Modelo que falta: `hierarchy-without-is-a` (una clase que está en el medio sin hacer nada).

## 2. Sondeo (oozx `87e376742`)

`emulator` 0, `machine` 2, `translation` 1.

## 3. Muestra

`Widgets` (16 métodos) y `TellsThePerson` (3) pasan todo a sus homónimos de plugdesk: son puentes dejados para no
romper a quien los llamaba. Verdaderos como descripción; si son deuda lo decide la compatibilidad con los plugins.

## 4. Decisión

No llega a 3 casos.
