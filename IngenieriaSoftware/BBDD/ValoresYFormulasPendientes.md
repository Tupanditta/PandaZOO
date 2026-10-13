# Valores y fórmulas pendientes de definir

## 1. Propósito y criterio

Este documento inventaría los datos necesarios para completar la construcción de la base de datos y del motor de cálculo. No añade requisitos ni decide valores de negocio.

Se usan estas etiquetas:

- `DEFINIDO`: aparece expresamente en requisitos, restricciones, casos de uso, contexto o en la especificación actual.
- `PENDIENTE_DE_DEFINIR`: el proyecto exige el dato o lo menciona, pero no proporciona una fórmula, tabla o valor suficiente para calcularlo.
- `DEBE_CONFIRMARSE`: existe un valor en el DDL o en la especificación, pero todavía debe confirmarse que sea una decisión funcional y no un valor provisional del esquema.

No se debe sustituir `PENDIENTE_DE_DEFINIR` por un valor supuesto.

## 2. Satisfacción individual

### 2.1. Estructura documentada y puntos no resueltos

La especificación actual establece:

$$S_i = (w_{bio}S_{bio} + w_{soc}S_{soc}) \times P_{hab} \times M_{crit}$$

También establece:

$$S_{bio} = \frac{nivel\_alimentacion}{100}$$

La documentación actual contiene la siguiente fórmula para la parte social, expresada en la escala `0..1` confirmada para el proyecto:

$$S_{soc} = w_{pareja}C_{pareja} + w_{amigo}C_{amigo} + w_{padre}C_{padre} + w_{madre}C_{madre} + w_{hermanos}C_{hermanos} + w_{afinidad}C_{afinidad}$$

con:

- $C_{pareja} = 1$ si tiene pareja, $0$ si no
- $C_{amigo} = 1$ si tiene mejor amigo, $0$ si no
- $C_{padre} = 1$ si tiene padre, $0$ si no
- $C_{madre} = 1$ si tiene madre, $0$ si no
- $C_{hermanos} = \min(1, \frac{num\_hermanos}{tope\_hermanos})$
- $C_{afinidad} = \frac{\sum A_{especie,otra}}{N}$, calculada con los animales del mismo espacio que no son de su familia y usando la afinidad correspondiente a sus especies

La afinidad familiar se considera `0` por diseño y no se suma a la afinidad no familiar. Si un animal no tiene animales no familiares en el espacio, entonces $C_{afinidad} = 0$ y ese valor resta en la satisfacción social.

La fórmula es compatible con la escala `0..1` de `S_i`, `Animal.satisfaccion_actual` y `MetricaSatisfaccion.valor`. La presencia o ausencia de pareja, mejor amigo, padre, madre y hermanos se interpreta de la misma forma en todas las especies; la diferencia entre especies se obtiene mediante sus pesos y la afinidad. La afinidad no familiar se define entre especies: todos los animales de una especie comparten la afinidad con una segunda especie.

| Elemento | Estado | Datos conocidos | Pendiente |
|---|---|---|---|
| `S_i` | DEFINIDO | Resultado de la ecuación individual para un animal vivo alojado en un espacio. | Redondeo, saturación adicional y tratamiento de datos ausentes. |
| `w_bio`, `w_soc` | DEFINIDO POR ESPECIE | Se almacenan en `Especie` y deben sumar `1.00`; cada especie define su conjunto de pesos y estos se registran en `valores_y_pesos.json`. | Completar la tabla por especie y validar la suma final. |
| `S_bio` | DEFINIDO | `nivel_alimentacion / 100`; el DDL restringe la alimentación a `0..100`. | No se define una conversión adicional. |
| `S_soc` | PARCIALMENTE_DEFINIDO | Se expresa en `0..1`, no se redondea, usa pesos propios de cada especie y considera pareja, mejor amigo, padre, madre, hermanos y afinidad no familiar entre especies dentro del mismo espacio. Las relaciones familiares se interpretan igual en todas las especies; una relación inexistente aporta `0` y no aplica penalización adicional. `w_afinidad` debe ser mayor que la suma de los pesos familiares. | Valores concretos de pesos y matriz de afinidad, contribución de cada relación y rango. |
| `P_hab` | PARCIALMENTE_DEFINIDO | Existe una fila para cada combinación especie-bioma; el valor es específico de cada especie y bioma y se multiplica directamente en la ecuación. El JSON contiene los valores de configuración, no el esquema real de BBDD. | Completar las especies, biomas y valores concretos de la tabla. |
| `M_crit` | PARCIALMENTE_DEFINIDO | Se calcula como `M_alimentacion * M_enfermedad`, con factores específicos por especie en `0..1`; alimentación al `100%` y estado sano tienen factor `1`. No se redondea. | Valores concretos por especie y nivel de alimentación/enfermedad. |

### 2.2. Alcance de los datos

- Solo se calcula una satisfacción activa para animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo.
- Los animales muertos o vendidos conservan histórico, genealogía y auditoría, pero no forman parte del estado activo.
- La alimentación y la salud no son editables directamente por el usuario (RES-03).
- El cálculo solo se dispara tras una mutación confirmada o al finalizar un ciclo (RF-15, RF-44, RES-10).

### 2.3. Agregaciones

La especificación actual describe estas agregaciones:

- Espacio: media directa de las satisfacciones de sus animales vivos.
- Hábitat: media directa de las satisfacciones de todos sus animales vivos.
- Partida: media directa de las satisfacciones de todos los animales vivos.

Los espacios, hábitats o partidas sin animales vivos quedan sin métrica y no participan en las medias de niveles superiores. El valor persistido se mantiene en `0..1`; la conversión a porcentaje entero solo se aplica en la presentación individual.

## 3. Prestigio

RF-35 exige que el prestigio actual dependa del número total de animales albergados y del porcentaje de satisfacción global. RF-36 exige conservar el máximo histórico.

| Elemento | Estado | Pendiente |
|---|---|---|
| Prestigio actual | PARCIALMENTE_DEFINIDO | Es el nivel más alto cuyos dos umbrales se cumplen: cantidad de animales vivos en el zoológico y satisfacción global requerida. Los niveles se numeran de `1` a `n`. | Valores concretos de cada nivel, comportamiento cuando disminuyen los indicadores y comportamiento inicial o con cero animales. |
| Número total de animales albergados | DEFINIDO | Cuenta únicamente animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo. |
| Satisfacción global | DEFINIDO | Debe existir y consultarse en el monitor global; se calcula como la media directa de todos los animales vivos. Un parque sin animales vivos queda sin métrica. |
| Prestigio máximo | DEFINIDO | Debe conservar el mayor valor histórico aunque descienda el actual (RF-36). | Fórmula de actualización y valor inicial. |
| Umbrales de nivel de prestigio | PARCIALMENTE_DEFINIDO | Cada nivel contiene cantidad de animales vivos requerida y satisfacción global requerida; los valores se configurarán en el JSON. | Valores concretos y reglas de notificación de ascensos (RF-43). |
| Desbloqueo por prestigio | PENDIENTE_DE_DEFINIR | Umbral de cada especie y relación exacta con el prestigio; el desbloqueo debe ser irreversible (RES-07). |

## 4. Economía y beneficios

| Cálculo o valor | Estado | Pendiente |
|---|---|---|
| Coste total de compra | PARCIALMENTE_DEFINIDO | El coste de un espacio se consulta por `capacidad_max` en `CosteEspacio`; el coste de compra es fijo por rango mediante `CosteRango`; el alimento tiene un precio fijo por kilogramo mediante `CosteAlimento`; el insumo tiene un precio fijo por unidad mediante `CosteInsumo`. | Costes concretos y fórmula de cantidades cuando corresponda. |
| Importe de venta | PENDIENTE_DE_DEFINIR | Regla e importe acreditado por la venta de un animal (RF-06). |
| Consumo de alimento | PENDIENTE_DE_DEFINIR | Ración requerida por especie, unidad, cantidad consumida y momento de descuento. |
| Consumo médico | PARCIALMENTE_DEFINIDO | Los insumos son comunes a todas las especies; la cantidad necesaria para curarse se consulta por `id_rango` e `id_insumo` en `ConsumoMedicoRango`, y las especies comparten rango. | Valores concretos por rango e insumo y regla de reposición de salud. |
| Beneficio o ingreso generado | PARCIALMENTE_DEFINIDO | Se calcula al finalizar cada ciclo y se actualiza tras mutaciones confirmadas que alteren animales vivos o satisfacción global. Depende de cantidad de animales vivos y satisfacción media global; una ponderación exclusiva elegible se multiplica directamente. | Operación base exacta, costes de mantenimiento y valores concretos de las ponderaciones exclusivas. |
| Ingreso neto acumulado | PENDIENTE_DE_DEFINIR | Fórmula de ingresos menos costes y reglas de acumulación. |
| Reclamación de beneficio | DEFINIDO | Transfiere ingresos netos acumulados al balance disponible (RF-11). Momento, importe exacto y fórmula de cálculo del saldo pendiente. |
| Costes periódicos de mantenimiento | PENDIENTE_DE_DEFINIR | Importe, periodicidad, entidades afectadas y aplicación durante ciclos. |
| Saldos e inventarios | DEFINIDO | No pueden ser negativos y no se permiten consumos superiores al inventario (RF-30, RF-31, RF-50, RES-12). | Unidades, conversiones y valores iniciales. |

## 5. Reproducción

| Cálculo o valor | Estado | Pendiente |
|---|---|---|
| Pareja válida | DEFINIDO | Misma especie, misma partida, referencias válidas, pareja recíproca, sin autorreferencia y estado permitido. |
| Umbral de satisfacción reproductiva | PARCIALMENTE_DEFINIDO | Cada rango tendrá su propio umbral y ambos progenitores deberán alcanzarlo para que la pareja pueda ser evaluada. | Valores concretos por rango. |
| Probabilidad de reproducción | PARCIALMENTE_DEFINIDO | Cada rango tiene una probabilidad inicial y una penalización por hijo; la probabilidad total es `max(0, probabilidad inicial - penalización por hijo * hijos vivos)`, y se decide mediante un sorteo aleatorio en cada ciclo. Los hijos muertos no aplican penalización. | Valores concretos por rango y tratamiento exacto del límite superior, aunque la configuración inicial está restringida a `0..1`. |
| Concepción | DEFINIDO | Como máximo una por pareja y ciclo; la opción aparece al inicio del ciclo posterior y puede aceptarse durante tres ciclos, aunque baje la satisfacción de los progenitores. Debe ser idempotente. | Regla de inicialización de la cría. |
| Aforo para nacimiento | DEFINIDO | No se permite el nacimiento si el espacio ha alcanzado su capacidad (RF-17, RF-18, RF-29). | Consulta exacta y comportamiento cuando no hay espacio. |
| Datos iniciales de la cría | DEFINIDO | Misma especie que sus progenitores, edad inicial de 0 años, alimentación inicial del 100%, salud sana, satisfacción calculada tras completar la ficha, personalidad y habilidades introducidas por el usuario, mejor amigo opcional y relaciones de parentesco completadas por el sistema. | Representación final de los datos de edad y de los campos de ficha en BBDD. |
| Límite de hermanos | DEBE_CONFIRMARSE | El DDL contiene `tope_hermanos` con un valor por defecto. | Confirmar el valor funcional y su uso en el cálculo o validación. |

## 6. Ciclos y simulación offline

| Cálculo o valor | Estado | Pendiente |
|---|---|---|
| Periodicidad del ciclo | DEFINIDO | Un ciclo periódico cada 10 minutos (RF-14). |
| Número de ciclos offline | DEFINIDO | Si `E < R`, se ejecutan 0 ciclos; si `E >= R`, se ejecutan `1 + floor((E - R) / 600)`, donde `R` es el remanente en segundos y `E` el tiempo transcurrido. | Aplicación de las restricciones biológicas ya definidas en cada ciclo. |
| Remanente del temporizador | DEFINIDO | Si `E < R`, el nuevo remanente es `R - E`; si `E >= R`, es `600 - ((E - R) mod 600)`, representando un resto cero como `600`. | Ninguna. |
| Descenso de alimentación offline | DEFINIDO | No más de dos tramos: `[100, 80, 60]% -> 40%` o `[40, 20]% -> 0%` (RF-22). | Aplicación detallada cuando hay varios ciclos y valores intermedios. |
| Enfermedad offline | DEFINIDO | Un animal que enferma computa un ciclo; uno previamente enfermo acumula como máximo dos adicionales (RF-23). | Estado y fórmula de transición para cada ciclo. |
| Ciclos críticos | DEFINIDO | Se mantienen contadores independientes de desnutrición y enfermedad; cada contador se reinicia al recuperarse su estado y se usa el mayor si ambos coinciden. La defunción se produce al alcanzar 3 ciclos en el contador determinante (RF-19). | Aplicación detallada durante ciclos simultáneos y offline. |
| Longevidad máxima | PARCIALMENTE_DEFINIDO | Se calcula una sola vez mediante `edad_media_especie * factor_aleatorio`, con `factor_aleatorio` entre `0.90` y `1.10`; se fija al nacer o comprar y no se puede editar. La edad se expresa en años, meses y días, y cada ciclo incrementa 6 días. | Tabla de edades medias por especie y regla exacta de conversión/normalización de fracciones a años, meses y días. |
| Orden del ciclo | DEFINIDO | Edad, alimentación, salud, ciclos críticos, defunciones, reproducción, beneficios, misiones, métricas y persistencia (RF-14). | Fórmula o tabla de cada transición no definida por RF. |

## 7. Capacidad y compatibilidad

| Elemento | Estado | Pendiente |
|---|---|---|
| Capacidad positiva | DEFINIDO | `capacidad_max > 0` y no puede modificarse (RF-02, RF-04, RES-04). |
| Aforo usado | DEFINIDO | La validación de la especificación cuenta animales vivos con espacio asignado dentro de la misma transacción del flujo correspondiente. | Ninguna. |
| Compatibilidad especie-bioma | PENDIENTE_DE_DEFINIR | El esquema contiene `CompatibilidadBioma` y factores permitidos `1.00`, `0.75`, `0.30`. | Confirmar que esos factores son valores funcionales, completar la tabla y definir ausencia de fila. |
| Compatibilidad del espacio | DEFINIDO | Debe validarse antes de compra, traslado y nacimiento. | Regla exacta que relaciona espacio, hábitat, bioma y especie. |

## 8. Misiones y recompensas

| Elemento | Estado | Pendiente |
|---|---|---|
| Progreso de misión | PARCIALMENTE_DEFINIDO | Compras y ventas confirmadas, y edades alcanzadas, son acumulativas; presencia en hábitat, satisfacción y prestigio son condiciones dinámicas y pueden disminuir. | Representación final de contadores acumulativos y valores concretos de los objetivos. |
| Cumplimiento de misión | DEFINIDO | La misión pasa inmediatamente a `CUMPLIDA` cuando alcanza su objetivo. | Valores concretos de cada objetivo. |
| Recompensa | DEFINIDO | Puede ser fondos, alimento, insumo o animal aleatorio según el DDL y debe acreditarse una sola vez (RF-40, RNF-12). Las recompensas de alimentos e insumos usan cantidad; el animal aleatorio puede desbloquear una especie exclusiva y aparecer una vez en tienda con coste cero. | Valores concretos, probabilidades por especie, representación estructural definitiva y momento exacto de disponibilidad. |
| Desbloqueo de especie exclusiva | DEFINIDO | Las especies exclusivas no se compran en tienda y se obtienen por misiones o eventos (RF-38, RF-49, RES-09). | Reglas de evento, recompensa y valores de desbloqueo. |

## 9. Avisos, estados y auditoría

Los estados enumerados en el DDL deben conservarse como estados técnicos hasta que los requisitos definan su semántica completa. Quedan por detallar:

- Transiciones válidas entre estados de notificación, recompensa, ciclo, beneficio y animal.
- Severidad y condiciones de cada aviso crítico.
- Texto o datos calculados para ciclos acumulados y ciclos restantes de RF-20 y RF-41.
- Tipos y origen de todos los eventos auditables de RF-47.
- Reglas de idempotencia por operación cuando la clave solo se menciona pero no se define su composición.

## 10. Tablas y catálogos incompletos

Deben completarse sin inventar datos:

- Filas de `Bioma` y sus nombres.
- Filas de `Especie`, incluyendo pesos, coste, estado de catálogo, umbral de desbloqueo y longevidad si aplica.
- Filas de `CompatibilidadBioma` y sus factores.
- Filas de `AfinidadEspecies` y significado de cada coeficiente.
- Catálogo de alimentos, insumos médicos y cantidades por operación.
- Misiones, objetivos, progreso requerido y recompensas.
- Rasgos de personalidad y habilidades sociales permitidos.
- Tabla de estados, transiciones y valores iniciales de animales, ciclos, beneficios y avisos.

La decisión funcional confirmada es que los pesos `peso_bio` y `peso_soc` no son globales para todas las especies: cada especie define su propio conjunto de pesos en `valores_y_pesos.json`, y la suma debe ser `1.00`.

La afinidad social no familiar se gestiona mediante pares de especies. Las relaciones familiares no forman parte de la matriz descrita y se tratan por separado mediante pareja, mejor amigo, padre, madre y hermanos.

Los valores por defecto presentes actualmente en el DDL, como `tope_hermanos`, fondos iniciales, alimentación inicial, satisfacción inicial o factores de compatibilidad, se consideran `DEBE_CONFIRMARSE` hasta que el proyecto los ratifique como valores funcionales. En la práctica, la decisión funcional real queda registrada en la tabla por especie del JSON y no se inventará un valor fuera de ese conjunto.

## 11. Dependencias y contradicciones a resolver

1. La ecuación de satisfacción usa componentes que el proyecto nombra, pero no define con suficiente detalle para obtener un resultado numérico reproducible.
2. RF-35 se resolverá mediante niveles con umbrales conjuntos de animales vivos y satisfacción global, pero no define todavía los valores concretos ni el comportamiento cuando los indicadores disminuyen.
3. RF-15 exige tasa de beneficios y costes, pero no define su cálculo económico.
4. RF-16 exige una probabilidad y un umbral de reproducción, pero no define cómo se calculan.
5. RF-14 y RF-19 definen la variación estadística de la longevidad y el límite de 3 ciclos críticos, pero queda pendiente completar las edades medias y la conversión/normalización de fracciones a años, meses y días.
6. RF-21 y RF-24 quedan resueltos mediante el remanente en segundos y el cálculo de ciclos offline documentado en la especificación BBDD.
7. El DDL contiene valores por defecto y factores que deben distinguirse de decisiones funcionales ratificadas.

Hasta que estas decisiones se incorporen a los requisitos o sean aprobadas explícitamente como reglas del proyecto, la especificación de BBDD no debe asignarles valores.
