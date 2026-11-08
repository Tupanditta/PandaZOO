# Cálculo de beneficios (resuelto)

## Decisiones definidas

- El beneficio se calcula para el parque completo (un importe por ciclo) como suma de la aportación de cada animal vivo, que depende de su satisfacción individual, de su nivel y del rango de su especie.
- Las especies exclusivas usan el rango de mayor nivel; no existen ponderaciones ni multiplicadores especiales. Los beneficios se configurarán por rango.
- Cada ciclo suma el importe generado al acumulado pendiente. Reclamar acredita todo el acumulado en los fondos, marca los registros incluidos como reclamados y deja el pendiente en cero.
- Los recálculos por mutaciones entre ciclos actualizan los datos mostrados, pero no modifican el importe acumulado reclamable; solo el cierre del ciclo suma beneficios al acumulado.
- La muerte, venta u otra salida solo afecta a los beneficios futuros. Sin animales vivos, el importe generado es cero.
- La moneda del juego se representa con un dibujo de una moneda dorada. Los importes tienen dos decimales, se redondean al centésimo más cercano con medios hacia arriba y se usa el valor redondeado en cálculos posteriores y presentación.
- Los recálculos se ejecutan por los eventos establecidos en RF-15 y RF-44, no por sondeo ni navegación.

## Definición resuelta

- Fórmula base: `B_ciclo = redondear(suma, sobre los animales vivos alojados, de beneficio_base_animal_ciclo(rango) * S_i * M_nivel(nivel))`, con `S_i` la satisfacción individual en `0..1` sin redondear, `M_nivel` el multiplicador lineal de nivel (`1` en el nivel 1 y `multiplicador_nivel_maximo` en el tope del rango) y redondeo de la suma al centésimo, medios hacia arriba. Sin animales vivos alojados vale `0`.
- Configuración por rango (`tablas.rangos_especie`): beneficio base `6.00`, `12.00`, `20.00`, `32.00` y `50.00` (rango 5, exclusivas); nivel máximo `15`, `29`, `41`, `51` y `61`; multiplicador en el tope `3.0`, `3.5`, `4.0`, `4.5` y `5.0`. No hay multiplicadores por especie.
- Se genera una vez por ciclo confirmado, en la etapa de beneficios y tras las defunciones, con la satisfacción global vigente al inicio de esa etapa. Un importe `0` no crea registro.
- Costes y cantidades económicos: espacios `325` a `1300` según capacidad (6 a 30 animales; el primero de 6 es gratis); animales `40`, `160`, `450` y `700` por rango; bambú `0.50` por kilogramo; medicina `10.00` por unidad; fondos iniciales `2000.00`. Valores completos en `Otros/valores_y_pesos.json`.
- Definición detallada: `EspecificacionBBDD.md`, sección 1.4.

No queda nada pendiente de definir en este apartado.
