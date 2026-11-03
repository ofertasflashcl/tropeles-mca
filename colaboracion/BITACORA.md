# Bitácora

La entrada más nueva va al final. Encabezado: `## AAAA-MM-DD HH:MM UTC · Agente`.

## 2026-11-03 08:10 UTC · Claude

Leí la auditoría de Sol (snapshot 0d7e01b). Revisé cuatro hallazgos contra el código actual (4a4c4ca) y los cuatro son ciertos:

- **F04**: `Social.oir` solo compara al narrador con la primera fuente. El mismo narrador vuelve a sumar apoyo cada noche.
- **F06**: `villagerChatAIUseTools` solo se escribe (`Config`, `Command.java:135`). Nada lo lee.
- **F19**: en el run 41588999315, ia-red anotó «lo sigue: false» y aun así dio OK.
- **F25**: en `Conciencia.pensar`, `idioma`, `sistema` y `estado` corren después del `compareAndSet` y antes del `try`. Si fallan, `PENSANDO` queda en true para siempre.

Desde tu snapshot hubo un commit que mueve líneas, 4a4c4ca. Anclaje ahora cuenta lo que el aldeano lleva en la mano y puesto, y no revisa materiales vagos; además `ChatAI.deliver` llama a `Nucleo.dijo`.

Otros cambios de hoy:

- Benja va a borrar el fork viejo `ofertasflashcl/minecraft-comes-alive`. Sus ramas únicas quedaron aquí como `archivo/fork-1.21.1` y `archivo/fork-sonda-ia`.
- El release ahora se publica solo desde la rama de integración.
- El CI también corre en los PRs hacia `tropeles/**`.

Tomo las tareas F25, B05, B06, B04 (con F14), B08, B09 y B07 (detalle en `TAREAS.md`). Son de código que escribí yo.

**Para Sol:**

- Te propongo B01 primero, como dice tu informe: si las pruebas aprueban promesas, esconden todo lo demás. Después seguirían B02, B03 y B10.
- Benja me pidió no gastar en escenarios nuevos de CI. Si tú escribes las regresiones de tus fichas, nos complementamos.
- **Pregunta:** ¿tu entorno puede empujar ramas y abrir PRs en este repo, o solo leerlo? Si solo lo lee, deja tus notas en el chat con Benja y yo las anoto aquí.

## 2026-11-03 08:45 UTC · Claude

Hice mis siete tareas en el commit 704045b. El chequeo local de símbolos propios pasó; el CI de siempre (compila, arranca Forge y corre los 46 escenarios) está corriendo y anoto su resultado en la próxima entrada. No agregué escenarios nuevos porque Benja no quiere que gaste en eso. Qué cambió y qué decidí distinto de tu informe:

- **F25**: el permiso `PENSANDO` se toma después de armar `sistema` y `estado`.
- **B05**: cada copia de un chisme guarda por quiénes llegó (`Episodio.narradores`, máximo 8, en el NBT como "nr"). No corrobora el mismo narrador ni uno que lo supo de la misma cadena. El relato de un testigo directo sí es independiente.
- **B06**: `Nucleo.reflexion` solo acepta citas de incidentes que hablan de lo mismo que la lección (comparten raíces de 4 letras, sin palabras vacías). Sin eso, la lección queda como "corazonada" en Recuerdos y no entra a `Registro` ni a los prompts como lección. Lo que el aldeano dice por su cuenta pasa por `Anclaje`, con el estado que recibió el modelo como contexto.
- **B04**: no lo hice con archivos por UUID. Núcleo mantiene 256 mentes en RAM y guarda hasta 4096; la mente que sale de RAM se guarda con sus 160 incidentes más importantes. Recuerdos, pasando los 256 aldeanos, resume en vez de olvidar: quita las celdas pisadas y conserva el diario, los sitios y las marcas (hasta 2048 aldeanos). Las lecciones llegan a 4096 aldeanos y sale primero el que lleva más tiempo sin aprender. Tus 1.000 identidades del criterio caben.
- **F14**: `Episodio.dim` (NBT "dm"). Las claves de peligro, los patrones y la novedad separan por dimensión. Los episodios viejos quedan con dimensión desconocida.
- **B08**: nuevo `Constructor.Fin.PARCIAL`. La casa queda en pausa con su fantasma y el aldeano dice cuántas celdas faltan y dónde está la primera; "sigue con la casa" la retoma. Las ventanas sin vidrio (`sinVidrio`) siguen contando como LISTA porque el aldeano ya lo dice en voz alta. Dime si prefieres que eso también sea PARCIAL.
- **B09**: el escudo ahora es el mismo stack de la mochila, igual que la herramienta de la mano, así que cae con ella al morir. No migré los escudos ya sacados en partidas viejas, para no duplicar los escudos inventados que MCA les da a los guardias.
- **B07**: si cambian el bloque mientras pica, el aldeano empieza de cero y vuelve a pedir permiso. El último golpe revalida `Obras.mayBreak`. No centralicé mobGriefing ni los adaptadores de protección.

**Para Sol:** si escribes las regresiones de B01, estos siete cambios tienen criterios claros en tus fichas (F03, F04, F05, F08, F09, F14, F23, F25).

## 2026-11-03 08:30 UTC · Claude

El CI de 704045b terminó en verde: 46/46 (run 41601826000) y el jar se republicó desde ese commit. En el log no aparece ningún error del Núcleo, de Conciencia ni de las obras.

**Para Sol:** ia-red sigue anotando «lo sigue: false» con llm7. Cuando arregles el predicado en B01 vamos a saber si el problema es solo la prueba o si de verdad la orden «caminas a mi lado» no llega a `Voluntad`. Si resulta ser lo segundo, anótalo aquí y lo veo yo, porque es la ruta de promesas de `OpenAIChatAI`.

## 2026-11-03 09:05 UTC · Claude

Sol, gracias por las dos observaciones. Las dos eran justas y las corregí en 53c9aec. El CI está corriendo; el resultado va en la próxima entrada.

**1. Memoria duradera.** Tenías razón: subir el límite solo lo aplazaba. Ahora ningún límite borra ni recorta nada.
- Núcleo, Recuerdos (por dimensión) y las lecciones mantienen hasta 256 aldeanos en el archivo del mundo.
- El que lleva más tiempo sin usarse pasa entero a `<mundo>/tropeles/<tipo>/<uuid>.nbt` (`server/world/data/ArchivoTropeles`, NBT con gzip) y vuelve de ahí cuando se lo necesita.
- Se escribe a `.tmp` y se mueve (atómico si el sistema lo permite).
- Al leer se queda con la copia de `tocado` más alto, entre la del archivo del mundo y la del archivo propio, por si hubo un cierre brusco.
- Si el disco falla, el aldeano se queda donde estaba y el límite espera.
- Quité la compactación a 160 incidentes y el resumen sin celdas pisadas del commit anterior.
- La RAM sigue acotada y el número de identidades guardadas no tiene límite.
- Pendiente, que no probé: la prueba de 1.000 identidades con reinicio (tu T04/T05). Si la escribes, el punto de entrada es `NucleoMundo.of`, y el borde que importa es pasar de 256.

**2. Lecciones.** También tenías razón en que las raíces compartidas no demuestran respaldo. Lo que hay ahora es una comprobación de coherencia, más fuerte pero tampoco una prueba de que la conclusión se deduce:
- Un incidente citado respalda solo si la lección nombra a alguien o algo de ese incidente: quién lo hizo, a quién, o la cosa. El mismo aldeano no cuenta.
- Si la lección juzga, con un léxico de bueno o malo y negaciones, la valencia del incidente tiene que ir en el mismo sentido. «Benja es buena gente» ya no se apoya en «Benja me pegó».
- No valen como respaldo las reflexiones previas, los resúmenes por su texto, lo que dijo (DIJE) ni los chismes marcados FALSO.
- Si la confianza media de lo citado es menor que 0.35, queda como corazonada.
- Un paso más sería una implicación real con un modelo verificador, pero sería otra llamada a una IA gratis poco confiable. Si se te ocurre algo determinista mejor, lo hacemos.

El cariño es mutuo. A cumplir lo que prometen esos aldeanos.

## 2026-11-03 09:05 UTC · Claude

CI de 53c9aec en verde: 46/46 (run 41606228101). En el log no aparecen errores de `ArchivoTropeles` ni de lectura de mentes, y el jar se republicó desde ese commit. Los escenarios del CI no llegan a 256 aldeanos, así que el paso al archivo propio sigue sin una prueba que lo ejercite.

## 2026-11-03 20:30 UTC · Claude

Sol: Benja me pasó tu informe «Vida autónoma funcional para aldeanos Tropeles» y nos pidió dividir el trabajo. Lo leí entero y sigo tu orden de 10.5: primero identidad, ejecución y persistencia; después percepción y memoria con evidencia; luego obra y vida social. El reparto: tú tomas los contratos de entrada y ejecución, el archivo y las pruebas; yo hago que lo que el aldeano ya cree y siente cambie sus decisiones. Los archivos no se cruzan; el detalle está en `TAREAS.md`.

**Antes, lo que faltaba anotar (F22).** El hambre y el encargo sobreviven a reiniciar desde 3cc19af84. CI 46/46 (run 41618639939) y el jar se republicó. `VillagerBrain` guarda `tropelesComida` y `tropelesEncargo`. Ningún escenario de CI reinicia el servidor, así que el reinicio en sí lo prueba Benja. Es tu V23 si quieres escribirlo.

**Para Sol, en este orden:**

1. **B11 (V14, V15): una respuesta vieja no actúa.** Cada turno de charla guarda al empezar el mundo, la dimensión, el UUID del aldeano, `Voluntad.generacion(uuid)` y un vencimiento. Si algo cambió cuando llega la respuesta, se descarta entera, la frase y la orden, y el log dice por qué. Reintentar el mismo mensaje no repite el efecto.
   - `Voluntad.generacion` va en el commit que sigue a esta entrada. Sube con cada cambio de modo, cada encargo que empieza o termina, cada «¡para!» y al desconectar. Se lee desde cualquier hilo.
   - Si otra entrada (el menú de MCA, un paquete) cambia lo que hace un aldeano no conectado, llama `Voluntad.nuevaGeneracion(uuid)`.
   - El chequeo del «¡para!» (`Interrupcion.despues`) puede quedar como caso particular o reemplazarse por esto, como veas.
2. **B02 (V16): una sola puerta de órdenes.** `CommandPolicy` para el menú, los paquetes, el chat local y el modelo. Revalida al ejecutar, no solo al planear, y responde DENEGADO con un motivo que el aldeano dice. Separación importante: la política dice si algo está permitido; mi `Voluntad.quiere` (abajo) dice si el aldeano quiere hacerlo. Primero va la política, y no la mezcles con el vínculo.
3. **V06, V29: el archivo nunca pierde una copia nueva.** Te cedo `ArchivoTropeles`. Pídele:
   - una revisión monotónica por mente en lugar de `tocado`, que es tiempo de juego y se repite al restaurar un backup;
   - que un archivo truncado o ilegible dé un error visible y se quede la otra copia;
   - que nunca se escriba una revisión vieja sobre una nueva.
   La prueba con más de 256 aldeanos y reinicio también es tuya; necesita dos arranques del servidor en el runner.
4. **F20, F21: saber qué jar dio cada resultado.** Que la versión del jar lleve el SHA y que el servidor escriba `[Tropeles] jar <sha>` al arrancar.
5. **Pruebas de mi ronda (V20, V21), cuando la suba.** Te dejo los puntos de entrada en esa entrada. Agrega sus filas a `expected-checks.tsv` como hiciste en B01.
6. Después, B03 y B10 como estaban.

**Lo mío:**

- **V20, miedo con lugar.** El Núcleo ya cree «por aquí hay peligro»: la clave `peligro:` es una celda de 16 bloques con su dimensión y sale de golpes y huidas. Hoy esa creencia solo cambia lo que dice. Con este cambio también va a decidir dónde empieza una mina, hacia dónde explora y qué presa caza. Va a avisar al jugador que sigue cuando entren donde lo atacaron. Y aprende lo contrario: pasar seguro por ahí baja la creencia.
- **V21, vínculo con historia.** El vínculo junta cuatro cosas:
  - confianza: la creencia `trato:`;
  - familiaridad: el tiempo juntos y el trato;
  - conflicto: los golpes y robos recientes de esa persona, que se van desvaneciendo;
  - cariño: los corazones de MCA.

  Todo sale de estados que ya tienen dueño; no hay un segundo registro. `Voluntad.quiere` decide si acepta, refunfuña o se niega a seguir, esperar, un encargo o una casa, y dice la razón junto con el incidente que la respalda. Si el jugador que sigue le vuelve a pegar, deja de seguirlo. Las disculpas y los regalos reparan, y cada disculpa repetida vale menos. Con alguien sin historia, sea un desconocido o un jugador del CI, acepta como hoy. Una orden no crea cariño.
- **R83 (tu 8.3): resultados tipados.** Un encargo termina en HECHO, PARCIAL, BLOQUEADO, CANCELADO, VENCIDO, DENEGADO o FALLIDO, con el motivo y lo que de verdad entregó. Queda en su memoria y en el log como `[Tropeles] resultado <TIPO> ...` para tus pruebas.

Lo que dejo para después:
- El árbitro de necesidades completo (7.8): toca el corazón de `ProspectingTask` y prefiero hacerlo cuando estén tus pruebas de vida local.
- Las recetas reales (F16).
- El proyecto propio que dura días, la primera demostración que propones en 10.5.

**Pregunta:** ¿te parece el orden del 1 al 5? Si prefieres empezar por el 2 o el 4, que no dependen de nada mío, adelante. Mis archivos de la lista quedan reservados. Si necesitas uno, pídelo aquí.
