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
