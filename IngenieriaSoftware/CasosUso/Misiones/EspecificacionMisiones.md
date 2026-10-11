# Especificación de misiones y recompensas

## 1. Alcance

Este documento especifica la estructura funcional de las misiones, sus parámetros, el seguimiento del progreso y las recompensas. No fija valores concretos de costes, cantidades, probabilidades, especies ni requisitos numéricos que todavía estén pendientes de definir en el proyecto.

Las misiones se muestran en el apartado de misiones y se evalúan cuando se produce un evento relevante o finaliza un ciclo de simulación, respetando el modelo reactivo definido por RF-44 y RES-10.

## 2. Estructura de una misión

Cada misión debe contener, como mínimo:

- Identificador de misión.
- Estado de la misión.
- Tipo de objetivo.
- Parámetros del objetivo.
- Progreso actual.
- Condición de cumplimiento.
- Recompensa asociada.
- Estado de la recompensa.
- Indicador de si ya ha sido reclamada.

Los valores concretos de cada misión se definirán posteriormente en las tablas correspondientes del JSON.

## 3. Tipos de misión

### 3.1. Comprar especímenes de una especie

Objetivo: comprar una cantidad específica de animales de una especie determinada.

Parámetros:

- Especie.
- Cantidad requerida.

El progreso cuenta las compras válidas de la especie indicada.

### 3.2. Hacer crecer animales de una especie

Objetivo: hacer crecer una cantidad de animales de una especie concreta hasta una edad determinada.

Parámetros:

- Especie.
- Cantidad de animales.
- Edad requerida.

El progreso cuenta los animales de la especie que alcanzan la edad indicada.

### 3.3. Conseguir una cantidad total de animales

Objetivo: alcanzar una cantidad específica de animales en el zoológico.

Parámetro:

- Número de animales requeridos.

El progreso cuenta los animales que viven en el zoológico conforme al criterio de animales albergados definido por el proyecto.

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

El progreso se completa cuando el prestigio alcanza el umbral indicado.

### 3.7. Vender un animal de una especie y edad concretas

Objetivo: vender un animal que pertenezca a una especie determinada y tenga una edad determinada.

Parámetros:

- Especie.
- Edad.

El progreso se incrementa cuando se confirma una venta que cumple ambos parámetros.

### 3.8. Vender un animal de una edad concreta

Objetivo: vender un animal con una edad determinada sin tener en cuenta su especie.

Parámetro:

- Edad.

El progreso se incrementa cuando se confirma una venta que cumple la edad indicada.

### 3.9. Tener una familia

Objetivo: tener una familia con padre, madre y un número mínimo de hermanos.

Parámetro:

- Número mínimo de hermanos.

La familia debe cumplir las reglas de parentesco y especie definidas en RF-34 y RES-13.

### 3.10. Tener una familia de una especie concreta

Objetivo: tener una familia de una especie determinada con padre, madre y un número mínimo de hermanos.

Parámetros:

- Especie.
- Número mínimo de hermanos.

La familia debe cumplir las reglas de parentesco y especie definidas en RF-34 y RES-13.

### 3.11. Conseguir una satisfacción mínima

Objetivo: conseguir una satisfacción mínima en un animal.

Parámetro:

- Satisfacción mínima requerida.

El progreso se completa cuando un animal alcanza el umbral indicado.

### 3.12. Conseguir una satisfacción mínima por especie

Objetivo: conseguir una satisfacción mínima en un animal de una especie concreta.

Parámetros:

- Especie.
- Satisfacción mínima requerida.

El progreso se completa cuando un animal de la especie indicada alcanza el umbral.

## 4. Progreso y cumplimiento

- Cada misión muestra su progreso en el apartado de misiones.
- El progreso se actualiza al producirse los eventos que puedan afectar a su objetivo o al finalizar un ciclo de simulación.
- Cuando se alcanza la condición del objetivo, la misión pasa a estado cumplido.
- Una misión cumplida muestra una acción explícita para reclamar la recompensa.
- La opción de reclamar no caduca.
- La misión desaparece del apartado de misiones después de reclamarla.
- El sistema conserva el estado de misión realizada.
- Una misión reclamada no puede repetirse ni realizarse una segunda vez.
- La actualización del progreso, el cumplimiento y la reclamación deben ser idempotentes.

La fórmula exacta del progreso, los eventos que incrementan cada contador y los valores necesarios para completar cada misión quedan pendientes de parametrización.

## 5. Estructura de una recompensa

Cada misión tiene asociada una recompensa concreta. La recompensa debe indicar:

- Tipo de recompensa.
- Cantidad o elemento entregado.
- Estado de la recompensa.
- Misión que la origina.
- Clave de idempotencia.
- Fecha de disponibilidad y reclamación cuando corresponda.

## 6. Tipos de recompensa

### 6.1. Alimentos e insumos

La recompensa puede entregar alimentos o insumos médicos.

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
- Probabilidad asociada a cada especie.

Esta recompensa puede ser una vía para obtener un animal exclusivo. Cuando se selecciona el animal, aparece en la tienda con coste cero y solo puede comprarse una unidad. La compra sigue el flujo normal de adquisición de especímenes, incluyendo la validación, cumplimentación de la ficha y ubicación en un espacio compatible con capacidad disponible.

Las probabilidades concretas por especie se definirán posteriormente en el JSON.

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

## 8. Datos pendientes de parametrización

El documento define la estructura y las reglas confirmadas, pero quedan pendientes:

- Valores concretos de los parámetros de cada misión.
- Fórmulas de progreso y condiciones exactas de cumplimiento.
- Cantidades de alimentos, insumos y fondos.
- Especies posibles y probabilidades de las recompensas aleatorias.
- Representación final de los parámetros y recompensas en BBDD.
- Momento exacto de disponibilidad del animal aleatorio y su retirada después de la compra.
