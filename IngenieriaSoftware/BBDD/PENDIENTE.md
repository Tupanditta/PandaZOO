# Pendiente: cálculo de beneficios

## 1. Motivo

El cálculo del beneficio es una decisión económica central del proyecto. No se fija todavía una fórmula definitiva ni valores económicos provisionales, porque una elección incorrecta afectaría al balance, los ingresos acumulados, los costes de mantenimiento, las recompensas y la progresión del zoológico.

Este documento concentra lo que ya está definido y lo que debe especificarse antes de completar el modelo económico.

## 2. Funcionamiento actualmente definido

El beneficio debe tener en cuenta, como mínimo:

- La cantidad de animales vivos del zoológico.
- La satisfacción media global del parque.
- Las ponderaciones de las especies exclusivas que sean aplicables.

El beneficio se actualiza:

- Al finalizar cada ciclo de simulación.
- Después de una mutación confirmada que altere la cantidad de animales vivos.
- Después de una mutación confirmada que altere la satisfacción global.

El valor mostrado en el juego debe mantenerse acorde con el estado confirmado de animales vivos, satisfacción global y ponderaciones aplicables. Esta actualización se realiza mediante los eventos definidos por RF-15 y RF-44; no se utiliza sondeo continuo ni cálculo durante la navegación o el renderizado.

## 3. Ponderaciones de especies exclusivas

Una especie exclusiva puede tener una configuración de beneficio formada por:

- Rango.
- Satisfacción mínima requerida.
- Multiplicador del beneficio entre `1` y `5`.

El multiplicador solo se aplica cuando el individuo exclusivo cumple la satisfacción mínima indicada. Los valores concretos se configurarán posteriormente en el JSON mediante la tabla `ponderacion_beneficio_rango`.

La BBDD contempla la tabla `PonderacionBeneficioRango` con el rango, la satisfacción mínima y el multiplicador. La validación de que el individuo pertenezca a una especie exclusiva pertenece a la capa de dominio.

## 4. Decisiones que faltan

Todavía debe definirse:

- La fórmula base que convierte animales vivos y satisfacción media global en un importe económico.
- La unidad monetaria y la escala del importe.
- Si la satisfacción global se utiliza como valor `0..1`, porcentaje `0..100` o mediante otra conversión interna.
- Cómo se combinan varios individuos o especies exclusivas que cumplen su satisfacción mínima.
- Si los multiplicadores se aplican de forma acumulada, individual o mediante otro criterio.
- Qué ocurre cuando una especie exclusiva deja de cumplir la satisfacción mínima tras una actualización.
- La fórmula, periodicidad e importe de los costes de mantenimiento.
- La diferencia entre beneficio bruto, costes y beneficio neto acumulado.
- Qué importe se registra en `BeneficioPendiente` al finalizar cada ciclo.
- Cómo se calcula el importe que puede reclamar el usuario mediante RF-11.
- Qué ocurre con los beneficios pendientes cuando un animal muere, se vende o deja de estar alojado.
- El redondeo de importes y el tratamiento de conjuntos sin animales vivos.

Hasta que estas decisiones se concreten, no debe añadirse una fórmula ni un valor económico al JSON o al esquema de BBDD.
