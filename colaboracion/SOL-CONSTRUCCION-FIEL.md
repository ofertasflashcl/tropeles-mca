# Sol: construcción fiel al plano

Benja autorizó aplicar los bloqueos señalados por Claude en PR #8 y exigir los bloques exactos del plano, sin intercambiar especies de madera.

Base: `tropeles/claude-catalogo-casas` en `35441e18b5c9aebe103e8425773c31e9cd74d9e1`. Rama: `sol/construccion-fiel`; PR apilado sobre #8. No se fusiona ni se publica release automáticamente.

## Incremento tomado por Sol

- `obra/Constructor.java`, `Plano.java`, `Taller.java`: puerta completa, material y orientación del plano, suministro/fabricación de la madera requerida y cuentas según piezas.
- `body/Progreso.java`: solo sobrecarga de pedido de troncos por especie, conservando el pedido genérico y las APIs del minero.
- `mente/Voluntad.java`: solo selección básica del diseño de casa en una obra nueva; no cambiar una obra activa.
- `chatAI/TriggerCommandInfos.java`: semántica de escolta por chat, sin relajar el predicado de movimiento.
- Pruebas nuevas del constructor real y registro central de las seis `catalogo-*` de Claude.

Claude conserva modelos/catálogo y la siguiente capa de propuestas, edición conversacional, vista previa y aprobación. No duplicar estos arreglos mientras Sol los entrega. La revisión del PR #8 se comprobará con el conjunto y sus escenarios; el revisor externo no está disponible en este entorno, y no se atribuirán resultados a él.

## Condiciones de entrega

- No consumir ni colocar una madera distinta porque sea la que hay en la bolsa; si falta la requerida, buscarla o explicar la falta.
- La puerta conserva ambas mitades y su objeto solo se descuenta si quedó colocada.
- Un bloque de otro tipo/especie no cuenta como terminado. Conservar permisos sobre cambios del jugador.
- Pruebas con inventario mixto, orientación de troncos/puertas, fabricación exacta y construcción ejecutada por el motor real, además de la suite existente.
- Entregar SHA, CI y límites a Claude para que prepare el JAR tras las integraciones que decida Benja.

## Entrega para Claude · 2026-11-04 20:44 UTC

- **PR:** [#9](https://github.com/ofertasflashcl/tropeles-mca/pull/9), sobre tu C02 (#8). Código final `99b671773abb2f88d318b155fb8f7f3fae614f6b`, rama `sol/construccion-fiel`. Incluye la historia de #6, #7 y #8; no se han fusionado ni publicado releases en esta sesión.
- **Forge real:** [run 41907919694](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41907919694), job 126847428730: **91/91 + segundo proceso 3/3**, ambos arranques y cierres limpios. [Forge y Fabric](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41907919495): aprobados. El merge de prueba `f9f32bfe55bfcb540418856c7bdfb220a13ae890` y el código final tienen el mismo árbol `4d291e3bb6a05fad28feb10ea649fc95288fd7bd`.

### Qué quedó implementado

1. Puerta puesta como pareja, sin convertir la mitad inferior en aire. Se comprueban las dos mitades antes de descontar un objeto; ante fallo se restaura el estado previo. Abrirla no invalida la casa; perder una mitad sí.
2. Cada pieza exige el bloque y la orientación del plano. Otro tronco, tabla, escalón o puerta no sirve por compartir categoría. Se comprueba con inventarios mixtos; no se consumen materiales rechazados.
3. La construcción cuenta, solicita y fabrica su especie de madera, derivada del snapshot. La tala eligió abeto real aunque había roble más cerca y 16 troncos de roble en la bolsa; conservó ese árbol. Los pedidos genéricos del minero siguen disponibles.
4. `Constructor.puedeApoyar` aplaza piezas sin pared o vecino de apoyo antes de gastar búsqueda de camino. No marca esa dependencia como ruta fallida; el colocador conserva sus controles de cara visible y alcance. Resuelve el orden de los escalones interiores.
5. Una obra nueva permite pedir cabaña, casa larga/alargada o la casa de dos pisos, conservando el diseño y la madera en la acción de chat. Una obra activa conserva su geometría. El presupuesto inicial sale de las piezas del plano, con herramientas y combustible aparte.
6. Seis casos `catalogo-*` registrados en el CI central y siete `obra-*` nuevos. `start-escort` en MCA sin conectar usa seguimiento con el jugador real; se exige desplazamiento y no se relajó `ia-red`.

### Qué hacer ahora

1. **Revisar #9 y preparar el JAR de prueba desde `sol/construccion-fiel`**, no desde una rama anterior: incluye tu catálogo y estos arreglos. El CI ya construyó un artefacto validado. Si recompilas: JDK 21 y `./gradlew :forge:remapJar --console=plain --non-interactive -Pmod_version=7.6.28-tropeles.1`.
2. Benja decide la integración de la pila #6 → #7 → #8 → #9. Para el JAR público, llevar el conjunto a `tropeles/ojo-combate-mineria-1.20.1` y dejar que su CI aprobado publique el release. Evitar fusionar además #4/#5 como si fueran trabajo nuevo: ya están combinados en #6.
3. Tomar **C03b**: propuestas y elección conversacional, editar ventanas/techo sobre un borrador, mostrar diferencia/coste y fantasma, aprobar antes de aplicar una revisión a una obra. Conservar identidad/hash/snapshot y los controles de bloque exacto. Modelos/catálogo y render siguen siendo tu parte.
4. En cliente, comprobar fantasma y una construcción autónoma por diseño, incluyendo materiales ausentes, pausa y reinicio. Las casas antiguas con piezas incorrectas protegidas por permisos no se destruyen automáticamente.

### Alcance de la evidencia

`obra-modelos` puso **715 objetos** de los tres diseños con `Constructor.colocar` y verificó las celdas finales. La posición del actor y sus suministros son asistidos: no acredita por sí sola recolección, navegación y andamios de una casa autónoma entera. Tala y fabricación tienen escenarios físicos separados. Sin probar cliente/modpack de Benja ni población masiva. Las tres casas actuales usan una especie cada una; esto no implementa todavía suministro general para paletas mixtas o recetas arbitrarias de otros mods. El presupuesto inicial es una base vanilla, no una simulación completa de todas las recetas.

La última revisión pasó sin reintentos. Antes se corrigieron errores de compilación, un filtro de tala incompleto y el orden de apoyos del banco de montaje. Hubo un reintento justificado por descarga HTTP/2 de Forge fallida antes de arrancar el servidor. El revisor externo no se ejecutó: su instalación fue rechazada automáticamente por ejecutar un script remoto sin verificar; no se atribuyen resultados a esa herramienta. Este cierre solo modifica colaboración y lleva `[skip ci]`.
