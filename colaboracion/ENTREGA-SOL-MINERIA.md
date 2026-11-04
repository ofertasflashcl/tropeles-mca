# Entrega de Sol: regreso de minas y continuidad de rutas

**PR:** [#4](https://github.com/ofertasflashcl/tropeles-mca/pull/4). **Reparto con Claude:** [PLAN-PARA-CLAUDE.md](PLAN-PARA-CLAUDE.md).

## Resultado

Cabeza de código: `1892ea5fd483eb9a00e8f7f98e72489840105bc6`.
Jar probado por el workflow de PR: merge de prueba `4319508ec550630d342563871d80706be7df6acf`.
Árbol idéntico de ambas referencias, comprobado: `3a6ac723aeacf77b42d537b7577f9dc73ec4e714`.

- [Forge y servidor real, run 41776038491](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41776038491): **60/60**, cierre limpio; mismo mundo en otro proceso: **2/2**, cierre limpio. El SHA estampado en el jar se verificó en los dos arranques.
- [Builds Java/Forge/Fabric, run 41776038535](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41776038535): **verde**.
- Comprobaciones locales: sintaxis Java 17, tres tests del verificador, manifiestos de 60 y 2 resultados, `bash -n` y `git diff --check`. La compilación local no pudo descargar Gradle; no se presenta como build local aprobado.

## Cambios que se pueden revisar

1. **Rastro protegido.** Mina ya no descarta la mitad antigua ni la entrada. Presupuesto activo de 16.384 posiciones, margen de 64 para volver antes de llenarlo y motivo ROUTE. Al llegar a casa espera, en vez de repetir automáticamente una expedición que agotó ese presupuesto. Un rebase demasiado grande se rechaza antes de destruir el corredor anterior.
2. **Reinicio dentro de un recorrido.** Si está sobre una posición del corredor guardado, no se rechaza la mina solo porque la entrada y el frente queden lejos de sus pies.
3. **Búsqueda peatonal continuable.** WalkSearch conserva frontera y costes entre ticks. FOUND, NO_PATH, BUDGET_EXHAUSTED y SEARCH_LIMIT son distintos. El retorno normal con entrada conocida usa cuotas de hasta 512 expansiones; si le falta presupuesto continúa sin excavar. El límite de 131.072 celdas aplaza y avisa en el log; no declara una pared. Se eliminó la caché negativa aproximada de diez segundos de aPie. Las excepciones en esa consulta de retorno también aplazan, sin convertirlas en permiso para cavar.
4. **Transiciones fallidas.** Si el destino sigue abierto, se anota el paso dirigido durante 40 ticks y se deja una pausa de 40 ticks para comprobar de nuevo. No se veta globalmente el destino durante un minuto. Los buscadores de Recuerdos y la consulta de retorno respetan las transiciones. Un bloqueo físico mantiene su tratamiento separado. Las frases distinguen atasco de obstrucción comprobada.
5. **Coste de rotura.** Escaleras y pilares consultan una estimación con Hands.ticksToBreak y herramientas disponibles, sin equipar ni gastar al calcular. En el escenario: piedra a mano 150 ticks, con pico de hierro disponible 8. Los demás pesos de movimiento siguen siendo heurísticos; no se promete un óptimo global de tiempo.
6. **Salida del agua.** Un pilar que todavía flotaba con la cabeza fuera del agua podía dejar de impulsarse. Mantiene el salto de natación cuando los pies siguen en agua y no está apoyado. La prueba existente agua-pozo vuelve a pasar sin modificar su criterio de éxito.
7. **Cancelación y diagnóstico.** Se libera la búsqueda al terminar el trabajo. Buscar o esperar una revalidación cuenta como pausa deliberada para Atasco; la navegación anterior se detiene mientras piensa. debug incluye cuerpo y búsqueda pendiente.

No se modificaron los archivos de recetas/casas/mente asignados a Claude ni Hands/Cofres.

## Evidencia nueva

| Prueba | Qué hizo el código real |
|---|---|
| mina-rastro | Conservó y releyó 1.500 coordenadas y la entrada mediante Mina/NBT; al límite conservó el prefijo y pidió volver. |
| mina-rastro-reinicio | Otro proceso leyó esos 1.500 pasos del mismo mundo sin recrearlos. |
| ruta-presupuesto | Primero BUDGET_EXHAUSTED; después FOUND en 33 cuotas de un nodo; la desconexión real fue NO_PATH. |
| ruta-transicion | Rodeó una arista vetada; la celda permaneció abierta, la vuelta directa fue válida y el veto caducó. |
| coste-rotura | Midió 150 frente a 8 ticks; la estimación no equipó ni consumió la herramienta. |
| regreso-sin-pico | Bajó naturalmente, volvió al frente, perdió sus picos y regresó a casa recorriendo seis escalones y rompiendo cero bloques. Usó una cuota de búsqueda de un nodo por tick. |
| escalera-real, existente | Reutilizó la misma boca y los apoyos en varios viajes; cero bloques rotos junto a su escalera. |
| agua-pozo, existente | Salió en 90 ticks; aire mínimo 285/300, vida 20/20 y ocho bloques de apoyo consumidos. |

## Límites y siguiente etapa

- No se reprodujo el mundo/jar exacto de Benja ni su modpack. No se atribuye su fallo concreto exclusivamente al límite del rastro.
- La prueba de 1.500 posiciones verifica grabado/serialización/carga de datos preparados; no demuestra 1.500 pasos físicos en una mina natural. La locomoción real se demuestra en los escenarios separados, más cortos.
- La memoria activa sigue acotada: este incremento protege el corredor y retira la expedición, no implementa un archivo jerárquico de minas ilimitadas.
- Las consultas antiguas de Recuerdos y el wrapper findWalk conservan su API acotada; no todos los consumidores del mod tienen búsqueda incremental. El uso incremental se conectó al regreso HOME_UP con destino conocido.
- No hay todavía grafo completo de transiciones observadas con modos y reversibilidad, estimación completa de reserva de durabilidad, exploración de cuevas por fronteras, reforma de casas, pruebas de cliente real ni medición con muchos aldeanos.
- Las heurísticas de herramientas no sustituyen un plan completo de recursos; las maniobras y su coste deben seguir ampliándose con casos adversos.
- Un verde controlado no garantiza toda la jugabilidad. Tras revisión de Claude y decisión de Benja, la integración debe generar y comprobar su propio jar.

## Para Claude

Puedes hacer F16 en paralelo conforme al plan: Progreso/Taller, servicio nuevo de recetas y pruebas separadas. Conserva APIs públicas del minero; indica contratos nuevos y comandos/resultados para el registro central del CI. Este PR queda listo para tu revisión; revisa especialmente continuidad de búsqueda, capacidad/retirada del rastro, transición de agua y conservación de permisos. Las siguientes tareas del informe se toman con una reserva nueva, no editando ahora los mismos archivos desde otra rama.
