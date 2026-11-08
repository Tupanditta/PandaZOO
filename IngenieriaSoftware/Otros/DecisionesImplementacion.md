# Decisiones de implementación posteriores al Sprint N

Este documento recoge las decisiones que la aplicación ya cumple y que las demás especificaciones no describían o describían de otra forma. Es la fuente de verdad de cada decisión listada. Donde otro documento diga algo distinto, gana este y el otro lleva una nota que remite aquí. Cada decisión indica qué sustituye y dónde vive en el código.

Convenciones: `valores_y_pesos.json` sigue siendo la fuente única de los valores configurables. Las pocas constantes que viven en el código, y no en el JSON, están reunidas en la sección 12.

## Índice

1. Partida nueva y regalo de bienvenida
2. Espacios: capacidades, primer espacio gratuito, ampliar y vender
3. Mejor amigo recíproco
4. Alimentación: la ración crece con el nivel y la acción por hábitat
5. Misiones: orden, misión de prestigio y unidades gratuitas
6. Prestigio: no baja y llega al 100
7. Avisos: descartar y caducidad
8. Consultas nuevas: estadísticas del animal, desglose de beneficios y guía de convivencia
9. Datos de configuración modificados
10. Esquema de datos y migraciones (versión 12)
11. Interfaz, terminología y funciones retiradas
12. Constantes que viven en el código

---

## 1. Partida nueva y regalo de bienvenida

**Sustituye a:** `EspecificacionBBDD.md` sección 2.4 (inicialización) en lo que añade; `sprints.md` Sprint 0.

Al crear la partida, en la misma transacción, el sistema concede el regalo de bienvenida. Solo ocurre una vez, al crear la partida; una partida existente no lo recibe de nuevo.

| Parte | Contenido |
|---|---|
| Fondos, bambú y medicinas | Los de `partida_inicial` (`2000.00`, `30` kg de bambú, `3` medicinas) |
| Animales comunes | `partida_inicial.bienvenida.animales_comunes` (`3`): especies comerciales de rango 1, distintas entre sí, elegidas al azar |
| Animales poco comunes | `partida_inicial.bienvenida.animales_poco_comunes` (`1`): una especie comercial de rango 2, elegida al azar |
| Animales sorteados | Un animal por cada tabla de `partida_inicial.bienvenida.sorteos` (`2` tablas). Primero se sortea el rango con las probabilidades de la tabla y después la especie dentro del rango |

Reglas del sorteo:

- Los animales de regalo son de especies distintas entre sí. Las especies ya elegidas se excluyen del sorteo de las tablas; si un rango se queda sin ninguna otra especie, la exclusión se ignora para ese rango.
- El prestigio no interviene: se sortean también especies que la tienda mantiene bloqueadas.
- Dentro de un rango 2 a 4 todas las especies comerciales pesan igual. En el rango 5 pesan las probabilidades de `recompensas_animales_aleatorios.especies`.
- Cada tabla debe sumar `100` y usar solo los rangos `2` a `5`. El validador de configuración lo comprueba y una configuración inválida impide crear la partida.
- Los animales no se crean: cada uno es una **unidad gratuita**. Se guarda en `UnidadGratuita` con origen `BIENVENIDA` y `unidad_pendiente = 1`. El jugador la compra después en la tienda de animales con coste `0`, por el flujo normal de CU-01 (ficha, aforo, satisfacción). Esa compra consume la unidad en la misma transacción, y si se rechaza no la consume.
- Una unidad de bienvenida **no desbloquea la especie**: si la especie estaba bloqueada, sigue bloqueada tras comprar su unidad.
- La tienda de animales ofrece una especie con unidad pendiente como gratuita, con prioridad sobre su estado de catálogo (comercial, bloqueada o exclusiva).

La interfaz muestra el regalo en ventanas flotantes, una por recurso y de una en una: fondos, bambú, medicinas y cada animal. La ventana de un animal sorteado entre varios rangos muestra primero la tabla de probabilidades y un botón de suerte, y después el animal. Un animal con un único rango posible se entrega sin tabla.

## 2. Espacios: capacidades, primer espacio gratuito, ampliar y vender

**Sustituye a:** CU-03, CU-14 (y su descripción en `CatalogoCasosUso.md`), RF-07, `EspecificacionBBDD.md` secciones 2.1 punto 2, 2.3.2 (fila `Habitat`, `Espacio`), 5 y 5.1, y las capacidades 2 y 4.

### 2.1. Capacidades y coste

Las capacidades de espacio son `6`, `8`, `10`, `15`, `20`, `25` y `30` animales. No existen espacios de 2 ni de 4. El coste de cada una está en `tablas.costes_espacios`. La capacidad de un espacio solo puede crecer (sección 2.3).

### 2.2. Primer espacio gratuito

- El primer espacio de la capacidad `partida_inicial.espacio_gratuito.capacidad` (`6`) que adquiere la partida cuesta `0.00`. Los demás cuestan lo indicado en `costes_espacios`.
- La partida lo marca con `Partida.espacio_gratuito_disponible` (`1` al crear la partida). Adquirir un espacio de esa capacidad mientras la marca vale `1` la pone a `0` en la misma transacción.
- Esa adquisición no modifica los fondos, no registra `MovimientoEconomico` y deja el espacio con `coste_pagado = 0`.
- Vender ese espacio no restituye la marca: el beneficio se usa una sola vez por partida.
- La oferta de capacidades de CU-03 muestra coste `0` en la capacidad gratuita mientras siga disponible.

### 2.3. Ampliar un espacio

Operación nueva, registrada bajo CU-03 con tipo de operación `AMPLIACION_ESPACIO` y entidad `ESPACIO`.

- Precondiciones: el espacio existe y está activo; la nueva capacidad existe en `CosteEspacio`; la nueva capacidad es **mayor** que la actual. Si no lo es, se rechaza con `CAPACIDAD_NO_AMPLIABLE`. Nunca se reduce una capacidad.
- Coste: `redondear_medio_arriba((coste(nueva) - coste(actual)) × 1,25)`. Es la diferencia de precio más un 25 % de recargo. Si la capacidad actual no figura en `CosteEspacio`, se toma su coste como `0`.
- Si el saldo no alcanza se rechaza con saldo insuficiente, sin cambios.
- Efectos, en una transacción: descuenta los fondos, registra un `MovimientoEconomico` de tipo `COMPRA` con origen `CU-03`, sube `Espacio.capacidad_max` y suma el coste de la ampliación a `Espacio.coste_pagado`.
- Idempotencia: `id_partida + AMPLIACION_ESPACIO + id_solicitud`; la huella es el espacio y la capacidad pedida.
- La interfaz pide confirmación en una ventana con el importe.

Sustituye a la regla de que la capacidad es inmutable (`EspecificacionBBDD.md` 5, última frase; RF y CU que la citan).

### 2.4. Vender un espacio vacío (antes «dar de baja»)

CU-14 pasa de baja lógica a **venta con borrado**.

- Precondiciones sin cambio: el espacio existe, está activo, no tiene animales vivos y no tiene dependencias (un aviso abierto que lo referencie o una concepción pendiente de un progenitor alojado en él). Si tiene dependencias se rechaza con `ESPACIO_CON_DEPENDENCIAS`.
- El espacio se **borra físicamente**, junto con su fila de `MetricaSatisfaccion` y los avisos de entidad `ESPACIO` que lo referencian. Ya no existe el estado `INACTIVO` en la práctica: ningún espacio pasa a ese estado. La columna y su `CHECK` se conservan por compatibilidad, y la migración 7 retira los espacios que habían quedado `INACTIVO` sin animales.
- Reembolso: el gestor recupera **todo el dinero que costó el espacio**, es decir `Espacio.coste_pagado` (compra más ampliaciones). El espacio gratuito reembolsa `0`.
- Si el reembolso es mayor que `0`, se acreditan los fondos y se registra un `MovimientoEconomico` de tipo `VENTA` con origen `CU-14`. Con reembolso `0` no se registra movimiento.
- El resultado de la operación incluye el reembolso, que se persiste para que un reintento con la misma clave lo devuelva sin repetir efectos.
- Las consultas de espacios de un hábitat y la selección de espacios para el recálculo de satisfacción solo incluyen los espacios activos.
- La interfaz ofrece «Vender espacio» en la pantalla de animales del espacio cuando está vacío y pide confirmación en una ventana que muestra lo que se recupera. El mensaje de éxito es «Espacio vendido. Recuperas <importe>.» o, sin reembolso, «Espacio vendido.».
- El tipo de auditoría `BAJA_ESPACIO` se conserva como código de la operación.

### 2.5. Compra de un animal desde un espacio

La pantalla de animales de un espacio ofrece «Comprar un animal para este espacio». La tienda y la ficha de compra reciben el espacio, que queda fijo como destino y no se vuelve a elegir; al terminar se vuelve al espacio. La compra sigue siendo CU-01.

## 3. Mejor amigo recíproco

**Sustituye a:** `sprints.md` Sprint 3 («el mejor amigo no es recíproco»; «un traslado conserva pareja y mejor amigo»), CU-01 paso 5.3, CU-05 3.d, CU-10 3.d, CU-04, `EspecificacionBBDD.md` 2.1 punto 4.

- El vínculo de mejor amigo es **recíproco**, igual que el de pareja: si B es mejor amigo de A, A lo es de B. Se guarda en ambas filas.
- Ambos animales deben ser vivos, estar en el mismo espacio y ser distintos. La especie no importa.
- Ambos deben estar **libres**: si cualquiera de los dos ya tiene otro mejor amigo, se rechaza con `AMIGO_OCUPADO`. Para cambiar de amigo hay que quitar primero el actual. Volver a elegir al amigo que ya se tiene no se rechaza.
- Quitar el mejor amigo (o fijar `NULL`) libera a los dos animales.
- Al crear un animal con mejor amigo (CU-10, cría) el vínculo también queda en ambos.
- **Trasladar un animal rompe su vínculo de mejor amigo** (CU-04), porque los mejores amigos comparten espacio. El recálculo de satisfacción incluye entonces el espacio de origen, el de destino y el espacio del antiguo amigo. La pareja no cambia.
- La migración 10 repara las partidas existentes: si A tiene a B de amigo y B no tiene ninguno, B pasa a tener a A (con varios candidatos, el de menor identificador); los vínculos que sigan sin reciprocidad se quitan.
- Para `C_afinidad` el mejor amigo sigue sin ser familiar, pero ahora el vínculo es simétrico, así que la fórmula de `S_soc` de ambos animales lo cuenta.

## 4. Alimentación

**Sustituye a:** `EspecificacionBBDD.md` 1.2, CU-13, RF de alimentación.

### 4.1. La ración crece con el nivel del animal

La cantidad base por especie (`tablas.alimentos_por_especie`, de 1 a 5 kg) corresponde a un animal de nivel 1. Cada nivel por encima del 1 suma un 5 % a lo que pide cada ración:

`kg_racion = ⌈ kg_base × (19 + nivel) / 20 ⌉`

Es decir, el nivel 1 usa `kg_base`, el nivel 2 usa `kg_base × 1,05`, y así hasta el tope del rango, siempre redondeado hacia arriba a un entero. Esta fórmula se usa en la alimentación individual (CU-13), en la grupal por espacio y por hábitat, y en todas las vistas previas (la ficha del animal y las ventanas de confirmación). Para llevar un animal al 100 % desde su alimentación `a`, el consumo es `kg_racion × (100 − a) / 10` (la alimentación siempre es múltiplo de 10).

### 4.2. Alimentar a todos, por espacio y por hábitat

Además de la alimentación individual y la grupal por espacio, existe la grupal **por hábitat** (CU-13), que alimenta a todos los animales vivos por debajo del 100 % de **todos los espacios activos del hábitat**.

- Atómica: si el bambú no alcanza para todos, no cambia nada. Resultado: animales alimentados, kilos consumidos y bambú restante.
- La solicitud lleva los kilos que el gestor vio. Si el total vigente es distinto, se rechaza con `CONJUNTO_CAMBIADO` (con el nuevo total) para que confirme de nuevo.
- Si ningún animal está por debajo del 100 %, se rechaza con `NADA_QUE_ALIMENTAR`.
- Como en las demás acciones de cuidado, resuelve la alerta de salud de cada animal alimentado y recalcula los espacios afectados.
- Tipo de operación `ALIMENTACION_HABITAT`, entidad `HABITAT`; clave de idempotencia `id_partida + ALIMENTACION_HABITAT + id_habitat + id_solicitud`.
- La interfaz pide confirmación en una ventana con el número de animales, el bambú necesario y el bambú en almacén. Si falta bambú, la cifra se marca en rojo y la operación se rechaza.

## 5. Misiones

**Sustituye a:** `Misiones/EspecificacionMisiones.md` (orden de la lista, misión de prestigio y reclamación de especie), CU-15, CU-20, `EspecificacionBBDD.md` 1.3 y 2.1 puntos 13 y 15, `sprints.md` Sprint 9.

### 5.1. Orden de la lista (CU-20)

La lista de misiones vigentes se ordena así:

1. Primero las **cumplidas pendientes de reclamar**, en el orden en que se cumplieron.
2. Después el resto, de más a menos avance (`progreso / objetivo`, acotado a `0..1`).
3. A igual avance, la que menos le falta para completarse; y a igual falta, por identificador de instancia.

El orden de cumplimiento se guarda en `ProgresoMision.orden_cumplida` (`1, 2, 3…` dentro de la partida). Se asigna **una sola vez**, al pasar la instancia a `CUMPLIDA` (el siguiente número libre), y queda `NULL` mientras no esté cumplida. Cada misión muestra una barra con su avance.

### 5.2. La misión de prestigio

- El tipo `subir_prestigio` crece hasta el prestigio `100` (`progresion_parametros.nivel_prestigio_requerido`, máximo `100`). Su texto es «Alcanza el prestigio N.».
- Su recompensa es **siempre un animal**. La especie no sale de la lista común: se sortea primero el **rango** y después la especie al azar dentro del rango (rango exclusivo: con las probabilidades de `recompensas_animales_aleatorios.especies`).
- La tabla de rangos depende del prestigio pedido por la instancia (`recompensas_animales_aleatorios.sorteo_prestigio`): múltiplo de 10 usa `multiplo_10` (siempre exclusivo); si no, múltiplo de 5 usa `multiplo_5`; en los demás casos, `base`. Cada tabla suma `100` y usa rangos `2` a `5`.
- Los dos números aleatorios (rango y especie) se obtienen del `Azar` del contenedor, en ese orden. El resto de misiones con recompensa de especie sortean con la lista común y un solo número.
- El resultado persiste el rango y la tabla usada, para que un reintento con la misma clave devuelva exactamente el mismo resultado sin volver a sortear.
- La lista de misiones muestra «Un animal exclusivo, gratis» solo cuando el animal es seguro exclusivo (múltiplo de 10); en otro caso, «Un animal aleatorio, gratis».

### 5.3. Reclamar una recompensa de animal

Reclamar una recompensa de especie **solo concede una unidad gratuita**: persiste la especie sorteada en `RecompensaMision` con `unidad_pendiente = 1`. **No desbloquea la especie** en la tienda ni emite el aviso `ESPECIE_DESBLOQUEADA`. Esto sustituye a «la especie se desbloquea (`DesbloqueoEspecie`, origen `RECOMPENSA`)». El origen `RECOMPENSA` queda sin uso en partidas nuevas.

La interfaz muestra el resultado en una ventana de premio, con la misma ventana de suerte que el regalo de bienvenida (sección 1) cuando el rango se sortea entre varios. El mensaje final es «Te ha tocado X: puedes comprarlo gratis en la tienda de animales.» y, según el caso, indica si la misión se renovó o no volverá a aparecer.

## 6. Prestigio

**Sustituye a:** `EspecificacionBBDD.md` 4.4 y 2.1 punto 14, CU-19, RF-35/RF-36 en lo que dicen del descenso.

- **El prestigio no baja.** El nivel guardado tras un recálculo es el mayor entre el nivel ya alcanzado y el que resultan de los umbrales. Si las estadísticas caen, el nivel se conserva. Los máximos históricos se siguen actualizando como hasta ahora.
- El nivel máximo es `100`. La tabla vigente es `tablas.niveles_prestigio` (100 niveles, escrita a mano); la fórmula geométrica de `multiplicadores_nivel_siguiente` ya no la genera.
- Los desbloqueos por nivel son los de `tablas.desbloqueos_prestigio`, que reparten las especies comerciales mezclando rangos a lo largo de los 100 niveles. Se conserva la regla de aplicar todos los desbloqueos de niveles menores o iguales al alcanzado.
- El texto de usuario dice «prestigio N», no «nivel de prestigio N». El aviso de ascenso dice «Has alcanzado el prestigio N.».
- El panel de prestigio (CU-19) muestra, además del nivel siguiente y sus umbrales, **qué especies y qué hábitats desbloquea el nivel siguiente**, por nombre (los hábitats sin la palabra «Hábitat»). Si se alcanzó el máximo, lo dice. Si el nivel siguiente no abre nada, no muestra el apartado.
- En la tienda de animales, una especie bloqueada muestra el prestigio que la desbloquea.

## 7. Avisos

**Sustituye a:** `EspecificacionBBDD.md` 2.3.2 (estados de `Notificacion`) y 5.2, CU-21 en lo que añade.

### 7.1. Descartar un aviso

El gestor puede descartar un aviso de severidad **baja** o **media** (en la interfaz, deslizándolo). Operación nueva con tipo `AVISO_DESCARTADO`, entidad `NOTIFICACION`, origen CU-21.

- Un aviso de severidad **alta** (la alerta de salud crítica) no se descarta: se rechaza con `AVISO_NO_DESCARTABLE`. Se cierra solo cuando se resuelve su causa.
- Un aviso inexistente se rechaza con `AVISO_INEXISTENTE`.
- Efecto: el aviso queda cerrado. Los informativos pasan a `VISTA` y los demás a `DESCARTADA`. `ciclo_cierre` es el mayor entre el ciclo del aviso y el ciclo vigente. Un aviso ya cerrado no cambia.

### 7.2. Caducidad

En la purga posterior a cada ciclo confirmado se cierran por caducidad los avisos abiertos:

- los de severidad **baja** cuyo ciclo es anterior al vigente (duran un ciclo);
- los de severidad **media** cuyo ciclo es como máximo `vigente − 3` (duran tres ciclos).

Se cierran con el mismo estado que al descartarlos. Quedan fuera de esta regla los de severidad alta y la oferta de cría (`REPRODUCCION_DISPONIBLE`), que tiene su propia caducidad de tres ciclos disponibles. Una vez cerrados, se rigen por la ventana de retención de `notificacion_cerrada`.

### 7.3. Presentación

La pantalla de avisos muestra solo los avisos **abiertos** (sin cierre), con un filtro por severidad: todos, alta, media o baja. Los cerrados dejan de mostrarse aunque la base de datos los conserve durante su ventana de retención. Si hay dos o más alertas de salud abiertas, la lista de avisos muestra una sola fila «N animales en estado crítico.» que lleva al seguimiento crítico. Con una sola alerta se muestra el aviso normal.

## 8. Consultas nuevas

### 8.1. Estadísticas de un animal

Consulta de solo lectura sobre un animal vivo y alojado. No abre transacción, no audita y no cuenta como recálculo. Calcula con el estado confirmado el desglose de `S_i`: `S_bio`, `S_soc`, `M_crit`, sus pesos, las seis partes de `S_soc` con su peso y su grado, y los factores `M_alimentación` y `M_enfermedad`. También devuelve el desglose del beneficio: beneficio base, nivel, `M_nivel`, satisfacción y beneficio por ciclo. Devuelve vacío si el animal no existe, no está vivo o no está alojado.

La ficha del animal solo muestra de ese desglose las **afinidades**: la afinidad con las especies de su espacio y la afinidad con su hábitat, en porcentaje. El resto no se muestra para no exponer la fórmula.

### 8.2. Desglose de beneficios

El apartado de beneficios (CU-16 y CU-08) ofrece un desglose por **hábitat → espacio → animal** del beneficio por ciclo. Aplica a lo persistido la misma fórmula que el ciclo, `b_i = base × S_i × M_nivel`. Cada total se redondea desde la suma **sin redondear** de sus animales, de modo que los redondeos no se acumulan. Solo se incluyen los animales vivos alojados; los espacios y hábitats sin animales no aparecen. Es solo lectura y no cambia el importe pendiente de reclamar.

### 8.3. Guía de convivencia

Consulta de catálogo, independiente de la partida y de solo lectura, accesible desde el icono de guía de la cabecera. Reutiliza `afinidad(especie, especie)` y `p_hab(especie, bioma)` de la regla de satisfacción y las traduce a tres niveles. **Nunca muestra el valor numérico.**

| Relación | Bien | Regular | Mal |
|---|---|---|---|
| Entre dos especies (afinidad) | `≥ 0,60` | `≥ 0,35` y `< 0,60` | `< 0,35` |
| Especie y hábitat (compatibilidad) | `≥ 0,75` | `≥ 0,40` y `< 0,75` | `< 0,40` |

- Incluye **todas las especies**, también las bloqueadas y las exclusivas, ordenadas por rango y nombre, y todos los hábitats.
- La pantalla «Guía» tiene por ahora un único apartado, «Convivencia», con dos pestañas:
  - **Hábitats**: tabla de especies (filas) por hábitats (columnas), con una cara por celda.
  - **Animales**: se elige una especie y se ve cómo se lleva con todas las demás, en tres bloques plegables: «Se llevan bien», «Indiferentes» (regular) y «Se llevan mal». El bloque «Indiferentes» empieza plegado. La especie elegida no aparece en su propia lista.
- Las caritas son 😊 (bien), 😐 (regular) y ☹ (mal), siempre con su texto en la leyenda: el estado no se comunica solo con la cara ni con el color.

## 9. Datos de configuración modificados

Los valores concretos siguen en `valores_y_pesos.json`; esta tabla recoge qué cambió respecto a los textos anteriores de la documentación, que ya no deben citar las cifras antiguas.

| Dato | Antes | Ahora |
|---|---|---|
| Nombre del insumo médico | «Insumo médico» | «Medicina» (el identificador interno `insumo_medico` no cambia) |
| Capacidades y costes de espacio | 2, 4, 6, 8, 10, 15 y 20 animales; `100` a `700` | 6, 8, 10, 15, 20, 25 y 30 animales; `325`, `415`, `520`, `715`, `910`, `1105` y `1300` |
| Coste de compra por rango (1 a 4) | `200`, `450`, `900`, `1800` | `40`, `160`, `450`, `700` |
| `valor_venta_base` por rango (1 a 4) | `200`, `450`, `900`, `1800` | `40`, `160`, `450`, `700` (igual al coste de compra); el rango 5 sigue en `4000` |
| Beneficio base por animal y ciclo | `12`, `24`, `40`, `64`, `100` | `6`, `12`, `20`, `32`, `50` |
| Probabilidad de enfermedad por rango | `0,010`, `0,010`, `0,015`, `0,015`, `0,020` | `0,020`, `0,020`, `0,025`, `0,025`, `0,030` |
| Niveles de prestigio | 10 | 100, con la tabla escrita a mano |
| Desbloqueos por prestigio | 10 niveles | repartidos en 100 niveles, mezclando rangos (el primer épico llega al prestigio 22) |
| Misión de prestigio | hasta el prestigio 10 | hasta el 100, siempre un animal |
| Afinidades con el panda | `0,10`–`0,20` con la mayoría de especies | `0,90`; además se subieron algunas parejas concretas (canguro–ballena azul, capibara–nutria, etc.) |
| Datos nuevos | | `partida_inicial.espacio_gratuito`, `partida_inicial.bienvenida`, `recompensas_animales_aleatorios.sorteo_prestigio` |

El coste de compra de los rangos 1 a 4 se calibró para que cada compra se amortice en 72 ciclos o menos con `S_i = 0,5` y en 90 o menos con `S_i = 0,4` (`tablas.costes_rangos_nota`).

## 10. Esquema de datos y migraciones (versión 12)

La base de datos pasa de la versión 3 a la **12** (`PRAGMA user_version`). Cada migración se aplica en orden y una sola vez, y las partidas existentes se conservan salvo en la versión 9.

Cambios en el DDL vigente (`data/schema/esquema.sql`):

| Tabla | Cambio |
|---|---|
| `Partida` | Columna `espacio_gratuito_disponible INTEGER NOT NULL DEFAULT 1 CHECK (IN (0,1))` |
| `Espacio` | Columna `coste_pagado REAL NOT NULL DEFAULT 0 CHECK (coste_pagado >= 0 AND coste_pagado = ROUND(coste_pagado, 2))` |
| `ProgresoMision` | Columna `orden_cumplida INTEGER` (nula si no está cumplida) |
| `UnidadGratuita` | Tabla nueva: `id_unidad` (clave), `id_partida`, `id_especie`, `origen` (`BIENVENIDA`), `unidad_pendiente` (`0` o `1`, por defecto `1`) y `fecha`; claves foráneas a `Partida` y `Especie` |

| Versión | Qué hace |
|---|---|
| 2 | La partida nueva empieza con 2000 monedas; las de la versión 1 reciben una compensación única de 1000 |
| 3 | Niveles de animal en lugar de edad; beneficio y venta según nivel (Sprint N) |
| 4 | `ProgresoMision.orden_cumplida`: las ya cumplidas se numeran por identificador |
| 5 | El beneficio base de cada rango baja a la mitad |
| 6 | Nuevo desbloqueo de especies por prestigio; se retiran los desbloqueos por prestigio que aún no tocan (los iniciales, los de recompensa y los animales ya comprados se conservan); la misión de prestigio llega al 100 |
| 7 | Nuevos costes de espacio y probabilidad de enfermedad; se retiran los espacios `INACTIVO` sin animales |
| 8 | Nuevo reparto de desbloqueos mezclando rangos |
| 9 | **Reinicio total**: se borran todos los datos y se recrea el esquema vigente. La siguiente apertura crea una partida nueva con su regalo de bienvenida. Es la única migración que pierde la partida |
| 10 | Mejor amigo recíproco (sección 3) |
| 11 | `Espacio.coste_pagado`: los espacios existentes reciben el precio actual de su capacidad como mejor aproximación, porque no se conservaba cuánto costó cada uno |
| 12 | Se añaden las capacidades de 25 y 30 animales a `CosteEspacio` |
| 13 | Animales más caros y reproducción más difícil (sección 12): coste y venta base ×5 en los rangos 1 a 4, probabilidad de reproducción reducida y columna `RangoEspecie.factores_hijos` |
| 14 | La diagonal de `AfinidadEspecies` pasa a `0.5` (sección 14) |
| 15 | Regalos semanales (sección 16): tabla `RegaloSemanal` y origen `SEMANAL` en `UnidadGratuita` |
| 16 | Se quitan los mejores amigos que son familiares (sección 17) |
| 17 | El panda tiene compatibilidad `1.0` con todos los hábitats (sección 19) |
| 18 | El beneficio base por animal y ciclo baja un 60 % (sección 20) |

El motor de pruebas puede abrir una base migrando solo hasta una versión intermedia, para probar las migraciones anteriores al reinicio de la versión 9.

## 11. Interfaz, terminología y funciones retiradas

- **Terminología.** Se dice «medicina» (singular) y «medicinas», no «insumo médico». Se dice «prestigio N», no «nivel de prestigio N». Los hábitats se muestran sin la palabra «Hábitat» («Bosque templado»). El bambú se muestra en kilogramos.
- **Modo de depuración retirado.** Ya no existen el botón «Adelantar ciclo», el generador de la carga de referencia, «añadir un panda» ni la función de borrar la carga de prueba, ni la señal de compilación de depuración en los contenedores. El mismo caso de uso CU-11 sigue siendo la única forma de ejecutar un ciclo. La carga de referencia de 500 animales y 50 espacios se conserva **solo como código de pruebas** (`DatosDePrueba`, usado por los tests de rendimiento). Esto sustituye al modo de depuración de `design.md` 2.5 y de `sprints.md` Sprints 2 y 5.
- **Unidad de bienvenida e interfaz.** Las ventanas flotantes de regalo y de premio se describen en las secciones 1 y 5.3.
- La identidad visual, la cabecera, la barra inferior y las ventanas de confirmación están en `Interfaz/EspecificacionInterfaz.md`, revisado en este mismo cambio.

## 12. Constantes que viven en el código

Estas constantes no están en `valores_y_pesos.json`. Aquí es donde se documentan; cambiarlas exige un cambio de código y de este documento.

| Constante | Valor | Dónde |
|---|---|---|
| Incremento de la ración por nivel | `5 %` por nivel sobre el 1 (`kg_racion = ⌈kg_base × (19 + nivel) / 20⌉`) | Dominio, `ReglasCuidado` |
| Recargo de ampliación de espacio | `25 %` sobre la diferencia de precio | `AmpliarEspacio` |
| Capacidad del primer espacio gratuito | `6` (también en `partida_inicial.espacio_gratuito.capacidad`; el caso de uso usa la constante) | `AdquirirEspacio` |
| Umbrales de la guía: afinidad | bien `≥ 0,60`, regular `≥ 0,35` | `ConsultarGuiaConvivencia` |
| Umbrales de la guía: hábitat | bien `≥ 0,75`, regular `≥ 0,40` | `ConsultarGuiaConvivencia` |
| Duración de un aviso de severidad media | `3` ciclos | `RetencionSql` |
| Duración de un aviso de severidad baja | `1` ciclo | `RetencionSql` |
| Rangos sorteables en bienvenida y prestigio | `2` a `5` (los de bienvenida comunes y poco comunes son fijos: rangos `1` y `2`) | Validador y `InicializadorPartida` |

## 12. Precios de los animales y reproducción (versión 13)

Se cambia el balance para que formar familias sea posible pero lento y para que comprar animales sea más caro.

| Dato | Antes | Ahora |
|---|---|---|
| Coste de compra, rangos 1 a 4 | `40`, `160`, `450`, `700` | `200`, `800`, `2250`, `3500` (×5) |
| `valor_venta_base`, rangos 1 a 4 | igual al coste | igual al coste nuevo (el validador lo exige); rango 5 sigue en `4000` |
| Nivel mínimo para formar pareja | `4` | `1` (sin mínimo práctico) |
| Probabilidad base de reproducción por ciclo (rangos 1 a 5) | `4 %`, `3 %`, `2 %`, `1,5 %`, `1 %` | `3 %`, `2,25 %`, `1,5 %`, `1,1 %`, `0,75 %` |
| Efecto de los hijos vivos | `p − penalización × hijos` | `p × factor(hijos vivos)` con la tabla `factores_hijos` |
| Máximo de hijos vivos comunes | sin tope | 5 (el factor con 5 hijos es 0) |

Factores por rango según los hijos vivos comunes (0, 1, 2, 3, 4 y 5): rango 1 `1; 0,125; 0,0625; 0,04; 0,01; 0`, rango 2 `1; 0,105; 0,04425; 0,018; 0,003; 0`, rango 3 `1; 0,0885; 0,0375; 0,017; 0,001; 0`, rango 4 `1; 0,07425; 0,03; 0,015; 0,001; 0` y rango 5 `1; 0,0625; 0,025; 0,013; 0,001; 0`.

La columna `penalizacion_hijo_reproduccion` queda en desuso con valor `0`. Los beneficios no cambian, así que la amortización se alarga (nota `tablas.costes_rangos_nota`). La migración 12 a 13 fija estos valores en las partidas existentes. No se añade ninguna forma de subir de nivel con dinero.

## 13. Afinidad con los familiares

`C_afinidad` es la media de afinidad con **todos** los demás animales vivos del espacio. Antes excluía a la pareja, el padre, la madre, los hermanos y los hijos (solo contaba a los no familiares, y el mejor amigo). Ahora los familiares cuentan como cualquier otro animal; los de la misma especie aportan el valor diagonal de la matriz de afinidad. Un animal sin otros en el espacio sigue teniendo `C_afinidad = 0`. No cambia ningún dato de configuración ni el esquema, así que no hace falta migración; la satisfacción se recalcula con la regla nueva en el siguiente evento.

## 14. Afinidad entre animales de la misma especie y familiar (versión 14)

Se sustituye la regla de la sección 13 para que en un espacio convenga tener familias o especies distintas:

| Pareja de animales en el espacio | Afinidad |
|---|---|
| Familiares (pareja, hijos, padres y hermanos) | `0.8` (`matriz_afinidad.afinidad_familiar`), además de lo que suman `C_pareja`, `C_hermanos`, `C_padre` y `C_madre` |
| Misma especie, sin parentesco | `0.5` (la diagonal de la matriz, antes `0.9`, `0.7` o `0.4` según la sociabilidad) |
| Especies distintas | el valor de la matriz (`0.1` a `0.9`) |

El mejor amigo no es familiar. La migración 13 a 14 pone la diagonal de `AfinidadEspecies` a `0.5`; la regla lee la matriz del JSON.

**Satisfacción máxima.** Con alimentación 100 %, sano, el mejor hábitat, pareja, amigo, padre, madre y hermanos hasta el tope, la satisfacción máxima de una especie es `(w_bio + w_soc · (1 − w_afinidad · (1 − A)))`, con `A` la mayor afinidad posible con quien comparte espacio (`max(0.8, mayor afinidad con otra especie)`, que es `0.9`). Sale entre 96,7 % y 97,2 % según la especie, así que ningún animal llega al 100 %.

## 15. Satisfacción exigida por el prestigio

**Tope real de satisfacción.** Con alimentación 100 %, sano, el mejor hábitat y todos los vínculos, un animal solo llega a entre 96,7 % y 97,2 % según la especie (sección 14): ninguna afinidad llega a 1,0.

**Prestigio.** El umbral de satisfacción del nivel 100 baja de `1.00` a `0.95`. Los niveles 1 a 60 no cambian (el 60 exige `0.8857`); del 61 al 100 la curva conserva su forma cóncava, pero va de `0.8857` a `0.95` (nivel 70: `0.9138`, antes `0.9357`; nivel 80: `0.9339`, antes `0.9714`; nivel 90: `0.946`, antes `0.9929`). Los umbrales de animales no cambian. Los niveles impares siguen compartiendo la satisfacción del par anterior. La tabla sale del JSON, así que no hace falta migración.

**Misiones.** Las misiones de satisfacción mínima no cambian: de `0.60` a `0.95`, en pasos de `0.05`.

## 16. Regalos semanales (versión 15)

Como el regalo de bienvenida (sección 1), pero cada semana: los **lunes** y los **viernes** el jugador recibe una unidad gratuita de un animal sorteado.

| Día | Exclusivo | Épico | Raro | Poco común | Común |
|---|---|---|---|---|---|
| Lunes (`dia_semana` 1) | 5 % | 20 % | 35 % | 40 % | — |
| Viernes (`dia_semana` 5) | 5 % | 10 % | 20 % | 30 % | 35 % |

- Las tablas y los días están en `regalos_semanales` del JSON (`dia_semana` ISO: 1 lunes … 7 domingo). El validador exige que cada tabla sume `100` con rangos distintos del 1 al 5 y que no haya dos regalos el mismo día.
- Primero se sortea el rango y después la especie dentro del rango (uniforme en los rangos 1 a 4; en el 5, las probabilidades de `recompensas_animales_aleatorios.especies`). El prestigio no interviene.
- El día es el de la hora local del dispositivo. Se comprueba cada vez que la aplicación pasa a primer plano. Si no se abre el juego ese día, el regalo se pierde: no se acumulan.
- **Identificador y unicidad.** Cada regalo tiene un identificador propio, `id_regalo:fecha` (por ejemplo `lunes:2026-11-09`). Se guarda en la tabla `RegaloSemanal` con clave primaria `(id_partida, id_regalo)`, por lo que no puede concederse dos veces; además se comprueba antes y dentro de la transacción, y la operación se audita como `REGALO_SEMANAL` con ese identificador como clave de idempotencia. La purga conserva los 20 regalos más recientes.
- Es una unidad gratuita como las demás (`UnidadGratuita` con origen `SEMANAL`): se compra en la tienda de animales con coste `0` y no desbloquea la especie.
- La interfaz muestra el animal en una ventana de premio, con la tabla de probabilidades del día, después de la ventana de bienvenida si la hay.
- Migración 14 a 15: crea `RegaloSemanal` y reconstruye `UnidadGratuita` para admitir el origen `SEMANAL`, conservando las unidades existentes.

## 17. Mejor amigo y tratamiento médico (versión 16)

**Un familiar no puede ser mejor amigo.** Son familiares de un animal su pareja, sus padres, sus hijos y quienes comparten padre o madre con él (los medio hermanos también). Se rechaza con `AMIGO_FAMILIAR` en:

- la compra (CU-01): la pareja elegida en la misma compra no puede ser el mejor amigo;
- la edición de la ficha (CU-05): ni el mejor amigo nuevo puede ser un familiar, ni la pareja nueva el mejor amigo;
- el registro de la cría (CU-10): ni sus progenitores ni sus hermanos (los hijos de alguno de ellos).

Las listas de candidatos de la interfaz ya no los ofrecen. La migración 15 a 16 quita, en los dos extremos, los mejores amigos que ya eran familiares.

**Tratamiento médico según el nivel.** Igual que el bambú (sección 4.1), las unidades de medicina de un tratamiento crecen un 5 % por cada nivel por encima del 1, redondeadas hacia arriba: `unidades = ⌈ unidades_base × (19 + nivel) / 20 ⌉`, con `unidades_base` la de `tablas.consumo_medico_rango` (1 a 5). Ejemplo, rango 3 (base 3): nivel 1, 3 unidades; nivel 10, 5; nivel 30, 8; nivel 41, 9. Vale para el tratamiento (CU-09) y para su ventana de confirmación. Las probabilidades de enfermar no cambian.

El bambú ya crecía con el nivel desde la sección 4.1.

## 18. Amigos, parejas y divorcio

Complementa la sección 17.

| Cambio de vínculo | Resultado |
|---|---|
| Dos mejores amigos pasan a ser pareja | Permitido. Al elegirse como pareja, la amistad termina en los dos. Cada uno queda libre para elegir otro mejor amigo en la misma edición. |
| Una pareja pasa a ser mejores amigos | **No permitido** (`AMIGO_FAMILIAR`): la pareja es familiar. Hay que divorciarse antes, en una edición aparte. |
| Pasar de una pareja a otra | No directamente: primero hay que divorciarse. La edición de la ficha rechaza cualquier cambio de una pareja existente (`DIVORCIO_REQUERIDO`). |
| Quitar la pareja (divorcio, CU-24) | Solo con el botón de divorcio de la ficha. Los dos quedan sin pareja. Los **hijos comunes** (los que tienen a ambos como padre y madre, vivos o no) pasan a no tener padre ni madre y, por tanto, tampoco hermanos por esa vía. Los hijos de cualquiera de los dos con otra pareja no cambian. No se borra ningún animal. |
| Tras el divorcio | Cada uno puede elegir una pareja distinta, o ser mejores amigos entre sí (ya no son familiares). |

- El divorcio es la única operación que borra `id_padre` e `id_madre`. Se recalcula la satisfacción de los espacios afectados, incluidos los de los hijos.
- La ficha avisa de las consecuencias junto al selector de pareja.
- **En la compra.** El mejor amigo y la pareja se pueden elegir al comprar si hay animales válidos: el amigo, cualquier animal del espacio sin mejor amigo; la pareja, un animal de la misma especie del espacio sin pareja. Si no hay ninguno, el selector no aparece. No se puede elegir al mismo animal como pareja y como amigo.

### 18.1. Divorcio en la interfaz (CU-24)

- La ficha de un animal **con pareja** muestra el botón «Divorcio»; sin pareja no aparece.
- Al pulsarlo sale una ventana flotante que explica las consecuencias (los hijos comunes dejan de ser sus hijos y quedan sin padres ni hermanos, no se borra ningún animal, después se puede elegir otra pareja o ser mejores amigos, no se puede deshacer) con los botones «Acepto» y «Cancelar». Si no se acepta (cancelar o tocar fuera) no se realiza nada.
- Al aceptar se ejecuta el divorcio (`DivorciarAnimal`, auditoría `DIVORCIO`, idempotente) y sale una ventana con «Ánimo My Sheila ❤» que se cierra tocando fuera de ella.
- **Editar ficha** de un animal con pareja muestra quién es su pareja pero no ofrece cambiarla ni quitarla. Sin pareja, el selector funciona como antes.
- Otros motivos de rechazo: `SIN_PAREJA` (el animal no tiene pareja) y `ANIMAL_INEXISTENTE`/`ANIMAL_NO_VIVO`.

## 19. El panda luce toques de diamante

El panda sigue siendo un animal exclusivo (rango 5, misma tabla de sorteo, mismo flujo de unidad gratuita, mismo valor), pero se distingue del resto de exclusivos con un aspecto de diamante en lugar del dorado:

- Contorno de degradado hielo, blanco y lila; resplandor y fondo azul hielo (en tema claro y oscuro).
- Facetas claras en diagonal y cuatro pequeñas estrellas de destellos sobre la ficha.
- Nombre en degradado azul con resplandor y un rombo «◆» a su lado; en la ventana de premio, «Animal exclusivo · diamante».
- Se aplica en la tienda, la compra, la lista de animales de un espacio, la ficha del animal y las ventanas de premio (bienvenida, regalo semanal y recompensa de misión).

Es solo presentación: la especie se reconoce por su identificador (`Textos.ESPECIE_DIAMANTE`, `panda`) y no cambia ningún dato de configuración ni el esquema. `AnimalFicha` y `RecompensaReclamada` llevan ahora `idEspecie` para poder decidirlo. En los tests de JavaScript, Mocha usa un límite de 20 s por test (antes 2 s), porque las simulaciones de cientos de ciclos se acercaban al límite.

**Ajustes posteriores.** El azul del diamante es algo más oscuro que al principio (filete `#1F84AE` en tema claro y `#5FC1E4` en oscuro, con el degradado del contorno y el nombre oscurecidos en la misma proporción). Ese mismo aspecto sale cuando el panda es el resultado de un sorteo: en la ventana de premio de un animal aleatorio (bienvenida, regalo semanal o recompensa de misión) el panda se revela con el tono de diamante tras pulsar «¡SUERTE!».

**Compatibilidad con los hábitats (versión 17).** El panda tiene `factor_bioma = 1.0` en los cinco biomas (antes `1.0` solo en el bosque templado y `0.4` en el resto), así que `P_hab` nunca le resta satisfacción. Es un cambio de `tablas.compatibilidad_especie_bioma`; la migración 16 a 17 actualiza el catálogo guardado de la partida. Con esto, el panda es la especie cuyo tope de satisfacción no depende del hábitat.

## 20. Beneficios un 60 % más bajos (versión 18)

`beneficio_base_animal_ciclo` (el beneficio de un animal de nivel 1 con satisfacción 1) se multiplica por `0,4`:

| Rango | Antes | Ahora |
|---|---|---|
| 1 Común | 6 | **2,4** |
| 2 Poco común | 12 | **4,8** |
| 3 Raro | 20 | **8** |
| 4 Épico | 32 | **12,8** |
| 5 Exclusivo | 50 | **20** |

La fórmula, el multiplicador por nivel y la satisfacción no cambian: todo beneficio (por animal, espacio, hábitat y ciclo) baja un 60 %. Los precios de compra y venta no cambian, así que la compra tarda más en amortizarse. Sin contar el bambú, con `S_i = 0,5`: 154, 293, 467 y 460 ciclos (1,1 a 3,2 días) en los rangos 1 a 4; contando el peor caso de bambú, 924, 1008, 855 y 888 ciclos (5,9 a 7 días). La migración 17 a 18 actualiza `RangoEspecie`; los beneficios ya generados y pendientes no se tocan.
