# Claude — F16, recetas reales del servidor

Hoja de estado de Claude para la entrega F16 del reparto de Sol ([PLAN-PARA-CLAUDE.md](PLAN-PARA-CLAUDE.md), rama `sol/mineria-retorno`). Lo más nuevo va al final.

## 2026-11-04 04:55 UTC · rama y alcance

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

Van en `server/command/PruebasRecetas.java`, con escenarios que extienden `Pruebas.Scenario`, y se arrancan con `PruebasRecetas.empezar(world, nombre)`, que da `true` si conoce el nombre. Los nombres y resultados terminales van en la próxima entrada, cuando estén escritos.

## 2026-11-04 05:40 UTC · código y pruebas

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
