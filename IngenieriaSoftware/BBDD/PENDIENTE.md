# Cálculo de beneficios (resuelto)

## Decisiones definidas

- El beneficio se calcula para el parque completo, en función del número de animales vivos y de la satisfacción global.
- Las especies exclusivas usan el rango de mayor nivel; no existen ponderaciones ni multiplicadores especiales. Los beneficios se configurarán por rango.
- Cada ciclo suma el importe generado al acumulado pendiente. Reclamar acredita todo el acumulado en los fondos, marca los registros incluidos como reclamados y deja el pendiente en cero.
- Los recálculos por mutaciones entre ciclos actualizan los datos mostrados, pero no modifican el importe acumulado reclamable; solo el cierre del ciclo suma beneficios al acumulado.
- La muerte, venta u otra salida solo afecta a los beneficios futuros. Sin animales vivos, el importe generado es cero.
- La moneda del juego se representa con un dibujo de una moneda dorada. Los importes tienen dos decimales, se redondean al centésimo más cercano con medios hacia arriba y se usa el valor redondeado en cálculos posteriores y presentación.
- Los recálculos se ejecutan por los eventos establecidos en RF-15 y RF-44, no por sondeo ni navegación.

## Definición resuelta

- Fórmula base: `B_ciclo = redondear(S_global * suma, sobre los animales vivos alojados, del beneficio_por_animal_ciclo del rango de su especie)`, con `S_global` en `0..1` sin redondear y redondeo al centésimo, medios hacia arriba. Sin animales vivos alojados vale `0`.
- Configuración por rango (`tablas.rangos_especie[].beneficio_por_animal_ciclo`): rango 1 `4.00`, rango 2 `8.00`, rango 3 `14.00`, rango 4 `22.00` y rango 5 (exclusivas) `35.00`. No hay multiplicadores.
- Se genera una vez por ciclo confirmado, en la etapa de beneficios y tras las defunciones, con la satisfacción global vigente al inicio de esa etapa. Un importe `0` no crea registro.
- Costes y cantidades económicos: espacios `100` a `700` según capacidad; animales `200`, `450`, `900` y `1800` por rango; bambú `0.50` por kilogramo; insumo médico `10.00` por unidad; fondos iniciales `1000.00`. Valores completos en `Otros/valores_y_pesos.json`.
- Definición detallada: `EspecificacionBBDD.md`, sección 1.4.

No queda nada pendiente de definir en este apartado.
