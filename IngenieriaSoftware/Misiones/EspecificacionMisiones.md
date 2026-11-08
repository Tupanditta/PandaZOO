# Especificación de misiones y recompensas

## 1. Alcance

Este documento especifica la estructura funcional de las misiones, sus parámetros, el seguimiento del progreso y las recompensas. Los valores concretos (parámetros base, progresión, recompensas y probabilidades) están en `catalogos.misiones` y `recompensas_animales_aleatorios` de `valores_y_pesos.json` y se resumen en la sección 8.

Las misiones se muestran en el apartado de misiones y se evalúan cuando se produce un evento relevante o finaliza un ciclo de simulación, respetando el modelo reactivo definido por RF-44 y RES-10.

## 2. Estructura de una misión

Cada misión debe contener, como mínimo:

- Identificador de tipo de misión.
- Identificador de la instancia vigente.
- Estado de la misión.
- Tipo de objetivo.
- Parámetros base definidos para el tipo.
- Parámetros actuales de la instancia.
- Para cada parámetro numérico progresable, operación y constante de progresión.
- Progreso actual.
- Condición de cumplimiento.
- Recompensa asociada.
- Estado de la recompensa.
- Indicador de si ya ha sido reclamada.

Los valores concretos de cada misión están en `catalogos.misiones` del JSON. Los parámetros categóricos, como especie y hábitat, permanecen fijos; los parámetros numéricos usan una operación, una constante, un valor mínimo y un valor máximo configurados por parámetro. En base de datos, la misión se guarda en `Mision` y `ParametroMision`; la instancia vigente, en `ProgresoMision` y `ParametroProgresoMision`; y la recompensa de cada instancia cumplida, en `RecompensaMision`.

## 3. Tipos de misión

### 3.1. Comprar especímenes de una especie

Objetivo: comprar una cantidad específica de animales de una especie determinada.

Parámetros:

- Especie.
- Cantidad requerida.

El progreso cuenta las compras confirmadas de la especie indicada realizadas después de crearse la instancia, incluida la compra de una unidad exclusiva concedida. Es acumulativo.

### 3.2. Hacer subir de nivel a animales de una especie

Objetivo: hacer que una cantidad de animales de una especie concreta alcance un nivel determinado.

Parámetros:

- Especie.
- Cantidad de animales.
- Nivel requerido (mínimo `2`: todo animal nace con nivel `1`, y alcanzarlo en el alta no es una subida).

El progreso cuenta los animales de la especie que, durante un ciclo posterior a la creación de la instancia, pasan de tener un nivel menor que el indicado a tener ese nivel o más. Como el nivel sube como máximo `1` por ciclo, un animal cruza el nivel requerido exactamente en el ciclo en que lo alcanza. Un animal que ya tenía ese nivel al crearse la instancia no cuenta. Es acumulativo.

### 3.3. Conseguir una cantidad total de animales

Objetivo: alcanzar una cantidad específica de animales en el zoológico.

Parámetro:

- Número de animales requeridos.

El progreso cuenta únicamente animales con estado vital `VIVO` y espacio asignado.

### 3.4. Conseguir animales en un hábitat

Objetivo: alcanzar una cantidad específica de animales dentro de un hábitat.

Parámetros:

- Hábitat.
- Número de animales requeridos.

El progreso cuenta los animales que viven en todos los espacios pertenecientes al hábitat indicado.

### 3.5. Conseguir espacios ocupados en un hábitat

Objetivo: conseguir una cantidad específica de espacios que tengan como mínimo un número determinado de animales dentro de un hábitat concreto.

Parámetros:

- Hábitat.
- Número mínimo de animales por espacio.
- Número de espacios requeridos.

El progreso cuenta los espacios del hábitat que cumplen el mínimo de animales indicado.

### 3.6. Subir de prestigio

Objetivo: alcanzar un nivel de prestigio determinado.

Parámetro:

- Nivel de prestigio requerido.

El progreso es el prestigio actual y la misión se cumple cuando es mayor o igual que el prestigio requerido. Como el prestigio nunca baja, su progreso tampoco disminuye. El prestigio requerido llega hasta el `100` y la recompensa es siempre un animal sorteado por rango (sección 6.3). El texto de la misión es «Alcanza el prestigio N.».

### 3.7. Vender un animal de una especie y nivel concretos

Objetivo: vender un animal que pertenezca a una especie determinada y tenga un nivel determinado.

Parámetros:

- Especie.
- Nivel requerido.

El objetivo es una única venta: el progreso pasa de `0` a `1` cuando, después de crearse la instancia, se confirma la venta de un animal de la especie cuyo nivel es mayor o igual que el indicado. Es acumulativo.

### 3.8. Vender un animal de un nivel concreto

Objetivo: vender un animal con un nivel determinado sin tener en cuenta su especie.

Parámetro:

- Nivel requerido.

El objetivo es una única venta: el progreso pasa de `0` a `1` cuando, después de crearse la instancia, se confirma la venta de un animal cuyo nivel es mayor o igual que el indicado. Es acumulativo.

### 3.9. Tener una familia

Objetivo: tener una familia con padre, madre y un número mínimo de hermanos vivos.

Parámetro:

- Número mínimo de hermanos.

La familia debe cumplir las reglas de parentesco y especie definidas en RF-34 y RES-13. Se cumple cuando existe un animal `VIVO` alojado cuyo padre y madre están `VIVO` y alojados y que tiene como mínimo ese número de hermanos `VIVO` (animales con el mismo padre o madre). El progreso es el mayor número de hermanos vivos entre los animales que cumplen lo anterior.

### 3.10. Tener una familia de una especie concreta

Objetivo: tener una familia de una especie determinada con padre, madre y un número mínimo de hermanos vivos.

Parámetros:

- Especie.
- Número mínimo de hermanos.

La familia debe cumplir las reglas de parentesco y especie definidas en RF-34 y RES-13. Se evalúa igual que en 3.9, limitada a animales de la especie indicada.

### 3.11. Conseguir una satisfacción mínima

Objetivo: conseguir una satisfacción mínima en un animal.

Parámetro:

- Satisfacción mínima requerida.

El progreso es la mayor satisfacción individual actual (`0..1`, sin redondear) entre los animales `VIVO` alojados; la misión se cumple cuando es mayor o igual que el umbral.

### 3.12. Conseguir una satisfacción mínima por especie

Objetivo: conseguir una satisfacción mínima en un animal de una especie concreta.

Parámetros:

- Especie.
- Satisfacción mínima requerida.

El progreso es la mayor satisfacción individual actual (`0..1`, sin redondear) entre los animales `VIVO` alojados de la especie indicada; la misión se cumple cuando es mayor o igual que el umbral.

## 4. Progreso y cumplimiento

- Cada misión muestra su progreso en el apartado de misiones.
- Las misiones de compra cuentan compras confirmadas y su progreso no disminuye.
- Las misiones de subida de nivel cuentan animales que alcanzan el nivel requerido y su progreso no disminuye aunque el animal muera o se venda posteriormente.
- Las misiones de animales en un hábitat cuentan animales vivos que cumplen la condición y su progreso disminuye si mueren, se venden o se reubican fuera del hábitat.
- En las misiones de cantidad total y por hábitat solo cuentan animales vivos con espacio asignado; un animal trasladado deja de contar en el hábitat anterior y pasa a contar en el nuevo.
- Las misiones de venta cuentan ventas confirmadas y su progreso no disminuye.
- Las misiones de satisfacción comprueban la satisfacción actual; su progreso disminuye si la satisfacción baja.
- Las misiones de prestigio comprueban el prestigio actual; como el prestigio nunca baja (`Otros/DecisionesImplementacion.md`, sección 6), su progreso no disminuye.
- El progreso se actualiza al producirse los eventos que puedan afectar a su objetivo o al finalizar un ciclo de simulación.
- Cuando se alcanza la condición del objetivo, la misión pasa inmediatamente a estado cumplido.
- Una misión cumplida muestra una acción explícita para reclamar la recompensa.
- La opción de reclamar no caduca.
- Se muestra como máximo una instancia vigente de cada tipo de misión configurado por partida, sin tipos duplicados.
- Una misión cumplida permanece visible y reclamable; no se crea su siguiente instancia antes de reclamar la recompensa.
- Al reclamar, el sistema acredita la recompensa una sola vez, aplica a cada parámetro numérico su operación y constante, retira la instancia cumplida y crea la siguiente instancia del mismo tipo con los parámetros resultantes. La sustitución y el crédito son atómicos.
- La instancia reclamada deja de mostrarse y no se conserva un historial funcional de instancias completadas. Se conservan los registros de recompensa y auditoría necesarios para idempotencia y trazabilidad transaccional.
- Cada tipo puede volver a cumplirse después de que se cree su instancia progresada.
- La actualización del progreso, el cumplimiento y la reclamación deben ser idempotentes.

### 4.1. Reglas de representación, progresión y cierre

- **Nivel.** Toda condición de nivel se compara con el nivel del animal (`Animal.nivel`) y se cumple con un nivel mayor o igual que el requerido. La subida de nivel de la misión 3.2 se evalúa en la etapa de misiones del ciclo, que ve el nivel resultante de la etapa de nivel del mismo ciclo y solo a los animales que siguen vivos.
- **Evaluación.** `ProgresoMision.progreso` guarda el valor actual (contador acumulativo o valor dinámico recalculado). Se evalúa en la transacción de cada evento que puede afectarlo y al cerrar cada ciclo, nunca por navegación. Una instancia `CUMPLIDA` no vuelve a `PENDIENTE` aunque un progreso dinámico baje después.
- **Progresión de parámetros.** Al reclamar, cada parámetro numérico se recalcula con su operación (`SUMAR`, `RESTAR`, `MULTIPLICAR`, `DIVIDIR`) y su constante, en aritmética exacta; después se redondea al entero más cercano (medios hacia arriba) si el parámetro es entero, o al centésimo (medios hacia arriba) si es de satisfacción. `DIVIDIR` exige una constante mayor que `0`. El resultado debe estar en `[valor_minimo, valor_maximo]` del parámetro.
- **Agotamiento.** Si el resultado de algún parámetro supera `valor_maximo` (o queda por debajo de `valor_minimo`), no se crea una nueva instancia: la instancia reclamada se sustituye por un marcador `AGOTADA`, el tipo deja de mostrarse como misión vigente y no vuelve a generarse en esa partida. La configuración distribuida solo usa `SUMAR` con constantes positivas.
- **Nueva instancia.** Se crea con `progreso = 0`; si es de tipo dinámico se evalúa de inmediato en la misma transacción y puede quedar `CUMPLIDA` al instante si el estado del parque ya cumple los nuevos parámetros.
- **Misiones imposibles.** Los máximos configurados garantizan que ningún objetivo supere lo alcanzable: nivel máximo requerido `15` (el menor `nivel_maximo` entre los rangos; todas las especies de misión pertenecen al rango 1), prestigio máximo `100` (`nivel_maximo`), satisfacción máxima `0.95`.

## 5. Estructura de una recompensa

Cada misión tiene asociada una recompensa concreta, fija para el tipo (no progresa). La recompensa debe indicar:

- Tipo de recompensa.
- Cantidad o elemento entregado.
- Estado de la recompensa.
- Misión que la origina.
- Clave de idempotencia (derivada de la instancia de misión, sin texto adicional).
- Fecha de disponibilidad y reclamación cuando corresponda.

## 6. Tipos de recompensa

### 6.1. Alimentos e insumos

La recompensa puede entregar alimentos o medicinas.

Parámetros:

- Tipo de alimento o insumo.
- Cantidad entregada.

La cantidad se acredita en el inventario correspondiente al reclamar la recompensa.

### 6.2. Dinero

La recompensa puede entregar fondos.

Parámetro:

- Cantidad de fondos.

La cantidad se acredita en el balance del usuario al reclamar la recompensa.

### 6.3. Animal aleatorio

La recompensa puede proporcionar un animal seleccionado aleatoriamente entre las especies habilitadas para este tipo de recompensa.

Parámetros:

- Cantidad de animales.
- Lista común de especies elegibles y probabilidad porcentual de cada especie.

La lista se configura en `recompensas_animales_aleatorios.especies` de `valores_y_pesos.json`, debe sumar exactamente 100% e incluir como mínimo todas las especies exclusivas. Al reclamar la recompensa, el sistema selecciona una especie mediante un sorteo ponderado y persiste el resultado en el registro de recompensa para que los reintentos idempotentes no vuelvan a sortear.

Reclamarla no desbloquea la especie en la tienda: solo concede la unidad gratuita (`Otros/DecisionesImplementacion.md`, sección 5.3). Esta recompensa puede ser una vía para obtener un animal exclusivo. Cuando se selecciona el animal, aparece en la tienda con coste cero y solo puede comprarse una unidad. La unidad está disponible desde la propia transacción de reclamación, no caduca y se retira de la tienda al comprarla (`RecompensaMision.unidad_pendiente`). La cantidad de animales de esta recompensa es siempre `1`. La compra sigue el flujo normal de adquisición de especímenes, incluyendo la validación, cumplimentación de la ficha y ubicación en un espacio con capacidad disponible. La compatibilidad entre especie y hábitat afecta a la satisfacción, pero no impide la ubicación.

**Sorteo por rango de la misión de prestigio.** La misión `subir_prestigio` no usa la lista común: sortea primero el rango y después la especie. La tabla de rangos depende del prestigio requerido por la instancia y está en `recompensas_animales_aleatorios.sorteo_prestigio`: múltiplo de 10, `100 %` exclusivo; si no, múltiplo de 5, rangos 2 a 5 con `30/40/20/10 %`; en los demás casos, rangos 2 a 5 con `50/25/15/10 %`. Dentro de un rango 2 a 4 todas las especies comerciales pesan igual; dentro del rango exclusivo pesan las probabilidades de la lista común. El sorteo consume dos números del `Azar` (rango y especie, en ese orden) y se persiste para los reintentos. La lista de misiones solo anuncia «Un animal exclusivo, gratis» cuando el animal es seguro exclusivo; en otro caso, «Un animal aleatorio, gratis».

## 7. Reclamación

La reclamación se realiza mediante una acción explícita del usuario cuando la misión está cumplida.

La operación de reclamación debe:

1. Validar que la misión está cumplida.
2. Validar que no ha sido reclamada anteriormente.
3. Acreditar los fondos o elementos de la recompensa.
4. Registrar la recompensa como reclamada.
5. Registrar la operación para auditoría.
6. Persistir el resultado de forma atómica.
7. Impedir que una repetición duplique la recompensa.

Una recompensa reclamada no puede acreditarse de nuevo.

## 8. Configuración definida

Todos los valores de esta sección están en `valores_y_pesos.json` y son valores de diseño; reajustarlos no cambia ninguna regla.

| Misión (`id_mision`) | Naturaleza | Parámetros base | Progresión (operación, constante, mín–máx) | Recompensa |
|---|---|---|---|---|
| `comprar_especie` | acumulativa | capibara, cantidad `2` | cantidad: sumar `1`, 1–50 | `200.00` fondos |
| `crecer_especie` | acumulativa | nutria, cantidad `1`, nivel `3` | cantidad: sumar `1`, 1–20; nivel: sumar `1`, 2–15 | `30` kg de bambú |
| `total_animales` | dinámica | `5` animales | sumar `5`, 1–500 | `300.00` fondos |
| `animales_habitat` | dinámica | hábitat bosque templado, `3` animales | sumar `2`, 1–200 | `5` medicinas |
| `espacios_ocupados_habitat` | dinámica | hábitat humedal de agua dulce, mínimo `2` animales por espacio, `1` espacio | mínimo: sumar `1`, 1–20; espacios: sumar `1`, 1–50 | `400.00` fondos |
| `subir_prestigio` | dinámica | prestigio `1` | sumar `1`, 1–100 | animal sorteado por rango (`1`) |
| `vender_especie_nivel` | acumulativa | erizo, nivel `4` | nivel: sumar `1`, 1–15 | `150.00` fondos |
| `vender_nivel` | acumulativa | nivel `4` | sumar `1`, 1–15 | `20` kg de bambú |
| `familia` | dinámica | `1` hermano | sumar `1`, 1–8 | `250.00` fondos |
| `familia_especie` | dinámica | zarigüeya, `1` hermano | sumar `1`, 1–8 | `4` medicinas |
| `satisfaccion_minima` | dinámica | `0.60` | sumar `0.05`, 0.05–0.95 | `500.00` fondos |
| `satisfaccion_minima_especie` | dinámica | tortuga, `0.60` | sumar `0.05`, 0.05–0.95 | `50` kg de bambú |

Las especies y hábitats de las misiones pertenecen al nivel 0 de desbloqueo, de modo que son alcanzables desde el inicio.

**Recompensa de animal aleatorio.** Lista de especies elegibles (exactamente las nueve exclusivas): panda `10 %`, leopardo de las nieves `12 %`, tití león dorado `14 %`, tigre de Bengala `12 %`, alce leucístico `14 %`, jaguar `12 %`, ballena azul `6 %`, pangolín `12 %` y rinoceronte blanco `8 %` (suma `100 %`).

**Disponibilidad.** El animal concedido queda disponible en la tienda, con coste cero y para una sola compra, desde la transacción de reclamación; se retira al comprarlo. La especie no se desbloquea por ello. Reclamar un animal no sustituye su compra ni su ubicación.

**Orden de la lista de misiones (CU-20).** Primero van las cumplidas pendientes de reclamar, en el orden en que se cumplieron (`ProgresoMision.orden_cumplida`, que se asigna una sola vez al pasar a `CUMPLIDA`); después el resto, de más a menos avance (`progreso / objetivo`, entre 0 y 1) y, a igual avance, la que menos le falta. Cada misión muestra su avance con una barra. Detalle en `Otros/DecisionesImplementacion.md`, sección 5.1.

**Representación en BBDD.** Cada tipo es una fila de `Mision` (con su naturaleza y recompensa) y tantas de `ParametroMision` como parámetros; la instancia vigente es una fila de `ProgresoMision` con sus `ParametroProgresoMision`. No quedan datos pendientes de parametrización.
