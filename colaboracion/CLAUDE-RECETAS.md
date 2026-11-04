# Claude — F16, recetas reales del servidor

Hoja de estado de Claude para la entrega F16 del reparto de Sol ([PLAN-PARA-CLAUDE.md](PLAN-PARA-CLAUDE.md), rama `sol/mineria-retorno`). Lo más nuevo va al final.

## 2026-11-04 04:48 UTC · rama y alcance

- **Rama:** `tropeles/claude-recetas`, desde la integración `553f37e8e`. El PR va hacia `tropeles/ojo-combate-mineria-1.20.1`; Sol lo revisa y Benja decide.
- **Archivos que toco:** `entity/ai/body/Progreso.java`, `entity/ai/obra/Taller.java`, el paquete nuevo `entity/ai/crafting/*`, el archivo nuevo `server/command/PruebasRecetas.java` y esta hoja.
- **No toco** `Pruebas.java`, `scripts/ci/*`, `Mina`, `Motor`, `Recuerdos`, `ProspectingTask` ni la mente.

### Primer alcance

1. `crafting/Recetario`: lee en cada consulta las recetas de crafteo vigentes del `RecipeManager` del servidor, con forma y sin forma. Las especiales (`isIgnoredInRecipeBook`) y los tipos de mods que no son forma/sin forma quedan fuera hasta tener adaptador. Dice cuál pide mesa de 3×3 (`!fits(2, 2)`). El índice se rehace al cambiar las recetas, por ejemplo con `/reload`, porque compara la identidad de un objeto receta.
2. `crafting/Planificador`: plan hacia atrás desde una demanda sobre una copia de la mochila; nunca la modifica. Lleva lotes, sobras, restos previstos, alternativas de ingredientes (tags reales vía `Ingredient.test`) y estación. Corta los ciclos: mientras fabrica X no gasta X ni vuelve a fabricar X, así trigo → heno → trigo no da nada. Tiene profundidad y presupuesto acotados.
3. `crafting/Ejecutor`: un crafteo real en el hilo del servidor.
   - Pone las pilas verdaderas de la mochila en una rejilla 2×2 o 3×3 y le pregunta al servidor (`getFirstMatch`).
   - Saca la salida con `craft` y los restos con `getRemainingStacks`, como los cubos del pastel.
   - Revisa antes de gastar que la salida quepa. Si no cabe, no gasta nada.
   - Después descuenta, entrega y deja en el suelo los restos que no quepan; nunca duplica ni pierde.
   - Resultado tipado: HECHO, FALTA, SIN_MESA, LLENO, CAMBIO o SIN_RECETA, con motivo y un log `[Tropeles] receta ...` que anota lo gastado y lo sacado.
4. `crafting/Reserva`: no gasta lo encantado ni lo que tiene nombre propio, y guarda cantidades que otro trabajo comprometió (por ejemplo los troncos de reserva de la obra). Solo trabaja con la mochila del aldeano: el heno puesto en el mundo nunca se gasta.
5. Conexión gradual:
   - `Progreso.craft` y la preparación de madera fabrican por el ejecutor. Las tablas, los palos y la mesa salen de la receta real de cada madera.
   - El pan sale del heno cuando falta trigo. Los encargos («hazme pan») aceptan el heno como trigo.
   - `Taller` hace por el ejecutor las piezas de la casa. Conserva sus chequeos y las firmas públicas que usa el `Constructor`.

### Contratos

No cambio ninguna firma pública de `Progreso` que use `ProspectingTask` o `Motor`: `pan`, `pedir`, `falta`, `queHacer`, `tiene`, `placeBeside`, `planksFor` y las demás siguen iguales.

Pendiente conocido: `pan()` acepta todavía una mesa en la mochila como si la pusiera un momento, para no cambiar el flujo de hambre de `ProspectingTask` sin ti. Con la mesa puesta (encargos, mesa propia de `atTable`, pruebas) la rejilla 3×3 es de verdad.

### Pruebas para que las registres (Sol)

Van en `server/command/PruebasRecetas.java`, con escenarios que extienden `Pruebas.Scenario`, y se arrancan con `PruebasRecetas.empezar(world, nombre)`, que da `true` si conoce el nombre. Los nombres y resultados terminales están en las entradas de abajo.

## 2026-11-04 05:02 UTC · código y pruebas

Código en `tropeles/claude-recetas`:

- Paquete nuevo `entity/ai/crafting/`: `Recetario`, `Planificador`, `Ejecutor`, `Reserva` y `Rejilla`.
- Cambios en `Progreso` (`craft`, `prepareWood`, `pan`, `falta` y la puerta de encargos de `nextGoal`) y en `Taller.hacer`.
- Las firmas públicas de `Progreso` no cambian.

Decisiones que conviene que revises:

- **Salida llena.** El ejecutor tiene dos modos. El estricto (`hacer(..., reserva)`) no gasta nada si la salida no cabe y da LLENO. El de siempre (`hacer(..., reserva, true)`) deja en el suelo lo que no cabe. `Progreso` y `Taller` usan el de siempre, porque un minero con la mochila llena tiene que poder hacer antorchas igual que antes. En ninguno de los dos modos se pierde ni se duplica nada.
- **Estación.** El plan solo usa recetas de 3×3 si hay mesa. Sin mesa, si la receta existe pero pide 3×3, el resultado es SIN_MESA y no FALTA.
- **Encargos.** Si la receta del servidor falla en la mesa durante un encargo (`atTable`), el encargo se deja con `faltaPedido`, para no reintentarlo cada segundo.
- **Log.** Cada crafteo deja `[Tropeles] receta <id>: <aldeano> gastó {...} y sacó N <item>[; le quedan ...]`. Un fallo deja `[Tropeles] <aldeano> no hace <item>: <motivo>`.

### Pruebas: `mca-prueba recetas`

`PruebasRecetas.empezar(world, which)` arranca un escenario si `which` es `"recetas"` y devuelve true. La arena está en x 10000..10196, z 34..46, y 99..104.

| Resultado | Qué demuestra |
|---|---|
| recetas-heno-pan | 1 heno + mesa al lado (vista real) → 3 panes, 0 trigo, 0 heno |
| recetas-un-pan | 1 heno + mesa, piden 1 pan → 1 pan y quedan 6 trigos |
| recetas-sin-mesa | sin mesa, mesa a 8 bloques o detrás de un muro → SIN_MESA y el heno intacto |
| recetas-lleno | mochila llena: con 2 heno da LLENO y no gasta; con 1 heno libera su casilla y da 9 trigos |
| recetas-restos | pastel: los 3 cubos de leche vuelven vacíos |
| recetas-ciclo | trigo de trigo imposible, heno de trigo en 1 paso, mochila vacía corta rápido, al pan le falta trigo |
| recetas-cambio | quitan el heno entre el plan y el trabajo: no sale nada; con el heno de vuelta, 3 panes |
| recetas-reserva | no usa su trigo con nombre; el heno guardado para otro trabajo da FALTA |
| recetas-progreso | `Progreso.pan` con heno y mesa en la mochila; «hazme pan» con heno vale y sin nada no |
| recetas-tablas | tablas de abedul y carmesí según la receta real del tronco; 2 tablas → 4 palos |

Para el registro central, Sol:

- `Pruebas`: `case "recetas" -> PruebasRecetas.empezar(world, which);`
- `commands.txt`: `mca-prueba recetas` antes de `await-checks`.
- `expected-checks.tsv`: diez filas `recetas<TAB>recetas-...`, una por resultado.

Para tener resultados terminales de verdad antes de pasártelas, abro una rama **temporal** `tropeles/claude-recetas-ci`, con esas tres líneas encima de mi rama, solo para que el CI las corra. No se integra y la borro cuando tú hagas el registro. Ahí anoto los resultados.

## 2026-11-04 05:30 UTC · resultados del CI

Código `22ea09656`, sin cambios desde la entrada anterior.

- [Mi rama, run 41776818561](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41776818561): **55/55** y reinicio **1/1**. Las pruebas de siempre pasan con `Progreso` y `Taller` fabricando por el ejecutor: `progreso`, `horno`, `hogar` y `explorar` hacen así sus picos, la mesa, el horno y el cofre.
- [Rama temporal con el registro, run 41776842910](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41776842910): **65/65** y reinicio **1/1**. Las diez de recetas pasaron a la primera.
- [Workflow del PR #5, run 41776917521](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41776917521): **54/55** en los dos intentos; solo falló `ia-red`. En los dos, para «¿me echas una mano y caminas a mi lado?» el modelo `mca` eligió `start-escort`; la tarea ESCORT de MCA se corta («Force-stopped chore because assigning player was not present») porque `getJobAssigner` busca al jugador con `world.getPlayerByUuid` y el jugador falso del CI no está en la lista del mundo. Es lo mismo de la bitácora de las 02:15. Este PR no toca la charla ni esa prueba. En las otras dos corridas el mismo modelo eligió `follow-player` y pasó.

### Resultados terminales

| Comando | Resultado | Lo que dijo el servidor |
|---|---|---|
| `mca-prueba recetas` | `recetas-heno-pan` | mesa al alcance true; HECHO; pan 3, trigo 0, heno 0 |
| | `recetas-un-pan` | HECHO; pan 1, trigo 6, heno 0 |
| | `recetas-sin-mesa` | sin mesa: SIN_MESA «Bread necesita una mesa de crafteo»; mesa a 8 bloques al alcance false; detrás de un muro al alcance false; heno 1, trigo 0 |
| | `recetas-lleno` | con 2 balas: LLENO (heno 2, trigo 0); con 1: HECHO (heno 0, trigo 9); tierra 1664 → 1664 |
| | `recetas-restos` | HECHO; pastel 1, cubos vacíos 3, con leche 0 |
| | `recetas-ciclo` | trigo de trigo false (2 pasos probados); heno de trigo true en 1 paso; trigo sin nada false; pan sin nada: le falta Wheat |
| | `recetas-cambio` | plan 2 pasos, mesa true; sin el heno: FALTA (trigo 0, pan 0); con el heno de vuelta: HECHO, pan 3 |
| | `recetas-reserva` | con trigo propio: HECHO, pan 1, trigo con nombre 3, trigo suelto 6; heno guardado: FALTA, heno 1 |
| | `recetas-progreso` | pan ahora true (pan 1, trigo 6); hazme pan con heno: puede; sin nada: «Me faltan 9 de trigo para eso.» |
| | `recetas-tablas` | abedul HECHO (4), carmesí HECHO (4), palos HECHO (4, quedan 6 tablas) |

Para registrarlas copia las tres líneas de la entrada de las 05:02. Están probadas tal cual en `tropeles/claude-recetas-ci` (commit `c23a9decf`, encima de mi rama): un `case` en `Pruebas`, una línea `mca-prueba recetas` antes de `await-checks 320` y diez filas en `expected-checks.tsv`. La arena (x 10000..10196, z 34..46) no se cruza con las tuyas de `PruebasNavegacion` (x 25200..25442) ni con `regreso-sin-pico` (x 24950). Cuando las registres, borro la rama temporal.

### Tu PR #4

Lo revisé y dejé [un comentario](https://github.com/ofertasflashcl/tropeles-mca/pull/4#issuecomment-6623294239) con dos hallazgos:

1. Con `breakingCost` en ticks y los demás pesos en unidades viejas, el modo escalera siempre hace pilar en roca.
2. La búsqueda hacia la boca recordada se reinicia cada tick si la entrada del rastro no tiene paso a pie.

Los dos PR no comparten archivos.

