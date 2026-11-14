# Plan: la superficie sellada

Diseñar cada capa primero como interfaces, sellarla en cerco, y que sólo crezca con la firma de una persona.

> **Estado, 2026-11-13: plan, nada construido.** Lo pidió Fernando al cierre de una conversación en zx-lift sobre
> cómo habría convenido empezar lift5 para trabajar mejor con IA. Las medidas de §1 son de ese día.
>
> **Dónde vive y por qué.** En el repo de cerco, junto a `plan-smells-de-modelo.md`, y no en cerco-demo: el agente
> que corre los experimentos no tiene que leerlo (§6.4).
>
> **Sobre qué cerco.** Desde el 2026-11-13 hay un solo checkout de cerco, en `main`; `tests-como-llamadores` ya
> está ahí. C0 salió en 0.18.0. cerco-demo y zx-lift siguen en 0.2.0.
>
> **Avance.** P0 y P1 hechos (§12). P2: C1 en 0.19.0, C2 en 0.20.0, C3 entró con C1 (§12). Falta que Fernando pruebe `approve` en una terminal
> y la autoaplicación de cerco bajo su propio sello.

---

## 0. En una página

**La idea** (Fernando). Antes de implementar una capa se conversa su diseño y se escribe como interfaces, sin
código. Ese conjunto se sella en cerco. Después se implementa, y cada vez que haga falta una interfaz o un método
público más, el trabajo se detiene, se diseña ese cambio y lo aprueba una persona. Así con cada capa. El agente no
se desvía del diseño porque cerco no lo deja, no porque se le pida.

**Por qué creemos que sirve.** Un proyecto bien modelado ya mantiene al agente en línea, porque usar la abstracción
que existe es lo más barato. Eso se rompe en un momento preciso: cuando la tarea necesita algo que el modelo no
tiene. Ahí el agente agrega un método, un helper o un flag, en silencio, y el modelo se erosiona. El sello pone el
freno en ese momento y lo convierte en un pedido de firma.

**Qué hay que probar.** Que el sello contiene, que la presión no se va a otro lado y que la atención humana que
cuesta es soportable (§2).

**Cómo.** Tres experimentos en cerco-demo sobre `vista` (§6):

- **E0**: medir lo que hay, sin frenar nada.
- **E1**: sellar `vista` tal cual y pedirle una cosa que cabe en su modelo y otra que no.
- **E2**: rehacer `vista` desde cero con el método completo, y comparar contra la que ya existe.

**Qué cuesta.** Un incremento de cerco: medir la superficie, sellarla, y un formulario de propuesta que sólo
`approve` acepta (§5). Reusa la firma, el rojo humano y la maquinaria de formularios de las autopsias.

**Qué necesito de vos antes de empezar.** Cuatro decisiones: D1, D2, D3 y D8 (§11).

---

## 1. De dónde sale

### 1.1 lift5-model: un modelo de interfaces que nadie frenó

El 2026-11-13 Fernando rechazó el modelo abstracto de lift5 (`zx-lift/oop/lift5-model`): 98 interfaces y 519 métodos
abstractos, de los cuales 226 eran «uno por fila o por forma». Su diagnóstico: faltan los conceptos que agruparían
esas interfaces y quitarían métodos públicos. El plan que lo corrige
(`zx-lift/doc/epicas/lift5/PLAN-MODELO-ABSTRACTO.md`, §1.1 y §5) midió además dos cosas:

- agregar la fila 177 costaba un método público más (indicador I9);
- 121 firmas abstractas decían `String` (indicador I5): tipos del dominio que no existen.

Ese módulo tiene un gate de forma, `ShapeTest`, con un techo fechado por indicador: bajar es libre y subir «se
decide con Fernando». Es esta idea en chico, con un hueco: los techos están escritos en el propio test, que el
agente puede editar. La regla es prosa.

Con la superficie sellada, cada uno de esos 226 métodos habría sido un pedido de firma. El pedido repetido habría
mostrado en la primera semana que una fila nueva no debería necesitar un método público.

### 1.2 El primer experimento de cerco-demo

Está en `CERCO.md` §6. Una sesión con cerco 0.1.0 leyó las reglas del jar y diseñó contra ellas: nunca pasó a rojo y
dejó `Element.class.cast(x)`, `getNodeType() ==`, una escalera de `if`, clases de sólo constantes y un `boolean`
descartado que escondía un bug. La lección que quedó escrita: **un agente que conoce las reglas busca la forma más
cercana que no las rompe**, y cada regla nueva hay que pensarla contra el rodeo que va a provocar. Este plan aplica
esa lección al sello: §7 lista los rodeos previsibles antes de construir nada.

### 1.3 `vista` hoy, medida

`vista` es el framework que dejó ese experimento: 6 módulos, 79 archivos, 2.390 líneas. Tiene 79 tipos (16
interfaces, 57 clases, 6 records), de los cuales 37 son públicos.

| módulo | interfaces | métodos de interfaz | tipos públicos | métodos públicos fuera de interfaces | de ellos, sin `@Override` | constructores públicos |
|---|---|---|---|---|---|---|
| vista-data | 4 | 8 | 10 | 17 | 13 | 0 |
| vista-view | 4 | 5 | 6 | 11 | 11 | 3 |
| vista-binding | 1 | 1 | 4 | 11 | 5 | 4 |
| vista-swing | 3 | 5 | 6 | 23 | 6 | 2 |
| vista-html | 4 | 7 | 4 | 26 | 4 | 4 |
| vista-demo | 0 | 0 | 7 | 15 | 13 | 6 |
| **total** | **16** | **26** | **37** | **103** | **52** | **19** |

Lo que otra clase puede nombrar sin pasar por una interfaz son 52 métodos y 19 constructores. Lo que está en
interfaces son 26 métodos. **Poco más de un cuarto de la superficie de `vista` está diseñada como interfaz**; el
resto es API de clases concretas (`ChangeListeners.add`, `Kinds.with`, `SwingView.root`, `Agenda.contacts`).

Cómo se contó: conteos estructurales con ast-grep sobre `src/main/java`, sin tocar cerco-demo. «Sin `@Override`»
aproxima «no lo declara ningún supertipo». La cuenta incluye las fábricas estáticas (`Text.of`) y no incluye los
componentes de los records ni los miembros sin modificador, así que es un piso. E0 la rehace con cerco.

### 1.4 Lo que cerco ya tiene, y lo que le falta

Tiene todo lo que el sello necesita alrededor:

- **Firma**: `approve` exige una terminal interactiva y que se escriba `yes`.
- **Protección**: el `deny` del editor y el `guard` del shell le niegan `.cerco/` al agente.
- **Rojo humano**: lo que sólo una persona resuelve no entra en bucle; el hook deja terminar y el build sigue rojo.
- **Formularios**: las autopsias ya tienen plantilla, validación (`FormCheck`), pendientes y archivadas, y un estado
  «espera firma» para los `none:`.
- **Testigo**: digests de código para detectar una regla aflojada.
- **Medir sin frenar**: `survey` corre reglas candidatas sin tocar la línea de base.
- **Quién usa qué**: desde 0.17.0 una regla puede oír los tests como llamadores, y `unused-member` tiene su mitad
  pública.

Le falta lo central: **ninguna regla mide ni sella la superficie pública**. Todas miran la implementación, y por eso
un módulo de sólo interfaces pasa por vacuidad.

---

## 2. La hipótesis, y lo que la refutaría

- **H1, contención.** Con la superficie sellada el agente no puede cambiar el diseño en silencio: todo crecimiento
  se quita o llega como propuesta. Si el gate funciona esto vale por construcción; lo que se prueba de verdad son
  las dos siguientes.
- **H2, sin desplazamiento.** La presión no se va a otro lado: los indicadores de rodeo de §7 no empeoran contra el
  control.
- **H3, costo soportable.** Las propuestas son pocas y una persona las revisa por nombres en minutos.
- **H4, señal.** Las propuestas repetidas del mismo tipo señalan un concepto que falta. Esta se observa, no se mide.

La refutan tres resultados:

1. Las tareas se completan sin propuestas y con los rodeos en alza: el diseño se mudó a donde el sello no mira.
2. Las propuestas son tantas que se firman sin leer.
3. El agente se traba: se detiene sin proponer, o da vueltas. El gate no le dejó una salida legítima.

---

## 3. Los conceptos

- **Superficie**: todo lo que un objeto puede usar de otro. Los tipos y sus métodos, constructores, componentes de
  record, constantes de enum y campos constantes, siempre que no sean privados.
- **Sello**: el conjunto de miembros de la superficie que una persona firmó. Vive en `.cerco/surface/`.
- **Propuesta**: el formulario con el que el agente pide que el sello crezca. La primera propuesta de una capa es su
  diseño inicial; las siguientes son **enmiendas**.
- **Capa**: un conjunto de tipos que se diseñan y se sellan juntos. En estos experimentos, un módulo Maven.
- **Presupuesto**: cuántos tipos y cuántos miembros puede tener una capa, fijado por la persona antes de diseñar.
- **Contrato**: un test ejecutable escrito contra una interfaz en la etapa de diseño.
- **Zona de exploración**: código descartable fuera del reactor, donde el agente aprende sin pedir permiso.

Las reglas del sello:

1. **Se sella el conjunto exacto, no la cantidad.** Con un tope numérico se cambia un método por otro sin crecer.
2. **Crecer exige firma.** Cambiar una firma o renombrar es quitar y agregar, así que también.
3. **Achicar es libre, y la firma siguiente lo lista** (D2).
4. **El primer diseño es la primera propuesta.** Una capa nueva empieza con el sello vacío, de modo que el diseño
   inicial y las enmiendas pasan por el mismo mecanismo.
5. **Una propuesta abierta por vez.** Obliga a juntar lo que se pide en un paso de diseño coherente.
6. **Qué cuenta como superficie tiene tres alcances posibles** (D1): `interfaces` (sólo las interfaces y sus
   métodos, la idea literal), `public` (todo lo público) y `all` (todo lo que no es privado).

---

## 4. El método, capa por capa

| paso | quién | qué sale | qué lo hace cumplir |
|---|---|---|---|
| 1. Conversación de diseño | persona y agente | el vocabulario: cada concepto con su nombre, su responsabilidad en una línea y quién le habla | nada; es conversación |
| 2. Presupuesto | la persona | cuántos tipos y miembros puede tener la capa | a mano en E2; configuración firmada después (C4) |
| 3. La capa en interfaces | el agente | interfaces, records y enums sin implementación, y un contrato por interfaz | compila; `sealed-surface` ya está en rojo porque todo es nuevo |
| 4. Primera propuesta y sello | el agente la llena, la persona firma | el sello de la capa y la propuesta archivada | `approve`, en terminal |
| 5. Implementación | el agente | las clases detrás de las interfaces | `sealed-surface` en `quick`, `check` y `audit`, más las demás reglas |
| 6. Enmienda | el agente propone, la persona firma o dice que no | el sello crecido y otra propuesta archivada | lo mismo que el paso 4 |
| 7. Capa siguiente | persona y agente | vuelve al paso 1 para los colaboradores que la implementación necesita | con alcance `all`, un colaborador nuevo ya es superficie nueva |
| 8. Cierre de capa | el agente mide, la persona juzga | los indicadores de §7 y la respuesta a «¿es el diseño que acordamos?» | — |

Tres aclaraciones:

- **El presupuesto va antes que las interfaces.** El sello limita lo que crece después, pero lo que falló en
  lift5-model fue el diseño inicial. Un tope chico obliga a encontrar los conceptos que agrupan, y mantiene la
  propuesta en un tamaño que una persona puede revisar por nombres.
- **El contrato se escribe en el paso 3.** Es una clase de test abstracta por interfaz, con un método que entrega el
  sujeto; cada implementación agrega su subclase. Sin él la firma queda sellada y el significado no.
- **Explorar no pide permiso.** Lo que el agente necesite probar para entender va a `spikes/`, que no es un módulo
  del reactor y que cerco por eso no analiza. Lo que salga de ahí hacia el código pasa por el sello como cualquier
  otra cosa.

---

## 5. Lo que hay que construir en cerco

Sobre 0.17.3, en una rama nueva (D8). Se describe por comportamiento; las clases las diseña quien lo construya.

| id | comportamiento | cómo se comprueba |
|---|---|---|
| **C0** | `survey` imprime la superficie de cada módulo en los tres alcances y los indicadores S1 a S4 de §7. No frena. | Sobre `vista` reproduce la tabla de §1.3 con la medida ya definida. Corre también sobre cerco y sobre lift5-model. |
| **C1** | El sello: `.cerco/surface/<módulo>.txt`, una línea por miembro, ordenado, legible en un diff. La regla `sealed-surface` marca todo miembro que está en el código y no en el sello. | Agregar un método público da rojo en `quick`, apenas se edita. Renombrarlo también. Quitarlo achica el sello solo y deja una nota. Un módulo nuevo empieza vacío. |
| **C2** | La propuesta: cerco escribe `surface/pending/<id>.md` con lo que crece, regenerado en cada corrida. El agente completa el resto. Completa, pasa a rojo humano hasta que `approve` la muestra, agranda el sello y la archiva. | Una propuesta incompleta es rojo del agente. Una completa deja terminar al hook. Sin `yes` en una terminal el sello no cambia. Hay una sola abierta por vez. |
| **C3** | El alcance (`interfaces`, `public`, `all`) es configuración, y como toda configuración distinta del default vale sólo con firma. | Cambiarlo sin firmar es rojo humano. |
| **C4** | Después de los experimentos, si la evidencia lo pide: presupuesto como techo, contratos sellados por digest, hallazgos atribuidos a la sesión que tocó el archivo. | — |

Lo que el formulario de C2 le pide al agente, además de lo que cerco regenera:

- **Need**: qué no se puede hacer con el sello actual, en 150 caracteres o más.
- **Considered**: al menos un miembro ya sellado que se consideró, y por qué no alcanza.
- **Removes**: qué miembros quita esta propuesta, o `none`.
- **Contract**: la ruta de un test que existe y que dice lo que el miembro nuevo promete.

El mensaje rojo es la única explicación del método que recibe el agente en E1, así que tiene que decir las tres
salidas. Un borrador:

```
[sealed-surface] 1 new
  principle: design is agreed before it is built: what one object can ask of another was signed by a person
  do: if nothing outside this type needs it, make it private; if the sealed surface already says it, use that;
      if the design really lacks it, stop and fill surface/pending/<id>.md. A person signs it with cerco:approve
  vista-view/src/main/java/com/fpetrola/vista/view/Editable.java:5  Editable is not in the sealed surface of vista-view
```

Cuatro condiciones más:

- **Adopción.** En un proyecto que ya tiene código, la primera corrida completa sella lo que hay, como se adopta
  cualquier regla nueva. Sigue valiendo «se agrega al pom y funciona».
- **Rechazo.** En esta versión es de palabra: la persona no firma y dice por qué, el agente quita el miembro y el
  pendiente desaparece cuando ya nada crece. Si los rechazos resultan frecuentes, archivarlos es el paso siguiente.
- **Auto-aplicación.** `SelfCheckTest` tiene que seguir verde con cerco bajo su propio sello.
- **Lo que no se construye todavía.** Resolución de tipos, y la regla «nadie nombra una clase concreta de otro
  paquete». Primero se mide con `survey` si hace falta.

---

### 5.1 El diseño acordado de C1 (2026-11-13)

Presupuesto: dos tipos nuevos, nueve miembros públicos, tres cambios en tipos existentes. Lo que haga falta más se
propone antes de escribirlo.

```java
/** What a person signed of each module's surface: .cerco/surface/<module>.txt, one signature per line, sorted. */
public final class Seal {
  public Seal(Path directory);
  public boolean covers(String module);
  public List<Member> unsigned(String module, List<Member> offered);
  public void adopt(String module, List<Member> offered);
  public List<String> shrink(String module, List<Member> offered);
}

/** Every member a module offers that its seal lacks. id "sealed-surface", per file. */
public final class SealedSurface implements Rule {
  public SealedSurface(Seal seal, Reach reach);
}
```

- `Rule.baselined()`, por defecto `true`. `SealedSurface` responde `false`: sus hallazgos no se congelan ni se
  exceptúan.
- `Settings.sealedSurface()`: `<sealedSurface>interfaces|public|all</sealedSurface>` en el pom. Si falta, la regla
  no existe; si está, es configuración distinta del default y vale sólo con firma. No hay alcance por defecto
  hasta que E1 lo decida.
- La adopción vale sólo para el proyecto que nunca tuvo sello: su primera corrida completa sella todos los módulos
  como están. Después, un módulo nuevo empieza vacío; si no, crear un módulo esquivaría el gate.
- `Seal` y `SealedSurface` son clases finales: en cerco hay una sola implementación de cada cosa.

## 6. Los experimentos

### 6.1 E0: medir, sin frenar

- La superficie de `vista` con C0, en los tres alcances.
- `survey -Dcerco.rules=all` sobre `vista` con el cerco nuevo: cuántos hallazgos da el control con las reglas de
  hoy, por regla y por mil líneas. `vista` se escribió contra 0.2.0; sin esta medida la comparación de E2 mezcla el
  efecto del sello con el de las reglas que llegaron después.

**Sale cuando** las dos tablas están en §12.

### 6.2 E1: sellar `vista` y pedirle dos cosas

Preparación, en un worktree de cerco-demo: subir el plugin, correr `audit` (adopta las reglas nuevas y sella la
superficie tal cual), commitear. Fernando lee el sello una vez, por nombres, y anota cuánto tardó.

**Tarea A, cabe en el modelo.** El pedido, textual:

> Agregá a vista una tercera tecnología de vista: texto plano para una terminal. La agenda de la demo tiene que
> poder mostrarse también ahí, con el mismo repositorio.

El README de `vista` promete que una vista nueva no toca los datos ni el dominio. Predicción: cero crecimiento en
`vista-data`, `vista-view` y `vista-binding`, y una propuesta con la superficie del módulo nuevo.

**Tarea B, no cabe.** El pedido, textual:

> Hacé que un campo de texto de la vista pueda editar el dato que muestra: lo que la persona escribe llega al
> repositorio y las otras vistas lo ven. En Swing y en HTML.

Hoy `vista` no tiene camino de la vista hacia el dato: `Textual.show(String)` sólo muestra, y lo único que vuelve es
`Actionable.offer(String, Runnable)`. La tarea necesita superficie nueva en los tres módulos del núcleo.

La tarea B se corre dos veces, desde el mismo commit:

| corrida | alcance del sello | predicción |
|---|---|---|
| **E1a** | `interfaces`, la idea literal | Se completa con una propuesta o ninguna. La edición viaja por `Actionable` con un `Runnable` que captura el campo, o por métodos públicos de clases concretas. |
| **E1b** | `all` | Al menos una propuesta por módulo del núcleo, y la edición aparece como una capacidad con nombre. |

Las predicciones están escritas antes para que puedan salir mal. Si E1a no muestra desplazamiento, el alcance
literal alcanza y `all` es costo sin beneficio.

### 6.3 E2: una capa nueva con el método completo

Rehacer `vista` desde el commit vacío de cerco-demo (`fd3860a`), como `vista2`, siguiendo los ocho pasos de §4 con
Fernando en la conversación. El pedido es el problema, sin el diseño de módulos:

> Un framework que separa qué se muestra de cómo se muestra. Los datos se publican como un árbol abstracto; las
> vistas son templates puros, Swing o XHTML, con sus partes marcadas por id; un mapping por vista dice qué parte
> muestra cada dato. Para una vista nueva se escribe un template y un mapping, sin tocar los datos ni el dominio.
> Demo: una agenda en una ventana Swing y en una página web, con el mismo repositorio.

Ese texto sale del README actual, que escribió el agente del primer experimento. Si Fernando conserva el pedido
original, va ése.

El control es la `vista` que existe. Como referencia para el presupuesto (D5): el núcleo del control (`data`,
`view`, `binding`) tiene 20 tipos públicos y unos 50 miembros nombrables.

### 6.4 Higiene de los experimentos

- **El agente no lee este plan.** Recibe el pedido y nada más. Puede leer el jar de cerco y sus reportes, como
  siempre: eso es parte de lo que se prueba. Lo que se quiere ver es la fuerza del gate, no la de una explicación.
- **Un worktree por sesión.** El 2026-11-13, en zx-lift, el `audit` del hook de Stop le marcó a una sesión tres
  hallazgos de un archivo que otra sesión estaba escribiendo en el mismo árbol, con «Fix the code». Un agente que
  obedece sin mirar habría pisado a la otra.
- **Se guarda la transcripción** de cada corrida, y la hora de cada propuesta y de cada firma.
- **Una corrida por celda es una anécdota.** Si un resultado es ambiguo, la celda se repite antes de decidir.

---

## 7. Qué se mide

Indicadores, todos contra el control:

| id | indicador | de dónde sale |
|---|---|---|
| S1 | tipos y miembros sellados, por módulo | el sello |
| S2 | parte de los miembros nombrables que declara una interfaz (hoy, poco más de un cuarto) | C0 |
| S3 | firmas selladas que dicen `String`, `Object`, un primitivo que elige comportamiento o una función | C0 |
| S4 | miembros sellados que nadie llama | `unused-member`, mitad pública |
| P1 | propuestas: cuántas, cuántos miembros cada una y por qué causa | las archivadas |
| P2 | rojos de `sealed-surface` antes de una propuesta: los desvíos atajados | la transcripción |
| P3 | minutos de la persona por firma | las horas anotadas |
| P4 | veces que el agente se trabó sin proponer | la transcripción |
| Q1 | hallazgos de todas las reglas y candidatas, por mil líneas | `survey -Dcerco.rules=all` |
| Q2 | «¿es el diseño que acordamos?», por capa | Fernando |

Los rodeos previsibles, cada uno con su detector. Son predicciones; los que aparezcan pasan a ser reglas, con ficha
en `doc/reglas/`, como en el primer experimento.

| # | rodeo | detector |
|---|---|---|
| 1 | Un método que hace de diez: un texto, un enum o un booleano que elige comportamiento. | `flag-argument`, `named-by-text`, `constant-argument`, `dispatch-by-key`; S3 |
| 2 | Tipos portadores que engordan: un record que gana componentes, un `Map`, un `Object`. | S1 sobre los records; `no-type-keyed-map` |
| 3 | API en la sombra: miembros públicos de clases concretas. | S2; con alcance `all` no puede ocurrir |
| 4 | Todo en una clase detrás de una interfaz angosta. | `file-length`, `method-length`, la lectura de cohesión |
| 5 | Misma firma, otro significado. | diff de los contratos después del sello, a mano |
| 6 | Comportamiento que entra como lambda (`Function`, `Supplier`, `Runnable` en una firma sellada). | S3 |
| 7 | Reflexión (`Class.forName`, `getMethod`). | conteo por texto |
| 8 | Esconder código como generado o en los tests. | 0.17.3 ya trata lo recién marcado `@Generated` como regla aflojada; tipos públicos bajo `src/test` |
| 9 | La propuesta como trámite: muchas, chicas y seguidas. | la distribución de P1 |
| 10 | Código de exploración que termina en el producto. | `spikes/` no figura en `<modules>` |

---

## 8. Cuándo se adopta, se ajusta o se descarta

Los números son una propuesta; los fija Fernando (D6).

- **Se adopta** como parte de cerco si en E1b y en E2 se cumplen las tres: S2 no baja y los detectores de §7 no
  suben contra el control; después del sello inicial hay cinco propuestas o menos por tarea y cada una se revisa en
  cinco minutos o menos; y Fernando reconoce el diseño (Q2).
- **Se ajusta** si el sello contiene pero domina un rodeo. Ese rodeo se vuelve regla y la celda se repite.
- **Se replantea** si el agente se traba dos veces sin proponer, si una tarea pasa de quince propuestas, o si el
  diseño que sale se juzga peor que el control.

---

## 9. Orden de trabajo

| paso | qué | sale cuando |
|---|---|---|
| P0 | Las decisiones que bloquean: D1, D2, D3, D8. | Están anotadas en §11. |
| P1 | C0 en cerco, y E0. | Las dos tablas de E0 están en §12. |
| P2 | C1, C2 y C3 en cerco. | Tests de cerco verdes, `SelfCheckTest` incluido bajo su propio sello; versión nueva instalada en `~/.m2`. |
| P3 | Preparación de E1 y tarea A. | Transcripción guardada e indicadores en §12. |
| P4 | Tarea B dos veces: E1a y E1b. | Lo mismo, y la predicción de §6.2 contrastada. |
| P5 | E2. | `vista2` corre la demo; indicadores contra el control en §12. |
| P6 | Conclusiones. | `CERCO.md` §6 tiene «El segundo experimento»; cada rodeo observado tiene ficha; el default de D1 está decidido. |

Quién: una sesión en un worktree de cerco para P1 y P2; una sesión por corrida en un worktree de cerco-demo para P3
a P5. El que cierra un paso lo anota en §12.

---

## 10. Riesgos y supuestos frágiles

- **Fatiga de firma.** Si las propuestas son muchas, se firman sin leer y el sello no vale nada. Lo mide P1 y P3.
- **Desplazamiento.** Es H2; si falla, la idea produce peor diseño que no tener sello.
- **El primer diseño es el que menos sabe.** En lift5 el concepto de etapa no estaba al empezar: apareció el 18 de
  septiembre, con 28 filas ya fragmentadas. El método lo admite con la zona de exploración y las enmiendas, pero E2
  es la primera vez que se prueba.
- **Sin resolución de tipos.** Las firmas se sellan con los tipos como están escritos. Dos tipos con el mismo nombre
  simple, o un genérico reescrito, pueden confundirse. `project/Imports` ya sabe de quién es un nombre; hay que ver
  si alcanza.
- **Alcance `all` demasiado ruidoso.** Puede pedir firma por cada ayudante de paquete. Es lo que mide E1b.
- **Control imperfecto.** La `vista` que existe la hizo otra sesión, con otro cerco. E0 corrige la parte de las
  reglas; la del modelo no se puede corregir. La comparación limpia es E1a contra E1b, que salen del mismo commit.
- **Pocas corridas.** Con una por celda se aprende por dónde se escapa, no cuánto.
- **Costo humano de E2.** La conversación de diseño y las firmas son tiempo de Fernando, y también son un resultado.

---

## 11. Lo que necesito que decidas

| id | decisión | recomendación |
|---|---|---|
| **D1** | Qué cuenta como superficie: `interfaces`, `public` o `all`. | Medir los tres desde C0; sellar primero con `interfaces`, que es tu idea literal, y después con `all`; elegir el default con lo que se vea en E1. |
| **D2** | Si achicar es libre o también pide firma. | Libre, y que la firma siguiente lo liste. |
| **D3** | El tema de E2: rehacer `vista` u otro problema. | Rehacer `vista`: es lo único que da un control. |
| D4 | Si el agente sabe del método por el pedido o sólo por el rojo. | Sólo por el rojo en E1. En E2 el método se conversa, porque vos participás. |
| D5 | El presupuesto de las capas de E2. | Lo fijás en la conversación; la referencia es el núcleo del control, 20 tipos y unos 50 miembros. |
| D6 | Los umbrales de §8. | Los de §8, hasta tener una corrida que los corrija. |
| D7 | Si las autopsias quedan prendidas durante los experimentos. | Sí: es el ambiente en el que se trabajaría de verdad. Se cuentan aparte. |
| **D8** | Sobre qué rama y versión se construye. | En `main`, el único checkout de cerco. C0 es 0.18.0; C1 a C3, 0.19.0. |

En negrita, las que bloquean P1. **Tomadas el 2026-11-13 con la recomendación** (Fernando dijo «dale empezá» sin
elegir otra): D1 a D8 como están en la tabla. D8 cambió porque ya no hay worktrees de cerco.

---

## 12. Registro

**2026-11-13.** Plan escrito. Medida preliminar de `vista` (§1.3) con conteos estructurales de ast-grep, sin tocar
cerco-demo. Verificado ese día: `vista` no tiene camino de la vista hacia el dato (ningún archivo de `src/main` con
`DocumentListener`, `<input` ni `Editable`), así que la tarea B es nueva; cerco-demo está en 0.2.0 con la línea de
base vacía, las excepciones firmadas y el árbol limpio; cerco 0.17.3 está en `cerco-lectura`, también limpio.

**2026-11-13, P1: C0 en cerco 0.18.0.** `survey` imprime, además de lo que ya imprimía, una tabla de superficie por
módulo: tipos y miembros en cada alcance, S2 (`promised`, la parte de los miembros públicos que declara una
interfaz), S3 (cuántas firmas públicas dicen `String`, `Object`, un `boolean` como parámetro o una función) y S4
(`unnamed`, miembros públicos cuyo nombre simple no aparece en ninguna llamada, referencia, creación ni nombre del
proyecto, tests incluidos). Paquete `com.fpetrola.cerco.surface`; `SurfacesTest` y `SelfCheckTest` verdes.

Decisiones de medida tomadas al construirlo, a revisar en C1:

- Un método con `@Override` no es superficie de su tipo: la ofrece el supertipo. Tampoco el accesor explícito de un
  componente de record, los constructores de enum ni lo que está en clases locales o anónimas.
- Alcance `interfaces`: interfaces efectivamente públicas y sus miembros. Una interfaz de paquete no cuenta, porque
  no la ve otro módulo; por eso `vista-binding` da 0 donde §1.3 contaba 1, y `vista-swing` 1 donde contaba 3.
- La apertura es la efectiva: un método público de una clase de paquete es de paquete.
- Los constructores implícitos no cuentan. Las firmas usan los tipos como están escritos, sin resolver.
- S4 se calcula en el mismo paso y no con `unused-member`, que no mira métodos abstractos.

**E0, superficie de `vista`** (tipos/miembros; corrido sin tocar cerco-demo, con
`mvn com.fpetrola:cerco-maven-plugin:0.18.0:survey -Dcerco.rules=all`):

| módulo | interfaces | public | all | promised | String | Object | boolean | función | unnamed |
|---|---|---|---|---|---|---|---|---|---|
| vista-data | 4/8 | 10/26 | 10/26 | 31% | 9 | 0 | 0 | 4 | 0 |
| vista-view | 4/5 | 6/19 | 6/19 | 26% | 8 | 0 | 0 | 1 | 0 |
| vista-binding | 0/0 | 4/9 | 8/13 | 0% | 3 | 0 | 0 | 0 | 0 |
| vista-swing | 1/1 | 6/9 | 28/57 | 11% | 3 | 0 | 0 | 0 | 0 |
| vista-html | 1/1 | 4/9 | 19/51 | 11% | 7 | 0 | 0 | 1 | 0 |
| vista-demo | 0/0 | 7/21 | 8/22 | 0% | 7 | 0 | 0 | 0 | 2 |
| **total** | **10/15** | **37/93** | **79/188** | **16%** | **37** | **0** | **0** | **6** | **2** |

Lectura: los 37 tipos públicos coinciden con §1.3. En el núcleo (`data`, `view`, `binding`) la parte diseñada como
interfaz es entre 0% y 31%; en `swing` y `html` la superficie `all` es seis veces la pública, que es lo que E1b va a
sellar. 37 de las 93 firmas públicas dicen `String`.

**E0, reglas de hoy sobre `vista`** (2.390 líneas, 53 reglas y candidatas; 44 sin hallazgos):

| regla | hallazgos | por mil líneas | dónde |
|---|---|---|---|
| no-cast | 5 | 2,09 | swing |
| no-suppressed-warning | 4 | 1,67 | swing |
| no-type-keyed-map | 3 | 1,26 | swing |
| no-type-code | 1 | 0,42 | swing |
| flag-argument | 1 | 0,42 | html |
| overridden-to-nothing | 1 | 0,42 | html |
| dispatch-by-key | 1 | 0,42 | html |
| constant-argument | 1 | 0,42 | data |
| handed-out-whole | 1 | 0,42 | demo |
| **total** | **18** | **7,53** | |

Es el piso de Q1 para el control: lo que las reglas llegadas después de 0.2.0 le marcan a `vista`.

**2026-11-13, P2: C1 en cerco 0.19.0.** Implementado el diseño de §5.1 sin superficie pública fuera de él, salvo
un constructor `Rules(Settings, Path)` que no estaba en la tabla: el catálogo necesita la raíz para encontrar el
sello. `SealedSurfaceTest` cubre los criterios de §5 (adopción, rojo en la edición, renombre, quitar sin deber
autopsia, módulo nuevo vacío, sin la opción no hay sello); 171 tests verdes, `SelfCheckTest` incluido.

Probado sobre una copia descartable de cerco-demo con `<sealedSurface>interfaces</sealedSurface>`: el primer
`audit` sella los seis módulos (`vista-view` queda en nueve líneas legibles); agregar `edit(Runnable)` a `Textual`
da `[sealed-surface] 1 new` con el principio y las tres salidas, y la configuración sin firmar es un problema de
persona. Visto de paso: esa copia, con línea de base de 0.2.0, da 13 hallazgos nuevos de reglas posteriores en el
`audit`; revisarlo al preparar E1.

Falta para cerrar P2: C2 (la propuesta en `surface/pending/` y que `approve` la firme y agrande el sello). Hasta
entonces la única salida del rojo es quitar el miembro o editar el sello a mano, que el guard le niega al agente.

**2026-11-13, P2: C2 en cerco 0.20.0.** La propuesta, con las tres recomendaciones aceptadas: `approve` no agranda el
sello sin una propuesta completa; las firmadas se archivan en `surface/NNNN.md`, fuera de `.cerco/`; hay una sola
propuesta para todos los módulos. Superficie nueva, la acordada: `Proposal` (constructor, `settle`, `missing`,
`sign`), `Seal.grow`, `Seal.dropped` y `Outcome.awaitingSignature`.

- Una corrida completa reescribe `surface/pending/proposal.md`: cerco escribe la sección Grows y conserva Need,
  Considered, Removes y Contract. Si ya nada crece, la borra.
- Mientras le falte algo, los hallazgos son del agente y lo que falta sale como nota. Cuando está completa dejan de
  ser frescos: pasan a `awaitingSignature` y queda un problema para una persona, así que el hook deja terminar.
- `approve` corre el reactor entero, muestra la propuesta y lo que salió del sello desde la última firma, y con
  `yes` firma lo que el código ofrece en ese momento, no lo que dice el archivo.
- La autoaplicación obligó a dos cambios: `Proposal` lee las secciones por su cuenta, porque usar `Form` de las
  autopsias armaba un ciclo de paquetes, y `approve` sacó la parte de la superficie a un método aparte por largo.

`SealedSurfaceTest` suma propuesta incompleta, propuesta completa y firma, lista de lo que salió y miembro
considerado que no está sellado; 175 tests verdes. Probado en la copia de cerco-demo: el `audit` escribe la
propuesta con `Textual#edit(Runnable)` en Grows y cuatro notas de lo que falta. La firma con `approve` no se puede
probar sin una terminal; queda para Fernando.

**2026-11-14, P3: preparación de E1.** Los 13 hallazgos de la copia no eran un bug. Sin `sealedSurface`, 0.20.0 da
verde sobre cerco-demo con 13 exceptuados; con la opción, la configuración cambia, la firma del 2026-11-09 deja de
valer y con ella las excepciones. Prender el sello pide `approve`, como toda configuración.

En cerco-demo, `main` queda intacto y hay tres ramas:

- `e1-base`: plugin 0.20.0, hooks reinstalados contra ese jar, reglas nuevas adoptadas (`flag-argument` y
  `handed-out-whole` congelan un hallazgo cada una). `audit` verde.
- `e1-interfaces` (worktree `cerco-demo-e1-interfaces`): la base más `<sealedSurface>interfaces</sealedSurface>`;
  sello adoptado, 25 líneas (`binding` y `demo` vacíos). Para la tarea A y E1a.
- `e1-all` (worktree `cerco-demo-e1-all`): lo mismo con `all`; 267 líneas. Para E1b.

Los dos sellos coinciden con E0 (10/15 y 79/188). Falta que Fernando lea cada sello, anote cuánto tardó y firme
con `approve` en cada worktree; después, un worktree por corrida, que sale de esas ramas.

**2026-11-14, cerco 0.20.1: la primera propuesta sobre un sello vacío.** Lo reportó la sesión de cerco-demo-2: 72
interfaces en cuatro módulos, 351 hallazgos, y la propuesta no se podía completar porque Considered exigía un miembro
sellado que no existía. `approve` firmó la configuración y dejó la propuesta sin decir por qué, y el hook de Stop
retuvo al agente sin que le quedara nada por hacer. Arreglo: Considered acepta `none:` con un motivo de 40
caracteres o más cuando ningún módulo que crece tiene algo sellado; `approve` dice qué le falta a una propuesta
pendiente que no toma; la nota de propuesta inexistente nombra `mvn cerco:audit`, que es la corrida que la escribe.
Lección para el método: el formulario se diseñó pensando en enmiendas, y el diseño inicial (regla 4 de §3) es la
primera propuesta.

**2026-11-14, cerco 0.20.2: una corrida parcial achicaba el sello.** Lo reportó la sesión de cerco-demo-2: después
de editar dos archivos, `tui-terminal.txt` pasó de 117 líneas a 10, y las 107 restantes, sin cambios en el código,
fueron a `dropped.txt` y volvieron como crecimiento sin firmar. Causa: la regla achicaba cuando el proyecto «era
entero», y una corrida parcial (el `quick` del hook, el `check` de un módulo) lo es cuando recuerda la última
corrida completa (`Project.recall`). Arreglo: `Project.sawEveryFile()`, entero y no por recuerdo; sólo esa corrida
adopta o achica. `SealedSurfaceTest.aRunOverAFewFilesNeverShrinksTheSeal` falla con la lógica vieja. Lección: «el
proyecto entero» tenía dos sentidos en cerco, el de las lecturas (alcanza con recordar) y el de los veredictos que
borran (hay que haber visto todo), y el sello es el primero de los segundos.

**2026-11-14, cerco 0.20.3: el hook de Stop deja de dar vueltas.** En cerco-demo-2, con el sello vaciado por el bug
de 0.20.2, el rojo pedía completar una propuesta que habría vuelto a firmar como nuevos 107 miembros ya firmados. El
agente, con razón, no quiso, y el hook lo bloqueó unas diez veces con el mismo reporte. Es el resultado 3 de §2
(«el agente se traba»), aunque esta vez por un bug. Arreglo: el hook bloquea mientras el rojo cambia. Si el agente
vuelve de un bloqueo (`stop_hook_active`) con exactamente el mismo rojo, sin contar los tiempos, la sesión termina
y la persona ve el reporte como `systemMessage`. El costo: un agente que no hace nada después de un bloqueo puede
irse. Queda a la vista de la persona, y para P4 cuenta como una trabada.

**2026-11-14, cerco 0.21.0: `unpromised-member`.** Lo pidió Fernando desde cerco-demo-2: con `all`, el agente
igual metía API propia en clases (métodos de clases auxiliares que no implementan ninguna interfaz), la proponía y
el único freno era que Fernando la rechazara. En `tui-ansi` quedaron así firmados `Grid#put`, `Decoder#next` y
otros. Es el rodeo 3 de §7, y E1 lo habría medido. La regla, que corre siempre que el sello está prendido y no se
congela ni se exceptúa, marca:

- un método no privado de una clase, record o enum que no lleva `@Override` y que no declara, con el mismo nombre
  y la misma cantidad de parámetros, ninguna interfaz del proyecto entre sus supertipos;
- un componente de record que ninguna interfaz pide;
- un campo no privado;
- un constructor público, o un método estático no privado, que el sello no tiene: la puerta de entrada del módulo
  pasa por una propuesta.

Sin resolución de tipos, `@Override` alcanza: el compilador garantiza que sobrescribe algo, y si sobrescribe un
método inventado en una superclase del proyecto, el hallazgo queda en el original. Lo de que la interfaz «esté
sellada» lo cubre `sealed-surface`. Sobre una copia de cerco-demo-2 da 13 hallazgos, todos en `tui-ansi`, los que
la sesión había listado. Sus hallazgos no van a la propuesta: no se firman en el sello, se arreglan.

**2026-11-14, cerco 0.22.0: el contrato nombra lo que crece.** Pedido de Fernando para cerco-tdd (TDD de afuera
hacia adentro): cada firma de Grows tiene que estar nombrada por alguno de los tests de Contract (el tipo por su
nombre simple, un constructor por `new Tipo` o `Tipo::new`, otro miembro por su nombre; lectura léxica). Si no, la
propuesta queda incompleta y lo dice miembro por miembro. Alcance nuevo `promises`: interfaces de cualquier apertura,
sin clases ni constructores. Bug: un proyecto sin código no escribía `_root.txt` y su primer módulo se habría
adoptado sin propuesta; ahora la primera corrida completa lo sella vacío. Queda sin hacer: reconocer renombres.

**2026-11-14, cerco 0.22.1: `promises` sella los constructores públicos de clases públicas.** Pedido desde
cerco-tdd: el test de aceptación entra por `new Tipo()`, y una clase pública nueva con constructor público es una
entrada nueva al módulo que tiene que pasar por propuesta (y que Contract nombre). Clases package-private y
constructores no públicos siguen afuera.

