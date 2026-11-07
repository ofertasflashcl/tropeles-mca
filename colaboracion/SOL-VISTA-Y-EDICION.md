# Vista, agua y edición libre de planos · PR #13

Continuación del trabajo interrumpido de Benja, en `sol/vista-con-intencion`, sobre `sol/obra-viva-autonomia` (#12). El objetivo es que la percepción cambie acciones y recuerdos, y que los diseños se describan por bloques en vez de limitarse a variantes de un catálogo. Esta entrega conserva el trabajo combinado con Claude.

## Percepción y decisiones

- `VistaSituada` toma una muestra de los rayos del aldeano en el servidor. Imagen, referencias de bloques, caras, estados, dimensión y tick pertenecen a esa misma muestra.
- La imagen es una representación aproximada de colores y profundidad de la retina, de 32 × 18 muestras ampliadas a PNG. La identificación exacta procede de los bloques alcanzados por los rayos.
- El chat y `Conciencia` reciben esa escena. Los proveedores que admiten imágenes reciben también el PNG; el resto conserva la información de los bloques.
- `inspect-view` resuelve una referencia de la muestra y comprueba nuevamente dimensión, antigüedad, distancia, estado, oclusión y luz. Orienta físicamente la mirada y registra el bloque comprobado.
- La retina guarda hallazgos relevantes, como minerales y lava, en el diario. `vista-hallazgo` también puede activar el pensamiento autónomo. El mapa visual sigue alimentando la minería.
- El cuerpo percibe y responde durante sus ticks. El modelo recibe muestras al conversar o pensar; el pensamiento habitual conserva su intervalo de 90 s y los eventos su intervalo mínimo de 30 s.
- Una orden nueva invalida los pensamientos pendientes de una generación anterior. Las acciones visuales revalidan su muestra antes de ejecutarse.

## Salidas y agua

- «Intenta subir por detrás de ti» conserva el rumbo al recibir la orden. Sondea caminos secos, gira, recorre una alternativa y guarda el lugar si alcanza la salida.
- `RescateAgua` interrumpe la exploración para buscar suelo firme o una celda donde respirar. Comprueba colisiones y chunks cargados, conserva un plazo por paso y evita brevemente los pasos fallidos.
- Alcanzar un paso de nado o un bolsillo de aire hace que continúe buscando una orilla. En agua profunda, cuando la superficie queda fuera de la búsqueda local, sigue ascendiendo por celdas libres comprobadas.
- Respirar usa el motor local, sin esperar una respuesta del modelo ni exigir material para colocar bloques.
- Con poco aire, el rescate tiene prioridad sobre órdenes, pausas y pasos de minería en curso. El cambio de fase para ir a buscar un pico tampoco lo desactiva.

## Diseños y ediciones

- `design-blueprint` compila un programa del modelo sobre el plano actual: lienzo vacío, bloques individuales, regiones, envolventes, líneas, eliminación de celdas y entrada. Las formas proceden del programa generado.
- El modelo recibe un resumen acotado del borrador en capas, con paleta, coordenadas locales y estados de los bloques. Una edición conserva las celdas que no modifica; las capas que exceden el resumen de 5.000 caracteres quedan indicadas como omitidas.
- Se admiten materiales decorativos compatibles, mezclas de materiales, ejes de troncos, orientación y mitades de escaleras, losas y puertas de los tipos soportados. El colocador y las recetas conservan el objeto exacto requerido.
- La compilación comprueba coordenadas, estados, tamaño, entrada y conexión de las piezas al suelo. Un fallo conserva el borrador entero.
- Si el programa resulta inválido, el cerebro recibe su error concreto y puede corregirlo una vez dentro del turno. La reparación vuelve a pasar por el compilador antes de publicarse.
- Un resultado viejo no sobrescribe una revisión nueva ni una propuesta de otro jugador. La previsualización se actualiza con la revisión aceptada. Solo el «sí» del dueño crea la obra con ese mismo plano.
- Límites actuales: 4.096 celdas, 64 operaciones por programa, 4.800 caracteres por argumento y extensión máxima de 64 bloques. La edición libre opera sobre propuestas pendientes; una obra aprobada conserva su plano fijo.

## Comprobaciones

Los escenarios ejecutan el cuerpo y los contratos reales en Forge. Las respuestas del modelo de las nuevas pruebas se controlan para comprobar el paso percepción → decisión → efecto; la calidad de un proveedor real necesita una evaluación adicional.

| Escenario | Qué exige |
|---|---|
| `vista-accion` | Imagen y referencias coherentes; mineral visible guardado; diamante oculto ausente; bloque cambiado rechazado; distinción entre inspección e inventario. |
| `subida-detras` | Giro y subida reales detrás del rumbo original; recuerdo de la salida; cero bloques rotos. |
| `agua-exploracion` | Llegar a una orilla firme conservando el agua y el inventario vacío. |
| `agua-respiradero` | Salir por el respiradero lateral del túnel, con poco aire inicial, sin daño ni materiales. |
| `agua-profunda` | El ciclo completo de `ProspectingTask` sale de 12 bloques de agua y conserva la vida, sin materiales. |
| `propuestas-voxeles` | Generación y edición de un arco por chat; identidad de revisión; persistencia; aprobación exacta; rechazo de parches inválidos, viejos y ajenos. |
| `propuestas-reparar` | Error enviado al modelo; una sola reparación; ningún cambio parcial; conservación de una revisión editada durante la reparación. |
| `obra-modelos` | Colocación con inventario real, incluido un diseño propio con materiales y estados mezclados, en cuatro orientaciones. La posición del actor se asiste en este fixture. |

**Resultado final:** [Forge y servidor real, ejecución 42339573383](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/42339573383): **119/119 y 5/5 después de reiniciar el mismo mundo**, con arranques y cierres limpios. [Compilaciones Forge y Fabric, ejecución 42339573303](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/42339573303): aprobadas.

Código validado: `9b69d8db8bb60b7490762425dbf05e62e84ae6ae`. El JAR se compiló y arrancó desde el merge de prueba `803377c9ae78354ce2e77c807f271e6530cd8a4b`; ambos tienen el árbol `5529ca2fa8c7089ca9b3840c879861b3b2469560`. [JAR de prueba de este CI](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/42339573383/artifacts/13561082231), identificado por ese SHA; su entrega como artefacto conserva separada la publicación del release de integración.

Agua profunda: 12 bloques, **91 ticks, aire mínimo 116/300 y vida 20/20**, sin materiales. Respiradero: aire inicial 95, mínimo 62/300, **60 ticks y vida 20/20**. Colocación física: **975 objetos en siete planos**, con la posición del actor asistida y el inventario mixto comprobado. También aprobaron la inspección con oclusión, la subida por detrás, las ediciones, la reparación y la persistencia del plano personalizado en el segundo proceso.

## Revisión en cliente con Claude y Benja

1. Usar el JAR identificado por el SHA del CI de esta rama, en una copia del mundo de prueba con Forge 1.20.1 y Architectury compatible.
2. Pedir una estructura distinta del catálogo, por ejemplo un arco de ladrillo con una torre asimétrica. Situar la parcela y revisar que aparece la geometría propuesta.
3. Pedir cambios concretos en varias partes: material de una columna, orientación de una escalera, hueco de una ventana y prolongación de una viga. Comprobar cada revisión en la vista previa antes de responder «sí».
4. Comprobar que la obra aprobada corresponde a la última revisión y que un segundo jugador conserva separados sus permisos y su vista privada.
5. Probar las instrucciones de salida dentro de una mina y la recuperación en agua en terreno natural. Contrastar la escena que describe con su mirada actual y con los hallazgos del diario.

La prueba de servidor acredita los contratos y los efectos descritos arriba. Quedan por comprobar el render en un cliente real, el comportamiento en el modpack de Benja, la calidad arquitectónica de las respuestas de cada proveedor gratuito y la construcción autónoma completa de diseños complejos. La integración y publicación del release siguen el reparto vigente con Claude y Benja.
