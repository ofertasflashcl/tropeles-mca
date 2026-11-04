# Claude: reparto operativo con Sol — minería y autonomía

## Encargo de Benja · 4 de noviembre de 2026

Benja autorizó implementar usando los dos informes que también te enviará: `tropeles-investigacion-autonomia.md` y `tropeles-guia-mineria-memoria-y-autonomia.md`. Primero debemos dividir archivos para trabajar en paralelo. Esta hoja concreta y ajusta las propuestas de ambos; no pretende implementar todos sus capítulos en un solo cambio.

Base leída: `553f37e8e07a6c9b0c3694a159fc2839bbeb3221` de `tropeles/ojo-combate-mineria-1.20.1`. PR #2 y su revisión ya están integrados; el informe general es anterior y los presenta pendientes. No deshacer sus permisos, generaciones, codecs, atribución o revisiones de memoria.

## Sol está haciendo: regreso de minas

Rama: `sol/mineria-retorno`; PR hacia la integración, revisado por Claude y decidido por Benja.

Archivos reservados a Sol en esta primera entrega, bajo `common/src/main/java/net/mca/`:

- `entity/ai/body/Mina.java`
- `entity/ai/body/Motor.java`
- `entity/ai/mente/Recuerdos.java`
- `entity/ai/brain/tasks/chore/ProspectingTask.java`
- Nuevos módulos de navegación que cree Sol.
- `server/command/Pruebas.java`, `scripts/ci/*` y pruebas de navegación nuevas.

Objetivo inmediato: conservar la entrada y la conexión del recorrido, diferenciar búsqueda incompleta de imposibilidad, no vetar una celda abierta por un simple fallo de transición y usar costes de rotura adecuados cuando sea aplicable. Probar descenso/regreso real, viajes repetidos, rutas largas, persistencia y fallos de paso; documentar qué se pudo reproducir. No prometer que un límite de 1.024 explica por sí solo la partida de Benja.

Sol no editará `Progreso`, `Taller`, `Hands`, `Cofres`, el generador de casas ni la mente de Claude durante esta entrega. Leerá sus APIs actuales. Mantener el código de cuerpo bajo un solo dueño permite una mejora integrada del retorno, en lugar de dividir una misma máquina de estados entre dos ramas.

## Claude: comienza ahora con F16, recetas del servidor

Tu primera entrega independiente es un adaptador compartido de recetas reales y su conexión gradual a progresión/construcción. La prioridad demostrable es **heno → nueve trigos → tres panes**, con mesa de 3×3 real para el pan. No hace falta esperar el PR de minería.

Archivos reservados a Claude para esta entrega:

- `entity/ai/body/Progreso.java`
- `entity/ai/obra/Taller.java`
- Paquete nuevo `entity/ai/crafting/*` para índice/planificación/ejecución de recetas, si lo consideras adecuado.
- Pruebas nuevas en archivos separados de `Pruebas.java` (por ejemplo `server/command/PruebasRecetas.java`); informa a Sol de los casos/comandos para que él haga el registro central y el manifiesto del CI.
- `colaboracion/CLAUDE-RECETAS.md` para estado, contratos y preguntas sin competir por la bitácora durante los cambios.

Conserva las firmas públicas de Progreso que consume ProspectingTask/Motor. No rehagas simultáneamente selección de herramientas, retorno, fases, hambre ni reserva del minero. Si el crafteo necesita cambiar un contrato público, documenta primero la propuesta para que Sol adapte su rama; no rompas consumidores durante el trabajo paralelo.

### Requisitos de tu PR

1. Consultar `RecipeManager` vigente, usando los mappings 1.20.1 de este repo, no recetas inventadas por el modelo. Unificar gradualmente los dos catálogos manuales detrás del servicio.
2. Comenzar por crafting vanilla con/sin patrón. Para cocción conservar lo que funciona y añadir soporte genérico solo con pruebas; máquinas de mods y recetas fuera de datapacks necesitan adaptadores explícitos.
3. Planificar demanda y lotes, alternativas de ingredientes, estación, salida y restos; detectar ciclos de compresión. Planificar no modifica el inventario.
4. Ejecutar en el hilo del servidor: revalidar recursos/receta/estación antes del efecto; conservar cantidades, datos de objetos y recipientes; manejar salida llena y cancelación sin duplicar.
5. Reaccionar a cambios/reload de recetas. No hacer de JEI una dependencia obligatoria ni afirmar compatibilidad universal con máquinas de otros mods.
6. Respetar propiedad y reservas: no consumir heno decorativo ajeno ni el último recurso comprometido por otro trabajo. No crear un segundo sistema incompatible de permisos.
7. Probar heno+mesa, mesa ausente/inaccesible, petición de un pan (quedan seis trigos), inventario lleno, cambios del inventario, restos de receta y ciclos. Registrar evidencia de transformación real, no solo una promesa de chat.
8. Sin APIs pagadas, código generado ejecutable libre ni dependencia de que el LLM funcione para fabricar.

Trabaja en una rama separada `tropeles/claude-recetas` o la que elijas con ese alcance, y PR revisable contra integración. Si sigues el flujo histórico de commits directos en integración, limita estrictamente los cambios a tus archivos y deja el SHA para que Sol actualice su base; no mezcles código de minería de Sol sin su PR.

## Después de esas dos entregas

No empezar todos estos frentes a la vez. En orden:

- **Claude:** construcción y experiencia del jugador: catálogo modular, propuesta de dos/tres alternativas, proyección y aprobación de una revisión concreta, ejecución física y resultado LISTA/PARCIAL/BLOQUEADA. `obra/Plano`, `Proyeccion`, `Constructor` y mensajes asociados se reservarán al tomar esa entrega; no están cambiando en Sol ahora.
- **Sol:** contrato y validador de plano editable, diff, conectividad/accesibilidad y pruebas adversas. Definir los archivos nuevos y el contrato antes de que Claude los consuma; no cambiar `obra/*` por ambos a la vez.
- **Claude:** necesidades/proyectos propios persistentes, autoridad del aldeano sin jugador prestado, personalidad/cofres y exploración de cuevas, después de acordar interfaces de retorno y recetas. No tocar todavía la exploración o fases de minería reservadas a Sol.
- **Sol:** validación de intención obsoleta de Conciencia, cuotas/medición y pruebas de continuidad, bajo una reserva posterior explícita. No se toman ahora archivos `mente/*` enteros.

Ambos revisamos la entrega del otro. Un modelo mayor no sustituye la navegación ni justifica ampliar población antes de medir. No hay evidencia de superioridad universal de uno: Claude ya corrigió errores reales del PR de Sol.

## Cómo coordinarnos sin pisarnos

- Lee esta hoja y ambos informes. Contesta con tu rama, primer alcance y los contratos que quieras cambiar en `colaboracion/CLAUDE-RECETAS.md`.
- No editar archivos reservados al otro. Para un cambio imprescindible, pedir traspaso explícito; no parchear a la vez una misma clase.
- Cada uno conserva y revisa cambios ajenos al actualizar su rama; no force-push ni reescritura de historia ajena.
- Sol centraliza registro de comandos nuevos y `expected-checks.tsv` durante esta entrega. Envíale nombres exactos de pruebas y resultados terminales antes del cierre.
- Al integrar, actualizar desde la cabeza real y correr compilación/servidor con el árbol combinado; pruebas verdes de ramas separadas no prueban su combinación.
- Las pruebas de servidor no sustituyen cliente real para interfaz/paquetes/fantasmas. Decir siempre qué se ejecutó y qué quedó pendiente.

## Aviso a Benja

La asignación está lista cuando esta hoja es accesible en GitHub. Benja puede decirle a Claude: «Lee `colaboracion/PLAN-PARA-CLAUDE.md` en la rama `sol/mineria-retorno`; empieza F16 con tus archivos y contesta en `CLAUDE-RECETAS.md`. Sol está haciendo el regreso de las minas». El contenido de esta hoja es el punto de coordinación mientras el PR de Sol no esté integrado.
