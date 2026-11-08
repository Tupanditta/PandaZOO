# Valores y fórmulas del modelo (inventario de decisiones)

## 1. Propósito y criterio

Este documento inventaría los datos necesarios para construir la base de datos y el motor de cálculo y dónde está definido cada uno. Todas las decisiones que estaban pendientes están ya resueltas; los valores numéricos son valores de diseño y viven en `Otros/valores_y_pesos.json` (fuente única), por lo que reajustarlos no cambia ninguna regla.

Estado de cada elemento:

- `DEFINIDO`: fórmula, regla y valores completos; no queda nada por decidir.

No existen elementos `PENDIENTE_DE_DEFINIR` ni `DEBE_CONFIRMARSE`. Los valores por defecto del DDL que antes debían confirmarse se han ratificado, sustituido o eliminado (sección 10).

## 2. Satisfacción individual

### 2.1. Estructura documentada

La especificación actual establece:

$$S_i = (w_{bio}S_{bio} + w_{soc}S_{soc}) \times P_{hab} \times M_{crit}$$

También establece:

$$S_{bio} = \frac{nivel\_alimentacion}{100}$$

La documentación actual contiene la siguiente fórmula para la parte social, expresada en la escala `0..1` confirmada para el proyecto:

$$S_{soc} = w_{pareja}C_{pareja} + w_{amigo}C_{amigo} + w_{padre}C_{padre} + w_{madre}C_{madre} + w_{hermanos}C_{hermanos} + w_{afinidad}C_{afinidad}$$

con:

- $C_{pareja} = 1$ si tiene pareja, $0$ si no
- $C_{amigo} = 1$ si tiene mejor amigo, $0$ si no (el vínculo es recíproco, igual que el de pareja: `Otros/DecisionesImplementacion.md`, sección 3)
- $C_{padre} = 1$ si tiene padre, $0$ si no
- $C_{madre} = 1$ si tiene madre, $0$ si no
- $C_{hermanos} = \min(1, \frac{num\_hermanos}{tope\_hermanos})$
- $C_{afinidad} = \frac{\sum A_{especie,otra}}{N}$, calculada con los animales del mismo espacio que no son de su familia y usando la afinidad correspondiente a sus especies

La afinidad familiar se considera `0` por diseño y no se suma a la afinidad no familiar. Si un animal no tiene animales no familiares en el espacio, entonces $C_{afinidad} = 0$ y ese valor resta en la satisfacción social.

La fórmula es compatible con la escala `0..1` de `S_i`, `Animal.satisfaccion_actual` y `MetricaSatisfaccion.valor`. La presencia o ausencia de pareja, mejor amigo, padre, madre y hermanos se interpreta de la misma forma en todas las especies; la diferencia entre especies se obtiene mediante sus pesos y la afinidad. La afinidad no familiar se define para pares de especies, incluidos los pares de una especie consigo misma; los animales de la misma especie comparten el coeficiente diagonal configurado para ella.

| Elemento | Estado | Datos conocidos | Valores |
|---|---|---|---|
| `S_i` | DEFINIDO | `S_i = (w_bio * S_bio + w_soc * S_soc) * P_hab * M_crit`, para animales vivos alojados. El resultado y sus componentes `S_bio` y `S_soc` se conservan en `0..1` sin redondeo ni saturación adicional. La presentación como porcentaje entero redondea los medios hacia arriba. | Ninguno. |
| `w_bio`, `w_soc` | DEFINIDO POR ESPECIE | Se almacenan en `Especie` y deben sumar `1.00`; cada especie define su conjunto de pesos y estos se registran en `valores_y_pesos.json`. | Ninguno. Valores en `especies[].pesos_satisfaccion`: `0.40/0.60` (gregarias), `0.50/0.50` (intermedias), `0.60/0.40` (solitarias). |
| `S_bio` | DEFINIDO | `nivel_alimentacion / 100`; el DDL restringe la alimentación a `0..100`. | No se define una conversión adicional. |
| `S_soc` | DEFINIDO | Fórmula ponderada de pareja, mejor amigo, padre, madre, hermanos vivos y afinidad no familiar del mismo espacio (familiares: pareja, padre, madre, hermanos e hijos vivos; el mejor amigo no es familiar). Los pesos sociales por especie son no negativos, suman `1.00` y `w_afinidad` supera la suma de los pesos familiares. Las afinidades se expresan en `0..1`; se incluyen pares diagonales para animales de la misma especie. Sin animales no familiares, `C_afinidad = 0`. No se redondea. | Ninguno. Pesos sociales por especie, `tope_hermanos` (4 gregarias, 3 intermedias, 2 solitarias) y 1128 afinidades (diagonal `0.90`, `0.70` o `0.40` según la sociabilidad) en el JSON. |
| `P_hab` | DEFINIDO | Debe existir una entrada para cada combinación especie-bioma; el factor es específico de cada especie y bioma y se multiplica directamente en la ecuación. No restringe la ubicación del animal. El JSON contiene los valores de configuración, no el esquema real de BBDD. | Ninguno. 235 valores en `tablas.compatibilidad_especie_bioma`. |
| `M_crit` | DEFINIDO | Se calcula como `M_alimentacion * M_enfermedad`, con factores específicos por especie en `0..1`; alimentación al `100%` y estado sano tienen factor `1`. No se redondea y ningún factor requerido puede faltar en la configuración. | Ninguno. Curva por especie en `tablas.factores_mcrit_especie`. |

### 2.2. Alcance de los datos

- Solo se calcula una satisfacción activa para animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo.
- Los animales muertos o vendidos conservan histórico, genealogía y auditoría, pero no forman parte del estado activo.
- La alimentación y la salud no son editables directamente por el usuario (RES-03).
- El cálculo solo se dispara tras una mutación confirmada o al finalizar un ciclo (RF-15, RF-44, RES-10).

### 2.3. Agregaciones

La especificación actual describe estas agregaciones:

- Espacio: media directa de las satisfacciones de sus animales vivos.
- Hábitat: media directa de las satisfacciones de todos sus animales vivos.
- Partida: media directa de las satisfacciones de todos los animales vivos; si no hay animales vivos, la satisfacción global es `0`.

Los espacios y hábitats sin animales vivos quedan sin métrica. La partida sin animales vivos conserva una métrica global de `0`. El valor persistido se mantiene en `0..1`; la conversión a porcentaje entero solo se aplica en la presentación individual.

## 3. Prestigio

| Elemento | Estado | Definición |
|---|---|---|
| Prestigio actual | DEFINIDO | Es el mayor nivel cuyos dos requisitos se cumplen (animales vivos alojados y satisfacción global mínima); si ninguno, `0`. Una partida nueva comienza en `0`. Reglas de redondeo de RF-35. |
| Umbrales y multiplicadores | DEFINIDO | Nivel 1: `2` animales y satisfacción `0.30`. Multiplicadores: `1.40` (animales) y `1.10` (satisfacción). Los multiplicadores ya no generan la tabla vigente: `nivel_maximo = 100` y `tablas.niveles_prestigio` (100 niveles, escrita a mano) fija los umbrales, desde `2` animales y `0.30` en el nivel 1 hasta `200` animales y satisfacción `1.00` en el nivel 100. El prestigio nunca baja (`Otros/DecisionesImplementacion.md`, sección 6). |
| Límites de configuración | DEFINIDO | Umbral base de animales: entero `>= 1`. Satisfacción base: `(0, 1]`. Multiplicadores: `1 < m <= 3.00`. El umbral de satisfacción de cada nivel hasta `nivel_maximo` debe ser `<= 1.00`; si no, la configuración es inválida. |
| Número total de animales albergados | DEFINIDO | Animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo. |
| Satisfacción global | DEFINIDO | Media directa de los animales vivos alojados; `0` si no hay ninguno. |
| Prestigio máximo | DEFINIDO | Tres máximos independientes (nivel, animales, satisfacción), inicializados al alcanzarse el primero. |
| Desbloqueo por prestigio | DEFINIDO | `tablas.desbloqueos_prestigio`: nivel 0 (inicial) con 6 especies y 2 hábitats; niveles 1 a 100 con el resto, mezclando rangos (38 especies comerciales y 5 hábitats en total). Se desbloquea todo lo de los niveles `<=` prestigio actual; es permanente (RES-07) y emite un aviso informativo por entidad. Las exclusivas solo se desbloquean por recompensa. |

## 4. Economía y beneficios

| Cálculo o valor | Estado | Definición |
|---|---|---|
| Coste total de compra | DEFINIDO | Espacio por `capacidad_max`: `6`→325, `8`→415, `10`→520, `15`→715, `20`→910, `25`→1105, `30`→1300; el primer espacio de 6 animales de la partida cuesta `0.00`. Ampliar un espacio cuesta la diferencia de precio más un 25 % y venderlo vacío devuelve lo pagado. Animal por rango: 40, 160, 450 y 700 (rangos 1 a 4); el rango 5 vale `0.00` (solo la unidad concedida por recompensa). Bambú `0.50` por kilogramo; medicina `10.00` por unidad. Importes con dos decimales, medios hacia arriba. |
| Importe de venta | DEFINIDO | Según nivel y rango (RF-06, RF-52): véase «Valor de venta». |
| Consumo de alimento | DEFINIDO | Único alimento: bambú, en kilogramos enteros: `1` a `5` kg por cada incremento de 10 puntos, según el tamaño de la especie (`tablas.alimentos_por_especie`). La cantidad es la de un animal de nivel 1 y crece un 5 % por cada nivel por encima del 1, redondeada hacia arriba. Descenso por ciclo ordinario: 20 puntos. La alimentación grupal (por espacio o por hábitat) consume el total en una operación atómica. |
| Consumo médico | DEFINIDO | Una única medicina universal; cantidad por tratamiento según el rango: `1`, `2`, `3`, `4` y `5` unidades (rangos 1 a 5). |
| Beneficio o ingreso generado | DEFINIDO | `B_ciclo = redondear(suma, sobre cada animal vivo alojado, de beneficio_base_animal_ciclo(rango) * S_i * M_nivel(nivel))`, con `M_nivel(n) = 1 + (multiplicador_nivel_maximo - 1) * (n - 1) / (nivel_maximo - 1)`; beneficio base por rango `6.00`, `12.00`, `20.00`, `32.00` y `50.00`; `0` sin animales. Sin multiplicadores por especie. Se genera una vez por ciclo confirmado. |
| Beneficio acumulado | DEFINIDO | Cada ciclo añade el beneficio generado al pendiente; las mutaciones entre ciclos no lo cambian; las salidas solo afectan al futuro. |
| Reclamación de beneficio | DEFINIDO | Acredita una sola vez el pendiente, marca los registros reclamados y deja el pendiente en `0` (RF-11). |
| Saldos e inventarios | DEFINIDO | No negativos. Partida nueva: `2000.00` de fondos, `30` kg de bambú y `3` medicinas (`partida_inicial`), más el regalo de bienvenida de animales gratuitos y el primer espacio de 6 animales gratis (`Otros/DecisionesImplementacion.md`, secciones 1 y 2). |

## 5. Reproducción

| Cálculo o valor | Estado | Definición |
|---|---|---|
| Valor de venta | DEFINIDO | `V = redondear(valor_venta_base(rango) * F_venta(nivel))`, lineal a tramos: `0.05` en el nivel 1, `1` (coste de compra) en el nivel de equilibrio `redondear(2/3 * nivel_maximo)` y `factor_venta_nivel_maximo` (`1.5` a `2.5`) en el tope. Valores base `40`, `160`, `450`, `700` y `4000` (rango 5, referencia). RF-52. |
| Pareja válida | DEFINIDO | Misma especie, misma partida, referencias válidas, pareja recíproca, sin autorreferencia, ambos con al menos el nivel mínimo de pareja (`4`) y estado permitido. |
| Umbral de satisfacción reproductiva | DEFINIDO | Por rango: `0.60`, `0.65`, `0.70`, `0.75` y `0.80`; comparación `>=` con la satisfacción sin redondear. |
| Probabilidad de reproducción | DEFINIDO | `max(0, probabilidad inicial - penalización * hijos vivos comunes)`. Probabilidad inicial por rango: `0.04`, `0.03`, `0.02`, `0.015`, `0.01`; penalización por hijo: `0.010`, `0.008`, `0.006`, `0.005`, `0.004`. Concepción si `aleatorio < probabilidad`. |
| Concepción | DEFINIDO | Una por pareja y ciclo; disponible desde el ciclo siguiente durante tres ciclos; idempotente. |
| Aforo para nacimiento | DEFINIDO | Hay aforo si `COUNT(animales VIVO del espacio) < capacidad_max`; sin aforo la aceptación se rechaza sin cambiar el estado ni consumir la notificación. |
| Datos iniciales de la cría | DEFINIDO | Misma especie, nivel 1, alimentación 100%, sana, alojada en el espacio de los progenitores; `id_padre` es el progenitor de identificador menor e `id_madre` el de mayor; hermanos derivados; satisfacción calculada tras completar la ficha. |
| Límite de hermanos | DEFINIDO | `C_hermanos = min(1, hermanos_vivos / tope_hermanos)` con `tope_hermanos` por especie (2, 3 o 4). |

## 6. Ciclos y simulación offline

| Cálculo o valor | Estado | Definición |
|---|---|---|
| Periodicidad del ciclo | DEFINIDO | 10 minutos (RF-14). |
| Número de ciclos offline y remanente | DEFINIDO | Fórmulas de RF-21 y RF-24 con tope `retencion.max_ciclos_offline = 1008`. |
| Descenso de alimentación | DEFINIDO | 20 puntos por ciclo ordinario; en offline, con los límites de RF-22. |
| Enfermedad | DEFINIDO | Un animal vivo, alojado y sano enferma si `aleatorio < probabilidad_enfermedad(rango) * (1 - satisfaccion_actual)`. Probabilidad por rango: `0.020`, `0.020`, `0.025`, `0.025`, `0.030`. En offline el sorteo se detiene al enfermar y rigen los límites de RF-23. |
| Ciclos críticos | DEFINIDO | El ciclo que deja al animal con alimentación `0%` o enfermo cuenta como el primero; contadores independientes; defunción al llegar a `3`. |
| Nivel del animal | DEFINIDO | Sustituye a la edad y a la longevidad. Nivel inicial `1` (compra, recompensa y cría); `+1` cada `72` ciclos en que el animal sigue vivo, sano y con alimentación mayor que `0`; tope por rango en `tablas.rangos_especie[].nivel_maximo` (`15`, `29`, `41`, `51`, `61`, es decir, 7, 14, 20, 25 y 30 días); nunca disminuye; nivel mínimo de pareja `4`. Sin muerte por vejez. Detalle: RF-51 y `EspecificacionBBDD.md`, secciones 1.4 y 2.3.1. |
| Orden del ciclo | DEFINIDO | Alimentación, salud, ciclos críticos, defunciones, nivel, reproducción, beneficios, misiones, métricas y persistencia (RF-14). |

## 7. Capacidad y compatibilidad

| Elemento | Estado | Definición |
|---|---|---|
| Capacidad positiva | DEFINIDO | `capacidad_max > 0` y presente en `CosteEspacio` (6, 8, 10, 15, 20, 25 o 30); solo puede aumentar mediante la ampliación (`Otros/DecisionesImplementacion.md`, sección 2.3). |
| Aforo usado | DEFINIDO | Animales `VIVO` con espacio asignado, contados dentro de la transacción. |
| Compatibilidad especie-bioma | DEFINIDO | 235 factores: `1.00` primario, `0.75` secundario, `0.40` neutro y `0.20` incompatible por clima. |
| Compatibilidad del espacio | DEFINIDO | Bioma del hábitat del espacio y especie; factor de satisfacción, no condición de ubicación. |

## 8. Misiones y recompensas

| Elemento | Estado | Definición |
|---|---|---|
| Progreso de misión | DEFINIDO | `Misiones/EspecificacionMisiones.md` (secciones 3, 4 y 4.1): contadores acumulativos y valores dinámicos en `ProgresoMision.progreso`; progresión con aritmética exacta, redondeo medio hacia arriba y límites mínimo y máximo; agotamiento al superar el máximo. |
| Cumplimiento de misión | DEFINIDO | Pasa a `CUMPLIDA` inmediatamente al alcanzar su objetivo. Valores de objetivo en el JSON (sección 8 del documento de misiones). |
| Recompensa | DEFINIDO | Fondos, alimento, insumo o animal aleatorio, una sola vez. Cantidades por misión en el JSON. |
| Animal aleatorio | DEFINIDO | Lista de las nueve exclusivas con probabilidades `10, 12, 14, 12, 14, 12, 6, 12, 8` (suma 100). Disponible en tienda desde la reclamación, con coste cero y una sola compra (`unidad_pendiente`). |
| Desbloqueo de especie exclusiva | DEFINIDO | Solo por recompensa (RF-38, RF-49, RES-09). |

## 9. Avisos, estados y auditoría

| Elemento | Estado | Definición |
|---|---|---|
| Transiciones de estado | DEFINIDO | `EspecificacionBBDD.md`, sección 2.3.2. |
| Severidad de avisos | DEFINIDO | Informativa = `BAJA`, operativa de reproducción = `MEDIA`, crítica de salud = `ALTA`; fija por clase. |
| Datos del panel crítico | DEFINIDO | Contadores separados cuando concurren; ciclos restantes `3 - max(contadores)`; el animal se identifica por nombre. |
| Eventos auditables | DEFINIDO | `EspecificacionBBDD.md`, sección 3.1; resultados `CONFIRMADO`, `RECHAZADO` y `REVERTIDO`. |
| Claves de idempotencia | DEFINIDO | `EspecificacionBBDD.md`, sección 3 (tabla de claves por operación). |

## 10. Tablas y catálogos

Todos están completos en `valores_y_pesos.json`:

- 5 biomas y 5 hábitats (uno por bioma).
- 47 especies (38 comerciales y 9 exclusivas), cinco rangos, pesos de satisfacción y sociales, `tope_hermanos` y kilogramos de bambú por incremento; cada rango incluye además beneficio base, nivel máximo y multiplicador de nivel máximo.
- 235 compatibilidades especie-bioma, 1128 afinidades entre pares de especies (incluidas las diagonales) y 47 curvas de `M_crit`.
- Un alimento (bambú) y una medicina (identificador `insumo_medico`), con sus precios y cantidades.
- 12 misiones con parámetros, progresión y recompensas, la lista de recompensas de animal aleatorio y sus tablas de sorteo por rango (`sorteo_prestigio`, `partida_inicial.bienvenida`).
- Catálogos cerrados de 10 rasgos de personalidad y 10 habilidades sociales (descriptivos; no intervienen en ninguna fórmula).
- Estados y transiciones: `EspecificacionBBDD.md`, sección 2.3.2. Valores iniciales de la partida: sección 2.4 y `partida_inicial`.

Valores por defecto del DDL: se eliminan los valores por defecto de `tope_hermanos`, fondos y satisfacción (se cargan de la configuración o se calculan); se ratifica la alimentación inicial del 100% y la salud inicial `SANO`.

## 11. Dependencias y contradicciones resueltas

1. Satisfacción: fórmulas, rangos y valores completos, incluidas las afinidades diagonales.
2. Prestigio: base, multiplicadores, límites y nivel máximo definidos.
3. RF-15: beneficio definido por la fórmula de la sección 4.
4. RF-16: umbral y probabilidad por rango definidos.
5. RF-14, RF-19 y RF-51: sin edad ni longevidad; nivel por ciclo sano definido.
6. RF-21 y RF-24: remanente y ciclos offline definidos.
7. Los valores por defecto del DDL están ratificados, sustituidos o eliminados (sección 10).
8. Duplicados del JSON eliminados: las probabilidades de reproducción y penalización por hijo viven solo en `rangos_especie`; `desbloqueos_prestigio` es la única fuente de desbloqueos.
