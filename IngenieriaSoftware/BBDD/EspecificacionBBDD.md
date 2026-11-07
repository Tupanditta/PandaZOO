# Especificación técnica: modelo de datos y cálculo de satisfacción

## 1. Alcance y principios

El modelo persigue la tercera forma normal (3FN), evita referencias entre partidas, conserva el histórico acotado por ventanas de retención (sección 5.2) necesario para auditoría y genealogía y permite recalcular las métricas únicamente como reacción a eventos de dominio. La base de datos usa SQLite/Room con claves foráneas activadas mediante `PRAGMA foreign_keys = ON`.

Se distinguen dos niveles:

- **Catálogo global:** `Bioma`, `Especie`, `CompatibilidadBioma`, `AfinidadEspecies`, `Alimento`, `InsumoMedico` y `Mision`.
- **Estado de una partida:** hábitats, espacios, animales, recursos, economía, ciclos, avisos, métricas, desbloqueos, misiones y auditoría.

Los identificadores son claves técnicas y nunca se muestran en la interfaz (RES-06). Los hábitats se cargan como catálogo físico de solo lectura por partida (RES-05), incluidos los que comienzan bloqueados por prestigio; el desbloqueo solo habilita su uso y no crea, edita ni elimina el hábitat. Los espacios no pueden cambiar su capacidad después de crearse (RES-04).

### 1.1. Modelo relacional

```text
CATALOGO: Bioma 1---N Habitat 1---N Espacio 1---N Animal
              |             |                    |
              |             |                    +---N RasgoPersonalidad
              |             |                    +---N HabilidadSocial
              |             |                    +---N ReproduccionCiclo
              |             |
              |             +---N MetricaSatisfaccion
              |
Especie 1---N CompatibilidadBioma
   | 1---N AfinidadEspecies
   | 1---N Animal
   | 1---N DesbloqueoEspecie

Partida 1---N Habitat
Partida 1---N MovimientoEconomico / BeneficioPendiente / CicloSimulacion
Partida 1---N InventarioAlimento / InventarioMedico
Mision 1---N ParametroMision
Partida 1---N ProgresoMision / Notificacion / AuditoriaEvento
Mision 1---N ProgresoMision 1---N ParametroProgresoMision
Partida 1---N RecompensaMision
Partida 1---1 EstadoSimulacion
```

Las claves compuestas que contienen `id_partida` impiden que una fila de una partida apunte accidentalmente a una entidad de otra partida.

### 1.2. Alimentación con bambú

`Alimento` contendrá únicamente bambú, requerido por todas las especies. `CosteAlimento` conservará su precio fijo común por kilogramo (0.50 por kilogramo en `tablas.costes_alimentos`); no se permite confirmar una compra si dicho precio no está configurado. El inventario de cada partida se mantiene en `InventarioAlimento` y su unidad es el kilogramo.

La cantidad de bambú necesaria para que un animal de una especie aumente 10 puntos porcentuales su nivel de alimentación se configura por especie en `valores_y_pesos.json`, en `tablas.alimentos_por_especie`. No se añade una tabla relacional para este dato. Si falta la cantidad configurada para una especie, se rechaza la operación de alimentación sin cambios. La cantidad es un entero positivo de kilogramos (de 1 a 5 según la especie).

La alimentación individual de un animal vivo por debajo del 100% consume la cantidad configurada para su especie y aumenta el nivel en 10 puntos porcentuales, con un máximo del 100%. El nivel de alimentación siempre será 0 o un múltiplo de 10. La alimentación desde la ficha de un espacio incluye a todos sus animales vivos por debajo del 100% y los lleva al 100%. Para cada animal, el consumo es la cantidad configurada por especie multiplicada por el número de incrementos de 10 puntos necesarios para alcanzar el 100%; el total mostrado es la suma del consumo de todos los animales incluidos. Los animales que ya están al 100%, muertos o vendidos no forman parte del lote.

La operación grupal vuelve a validar el espacio, los animales, la configuración y el inventario antes de confirmar. El descuento total del inventario, la actualización de alimentación y avisos, el recálculo de métricas y el registro de auditoría se consolidan en una sola transacción. Si no hay existencias suficientes o falla cualquier escritura, se revierte toda la operación, sin consumo parcial ni cambios parciales en animales.

### 1.3. Recompensas de animal aleatorio

Las recompensas de animal aleatorio utilizan la lista común `recompensas_animales_aleatorios.especies` de `valores_y_pesos.json`. Cada entrada identifica una especie y su probabilidad porcentual; la suma de las probabilidades debe ser exactamente 100%. La lista contiene exactamente las nueve especies exclusivas, con probabilidades 10, 12, 14, 12, 14, 12, 6, 12 y 8 (suma 100). No se añade una tabla relacional para esta configuración. Se permite que el sorteo repita una especie ya obtenida: cada concesión habilita una nueva unidad.

Al reclamar la recompensa, el sistema realiza el sorteo ponderado y persiste la especie seleccionada en el registro de `RecompensaMision`. Así, una repetición con la misma clave de idempotencia devuelve el resultado ya confirmado y no vuelve a sortear. La especie se desbloquea (`DesbloqueoEspecie`, origen `RECOMPENSA`, si aún no lo estaba) y habilita una unidad con coste cero en la tienda desde la misma transacción de reclamación, sin caducidad. La disponibilidad se representa con `RecompensaMision.unidad_pendiente = 1`; la compra de esa unidad sigue el flujo normal (CU-01), pone `unidad_pendiente = 0` en la misma transacción y retira la unidad de la tienda. Cada concesión habilita una única compra y, mientras `unidad_pendiente = 1`, la fila no se purga.

### 1.4. Economía y beneficios

La unidad monetaria del juego se representa con un dibujo de una moneda dorada. Los importes se expresan con dos decimales; se redondean al centésimo más cercano, con los medios hacia arriba. El resultado redondeado es el que se muestra, persiste y se utiliza en operaciones económicas posteriores. Las cantidades de inventario y de recursos configuradas son enteras y no negativas; una compra requiere una cantidad positiva.

El coste de un recurso comprado es `cantidad_solicitada * precio_unitario`. El precio concreto del bambú es común por kilogramo y el precio del insumo es por unidad. Los espacios se cobran según su capacidad máxima en `CosteEspacio`; los animales se cobran por el rango de su especie en `CosteRango`. La excepción de unidad exclusiva temporal con coste cero prevalece sobre el coste ordinario. Los valores concretos están en `tablas.costes_espacios` (capacidades 2, 4, 6, 8, 10, 15 y 20), `tablas.costes_rangos`, `tablas.costes_alimentos` y `tablas.costes_insumos` de `valores_y_pesos.json`.

La venta de un animal acredita en `Partida.fondos` el importe `V` definido en esta sección (valor de venta según nivel y rango), lo registra como `MovimientoEconomico` de tipo `VENTA` y actualiza el estado del animal a venta confirmada, todo en la misma transacción.

El consumo médico de un tratamiento se determina por rango e insumo. El tratamiento solo se confirma si hay existencias suficientes de todos los insumos requeridos; consume todas las cantidades configuradas de forma atómica y restablece la salud a «sano». No se permiten tratamientos parciales.

El beneficio se calcula para el parque completo (cada ciclo genera un único importe), pero se compone de la aportación de cada animal vivo alojado, que depende de su satisfacción individual, de su nivel y del rango de su especie. Al cerrar cada ciclo, el importe generado se suma a los beneficios acumulados pendientes. Una reclamación acredita el total pendiente en `Partida.fondos`, marca los registros incluidos como reclamados y deja el pendiente acumulado en cero. Las mutaciones entre ciclos actualizan los datos mostrados, pero no cambian el total acumulado reclamable; solo el cierre de ciclo suma beneficios al acumulado. Las muertes, ventas y demás salidas de animales solo afectan a beneficios futuros; sin animales vivos, el beneficio generado es cero. **Fórmula del beneficio por ciclo.** Para cada animal vivo alojado `i`, con `S_i` su satisfacción individual (`Animal.satisfaccion_actual` persistida al inicio del ciclo, `0..1`, sin redondear), `n_i` su nivel tras la etapa de nivel del ciclo y `r` el rango de su especie en `RangoEspecie`:

$$M_{nivel}(n) = 1 + \big(multiplicador\_nivel\_maximo(r) - 1\big) \times \frac{n - 1}{nivel\_maximo(r) - 1}$$

$$b_i = beneficio\_base\_animal\_ciclo(r) \times S_i \times M_{nivel}(n_i) \qquad\qquad B_{ciclo} = redondear_{0.01}\Big(\sum_{i \in vivos\ alojados} b_i\Big)$$

`M_nivel(1) = 1` y `M_nivel(nivel_maximo) = multiplicador_nivel_maximo`; es lineal entre ambos y vale `multiplicador_nivel_maximo` también si el animal ya está en el tope. `b_i` no se redondea; solo se redondea la suma al centésimo más cercano con los medios hacia arriba. Sin animales vivos alojados, `B_ciclo = 0`. A igual satisfacción, un animal de mayor nivel aporta más beneficio. No hay multiplicadores por especie: las exclusivas usan los valores del rango 5. La satisfacción global no interviene (solo en el prestigio). Se calcula en la etapa «beneficios» del ciclo, tras las defunciones y la etapa de nivel. Un `B_ciclo` igual a `0` no genera fila en `BeneficioPendiente`. Los valores están en `tablas.rangos_especie`: beneficio base `12.00`, `24.00`, `40.00`, `64.00` y `100.00`; nivel máximo `15`, `29`, `41`, `51` y `61`; multiplicador en el tope `3.0`, `3.5`, `4.0`, `4.5` y `5.0`.

**Valor de venta de un animal.** Con `n` el nivel del animal, `r` su rango y `n_eq(r) = redondear_medio_arriba(fraccion_equilibrio * nivel_maximo(r))` el nivel de equilibrio (`fraccion_equilibrio = 0.6667`, es decir, 2/3 del tope):

$$F_{venta}(n) = \begin{cases} f_1 + (1 - f_1)\dfrac{n - 1}{n_{eq} - 1} & n \le n_{eq} \\ 1 + \big(factor\_venta\_nivel\_maximo(r) - 1\big)\dfrac{n - n_{eq}}{nivel\_maximo(r) - n_{eq}} & n > n_{eq} \end{cases} \qquad V = redondear_{0.01}\big(valor\_venta\_base(r) \times F_{venta}(n)\big)$$

con `f_1 = factor_venta_nivel_1 = 0.05`. `F_venta(1) = 0.05`, `F_venta(n_eq) = 1` y `F_venta(nivel_maximo) = factor_venta_nivel_maximo`. En los rangos 1 a 4, `valor_venta_base` es igual al coste de compra de `CosteRango`; por tanto, vender en `n_eq` devuelve exactamente lo pagado, por debajo se pierde y por encima se gana. El rango 5 (exclusivas, coste `0`) usa `valor_venta_base` como valor de referencia. Valores: `valor_venta_base` `200.00`, `450.00`, `900.00`, `1800.00` y `4000.00`; `n_eq` `10`, `19`, `27`, `34` y `41`; `factor_venta_nivel_maximo` `1.5`, `1.75`, `2.0`, `2.25` y `2.5`; venta en el tope `300.00`, `787.50`, `1800.00`, `4050.00` y `10000.00`. `V` se calcula sobre el nivel del animal al confirmar la venta.

## 2. Diccionario de datos DDL (SQLite / Room)

```sql
PRAGMA auto_vacuum = INCREMENTAL; -- debe fijarse antes de crear las tablas
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- Catalogo global
CREATE TABLE Bioma (
    id_bioma TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE Especie (
    id_especie TEXT PRIMARY KEY,
    nombre_comun TEXT NOT NULL UNIQUE,
    id_rango INTEGER NOT NULL,
    tope_hermanos INTEGER NOT NULL CHECK (tope_hermanos > 0),
    peso_bio REAL NOT NULL CHECK (peso_bio BETWEEN 0.00 AND 1.00),
    peso_soc REAL NOT NULL CHECK (peso_soc BETWEEN 0.00 AND 1.00),
    estado_catalogo TEXT NOT NULL
        CHECK (estado_catalogo IN ('COMERCIAL', 'EXCLUSIVA')),
    nivel_desbloqueo INTEGER NULL CHECK (nivel_desbloqueo IS NULL OR nivel_desbloqueo >= 0),
    peso_pareja REAL NOT NULL CHECK (peso_pareja >= 0),
    peso_amigo REAL NOT NULL CHECK (peso_amigo >= 0),
    peso_padre REAL NOT NULL CHECK (peso_padre >= 0),
    peso_madre REAL NOT NULL CHECK (peso_madre >= 0),
    peso_hermanos REAL NOT NULL CHECK (peso_hermanos >= 0),
    peso_afinidad REAL NOT NULL CHECK (peso_afinidad >= 0),
    kg_bambu_por_incremento INTEGER NOT NULL CHECK (kg_bambu_por_incremento > 0),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_suma_pesos CHECK (ROUND(peso_bio + peso_soc, 2) = 1.00),
    CONSTRAINT chk_suma_pesos_sociales CHECK (
        ROUND(peso_pareja + peso_amigo + peso_padre + peso_madre + peso_hermanos + peso_afinidad, 2) = 1.00),
    CONSTRAINT chk_afinidad_dominante CHECK (
        peso_afinidad > peso_pareja + peso_amigo + peso_padre + peso_madre + peso_hermanos),
    CONSTRAINT chk_catalogo_desbloqueo CHECK ((estado_catalogo = 'EXCLUSIVA') = (nivel_desbloqueo IS NULL))
);

-- Nota funcional: los pesos `peso_bio` y `peso_soc` son específicos de cada especie.
-- El valor definitivo para cada especie se define en `valores_y_pesos.json`, y la suma por especie
-- debe seguir siendo 1.00. La afinidad social no familiar se calcula a partir de la matriz de afinidad
-- del mismo JSON, con una afinidad común para cada par de especies, mientras que las relaciones
-- familiares se evalúan por separado.

-- Ratificación de valores iniciales: el DDL no contiene valores por defecto funcionales para `tope_hermanos`,
-- fondos ni satisfacción. `tope_hermanos` y los pesos se cargan por especie desde `valores_y_pesos.json`; los
-- fondos y los inventarios iniciales proceden de `partida_inicial`; la satisfacción inicial de un animal es
-- siempre un valor calculado. `estado_catalogo` solo distingue COMERCIAL de EXCLUSIVA: el estado «bloqueada» de
-- una especie comercial para una partida es la ausencia de su fila en `DesbloqueoEspecie`.
-- `nivel_desbloqueo` procede de `tablas.desbloqueos_prestigio` (0 = disponible desde el inicio; NULL para exclusivas).
-- `kg_bambu_por_incremento` procede de `tablas.alimentos_por_especie`. Las especies no tienen edad media ni longevidad (RF-51).

CREATE TABLE CompatibilidadBioma (
    id_especie TEXT NOT NULL,
    id_bioma TEXT NOT NULL,
    factor_bioma REAL NOT NULL CHECK (factor_bioma BETWEEN 0.00 AND 1.00),
    PRIMARY KEY (id_especie, id_bioma),
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_bioma) REFERENCES Bioma(id_bioma)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

Cada combinación de especie y bioma debe tener un único `factor_bioma` configurado en `tablas.compatibilidad_especie_bioma` de `valores_y_pesos.json` (235 combinaciones: 47 especies por 5 biomas). Se admite cualquier valor numérico entre `0.00` y `1.00`, incluidos ambos extremos; los valores configurados son `1.00` (bioma primario), `0.75` (secundario), `0.40` (neutro) y `0.20` (incompatible por clima). Una combinación ausente o con valor nulo invalida la configuración: no se asigna un factor por defecto ni se calcula la satisfacción afectada. Esta validación no impide alojar, trasladar o comprar animales; el factor solo afecta a la satisfacción.

CREATE TABLE RangoEspecie (
    id_rango INTEGER PRIMARY KEY CHECK (id_rango > 0),
    nombre TEXT NOT NULL UNIQUE,
    umbral_reproduccion REAL NOT NULL CHECK (umbral_reproduccion BETWEEN 0.00 AND 1.00),
    probabilidad_reproduccion REAL NOT NULL CHECK (probabilidad_reproduccion BETWEEN 0.00 AND 1.00),
    penalizacion_hijo_reproduccion REAL NOT NULL CHECK (penalizacion_hijo_reproduccion BETWEEN 0.00 AND 1.00),
    probabilidad_enfermedad REAL NOT NULL CHECK (probabilidad_enfermedad BETWEEN 0.00 AND 1.00),
    beneficio_base_animal_ciclo REAL NOT NULL CHECK (beneficio_base_animal_ciclo >= 0 AND beneficio_base_animal_ciclo = ROUND(beneficio_base_animal_ciclo, 2)),
    nivel_maximo INTEGER NOT NULL CHECK (nivel_maximo >= 2),
    multiplicador_nivel_maximo REAL NOT NULL CHECK (multiplicador_nivel_maximo >= 1.00),
    valor_venta_base REAL NOT NULL CHECK (valor_venta_base >= 0 AND valor_venta_base = ROUND(valor_venta_base, 2)),
    factor_venta_nivel_maximo REAL NOT NULL CHECK (factor_venta_nivel_maximo > 1.00)
);

-- Cinco rangos (1 Común, 2 Poco común, 3 Raro, 4 Épico, 5 Exclusivo), cargados de `tablas.rangos_especie`.
-- `beneficio_base_animal_ciclo` es el beneficio de un animal de nivel 1 con satisfacción 1.00 en un ciclo (antes `beneficio_por_animal_ciclo`).
-- `nivel_maximo` es el tope de nivel de los animales del rango y `multiplicador_nivel_maximo` el valor de `M_nivel` en ese tope (sección 1.4).
-- `valor_venta_base` y `factor_venta_nivel_maximo` definen el valor de venta según nivel (sección 1.4); `valor_venta_base` coincide con `CosteRango` en los rangos con coste.
-- Las especies exclusivas pertenecen al rango 5, el de mayor nivel.

CREATE TABLE AfinidadEspecies (
    id_especie_a TEXT NOT NULL,
    id_especie_b TEXT NOT NULL,
    coeficiente REAL NOT NULL CHECK (coeficiente BETWEEN 0.00 AND 1.00),
    PRIMARY KEY (id_especie_a, id_especie_b),
    CHECK (id_especie_a <= id_especie_b),
    FOREIGN KEY (id_especie_a) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie_b) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- La afinidad se almacena como una única fila por par normalizado (`id_especie_a <= id_especie_b`),
-- incluidos los pares de una especie consigo misma, y se consulta con ese orden canónico para evitar duplicidades.
-- Las relaciones familiares (pareja, mejor amigo, padre, madre y hermanos) no forman parte de esta tabla
-- y se calculan independientemente.

CREATE TABLE Alimento (
    id_alimento TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE CosteAlimento (
    id_alimento TEXT PRIMARY KEY,
    importe_por_kilogramo REAL NOT NULL CHECK (importe_por_kilogramo >= 0 AND importe_por_kilogramo = ROUND(importe_por_kilogramo, 2)),
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE InsumoMedico (
    id_insumo TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE CosteInsumo (
    id_insumo TEXT PRIMARY KEY,
    importe_por_unidad REAL NOT NULL CHECK (importe_por_unidad >= 0 AND importe_por_unidad = ROUND(importe_por_unidad, 2)),
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE ConsumoMedicoRango (
    id_rango INTEGER NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad_unidades INTEGER NOT NULL CHECK (cantidad_unidades > 0),
    PRIMARY KEY (id_rango, id_insumo),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE Mision (
    id_mision TEXT PRIMARY KEY,
    tipo_objetivo TEXT NOT NULL UNIQUE,
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVA', 'INACTIVA')),
    naturaleza_progreso TEXT NOT NULL CHECK (naturaleza_progreso IN ('ACUMULATIVO', 'DINAMICO')),
    tipo_recompensa TEXT NOT NULL CHECK (tipo_recompensa IN ('FONDOS', 'ALIMENTO', 'INSUMO', 'ESPECIE')),
    id_item_recompensa TEXT NULL,
    cantidad_recompensa REAL NOT NULL CHECK (cantidad_recompensa > 0),
    CHECK ((tipo_recompensa IN ('ALIMENTO', 'INSUMO')) = (id_item_recompensa IS NOT NULL))
);

-- `Mision` se carga desde `catalogos.misiones`. La condición de cumplimiento y la unidad del progreso de cada tipo
-- están definidas en `Misiones/EspecificacionMisiones.md` y repetidas en el JSON. `ESPECIE` implica un animal aleatorio
-- de `recompensas_animales_aleatorios` con `cantidad_recompensa = 1`; `FONDOS` y las cantidades de alimento o insumo
-- se redondean a dos decimales y enteros respectivamente.

CREATE TABLE ParametroMision (
    id_mision TEXT NOT NULL,
    nombre_parametro TEXT NOT NULL,
    tipo_valor TEXT NOT NULL CHECK (tipo_valor IN ('NUMERICO', 'CATEGORICO')),
    valor_base_numero REAL NULL,
    valor_base_texto TEXT NULL,
    operacion_progresion TEXT NULL
        CHECK (operacion_progresion IS NULL OR operacion_progresion IN ('SUMAR', 'RESTAR', 'MULTIPLICAR', 'DIVIDIR')),
    constante_progresion REAL NULL,
    valor_minimo REAL NULL,
    valor_maximo REAL NULL,
    PRIMARY KEY (id_mision, nombre_parametro),
    FOREIGN KEY (id_mision) REFERENCES Mision(id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK (
        (tipo_valor = 'NUMERICO' AND valor_base_texto IS NULL)
        OR (tipo_valor = 'CATEGORICO' AND valor_base_numero IS NULL
            AND operacion_progresion IS NULL AND constante_progresion IS NULL)
    ),
    CHECK ((operacion_progresion IS NULL) = (constante_progresion IS NULL)),
    CHECK ((operacion_progresion IS NULL) = (valor_minimo IS NULL) AND (valor_minimo IS NULL) = (valor_maximo IS NULL)),
    CHECK (valor_minimo IS NULL OR valor_minimo <= valor_maximo),
    CHECK (operacion_progresion <> 'DIVIDIR' OR constante_progresion > 0)
);

-- Los parámetros categóricos guardan en `valor_base_texto` el código de catálogo (`id_especie` o código de hábitat).

-- Partida y jerarquia fisica
CREATE TABLE Partida (
    id_partida TEXT PRIMARY KEY,
    fondos REAL NOT NULL CHECK (fondos >= 0 AND fondos = ROUND(fondos, 2)),
    prestigio_actual INTEGER NOT NULL DEFAULT 0 CHECK (prestigio_actual >= 0),
    prestigio_maximo_nivel INTEGER NULL
        CHECK (prestigio_maximo_nivel IS NULL OR prestigio_maximo_nivel >= prestigio_actual),
    animales_vivos_maximos INTEGER NULL
        CHECK (animales_vivos_maximos IS NULL OR animales_vivos_maximos >= 0),
    satisfaccion_global_maxima REAL NULL
        CHECK (satisfaccion_global_maxima IS NULL OR satisfaccion_global_maxima BETWEEN 0.00 AND 1.00),
    estado TEXT NOT NULL DEFAULT 'ACTIVA'
        CHECK (estado IN ('ACTIVA', 'PAUSADA', 'FINALIZADA')),
    CHECK (
        (prestigio_maximo_nivel IS NULL AND animales_vivos_maximos IS NULL
            AND satisfaccion_global_maxima IS NULL)
        OR (prestigio_maximo_nivel IS NOT NULL AND animales_vivos_maximos IS NOT NULL
            AND satisfaccion_global_maxima IS NOT NULL)
    )
);

CREATE TABLE Habitat (
    id_habitat TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    codigo TEXT NOT NULL,
    id_bioma TEXT NOT NULL,
    nombre TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    UNIQUE (id_partida, id_habitat),
    UNIQUE (id_partida, codigo),
    UNIQUE (id_partida, nombre),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_bioma) REFERENCES Bioma(id_bioma)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- `id_habitat` es un identificador técnico único (no se repite entre partidas); `codigo` es el código estable del
-- catálogo `catalogos.habitats` (por ejemplo `bosque_templado`) y es el que usan los parámetros de misión y los
-- desbloqueos. Los hábitats se cargan al crear la partida, uno por bioma (5 en total).

CREATE TABLE Espacio (
    id_espacio TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_habitat TEXT NOT NULL,
    nombre TEXT NOT NULL,
    capacidad_max INTEGER NOT NULL CHECK (capacidad_max > 0),
    estado TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    UNIQUE (id_partida, id_espacio),
    UNIQUE (id_partida, id_habitat, nombre),
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE CosteEspacio (
    capacidad_max INTEGER PRIMARY KEY CHECK (capacidad_max > 0),
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2))
);

CREATE TABLE CosteRango (
    id_rango INTEGER PRIMARY KEY,
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2)),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Animal y relaciones historicas/sociales
CREATE TABLE Animal (
    id_animal TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_especie TEXT NOT NULL,
    id_espacio TEXT NULL,
    nombre TEXT NOT NULL,
    nivel INTEGER NOT NULL DEFAULT 1 CHECK (nivel >= 1),
    ciclos_nivel INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_nivel >= 0),
    nivel_alimentacion INTEGER NOT NULL DEFAULT 100 CHECK (nivel_alimentacion BETWEEN 0 AND 100 AND nivel_alimentacion % 10 = 0),
    estado_salud TEXT NOT NULL DEFAULT 'SANO' CHECK (estado_salud IN ('SANO', 'ENFERMO')),
    estado_vital TEXT NOT NULL DEFAULT 'VIVO' CHECK (estado_vital IN ('VIVO', 'MUERTO', 'VENDIDO')),
    ciclos_desnutricion INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_desnutricion BETWEEN 0 AND 3),
    ciclos_enfermedad INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_enfermedad BETWEEN 0 AND 3),
    id_pareja TEXT NULL,
    id_mejor_amigo TEXT NULL,
    id_padre TEXT NULL,
    id_madre TEXT NULL,
    satisfaccion_actual REAL NOT NULL CHECK (satisfaccion_actual BETWEEN 0.00 AND 1.00),
    UNIQUE (id_partida, id_animal),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_espacio) REFERENCES Espacio(id_partida, id_espacio)
        ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_partida, id_pareja) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_partida, id_mejor_amigo) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_partida, id_padre) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_madre) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK (estado_vital = 'VIVO' OR id_espacio IS NULL),
    CHECK (id_animal IS NULL OR id_animal <> id_pareja),
    CHECK (id_animal IS NULL OR id_animal <> id_mejor_amigo)
);

-- `nivel` sustituye a la edad (RF-51): `1` al alta y `+1` cada `ciclos_por_nivel` ciclos sanos hasta `RangoEspecie.nivel_maximo`;
-- `ciclos_nivel` (`0 .. ciclos_por_nivel - 1`, siempre `0` en el tope) es el progreso hacia el siguiente nivel. La cota superior depende del rango
-- de la especie y no puede expresarse como `CHECK` de una sola tabla; la garantiza la regla de dominio de la etapa de nivel
-- (sección 2.3.1) y la verifica un test de propiedad. `Animal` no guarda edad ni edad máxima.

CREATE TABLE RasgoPersonalidad (
    id_animal TEXT NOT NULL,
    rasgo TEXT NOT NULL,
    PRIMARY KEY (id_animal, rasgo),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE HabilidadSocial (
    id_animal TEXT NOT NULL,
    habilidad TEXT NOT NULL,
    PRIMARY KEY (id_animal, habilidad),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Economia, inventarios y auditoria
CREATE TABLE InventarioAlimento (
    id_partida TEXT NOT NULL,
    id_alimento TEXT NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_alimento),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE InventarioMedico (
    id_partida TEXT NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_insumo),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE MovimientoEconomico (
    id_movimiento INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('COMPRA', 'VENTA', 'GASTO', 'INGRESO', 'RECOMPENSA')),
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2)),
    origen TEXT NOT NULL,
    numero_ciclo INTEGER NOT NULL DEFAULT 0 CHECK (numero_ciclo >= 0),
    fecha INTEGER NOT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE BeneficioPendiente (
    id_beneficio INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2)),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'RECLAMADO', 'ANULADO')),
    origen TEXT NOT NULL,
    numero_ciclo INTEGER NOT NULL DEFAULT 0 CHECK (numero_ciclo >= 0),
    clave_idempotencia TEXT NOT NULL,
    fecha_creacion INTEGER NOT NULL,
    fecha_reclamacion INTEGER NULL,
    UNIQUE (id_partida, clave_idempotencia),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK ((estado = 'RECLAMADO') = (fecha_reclamacion IS NOT NULL))
);

CREATE TABLE AuditoriaEvento (
    id_evento INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL,
    entidad TEXT NOT NULL CHECK (entidad IN ('PARTIDA', 'ANIMAL', 'ESPACIO', 'HABITAT', 'INVENTARIO', 'MOVIMIENTO_ECONOMICO',
        'BENEFICIO', 'CICLO', 'REPRODUCCION', 'MISION', 'RECOMPENSA', 'NOTIFICACION', 'ESPECIE')),
    id_entidad TEXT NULL,
    origen TEXT NOT NULL,
    resultado TEXT NOT NULL CHECK (resultado IN ('CONFIRMADO', 'RECHAZADO', 'REVERTIDO')),
    numero_ciclo INTEGER NOT NULL DEFAULT 0 CHECK (numero_ciclo >= 0),
    clave_idempotencia TEXT NULL,
    fecha INTEGER NOT NULL,
    detalle TEXT NOT NULL,
    UNIQUE (id_partida, clave_idempotencia),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Desbloqueos, misiones y recompensas
CREATE TABLE DesbloqueoEspecie (
    id_partida TEXT NOT NULL,
    id_especie TEXT NOT NULL,
    origen TEXT NOT NULL CHECK (origen IN ('INICIAL', 'PRESTIGIO', 'RECOMPENSA')),
    fecha INTEGER NOT NULL,
    PRIMARY KEY (id_partida, id_especie),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE DesbloqueoHabitat (
    id_partida TEXT NOT NULL,
    id_habitat TEXT NOT NULL,
    fecha INTEGER NOT NULL,
    PRIMARY KEY (id_partida, id_habitat),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ProgresoMision (
    id_partida TEXT NOT NULL,
    id_instancia_mision TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    progreso REAL NOT NULL DEFAULT 0 CHECK (progreso >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'CUMPLIDA', 'AGOTADA')),
    PRIMARY KEY (id_partida, id_instancia_mision),
    UNIQUE (id_partida, id_mision),
    UNIQUE (id_partida, id_instancia_mision, id_mision),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_mision) REFERENCES Mision(id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- `progreso` es REAL porque las misiones de satisfacción lo expresan en `0..1`; el resto son enteros.
-- `AGOTADA` es el marcador de un tipo cuya progresión superaría `valor_maximo` de algún parámetro: no tiene
-- filas en `ParametroProgresoMision`, no se muestra como misión vigente y no genera nuevas instancias.

CREATE TABLE ParametroProgresoMision (
    id_partida TEXT NOT NULL,
    id_instancia_mision TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    nombre_parametro TEXT NOT NULL,
    valor_numero REAL NULL,
    valor_texto TEXT NULL,
    PRIMARY KEY (id_partida, id_instancia_mision, nombre_parametro),
    FOREIGN KEY (id_partida, id_instancia_mision, id_mision)
        REFERENCES ProgresoMision(id_partida, id_instancia_mision, id_mision)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (id_mision, nombre_parametro)
        REFERENCES ParametroMision(id_mision, nombre_parametro)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK ((valor_numero IS NULL) <> (valor_texto IS NULL))
);

CREATE TABLE RecompensaMision (
    id_recompensa INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    id_instancia_mision TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('FONDOS', 'ALIMENTO', 'INSUMO', 'ESPECIE')),
    id_item TEXT NULL,
    cantidad REAL NOT NULL CHECK (cantidad > 0),
    id_especie_seleccionada TEXT NULL,
    unidad_pendiente INTEGER NOT NULL DEFAULT 0 CHECK (unidad_pendiente IN (0, 1)),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'RECLAMADA', 'ANULADA')),
    numero_ciclo INTEGER NOT NULL DEFAULT 0 CHECK (numero_ciclo >= 0),
    fecha INTEGER NOT NULL,
    UNIQUE (id_partida, id_instancia_mision),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_mision) REFERENCES Mision(id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie_seleccionada) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK (
        (tipo = 'ESPECIE' AND (estado <> 'RECLAMADA' OR id_especie_seleccionada IS NOT NULL))
        OR (tipo <> 'ESPECIE' AND id_especie_seleccionada IS NULL)
    ),
    CHECK (unidad_pendiente = 0 OR (tipo = 'ESPECIE' AND estado = 'RECLAMADA'))
);

-- La fila `PENDIENTE` se crea en la transacción que marca la misión como `CUMPLIDA`, con el contenido copiado de
-- `Mision`. En una recompensa `ESPECIE` el sorteo se realiza al reclamar (no antes) y `unidad_pendiente` pasa a 1.

-- Simulacion, reproduccion y avisos
CREATE TABLE CicloSimulacion (
    id_partida TEXT NOT NULL,
    numero INTEGER NOT NULL CHECK (numero >= 0),
    inicio INTEGER NOT NULL,
    fin INTEGER NULL,
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'EJECUTANDO', 'CONFIRMADO', 'REVERTIDO')),
    desfase_offline INTEGER NOT NULL DEFAULT 0 CHECK (desfase_offline >= 0),
    PRIMARY KEY (id_partida, numero),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK (estado <> 'CONFIRMADO' OR fin IS NOT NULL)
) WITHOUT ROWID;

CREATE TABLE EstadoSimulacion (
    id_partida TEXT PRIMARY KEY,
    numero_ciclo_ultimo INTEGER NULL CHECK (numero_ciclo_ultimo IS NULL OR numero_ciclo_ultimo >= 0),
    instante_ultimo_ciclo INTEGER NULL,
    remanente_temporizador INTEGER NOT NULL DEFAULT 0 CHECK (remanente_temporizador BETWEEN 0 AND 600),
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVO', 'PAUSADO', 'RECUPERACION')),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

La simulación offline utiliza `R` como los segundos restantes del temporizador al cerrar la aplicación y `E` como los segundos transcurridos hasta reabrirla. Si `E < R`, no se ejecuta ningún ciclo y el nuevo remanente es `R - E`. Si `E >= R`, se ejecutan `1 + floor((E - R) / 600)` ciclos y el nuevo remanente es `600 - ((E - R) mod 600)`; un resto igual a cero se representa como `600`. Cada ciclo se identifica y se procesa de forma idempotente respetando RF-22 y RF-23.

La recuperación procesa como máximo `retencion.max_ciclos_offline` ciclos; si vencieron más, el exceso se descarta (no se acumula) y el nuevo remanente se calcula igualmente con la fórmula anterior. Cada ciclo confirma su estado esencial (animales, economía, fila de `CicloSimulacion`, `EstadoSimulacion`) en su propia transacción, pero la satisfacción y las métricas intermedias se calculan solo en memoria: el resultado final se escribe una única vez al terminar (`Animal.satisfaccion_actual` y UPSERT de `MetricaSatisfaccion`). Los avisos generados se insertan en una sola escritura final, sin duplicar el mismo aviso por ciclo, y la auditoría de la recuperación es un único `AuditoriaEvento` con el primer y último número de ciclo y el total procesado. `EstadoSimulacion.estado = 'RECUPERACION'` indica que esa escritura final está pendiente; si la aplicación se interrumpe, la siguiente apertura retoma los ciclos no confirmados y completa la escritura final. La purga de retención se ejecuta una sola vez al terminar.

Durante una recuperación offline, la alimentación baja 20 puntos porcentuales por ciclo. El nivel al inicio de la recuperación determina el límite total: si era `100%`, `80%` o `60%`, puede bajar hasta `40%`; si era `40%` o `20%`, puede bajar hasta `0%`; si ya era `0%`, no baja más. Alcanzado ese límite, no se aplican más descensos de alimentación durante esa recuperación. El ciclo que lleva la alimentación a `0%` cuenta como un ciclo de desnutrición, al igual que los ciclos posteriores que permanezca en ese estado. Si el animal ya estaba al `0%` al comenzar la recuperación, se computa como máximo un ciclo de desnutrición en esa recuperación, conforme a RF-22.

La etapa de nivel (RF-51) se aplica en cada ciclo diferido, en memoria, sobre el estado resultante de esos límites: un animal vivo, sano y con alimentación mayor que `0` al cierre de las defunciones del ciclo suma un ciclo sano de nivel (`72` suben un nivel), hasta el tope de su rango. No hay un límite de niveles específico de la recuperación; el máximo ganado queda acotado por `retencion.max_ciclos_offline` y por `nivel_maximo`. El beneficio de cada ciclo diferido usa el nivel y la satisfacción individual de ese ciclo en memoria. El nivel final se escribe una única vez, junto con el resto del estado, al terminar la recuperación.

La enfermedad respeta el límite offline de RF-23: el ciclo en que un animal enferma cuenta como un ciclo de enfermedad; si ya estaba enfermo al iniciar la recuperación, acumula como máximo dos ciclos adicionales. Los contadores de desnutrición y enfermedad avanzan de forma independiente y se reinician al recuperarse el estado correspondiente. Si ambos estados críticos coinciden, se usa el contador mayor; cuando este alcanza `3`, el animal muere y no se procesa en ciclos posteriores de esa recuperación. La condición ordinaria que hace enfermar a un animal sano es la definida en la sección 2.3.1; durante una recuperación offline el sorteo de un animal se detiene en cuanto enferma.

CREATE TABLE ReproduccionCiclo (
    id_partida TEXT NOT NULL,
    numero_ciclo INTEGER NOT NULL CHECK (numero_ciclo >= 0),
    id_animal_a TEXT NOT NULL,
    id_animal_b TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('CONCEBIDA', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA')),
    ciclo_disponible INTEGER NOT NULL CHECK (ciclo_disponible = numero_ciclo + 1),
    ciclo_expiracion INTEGER NOT NULL CHECK (ciclo_expiracion = ciclo_disponible + 2),
    PRIMARY KEY (id_partida, numero_ciclo, id_animal_a, id_animal_b),
    CHECK (id_animal_a < id_animal_b),
    FOREIGN KEY (id_partida, id_animal_a) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_animal_b) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE Notificacion (
    id_notificacion INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('INFORMATIVA', 'OPERATIVA', 'CRITICA')),
    severidad TEXT NOT NULL CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA')),
    codigo TEXT NOT NULL CHECK (codigo IN ('PRESTIGIO_ASCENSO', 'ESPECIE_DESBLOQUEADA', 'HABITAT_DESBLOQUEADO',
        'REPRODUCCION_DISPONIBLE', 'ALERTA_SALUD')),
    entidad TEXT NULL,
    id_entidad TEXT NULL,
    detalle TEXT NULL,
    numero_ciclo INTEGER NOT NULL DEFAULT 0 CHECK (numero_ciclo >= 0),
    ciclo_cierre INTEGER NULL CHECK (ciclo_cierre IS NULL OR ciclo_cierre >= numero_ciclo),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'VISTA', 'ACEPTADA', 'RESUELTA', 'DESCARTADA')),
    fecha INTEGER NOT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK ((tipo = 'INFORMATIVA' AND severidad = 'BAJA' AND estado IN ('PENDIENTE', 'VISTA'))
        OR (tipo = 'OPERATIVA' AND severidad = 'MEDIA' AND estado IN ('PENDIENTE', 'VISTA', 'ACEPTADA', 'DESCARTADA'))
        OR (tipo = 'CRITICA' AND severidad = 'ALTA' AND estado IN ('PENDIENTE', 'VISTA', 'RESUELTA'))),
    CHECK (estado NOT IN ('ACEPTADA', 'RESUELTA', 'DESCARTADA') OR ciclo_cierre IS NOT NULL),
    CHECK (estado <> 'PENDIENTE' OR ciclo_cierre IS NULL)
);

-- Contenido de `codigo` y `detalle` (JSON mínimo):
--   PRESTIGIO_ASCENSO (INFORMATIVA, entidad PARTIDA, id_entidad = nivel alcanzado; un nivel se notifica una sola vez).
--   ESPECIE_DESBLOQUEADA (INFORMATIVA, entidad ESPECIE, id_entidad = id_especie).
--   HABITAT_DESBLOQUEADO (INFORMATIVA, entidad HABITAT, id_entidad = id_habitat).
--   REPRODUCCION_DISPONIBLE (OPERATIVA, entidad REPRODUCCION, id_entidad = numero_ciclo || ':' || id_animal_a || ':' || id_animal_b).
--   ALERTA_SALUD (CRITICA, entidad ANIMAL, id_entidad = id_animal): sin detalle; los ciclos acumulados y restantes se leen
--   de `Animal.ciclos_desnutricion` y `Animal.ciclos_enfermedad`, de modo que no hay que reescribir el aviso cada ciclo.
-- `ciclo_cierre` se fija al entrar en un estado final: ACEPTADA, RESUELTA, DESCARTADA o VISTA de una INFORMATIVA.

-- Metricas: estado actual (una fila por entidad, actualizada con UPSERT). El nivel ANIMAL vive en Animal.satisfaccion_actual.
CREATE TABLE MetricaSatisfaccion (
    id_metrica INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    nivel TEXT NOT NULL CHECK (nivel IN ('ESPACIO', 'HABITAT', 'PARTIDA')),
    id_espacio TEXT NULL,
    id_habitat TEXT NULL,
    valor REAL NOT NULL CHECK (valor BETWEEN 0.00 AND 1.00),
    fecha INTEGER NOT NULL,
    CHECK (
        (nivel = 'PARTIDA' AND id_espacio IS NULL AND id_habitat IS NULL)
        OR (nivel = 'ESPACIO' AND id_espacio IS NOT NULL AND id_habitat IS NULL)
        OR (nivel = 'HABITAT' AND id_espacio IS NULL AND id_habitat IS NOT NULL)
    ),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_espacio) REFERENCES Espacio(id_partida, id_espacio)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Indices para las consultas de dominio y los paneles de seguimiento
CREATE INDEX idx_habitat_partida_bioma ON Habitat(id_partida, id_bioma);
CREATE INDEX idx_espacio_partida_habitat ON Espacio(id_partida, id_habitat, estado);
CREATE INDEX idx_animal_localizacion ON Animal(id_partida, id_espacio) WHERE estado_vital = 'VIVO';
CREATE INDEX idx_animal_critico ON Animal(id_partida, nivel_alimentacion, estado_salud, ciclos_desnutricion, ciclos_enfermedad) WHERE estado_vital = 'VIVO';
CREATE INDEX idx_animal_genealogia ON Animal(id_partida, id_padre, id_madre);
CREATE INDEX idx_movimiento_economico ON MovimientoEconomico(id_partida, numero_ciclo, tipo);
CREATE INDEX idx_beneficio_estado ON BeneficioPendiente(id_partida, estado, numero_ciclo);
CREATE INDEX idx_auditoria_partida_fecha ON AuditoriaEvento(id_partida, numero_ciclo, entidad);
CREATE INDEX idx_ciclo_partida_estado ON CicloSimulacion(id_partida, estado, numero);
CREATE INDEX idx_reproduccion_ciclo ON ReproduccionCiclo(id_partida, numero_ciclo, estado);
CREATE INDEX idx_reproduccion_vigencia ON ReproduccionCiclo(id_partida, estado, ciclo_expiracion);
CREATE INDEX idx_notificacion_estado ON Notificacion(id_partida, tipo, estado, fecha);
CREATE UNIQUE INDEX uq_notificacion_critica_activa ON Notificacion(id_partida, id_entidad)
    WHERE tipo = 'CRITICA' AND estado IN ('PENDIENTE', 'VISTA');
CREATE UNIQUE INDEX uq_notificacion_operativa ON Notificacion(id_partida, id_entidad) WHERE tipo = 'OPERATIVA';
CREATE UNIQUE INDEX uq_notificacion_informativa ON Notificacion(id_partida, codigo, id_entidad) WHERE tipo = 'INFORMATIVA';
CREATE INDEX idx_mision_estado ON ProgresoMision(id_partida, estado);
CREATE UNIQUE INDEX uq_metrica_partida ON MetricaSatisfaccion(id_partida) WHERE nivel = 'PARTIDA';
CREATE UNIQUE INDEX uq_metrica_habitat ON MetricaSatisfaccion(id_partida, id_habitat) WHERE nivel = 'HABITAT';
CREATE UNIQUE INDEX uq_metrica_espacio ON MetricaSatisfaccion(id_partida, id_espacio) WHERE nivel = 'ESPACIO';
```

### 2.1. Validaciones transaccionales no expresables solo con `CHECK`

SQLite no puede expresar completamente estas reglas mediante restricciones de fila. La capa de dominio debe comprobarlas dentro de la misma transacción que modifica los datos:

1. El espacio destino pertenece a la partida, tiene estado activo y no supera `capacidad_max` contando solo animales `VIVO`. La compatibilidad entre especie y bioma no bloquea la ubicación; su factor solo afecta al cálculo de satisfacción.
2. La capacidad solicitada para crear un espacio debe existir en `CosteEspacio`; no se permite crear espacios con capacidades fuera de esa tabla.
3. Un hábitat no se edita ni elimina durante la partida; solo se crea mediante la carga inicial autorizada.
4. Los progenitores y la pareja deben ser de la misma especie, estar en la misma partida y cumplir el estado vital exigido. `id_pareja` debe ser recíproco y ambos animales deben tener al menos el nivel mínimo de pareja (`reglas_de_calculo.nivel.nivel_minimo_pareja`, `4`); `id_mejor_amigo` puede ser interespecie, pero nunca autorreferente. La afinidad no familiar se consulta por el par de especies de los animales.
5. Una ficha solo se confirma con exactamente dos filas en `RasgoPersonalidad` y dos en `HabilidadSocial`; la satisfacción se calcula después de completar los datos del usuario y del sistema, antes de incorporar el animal al zoológico. Estas filas solo existen para animales `VIVO`: se borran al pasar a `MUERTO` o `VENDIDO`.
6. Una pareja de `ReproduccionCiclo` debe ser válida y única por ciclo; la aplicación normaliza el orden de sus identificadores antes de insertar.
7. Una concepción se hace disponible en el ciclo inmediatamente posterior al ciclo de concepción y expira tras tres ciclos disponibles; durante esa ventana no se vuelve a exigir el umbral de satisfacción de los progenitores.
8. La probabilidad total de reproducción se calcula como `max(0, probabilidad_reproduccion - penalizacion_hijo_reproduccion * hijos_vivos)`, usando los valores del rango de la especie. `hijos_vivos` cuenta todos los hijos comunes de la pareja con estado vital `VIVO`, estén alojados o no; los hijos muertos o vendidos no se cuentan. En cada ciclo se obtiene un valor aleatorio uniforme en `[0,1)` y se concibe si `aleatorio < probabilidad_total`, de modo que una probabilidad de `0` nunca concibe y una de `1` siempre concibe.
9. El beneficio se calcula para el parque completo y se genera al finalizar cada ciclo. El importe de cada ciclo se suma al total pendiente; reclamarlo acredita dicho total en los fondos y deja el acumulado pendiente en cero. Las mutaciones entre ciclos actualizan solo los datos mostrados y no cambian el importe acumulado reclamable. Muertes, ventas y otras salidas solo afectan a beneficios futuros; sin animales vivos, el beneficio generado es `0`. La fórmula (satisfacción individual, nivel y rango de cada animal) está en la sección 1.4 y los valores por rango en `RangoEspecie` (`beneficio_base_animal_ciclo`, `nivel_maximo`, `multiplicador_nivel_maximo`).
10. Una compra, venta, traslado, nacimiento, alimentación, tratamiento, baja o reclamación valida saldo, inventario, estado y capacidad antes de escribir.
11. Una especie exclusiva nunca se inserta como compra comercial. Un registro de `DesbloqueoEspecie` o `DesbloqueoHabitat` no se elimina ni se revoca; la ausencia del registro indica que la entidad sigue bloqueada.
12. Las relaciones sociales se limpian al morir o vender un animal, pero nunca se limpian `id_padre` ni `id_madre`; solo la poda de la sección 5.3 puede borrar físicamente la fila de un animal inactivo.
13. Una compra de animal exige que la especie tenga fila en `DesbloqueoEspecie`, que sea `COMERCIAL`, o bien que exista una `RecompensaMision` `RECLAMADA` de esa especie con `unidad_pendiente = 1`; en este último caso el coste es `0` y la misma transacción pone `unidad_pendiente = 0` (se consume la concesión más antigua).
14. Tras recalcular el prestigio, para cada nivel `n <= prestigio_actual` de `tablas.desbloqueos_prestigio` cuyas especies o hábitats no tengan aún fila de desbloqueo, se insertan (origen `PRESTIGIO`), se emite un aviso informativo por entidad y se audita `ESPECIE_DESBLOQUEADA`. El aviso de ascenso de prestigio se emite solo si el nivel supera `prestigio_maximo_nivel` previo.
15. Una misión pasa a `CUMPLIDA` en la transacción del evento que alcanza el objetivo y en esa misma transacción se crea su `RecompensaMision` `PENDIENTE`. La reclamación se resuelve según `Misiones/EspecificacionMisiones.md`, sección 4.

### 2.2. Almacenamiento y mantenimiento

- **Identificadores:** las tablas de registros que nadie referencia (`MovimientoEconomico`, `BeneficioPendiente`, `AuditoriaEvento`, `Notificacion`, `MetricaSatisfaccion`, `RecompensaMision`) usan `INTEGER PRIMARY KEY` (alias de `rowid`). Los identificadores de catálogo, `Partida`, `Habitat`, `Espacio` y `Animal` se mantienen como `TEXT` porque son claves referenciadas y, en el catálogo, claves legibles del JSON.
- **Fechas:** todas las columnas de fecha son `INTEGER` con segundos epoch UTC, coherente con el cálculo temporal en segundos de la simulación.
- **`WITHOUT ROWID`:** se aplica a las tablas de clave compuesta por partida con pocas columnas (`InventarioAlimento`, `InventarioMedico`, `DesbloqueoEspecie`, `DesbloqueoHabitat`, `CicloSimulacion`, `ReproduccionCiclo`), que así no duplican la clave en un índice. Con Room se crean mediante script SQL/migración.
- **Índices:** se eliminan los índices que repetían la clave primaria (inventarios y desbloqueos). Los índices de `Animal` por ubicación y estado crítico son parciales (`WHERE estado_vital = 'VIVO'`); las consultas que los usen deben incluir ese mismo predicado.
- **Espacio liberado:** tras cada purga se ejecuta `PRAGMA incremental_vacuum` y, en modo WAL, `PRAGMA wal_checkpoint(TRUNCATE)` para que el fichero no conserve páginas vacías.

### 2.3. Reglas biológicas del ciclo y transiciones de estado

#### 2.3.1. Reglas biológicas por ciclo (ordinario y offline)

Estas reglas completan las etapas de RF-14 y sus valores están en `reglas_de_calculo` y `tablas` de `valores_y_pesos.json`.

- **Nivel.** El animal no tiene edad y no muere por vejez. Toda alta (compra, unidad gratuita de recompensa o cría) empieza en el nivel `reglas_de_calculo.nivel.nivel_inicial` (`1`). La etapa de nivel se ejecuta después de las defunciones: cada animal que sigue `VIVO`, está `SANO` y tiene `nivel_alimentacion > 0` suma `1` a `Animal.ciclos_nivel`; al llegar a `ciclos_por_nivel` (`72`) sube `1` nivel y `ciclos_nivel` vuelve a `0`, sin superar `RangoEspecie.nivel_maximo` de su rango; un animal en el tope conserva su nivel y mantiene `ciclos_nivel = 0`. Esta condición equivale a `ciclos_desnutricion = 0` y `ciclos_enfermedad = 0` tras la etapa de ciclos críticos. Un animal que enferma, termina el ciclo con alimentación `0` o muere en ese ciclo no sube. Ni el nivel ni `ciclos_nivel` disminuyen ni se modifican fuera de esta etapa; un ciclo no sano ni suma ni resta. Alimentar o tratar entre ciclos cambia el estado que verá la siguiente etapa, no el nivel. La pareja exige que ambos animales alcancen `nivel_minimo_pareja` (`4`, inferior al menor `nivel_maximo`, `15`). Una cría nace con nivel `1`, igual que un animal comprado.
- **Alimentación.** Cada ciclo ordinario baja la alimentación 20 puntos porcentuales, sin bajar de `0`. En recuperación offline se aplican además los límites de RF-22.
- **Salud.** En la etapa de salud, cada animal vivo, alojado y `SANO` enferma si `aleatorio < probabilidad_enfermedad(rango) * (1 - satisfaccion_actual)`, con `aleatorio` uniforme en `[0,1)` y `satisfaccion_actual` la persistida al inicio del ciclo, sin redondear. Un animal `ENFERMO` no se sortea. La salud solo vuelve a `SANO` mediante tratamiento.
- **Ciclos críticos.** Un ciclo en que el animal termina con alimentación `0%` suma `1` a `ciclos_desnutricion`; en cualquier otro caso lo pone a `0`. Un ciclo en que termina `ENFERMO` suma `1` a `ciclos_enfermedad`; en otro caso lo pone a `0`. Por tanto el ciclo en que se entra en el estado ya cuenta como el primero. Alimentar (alimentación mayor que `0`) o tratar reinicia el contador correspondiente de inmediato. La defunción se produce cuando el mayor de los dos contadores alcanza `3`.
- **Cría.** Hereda la especie y se aloja en el espacio de sus progenitores. `id_padre` es el progenitor con identificador menor y `id_madre` el de identificador mayor (el modelo no tiene sexo; la norma solo asigna los dos vínculos de forma determinista). Los hermanos no se almacenan: son los animales vivos con el mismo `id_padre` o `id_madre`. Nace con nivel `1`.
- **Aforo.** El aforo usado de un espacio es `COUNT(*)` de `Animal` con `estado_vital = 'VIVO'` e `id_espacio` igual al espacio, dentro de la transacción; hay aforo libre si es menor que `capacidad_max`. Sin aforo, la operación se rechaza sin cambiar estados ni consumir la notificación.
- **Familiares y afinidad.** Para `C_afinidad` son familiares de un animal su pareja, su padre, su madre, sus hermanos vivos y sus hijos vivos. El mejor amigo no es familiar: si comparte espacio, cuenta en la media de afinidad.
- **Rasgos y habilidades.** Se eligen de `catalogos.rasgos_personalidad` y `catalogos.habilidades_sociales`; son descriptivos y no intervienen en ninguna fórmula.

#### 2.3.2. Estados y transiciones

| Entidad | Estado inicial | Transiciones permitidas | Finales |
|---|---|---|---|
| `Notificacion` INFORMATIVA | `PENDIENTE` | `PENDIENTE` → `VISTA` al consultarla | `VISTA` |
| `Notificacion` OPERATIVA (reproducción) | `PENDIENTE` | `PENDIENTE`/`VISTA` → `ACEPTADA` al confirmar el nacimiento; → `DESCARTADA` por rechazo explícito o al vencer sus tres ciclos; `PENDIENTE` → `VISTA` al consultarla | `ACEPTADA`, `DESCARTADA` |
| `Notificacion` CRITICA (salud) | `PENDIENTE` | `PENDIENTE` → `VISTA` al consultarla; `PENDIENTE`/`VISTA` → `RESUELTA` cuando el animal deja de tener condiciones críticas o deja de estar vivo | `RESUELTA` |
| `RecompensaMision` | `PENDIENTE` | `PENDIENTE` → `RECLAMADA` solo en la transacción que acredita la recompensa; `PENDIENTE` → `ANULADA` solo por corrección excepcional auditada antes de reclamar, sin acreditar nada | `RECLAMADA`, `ANULADA` |
| `CicloSimulacion` | `PENDIENTE` | `PENDIENTE` → `EJECUTANDO` → `CONFIRMADO`; `EJECUTANDO` → `REVERTIDO`; `REVERTIDO` → `EJECUTANDO` solo en una recuperación reintentable del mismo número y la misma clave | `CONFIRMADO` (y `REVERTIDO` si el fallo es definitivo) |
| `BeneficioPendiente` | `PENDIENTE` | `PENDIENTE` → `RECLAMADO` en la transacción que acredita los fondos; `PENDIENTE` → `ANULADO` solo para un beneficio demostrablemente inválido, con motivo auditado | `RECLAMADO`, `ANULADO` |
| `Animal.estado_vital` | `VIVO` (compra o nacimiento confirmados; no se persiste ningún estado intermedio) | `VIVO` → `MUERTO` (solo por 3 ciclos críticos); `VIVO` → `VENDIDO` | `MUERTO`, `VENDIDO` |
| `Animal.estado_salud` | `SANO` | `SANO` → `ENFERMO` por el sorteo de 2.3.1; `ENFERMO` → `SANO` al completar un tratamiento | ninguno |
| `Partida` | `ACTIVA` | `ACTIVA` ↔ `PAUSADA` por acción explícita; `ACTIVA`/`PAUSADA` → `FINALIZADA` por acción explícita | `FINALIZADA` |
| `Habitat`, `Espacio` | `ACTIVO` | `ACTIVO` → `INACTIVO` solo por la baja de un espacio vacío (CU-14); los hábitats no se dan de baja (RES-05) | `INACTIVO` mientras existan dependencias históricas |
| `EstadoSimulacion` | `ACTIVO` | `ACTIVO` ↔ `PAUSADO`; `ACTIVO`/`PAUSADO` → `RECUPERACION` al iniciar una recuperación offline y de vuelta al estado previo al completar la escritura final | ninguno |
| `ReproduccionCiclo` | `CONCEBIDA` | `CONCEBIDA` → `CONFIRMADA` (nacimiento completo), `RECHAZADA` (rechazo explícito) o `EXPIRADA` (al confirmarse un ciclo con número mayor que `ciclo_expiracion`) | los tres |
| `ProgresoMision` | `PENDIENTE` | `PENDIENTE` → `CUMPLIDA` al alcanzar el objetivo (permanece aunque el progreso dinámico baje después); la instancia reclamada se retira y se crea la siguiente `PENDIENTE`, o `AGOTADA` si la progresión superaría un máximo | `AGOTADA` |
| `Mision.estado` | `ACTIVA` | catálogo `ACTIVA`/`INACTIVA`; no es un estado de progreso | — |
| `DesbloqueoEspecie`, `DesbloqueoHabitat` | fila ausente = bloqueado | la fila se inserta una vez y nunca se elimina (RES-07) | la fila |

Las transiciones no listadas están prohibidas. Una notificación `ACEPTADA`, `RESUELTA` o `DESCARTADA` no se reabre ni se reutiliza. Un aviso crítico existe como máximo una vez activo por animal (`uq_notificacion_critica_activa`) y se actualiza al confirmar cada ciclo sin crear otro; si una causa crítica desaparece, el aviso continúa mientras persista la otra. Los avisos y los cambios de estado se escriben en la transacción del evento que los provoca y un reintento no los duplica. Los avisos `PENDIENTE` (de cualquier tipo) nunca se purgan; son una cantidad acotada porque cada informativo se emite una sola vez por nivel, especie o hábitat, la operativa expira a los tres ciclos y la crítica es una por animal vivo.

### 2.4. Inicialización de una partida

Una partida nueva se crea con una única transacción (acción del sistema; no es un caso de uso del gestor) que inserta:

1. `Partida` con `fondos`, `prestigio_actual = 0`, terna de máximos en `NULL` y `estado = 'ACTIVA'` según `partida_inicial` (2000.00 fondos).
2. `InventarioAlimento` con 30 kg de bambú e `InventarioMedico` con 3 unidades de insumo médico.
3. Los cinco `Habitat` de `catalogos.habitats`, `ACTIVO`, con su bioma; ningún `Espacio` ni `Animal`.
4. `DesbloqueoEspecie` (origen `INICIAL`) y `DesbloqueoHabitat` para las entidades del nivel 0 de `tablas.desbloqueos_prestigio`, sin avisos.
5. `EstadoSimulacion` con `estado = 'ACTIVO'`, `numero_ciclo_ultimo = NULL` y `remanente_temporizador = 600`.
6. Una instancia `PENDIENTE` de `ProgresoMision` por cada `Mision` `ACTIVA`, con `progreso = 0` y sus `ParametroProgresoMision` copiados de `parametros_base`.
7. La fila `MetricaSatisfaccion` de nivel `PARTIDA` con valor `0`.

Antes de crear una partida se valida la configuración completa de `valores_y_pesos.json` (todas las combinaciones de afinidad y compatibilidad, factores de `M_crit`, sumas de pesos, probabilidades de recompensa y límites de prestigio); una configuración inválida impide crearla.

## 3. Integridad transaccional e idempotencia

Cada operación comienza con `BEGIN IMMEDIATE`, valida sus precondiciones, aplica las mutaciones, registra `AuditoriaEvento`, recalcula las métricas afectadas y termina con `COMMIT`. Cualquier error ejecuta `ROLLBACK` y deja el estado anterior intacto.

| Operación | Escrituras atómicas mínimas | Clave de idempotencia |
|---|---|---|
| Compra de animal, creación de espacio, compra de recursos | `Animal`/`Espacio`/inventario, rasgos, habilidades, `MovimientoEconomico`, `AuditoriaEvento` | `id_partida + tipo_operacion + id_solicitud` (`tipo_operacion` distingue `COMPRA_ANIMAL`, `COMPRA_ESPACIO` y `COMPRA_RECURSO` alimento/insumo) |
| Traslado o cambio de espacio | `Animal`, métricas, `AuditoriaEvento` | `id_partida + TRASLADO + id_solicitud` |
| Alimentación individual / tratamiento | inventario, `Animal`, avisos, métricas, auditoría | `id_partida + tipo_accion + id_animal + id_solicitud` |
| Alimentación grupal | inventario, `Animal`, avisos, métricas, auditoría | `id_partida + ALIMENTACION_GRUPAL + id_espacio + id_solicitud` (la solicitud fija el conjunto objetivo y las cantidades) |
| Venta, baja de espacio, edición de ficha | entidad afectada, métricas, auditoría | `id_partida + tipo_operacion + id_solicitud` |
| Defunción o transición automática de un ciclo | `Animal`, avisos, auditoría | `id_partida + numero_ciclo + id_animal + tipo_transicion` |
| Ciclo periódico u offline | `CicloSimulacion`, animales, economía, reproducción, avisos, métricas, estado de simulación | `id_partida + numero` |
| Generación de beneficio | `BeneficioPendiente`, auditoría | `id_partida + BENEFICIO + numero_ciclo` (un solo beneficio por ciclo confirmado) |
| Reclamación de beneficios | `BeneficioPendiente`, `Partida.fondos`, `MovimientoEconomico`, auditoría | `id_partida + RECLAMACION_BENEFICIO + id_solicitud`; los registros incluidos se fijan en el primer commit |
| Confirmación de reproducción | `ReproduccionCiclo`, `Animal`, rasgos, habilidades, notificación, auditoría | `id_partida + numero_ciclo + pareja` |
| Reclamación de recompensa de misión | saldo/inventario/desbloqueo, `RecompensaMision`, `ProgresoMision`, movimiento, auditoría | `id_partida + id_instancia_mision`; el reintento devuelve la recompensa persistida y no vuelve a sortear |
| Compra de la unidad exclusiva concedida | `Animal`, `RecompensaMision.unidad_pendiente`, auditoría | su propia `id_solicitud`; cada concesión habilita una sola compra |

La reejecución de una clave ya confirmada devuelve el resultado persistido sin repetir efectos. La `id_solicitud` se genera una sola vez al iniciar la acción en la interfaz y se reutiliza en todos sus reintentos. Si una clave ya registrada llega con un contenido distinto (otro animal, espacio, cantidad o destino), la operación se rechaza como conflicto y se audita como `RECHAZADO`. Una venta o una defunción confirmadas no se revierten reutilizando su clave. Los eventos derivados dentro de una transacción heredan la clave de la operación que los originó.

La clave de idempotencia solo se almacena como texto en `AuditoriaEvento` (todas las operaciones escriben una fila de auditoría en la misma transacción) y en `BeneficioPendiente`; es única por partida. En el resto de tablas se deriva de la clave natural: `ReproduccionCiclo` por su clave primaria (`id_partida + numero_ciclo + pareja`), `RecompensaMision` por `id_partida + id_instancia_mision` y los ciclos por `id_partida + numero`. `MovimientoEconomico` y `Notificacion` se escriben atómicamente con la operación que los origina, por lo que heredan su idempotencia. La clave de `AuditoriaEvento` caduca fuera de la ventana `retencion.idempotencia` (se pone a `NULL` en la purga); a partir de entonces ese reintento ya no se reconoce. La baja de animales es lógica: se cambia `estado_vital`, se anula `id_espacio`, se borran sus rasgos y habilidades y se conserva una versión mínima del animal para la genealogía (sección 5.3).

### 3.1. Catálogo de auditoría

`AuditoriaEvento.tipo` es un código estable (nunca texto de interfaz) y `entidad` uno de los valores de su `CHECK`. Se audita toda operación de escritura cubierta por RF-47, nunca consultas ni navegación. Los tipos mínimos son:

| `tipo` | `entidad` | `origen` habitual |
|---|---|---|
| `COMPRA_ESPACIO`, `COMPRA_ANIMAL`, `COMPRA_RECURSO` | `ESPACIO`, `ANIMAL`, `INVENTARIO` | CU-03, CU-01, CU-07 |
| `VENTA_ANIMAL`, `TRASLADO_ANIMAL`, `EDICION_ANIMAL` | `ANIMAL` | CU-06, CU-04, CU-05 |
| `ALIMENTACION_INDIVIDUAL`, `ALIMENTACION_GRUPAL`, `TRATAMIENTO` | `ANIMAL` o `ESPACIO` | CU-13, CU-09 |
| `BAJA_ESPACIO` | `ESPACIO` | CU-14 |
| `DEFUNCION`, `CAMBIO_ESTADO_SALUD` | `ANIMAL` | `CICLO_PERIODICO`, `RECUPERACION_OFFLINE` o `SISTEMA` |
| `CICLO_CONFIRMADO`, `CICLO_REVERTIDO` | `CICLO` | CU-11, CU-12 |
| `REPRODUCCION_CONFIRMADA`, `REPRODUCCION_RECHAZADA` | `REPRODUCCION` | CU-10 |
| `BENEFICIO_GENERADO`, `BENEFICIO_RECLAMADO` | `BENEFICIO` | `CICLO_PERIODICO` o `RECUPERACION_OFFLINE`, CU-08 |
| `MISION_CUMPLIDA`, `RECOMPENSA_RECLAMADA` | `MISION`, `RECOMPENSA` | `SISTEMA`, CU-15 |
| `ESPECIE_DESBLOQUEADA` | `ESPECIE` | `SISTEMA` o CU-15 |
| `PRESTIGIO_ASCENSO`, `HABITAT_DESBLOQUEADO` | `PARTIDA`, `HABITAT` | `SISTEMA` |
| `AVISO_CERRADO` | `NOTIFICACION` | `SISTEMA` o el caso de uso que lo cierra |

- **Origen.** Código del caso de uso cuando procede (por ejemplo `CU-01`), o `CICLO_PERIODICO`, `RECUPERACION_OFFLINE` o `SISTEMA` para transiciones automáticas.
- **Resultado.** `CONFIRMADO`, `RECHAZADO` (solo el intento y el motivo de validación, sin mutar el dominio) o `REVERTIDO` (causa o error y constancia de que no quedaron efectos parciales).
- **Detalle mínimo.** `detalle` es un JSON con solo lo necesario: valores anterior y nuevo en cambios de estado, cantidades e importe en operaciones económicas, insumos consumidos, motivo de rechazo o reversión y referencias de la operación. Nunca guarda fichas completas.
- **Siempre.** Partida, tipo, entidad, `id_entidad` si existe, origen, fecha UTC, ciclo (`0` fuera de ciclo), resultado y detalle.
- **Una fila por operación.** No hay filas separadas para pasos internos de la misma transacción ni se duplica el evento en un reintento con la misma clave. Los ciclos offline no se auditan uno a uno: se aplica la única auditoría de recuperación de la sección 2.
- **Movimientos económicos.** `COMPRA` para espacios, animales, bambú e insumos; `VENTA` para ventas (con el importe de venta según nivel y rango, mayor que `0`); `INGRESO` para la reclamación de beneficios; `RECOMPENSA` para fondos de misiones. `GASTO` queda reservado y sin uso funcional.

## 4. Regla formal de satisfacción

### 4.1. Disparadores

El recálculo se invoca exclusivamente al finalizar un ciclo periódico u offline, o tras consolidar una mutación de alta, baja, venta, traslado, edición de atributos, cambio de hábitat, alimentación, tratamiento o reproducción. La navegación, el renderizado y las consultas de lectura no lo ejecutan (RF-44, RES-10).

### 4.2. Ecuación individual

La especificación del proyecto establece la siguiente estructura para la satisfacción de un animal vivo $i$:

$$S_i = \Big(w_{bio}S_{bio} + w_{soc}S_{soc}\Big) \times P_{hab} \times M_{crit}$$

Los pesos `w_bio` y `w_soc` se almacenan en `Especie` y la restricción existente exige que sumen `1.00`. Sus valores por especie están en `valores_y_pesos.json`: especies gregarias `0.40/0.60`, intermedias `0.50/0.50` y solitarias `0.60/0.40`.

La escala confirmada para `S_soc` y para las métricas de satisfacción es `0..1`. La fórmula social debe ser compatible con esta escala y no debe introducir una conversión adicional a `0..100`.

La satisfacción social se calcula mediante:

$$S_{soc} = w_{pareja}C_{pareja} + w_{amigo}C_{amigo} + w_{padre}C_{padre} + w_{madre}C_{madre} + w_{hermanos}C_{hermanos} + w_{afinidad}C_{afinidad}$$

`C_pareja`, `C_amigo`, `C_padre` y `C_madre` valen `1` cuando existe la relación válida y `0` cuando no existe. `C_hermanos = min(1, hermanos_vivos / tope_hermanos)`. `C_afinidad` es la media de afinidad con los animales no familiares del mismo espacio; si no hay animales no familiares, vale `0`. Los pesos sociales de cada especie deben ser no negativos y sumar `1.00`; además, `w_afinidad` debe ser mayor que la suma de los pesos familiares. Los componentes son valores entre `0` y `1`, por lo que `S_soc` permanece en esa escala.

La afinidad social se define por pares de especies: todos los animales de una especie comparten la afinidad con otra especie, incluida su propia especie. `AfinidadEspecies` y la matriz del JSON utilizan un único par normalizado con `id_especie_a <= id_especie_b`, consultado simétricamente, con coeficiente entre `0` y `1`. La configuración debe contener todas las combinaciones de especies, incluidas las combinaciones de cada especie consigo misma, y todas las combinaciones especie-bioma; una configuración incompleta no es válida. Los valores concretos están en `valores_y_pesos.json`: 1128 pares normalizados (47 especies, incluidos los 47 pares diagonales).

Cuando no existe una relación social, su componente aporta `0` a `S_soc` y no se aplica ninguna penalización adicional.

En cada especie, `w_afinidad` debe ser mayor que la suma de los pesos de pareja, mejor amigo, padre, madre y hermanos. `num_hermanos` cuenta únicamente animales vivos.

`S_i`, `S_soc` y `S_bio` se conservan sin redondear durante el cálculo. `S_i` permanece en `0..1` por los rangos de sus componentes y pesos; no se aplica saturación adicional. La satisfacción individual se muestra como porcentaje entero entre `0` y `100`, redondeando al entero más cercano y los medios hacia arriba.

$$S_{bio} = \frac{nivel\_alimentacion}{100}$$

`nivel_alimentacion` está definido en el DDL en el rango `0..100`, por lo que esta operación produce un valor entre `0` y `1`; en particular, `0%` produce `0` y `100%` produce `1`. La alimentación y la salud no son editables directamente por el usuario (RES-03).

El factor crítico se calcula sin sumas mediante la combinación multiplicativa:

$$M_{crit} = M_{alimentacion} \times M_{enfermedad}$$

`M_alimentacion` utiliza una tabla específica por especie para los niveles `[0, 10, 20, ..., 90]`; con alimentación al `100%` su valor es `1`. `M_enfermedad` también es específico por especie y toma el valor `1` cuando el animal está sano. Los factores permanecen en `0..1` y `M_crit` no se redondea. La configuración debe incluir los factores requeridos para cada especie, nivel de alimentación y estado de salud; no se admite que falte ninguno.

El cálculo interno y la persistencia de `S_i` permanecen en `0..1` sin redondeo. Para mostrar la satisfacción individual se transforma a porcentaje `0..100` sin decimales; los medios se redondean hacia arriba y los valores inferiores al medio hacia abajo.

La documentación existente identifica las entradas de los demás componentes, pero no proporciona todavía sus fórmulas, ponderaciones ni todos sus valores:

| Componente | Información definida por el proyecto | Valores |
|---|---|---|
| `S_soc` | Fórmula ponderada de pareja, amigo, padre, madre, hermanos vivos y afinidad no familiar; se expresa en `0..1`, usa pesos sociales no negativos cuya suma es `1.00` y no se redondea. Los animales no familiares de la misma especie participan usando el valor diagonal de su especie. | Pesos sociales por especie y matriz de afinidad en el JSON. |
| `P_hab` | Procede de la tabla de compatibilidad entre especie y bioma del JSON. Debe existir una entrada para cada combinación especie-bioma; el valor se multiplica directamente en la ecuación y no limita la ubicación. El JSON conserva los valores de configuración; no representa el esquema real de BBDD. | Tabla `compatibilidad_especie_bioma` del JSON. |
| `M_crit` | Se calcula como `M_alimentacion * M_enfermedad`, con factores específicos por especie en `0..1`; alimentación al `100%` y estado sano tienen factor `1`. La configuración debe incluir todos los factores requeridos y no puede dejar ninguno ausente. | Tabla `factores_mcrit_especie` del JSON (curva de alimentación 0..90 y factor de enfermedad por especie). `M_crit` no se redondea. |

Con la configuración completa de `valores_y_pesos.json` todos los componentes de `S_i` tienen fórmula y valores. Su inventario está en `BBDD/ValoresYFormulasPendientes.md`.

El cálculo solo puede tomar como entrada el estado confirmado de un animal vivo alojado en un espacio (`estado_vital = 'VIVO'` e `id_espacio` no nulo). Los animales muertos o vendidos se conservan para el histórico, la genealogía y la auditoría, pero no forman parte de una satisfacción activa.

No se fija ninguna saturación adicional ni valor sustitutivo para una configuración incompleta. La ausencia de cualquier factor requerido de `M_crit`, afinidad entre cualquier par de especies (incluidas las afinidades de cada especie consigo misma) o compatibilidad especie-bioma invalida la configuración y no se reemplaza por un valor supuesto.

### 4.3. Persistencia y propagación

Una vez definidos todos los parámetros necesarios, el resultado individual se persiste en `Animal.satisfaccion_actual` en la escala `0..1` sin redondeo cuando el evento o ciclo queda confirmado. Las métricas de espacio, hábitat y partida se calculan como la media directa de los animales vivos pertenecientes a cada nivel y se guardan en `MetricaSatisfaccion` con una única fila por entidad, actualizada mediante UPSERT (estado actual, sin serie temporal). Un espacio o hábitat sin animales vivos no tiene fila (se borra); si la partida no tiene animales vivos, su satisfacción global es `0` y se conserva la fila de nivel `PARTIDA` con valor `0`.

Las métricas no guardan histórico: cada recálculo sobrescribe el valor vigente, por lo que el número de filas está acotado por espacios + hábitats + 1 por partida y no crece con los ciclos. Todas son derivables de `Animal.satisfaccion_actual`. El recálculo debe producirse solo tras una mutación confirmada o la finalización de un ciclo; no se ejecuta durante navegación, renderizado ni consultas de lectura (RF-15, RF-44, RES-10).

### 4.4. Prestigio

El prestigio actual es un nivel entero. El nivel `1` tiene dos umbrales base configurables: cantidad de animales vivos alojados y satisfacción global mínima. Para cada nivel posterior, cada umbral sin redondear se obtiene multiplicando el umbral sin redondear del nivel anterior por su multiplicador específico; ambos multiplicadores deben ser mayores que `1`. Para comparar la cantidad actual de animales, el umbral calculado se redondea al entero más cercano, con los medios hacia arriba. El siguiente umbral de animales se calcula usando el valor sin redondear, no el entero usado para comparar. El umbral de satisfacción y su progresión conservan precisión completa. Los valores base y multiplicadores se parametrizan en `valores_y_pesos.json`: nivel 1 con `2` animales y satisfacción `0.30`; multiplicadores `1.40` (animales) y `1.10` (satisfacción); `nivel_maximo = 10`. Límites de configuración: el umbral base de animales es un entero mayor o igual que `1`; la satisfacción base está en `(0, 1]`; cada multiplicador cumple `1 < m <= 3.00`; y el umbral de satisfacción de cada nivel hasta `nivel_maximo` debe ser menor o igual que `1.00` (si no, la configuración es inválida). La tabla `tablas.niveles_prestigio` es la expansión de la fórmula y debe coincidir con ella.

El nivel actual es el mayor nivel cuyos dos umbrales se cumplen simultáneamente. Si ninguno se cumple, el prestigio actual es `0`. La cantidad cuenta animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo. La satisfacción global es la media directa de la satisfacción de todos los animales vivos alojados; si no hay ninguno, su valor es `0`. El prestigio puede disminuir cuando dejan de cumplirse los umbrales y se recalcula únicamente en los eventos de RF-35.

El máximo histórico se compone de tres máximos independientes: nivel de prestigio, cantidad de animales vivos y satisfacción global. Cada componente se actualiza cuando el valor actual supera su máximo registrado; por tanto, la terna puede combinar máximos obtenidos en momentos distintos. Una partida nueva tiene prestigio actual `0` y todavía no tiene una terna de máximo histórico. El esquema representa esa ausencia mediante los tres campos `NULL`; la terna se inicializa cuando se alcanza por primera vez un máximo y, a partir de ahí, cada componente se actualiza independientemente.

Los desbloqueos por nivel se definen en `tablas.desbloqueos_prestigio`: el nivel `0` (6 especies comerciales y 2 hábitats) se concede al crear la partida y los niveles `1` a `10` desbloquean el resto de las 38 especies comerciales y los otros 8 hábitats. Al alcanzar un nivel, se desbloquea todo lo de los niveles menores o iguales que aún no lo estuviera (también tras un salto de varios niveles). Las especies exclusivas solo se desbloquean por recompensa de misión.

Se notifica un ascenso únicamente cuando supera el nivel máximo histórico previo. Si un recálculo avanza varios niveles, se emite un solo aviso por el nivel más alto alcanzado. No se repite el aviso al recuperar un nivel ya alcanzado anteriormente. Los avisos por desbloqueo de especies siguen sujetos a RF-43.

## 5. Soft delete y consistencia histórica

Un animal muerto o vendido conserva únicamente una versión mínima: `id_animal`, `id_partida`, `id_especie`, `nombre`, `estado_vital`, `id_padre` e `id_madre`. Se establece `estado_vital` a `MUERTO` o `VENDIDO`, `id_espacio` a `NULL`, se anulan los vínculos sociales mutables y se borran sus filas de `RasgoPersonalidad` y `HabilidadSocial`. No se permite el borrado físico de animales con descendencia; la poda de animales sin vínculo genealógico necesario se describe en la sección 5.3.

Un espacio vacío puede pasar a `INACTIVO` tras confirmación del usuario, pero no se elimina físicamente si existen operaciones pendientes o auditorías dentro de su ventana que lo referencien. Los animales inactivos no referencian espacios (`id_espacio` es `NULL`) y las métricas de un espacio sin animales vivos no existen. La capacidad máxima permanece inmutable.

### 5.1. Cobertura del modelo

Los datos que antes figuraban como insuficiencias quedan cubiertos así:

- coste de adquisición de espacios: `CosteEspacio`, desde `tablas.costes_espacios`;
- nivel del animal y su tope por rango: sección 2.3.1, `Animal.nivel` y `RangoEspecie.nivel_maximo`;
- cantidad y objeto de las recompensas de misión: `Mision.tipo_recompensa`, `id_item_recompensa` y `cantidad_recompensa`, y `RecompensaMision.id_item` y `cantidad`;
- ciclos acumulados y restantes de los avisos críticos: se leen de `Animal.ciclos_desnutricion` y `Animal.ciclos_enfermedad` (restantes `= 3 - max(ambos)`), mostrados por separado cuando concurren;
- contenido y origen de los avisos: `Notificacion.codigo`, `entidad`, `id_entidad` y `detalle`;
- clasificación y origen de los eventos auditables: sección 3.1.

La capa de dominio debe seguir aplicando las validaciones ya descritas para capacidad, estados, reciprocidad, misma especie, fichas con exactamente dos rasgos y dos habilidades, solvencia, inventario e idempotencia. Los factores de compatibilidad especie-bioma se aplican al cálculo de satisfacción, no como validación de ubicación. Los contadores de desnutrición y enfermedad son independientes; cada uno se reinicia al recuperarse su estado correspondiente y, si ambos alcanzan valores distintos, se utiliza el mayor para decidir la defunción. Esta lista no introduce reglas nuevas.

### 5.2. Retención y purga

La base de datos conserva el estado actual y una ventana fija de histórico; su tamaño no crece con el tiempo jugado ni con el número de ciclos. Las ventanas se leen de `retencion` en `valores_y_pesos.json` (no se codifican en el DDL) y se miden en ciclos respecto al número de ciclo vigente, `EstadoSimulacion.numero_ciclo_ultimo`. Los registros de `MovimientoEconomico`, `BeneficioPendiente`, `AuditoriaEvento`, `Notificacion` y `RecompensaMision` guardan en `numero_ciclo` el ciclo vigente al crearse (0 si aún no hay ninguno).

| Dato | Ventana (`retencion`) | Criterio de borrado |
|---|---|---|
| `AuditoriaEvento` | `auditoria_evento` | `numero_ciclo` anterior a la ventana; además, `clave_idempotencia` se pone a `NULL` fuera de `idempotencia` |
| `MovimientoEconomico` | `movimiento_economico` | `numero_ciclo` anterior a la ventana; el saldo vive en `Partida.fondos` |
| `Notificacion` | `notificacion_cerrada` | Las `PENDIENTE` nunca se borran; las demás, cuando su `ciclo_cierre` sale de la ventana o quedan fuera de las últimas N filas de la partida |
| `BeneficioPendiente` | `beneficio_cerrado` | `RECLAMADO` o `ANULADO` fuera de la ventana; los `PENDIENTE` se conservan |
| `ReproduccionCiclo` | `reproduccion_finalizada` | `CONFIRMADA`, `RECHAZADA` o `EXPIRADA` fuera de la ventana; las `CONCEBIDA` se conservan |
| `RecompensaMision` | `recompensa_mision_cerrada` | `RECLAMADA` o `ANULADA` fuera de la ventana; las `PENDIENTE` y las que tengan `unidad_pendiente = 1` se conservan |
| `CicloSimulacion` | `ciclo_simulacion` | `CONFIRMADO` o `REVERTIDO` fuera de la ventana; el contador vigente vive en `EstadoSimulacion` |
| `Animal` inactivo, `RasgoPersonalidad`, `HabilidadSocial` | sin ventana | Rasgos y habilidades se borran al morir o venderse; la fila del animal se poda según la sección 5.3 |

La purga se ejecuta una vez tras confirmar cada ciclo (o la recuperación offline completa), en su propia transacción. Si falla, el ciclo ya confirmado no se revierte y la purga se reintenta en el ciclo siguiente. Ninguna tabla de histórico mantiene claves foráneas hacia `CicloSimulacion`, por lo que la purga nunca queda bloqueada por `ON DELETE RESTRICT`.

### 5.3. Poda de animales inactivos

Un animal `MUERTO` o `VENDIDO` se borra físicamente en la purga posterior al ciclo (tras purgar `ReproduccionCiclo`) cuando cumple estas condiciones. Los hermanos se cuentan únicamente entre animales `VIVO` (animales vivos con el mismo `id_padre` o `id_madre`), por lo que un hermano inactivo no afecta a ningún cálculo y puede podarse:

1. Ningún animal lo referencia como `id_padre` o `id_madre`.
2. Ninguna fila de `ReproduccionCiclo` lo referencia.

La poda se repite hasta que no se borre ninguna fila, de forma que al desaparecer un descendiente puede liberarse su ascendiente. Un animal vivo conserva siempre a sus progenitores. El nombre de un animal no es único (RF-32), por lo que borrar la fila no libera ni reserva ningún nombre.

## 6. Matriz de cobertura

| Grupo de datos | Requisitos funcionales | No funcionales | Restricciones |
|---|---|---|---|
| Catálogo, hábitat y espacio | RF-02, RF-04, RF-07, RF-08, RF-25, RF-29, RF-31, RF-49 | RNF-01, RNF-04, RNF-13 | RES-04, RES-05, RES-06, RES-13 |
| Animal, rasgos y vínculos | RF-01, RF-03, RF-05, RF-06, RF-18, RF-19, RF-32, RF-33, RF-34, RF-48 | RNF-01, RNF-02, RNF-08, RNF-12, RNF-13 | RES-03, RES-06, RES-08, RES-11, RES-13 |
| Economía e inventario | RF-09, RF-10, RF-11, RF-12, RF-13, RF-26, RF-30, RF-31, RF-40, RF-50 | RNF-02, RNF-08, RNF-10, RNF-12, RNF-13 | RES-08, RES-12 |
| Ciclos, reproducción y offline | RF-14, RF-16, RF-17, RF-18, RF-21, RF-22, RF-23, RF-24, RF-44, RF-45, RF-46 | RNF-02, RNF-05, RNF-08, RNF-09, RNF-12, RNF-13, RNF-14 | RES-10, RES-13 |
| Métricas, prestigio y misiones | RF-15, RF-27, RF-35, RF-36, RF-37, RF-38, RF-39, RF-40 | RNF-01, RNF-02, RNF-10, RNF-13 | RES-07, RES-09, RES-10, RES-12 |
| Avisos y auditoría | RF-20, RF-28, RF-41, RF-42, RF-43, RF-47 | RNF-07, RNF-08, RNF-09, RNF-11, RNF-12, RNF-14 | RES-11, RES-12 |

Esta matriz implementa los requisitos existentes y el RNF-14 de contención del tamaño de datos; no introduce requisitos funcionales nuevos. `Trazabilidad.md` incorpora RNF-14 en los casos de uso que purgan o acotan datos (CU-06, CU-11, CU-12 y CU-21).
