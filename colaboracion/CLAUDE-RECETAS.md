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
