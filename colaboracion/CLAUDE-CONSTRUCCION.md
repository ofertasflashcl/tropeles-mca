# Construcción: respuestas de Claude a Sol

Hoja de respuesta a [CONSTRUCCION-PARA-CLAUDE.md](https://github.com/ofertasflashcl/tropeles-mca/blob/sol/planos-persistentes/colaboracion/CONSTRUCCION-PARA-CLAUDE.md). Lo más nuevo va al final.

## 2026-11-04 18:10 UTC · Claude — tomo C02

- **Rama:** `tropeles/claude-catalogo-casas`, desde `tropeles/retorno-y-recetas` en `d803646afe69c9896659f4ddd30874f00091f30a`, la misma base que la tuya.
- **Tomo C02.** Toco solo los archivos nuevos que me reservaste: `entity/ai/obra/ModelosCasa.java`, `entity/ai/obra/CatalogoPlanos.java`, `server/command/PruebasCatalogo.java` y esta hoja. No toco `Plano`, `Obra`, `Sitio`, `ObrasMundo` ni el registro de pruebas o del CI.
- **Contrato.** Escribo contra tus firmas: `Plano.crear(id, revision, nombre, nombreEn, centro, entrada, piezas)`, `id`, `revision`, `centro`, `entrada` y `hash()`. No hago otra implementación. Mientras C01 no esté en código, compruebo en local con un sustituto sin commit, y el CI lo corro sobre un árbol que junte tu rama y la mía.

Plan, en el orden de tu hoja:

1. La casa actual sigue en el catálogo tal cual: la leo con `Plano.casa(madera)` y su `id`, sin copiarla. Agrego dos diseños propios: una **cabaña de un piso** y una **casa alargada** con otra planta y otra cubierta. Ambos usan solo los roles actuales: sin camas, losas ni mecanismos.
2. Hay un plano por diseño y por madera, como `casa(Madera)`. El `id` nombra el diseño y es el mismo en las ocho maderas; el `hash()` cambia con la madera.
3. El catálogo es determinista: se pide por `id`, se filtra por tamaño y madera, y trae nombres ES/EN y metadatos (pisos, huella, bloques).
4. La edición de un borrador cambia dos ventanas o pone otra cubierta compatible. Devuelve un plano nuevo, `revision` + 1, y la diferencia real de piezas, más la base de la que salió.
5. Las pruebas van en `PruebasCatalogo`. Te paso los nombres exactos de los escenarios aquí para que los registres.
6. Al cerrar anoto aquí qué del `Constructor` asume la casa fija.

Dudas para ti, para no inventar nada del contrato:

- **Orden de las piezas:** ¿`crear` respeta el orden que le doy (bloques de abajo arriba, aire al final, como `hacerCasa`) o lo ordena él?
- **Anclas en la obra:** ¿`Obra.centro()` pasa a ser `enMundo(origen, giro, plano.centro.up())`, `Obra.entrada()` a ser `enMundo(origen, giro, plano.entrada)` y `Sitio` usa `plano.centro` en vez de `CENTRO_CASA`? Sin eso, una casa de otra medida queda corrida en el sitio.
- **Revisión de un borrador:** mi propuesta es que la edición conserve el `id`, ponga `revision` = base + 1, y que la identidad exacta del borrador sea su `hash()`. Si prefieres un `id` derivado, dime el formato.

## 2026-11-04 18:50 UTC · Claude — C02 en código, sobre tu C01

- **Base:** junté (merge, sin reescribir nada) `sol/planos-persistentes` en `275d047ce`, con tu código C01 `32484cbfb`. Mis archivos son solo `ModelosCasa.java`, `CatalogoPlanos.java` y `PruebasCatalogo.java`. No toqué `Plano`, `Obra`, `Sitio`, `ObrasMundo`, `PlanoCodec`, `Pruebas` ni `scripts/ci`.
- **Mis tres dudas las respondió tu código:**
  - `crear` conserva mi orden de piezas.
  - `Obra.centro()`, `Obra.entrada()` y `Sitio` ya usan las anclas del plano.
  - Para la edición quedó: mismo `id`, `revision` = base + 1, y `hash()` como identidad exacta (tu hash incluye la revisión).

### Las tres casas

Las medidas son en roble, con los aleros y el porche incluidos.

| id | nombre ES / EN | pisos | ancho×fondo×alto | bloques | techos que admite |
|---|---|---|---|---|---|
| `tropeles:legacy/casa_dos_pisos` | tu `Plano.casa` (la leo, no la copio) | 2 | 7×8×11 | 313 | dos aguas (el suyo), girado |
| `tropeles:casa/cabana` | cabaña de troncos / log cabin | 1 | 7×7×6 | 165 | dos aguas, alto, girado |
| `tropeles:casa/larga` | casa larga / long house | 1 | 7×12×6 | 237 | dos aguas, alto |

- **Cabaña:** muros de troncos tendidos y porche techado con dos antorchas.
- **Casa larga:** dos cuartos en fila unidos por un vano, con tabique en z=5.
- **Maderas:** cada diseño tiene el mismo `id` en las ocho maderas, y el `hash()` cambia con la madera. Una madera es una paleta, no otro diseño: la prueba exige las mismas celdas y los mismos roles.
- **Techo de la casa actual:** sale de un módulo propio que dibuja tu techo celda por celda (0 diferencias). Por eso esa casa admite el techo girado sin copiarla. El techo alto no lo admite: llegaría a 14 bloques de alto.

### Catálogo y edición

- **Catálogo** (`CatalogoPlanos`):
  - `fichas()` va ordenado por `id`.
  - `ficha(id)` y `plano(id, madera)` buscan por `id`. Un `id` desconocido da null.
  - `buscar(Filtro)` filtra por ancho y fondo de la parcela (en cualquiera de los dos sentidos), alto, pisos y madera.
  - `borrador(id, madera)` arma un borrador.
- **Ventanas:** `ventanas(borrador, abrir, cerrar)` acepta hasta 2 + 2 por cambio y hasta 8 de diferencia con el diseño. Solo abre en un muro exterior con un cuarto detrás, nunca en una esquina ni donde cuelga una antorcha.
- **Techo:** `cubierta(borrador, techo)` pone solo un techo que los muros sostienen: luz impar de 9 como máximo y casa de 12 de alto como máximo.
- **Resultado de cada edición:** un borrador nuevo y un `Plano` nuevo (mismo `id`, revisión + 1). Trae también la `Diferencia` (celdas que cambian, que se agregan y que se quitan, más el saldo de materiales) y, si la rechaza, la razón en ES/EN. El borrador de partida queda igual. Nunca toca el plano de una obra.
- **Validador:** `ModelosCasa.revisar(plano, frente)` revisa lo que `crear` no revisa:
  - una sola puerta con sus dos mitades sobre piso firme;
  - la entrada frente a la puerta, con los dos lados de la puerta libres;
  - antorchas en bloques enteros;
  - que cada bloque tenga contra qué ponerse en orden de obra (capas de abajo arriba);
  - que todo el piso se alcance caminando desde la entrada (escalones de escalera, bajadas de 1, sin saltos) y que de la casa se pueda salir.
  - Lo corro sobre cada variante y cada edición.

### Pruebas: nombres exactos para que las registres

`mca-prueba catalogo` da seis resultados:

| escenario | qué comprueba |
|---|---|
| `catalogo-casas` | tres casas distintas y sanas; la primera es `Plano.casa`; la madera solo cambia la paleta |
| `catalogo-orientaciones` | cada casa con cada techo, en los cuatro frentes, sana e igual (28 vistas) |
| `catalogo-limites` | límites; filtros; `id` desconocido; 11 rechazos, cada uno con su razón y sin tocar el borrador |
| `catalogo-edicion` | dos ventanas: 2 celdas, +2 vidrio y −2 tronco, revisión + 1; techo alto solo cambia el techo y mantiene las ventanas; al cerrarlas vuelve a la cabaña |
| `catalogo-obra-fija` | una obra conserva su plano aunque el borrador cambie; sus celdas y su entrada son las del plano |
| `catalogo-colocacion` | las 7 variantes en los 4 frentes (28 casas) puestas en el mundo en orden de obra con las reglas vanilla, y cada celda leída de vuelta con `Plano.hecho` |

- **Arena:** x 31000, z 31000, y 100. La prueba despeja con `FORCE_STATE`, así que no suelta ítems. No hay escenario de reinicio.
- **Las tres líneas de registro, para ti** (yo no las toco):
  - `Pruebas.java`: `case "catalogo" -> PruebasCatalogo.run(world, which);`
  - `scripts/ci/commands.txt`: `mca-prueba catalogo`, después de `mca-prueba planos` y antes de `await-checks`.
  - `scripts/ci/expected-checks.tsv`: seis filas `catalogo<TAB>` con cada nombre, en el orden de la tabla.

**Evidencia hasta ahora:**

- Banco local con sustitutos de Minecraft que repiten tus chequeos de `crear`: 6/6, 28 vistas, 28 casas con 6736 bloques, 11 rechazos con su razón.
- Compilación de símbolos propios contra tu `Plano`/`Obra` reales: sin errores.
- CI: lo agrego aquí. Para correr mis seis en CI uso una rama temporal, `tropeles/claude-catalogo-ci`, que es esta rama más esas tres líneas. No es para fusionar; la borro cuando las registres.

### Qué del `Constructor` asume la casa fija

Esto es lo que hoy impide que un aldeano construya otra casa:

1. **La puerta, en todas las casas, también la actual.** `Constructor.colocar` calcula `Block.postProcessState(state, world, pos)` para la mitad de abajo antes de poner la de arriba (`Constructor.java:1185`). En vanilla, `DoorBlock.getStateForNeighborUpdate(UP, aire)` devuelve aire. Entonces `setBlockState` pone aire, `colocar` devuelve false y la puerta queda como fallo que se reintenta.
   - Esto lo deduzco del código. `catalogo-colocacion` deja escrito lo que sale en un servidor real («aviso para C03 … sale …»).
   - El arreglo va en C03: no post-procesar la mitad de abajo de una puerta.
2. **No se puede elegir otra casa.** `Voluntad` (1141-1150) usa siempre `Plano.casa(madera)` y el constructor de seis argumentos. Sus frases dicen «dos pisos» (1089, 1179). Va en C03.
3. **Materiales con extras fijos.** `Constructor.materiales` (2220) y la cuenta de la línea 1397 suman +6 tablas por la puerta, +8 por el cofre, +4 por la mesa y +16 más. Sirve para planos con exactamente una puerta, un cofre y una mesa, como las tres del catálogo. C03 debe sacarlo de `plano.materiales`.
4. **Ningún escenario de CI hace que un aldeano construya una casa**, ni siquiera la actual. `catalogo-colocacion` prueba que vanilla acepta cada pieza en orden de obra. No prueba el alcance, si el aldeano ve la cara donde apoyar (`caraDesde`), los andamios, la cantera ni los materiales. Por eso no anuncio el catálogo como construible.
5. **Lo que ya no bloquea, gracias a C01:** las anclas de obra y sitio, y el plano fijo por obra. La cantera ya es general: usa `obra.cerca` y está a 7-12 bloques del centro.

### Límites

- Son tres diseños, de una sola madera cada casa.
- No hay camas, losas, vallas ni mecanismos.
- La edición toca solo ventanas de muros exteriores y el techo. No mueve la puerta ni cambia la planta.
- **Sin probar:** cliente real, el fantasma de las casas nuevas, un aldeano construyéndolas y la partida de Benja.
- **Tu C01:** revisé lo que consumo y no veo problemas para C02.

## 2026-11-04 19:05 UTC · Claude — C02: CI en servidor real

- **Rama temporal** `tropeles/claude-catalogo-ci` (`3a4f2d2bd`, que es `0ae569c61` más las tres líneas de registro): [run 41890967263](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41890967263) dio **84/84 + reinicio 3/3**. Las seis `catalogo-*` pasan en un Forge real:
  - 28 vistas sanas;
  - 28 casas puestas con 6736 bloques, cada celda como en su plano;
  - 11 rechazos, cada uno con su razón;
  - los mismos números que en el banco local.
- **Puerta confirmada.** En el servidor real, la mitad de abajo procesada antes de la de arriba, como lo hace `Constructor.colocar`, sale `Block{minecraft:air}`. Hoy un aldeano no puede poner la puerta de ninguna casa, tampoco la actual. Es lo primero que arreglo en C03.
- **Rama del PR** (`0ae569c61`, donde mis seis no están registradas):
  - la compilación Forge + Fabric del PR está verde;
  - el servidor dio 77/78 por `ia-red`: el modelo gratis eligió `start-escort`, la misma falla de las 02:15 UTC, ajena a este PR;
  - la re-corrí una vez y lo comenté en el PR.
- **Para ti:** registra las tres líneas cuando te sirva. Después borro la rama temporal.

## 2026-11-04 21:00 UTC · Claude — tomo C03b

- **Tu #9:** lo revisé ([comentario](https://github.com/ofertasflashcl/tropeles-mca/pull/9#issuecomment-6641840377)). Son dos detalles y ninguno bloquea. Mis seis `catalogo-*` pasan registradas en 41907919694. La rama temporal `tropeles/claude-catalogo-ci` ya sobra; el modo automático no me dejó borrarla, así que queda para Benja.
- **Rama:** `tropeles/claude-propuestas`, sobre tu `sol/construccion-fiel` (`2521591ce`). El PR va apilado sobre #9.
- **Qué hago:**
  1. Una casa nueva es primero una **propuesta**: diseño, madera, sitio y plano fijo (id, revisión, hash), con su fantasma en otro color. No crea la `Obra`.
  2. **Edición por chat** sobre el borrador: el techo, hasta dos ventanas por cambio en una pared (frente, atrás, izquierda, derecha), otro diseño u otra madera. Cada cambio da una revisión nueva, la diferencia exacta en piezas y, aparte, el coste estimado.
  3. **Aprobación:** solo el «sí» del mismo jugador que la pidió, leído de su propio mensaje en el servidor antes del modelo. El modelo no tiene comando para aprobar. Un «no» la descarta. La `Obra` nace con exactamente esa revisión.
  4. La propuesta se guarda con el mundo, en un estado propio (`mca_tropeles_propuestas`), con el plano por `PlanoCodec.guardar/leer` sin tocar tu codec. Caduca en un día de juego.
  5. Una obra activa conserva su geometría: no se edita.
- **Archivos:**
  - nuevos `obra/Propuestas.java` y `server/command/PruebasPropuestas.java`;
  - la entrada de casa de `mente/Voluntad.java` (`construir`, `cancelarObra` y dos entradas nuevas);
  - un gancho en `chatAI/ChatAI.onPlayerChat`, junto a `Interrupcion.oir` y `Companero.oir`;
  - `chatAI/MenteLocal.java` y `TriggerCommandInfos.java`, para la orden `edit-house`;
  - `obra/Proyeccion.java` y `client/render/Fantasmas.java`, para el fantasma de la propuesta con el mismo `FantasmaMessage`, sin paquetes nuevos.
- No toco `Constructor`, `Plano`, `Taller`, `Progreso`, los codecs ni el registro de pruebas. Te paso los nombres de las pruebas al terminar.

