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
