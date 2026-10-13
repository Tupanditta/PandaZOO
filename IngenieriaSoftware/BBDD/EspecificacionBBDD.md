# Especificación técnica: modelo de datos y cálculo de satisfacción

## 1. Alcance y principios

El modelo persigue la tercera forma normal (3FN), evita referencias entre partidas, conserva el histórico necesario para auditoría y genealogía y permite recalcular las métricas únicamente como reacción a eventos de dominio. La base de datos usa SQLite/Room con claves foráneas activadas mediante `PRAGMA foreign_keys = ON`.

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
Partida 1---N ProgresoMision / Notificacion / AuditoriaEvento
Partida 1---1 EstadoSimulacion
```

Las claves compuestas que contienen `id_partida` impiden que una fila de una partida apunte accidentalmente a una entidad de otra partida.

## 2. Diccionario de datos DDL (SQLite / Room)

```sql
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
    tope_hermanos INTEGER NOT NULL DEFAULT 2 CHECK (tope_hermanos > 0),
    peso_bio REAL NOT NULL CHECK (peso_bio BETWEEN 0.00 AND 1.00),
    peso_soc REAL NOT NULL CHECK (peso_soc BETWEEN 0.00 AND 1.00),
    estado_catalogo TEXT NOT NULL DEFAULT 'BLOQUEADA'
        CHECK (estado_catalogo IN ('BLOQUEADA', 'COMERCIAL', 'EXCLUSIVA')),
    umbral_desbloqueo REAL CHECK (umbral_desbloqueo IS NULL OR umbral_desbloqueo BETWEEN 0.00 AND 1.00),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_suma_pesos CHECK (ROUND(peso_bio + peso_soc, 2) = 1.00)
);

-- Nota funcional: los pesos `peso_bio` y `peso_soc` son específicos de cada especie.
-- El valor definitivo para cada especie se define en `valores_y_pesos.json`, y la suma por especie
-- debe seguir siendo 1.00. La afinidad social no familiar se calcula a partir de la matriz de afinidad
-- del mismo JSON, con una afinidad común para cada par de especies, mientras que las relaciones
-- familiares se evalúan por separado.

-- Los valores por defecto del DDL, incluidos `tope_hermanos`, fondos iniciales, alimentación,
-- satisfacción inicial y factores de compatibilidad, son valores de esquema pendientes de confirmación.
-- No representan decisiones funcionales mientras no estén ratificados por el proyecto.

CREATE TABLE CompatibilidadBioma (
    id_especie TEXT NOT NULL,
    id_bioma TEXT NOT NULL,
    factor_bioma REAL NOT NULL CHECK (factor_bioma IN (1.00, 0.75, 0.30)),
    PRIMARY KEY (id_especie, id_bioma),
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_bioma) REFERENCES Bioma(id_bioma)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE RangoEspecie (
    id_rango INTEGER PRIMARY KEY CHECK (id_rango > 0),
    nombre TEXT NOT NULL UNIQUE,
    umbral_reproduccion REAL NOT NULL CHECK (umbral_reproduccion BETWEEN 0.00 AND 1.00),
    probabilidad_reproduccion REAL NOT NULL CHECK (probabilidad_reproduccion BETWEEN 0.00 AND 1.00),
    penalizacion_hijo_reproduccion REAL NOT NULL CHECK (penalizacion_hijo_reproduccion BETWEEN 0.00 AND 1.00)
);

CREATE TABLE AfinidadEspecies (
    id_especie_a TEXT NOT NULL,
    id_especie_b TEXT NOT NULL,
    coeficiente REAL NOT NULL CHECK (coeficiente BETWEEN -1.00 AND 1.00),
    PRIMARY KEY (id_especie_a, id_especie_b),
    CHECK (id_especie_a <> id_especie_b),
    CHECK (id_especie_a < id_especie_b),
    FOREIGN KEY (id_especie_a) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie_b) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- La afinidad entre especies se almacena como una única fila por par normalizado (`id_especie_a < id_especie_b`)
-- y se consulta con ese orden canónico para evitar duplicidades y permitir la búsqueda por la clave primaria.
-- Las relaciones familiares (pareja, mejor amigo, padre, madre y hermanos) no forman parte de esta tabla
-- y se calculan independientemente.

CREATE TABLE Alimento (
    id_alimento TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE CosteAlimento (
    id_alimento TEXT PRIMARY KEY,
    importe_por_kilogramo REAL NOT NULL CHECK (importe_por_kilogramo >= 0),
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE InsumoMedico (
    id_insumo TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE CosteInsumo (
    id_insumo TEXT PRIMARY KEY,
    importe_por_unidad REAL NOT NULL CHECK (importe_por_unidad >= 0),
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE ConsumoMedicoRango (
    id_rango INTEGER NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad_unidades REAL NOT NULL CHECK (cantidad_unidades > 0),
    PRIMARY KEY (id_rango, id_insumo),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE Mision (
    id_mision TEXT PRIMARY KEY,
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVA', 'INACTIVA')),
    objetivo TEXT NOT NULL,
    recompensa TEXT NOT NULL
);

-- Partida y jerarquia fisica
CREATE TABLE Partida (
    id_partida TEXT PRIMARY KEY,
    fondos REAL NOT NULL DEFAULT 0 CHECK (fondos >= 0),
    prestigio_actual REAL NOT NULL DEFAULT 0 CHECK (prestigio_actual >= 0),
    prestigio_maximo REAL NOT NULL DEFAULT 0 CHECK (prestigio_maximo >= prestigio_actual),
    estado TEXT NOT NULL DEFAULT 'ACTIVA'
        CHECK (estado IN ('ACTIVA', 'PAUSADA', 'FINALIZADA'))
);

CREATE TABLE Habitat (
    id_habitat TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_bioma TEXT NOT NULL,
    nombre TEXT NOT NULL,
    estado TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    UNIQUE (id_partida, id_habitat),
    UNIQUE (id_partida, nombre),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_bioma) REFERENCES Bioma(id_bioma)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

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
    importe REAL NOT NULL CHECK (importe >= 0)
);

CREATE TABLE CosteRango (
    id_rango INTEGER PRIMARY KEY,
    importe REAL NOT NULL CHECK (importe >= 0),
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
    edad_anios INTEGER NOT NULL DEFAULT 0 CHECK (edad_anios >= 0),
    edad_meses INTEGER NOT NULL DEFAULT 0 CHECK (edad_meses BETWEEN 0 AND 12),
    edad_dias INTEGER NOT NULL DEFAULT 0 CHECK (edad_dias BETWEEN 0 AND 31),
    edad_maxima_anios INTEGER NOT NULL CHECK (edad_maxima_anios >= 0),
    edad_maxima_meses INTEGER NOT NULL CHECK (edad_maxima_meses BETWEEN 0 AND 12),
    edad_maxima_dias INTEGER NOT NULL CHECK (edad_maxima_dias BETWEEN 0 AND 31),
    nivel_alimentacion INTEGER NOT NULL DEFAULT 100 CHECK (nivel_alimentacion BETWEEN 0 AND 100),
    estado_salud TEXT NOT NULL DEFAULT 'SANO' CHECK (estado_salud IN ('SANO', 'ENFERMO')),
    estado_vital TEXT NOT NULL DEFAULT 'VIVO' CHECK (estado_vital IN ('VIVO', 'MUERTO', 'VENDIDO')),
    ciclos_desnutricion INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_desnutricion BETWEEN 0 AND 3),
    ciclos_enfermedad INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_enfermedad BETWEEN 0 AND 3),
    id_pareja TEXT NULL,
    id_mejor_amigo TEXT NULL,
    id_padre TEXT NULL,
    id_madre TEXT NULL,
    satisfaccion_actual REAL NOT NULL DEFAULT 1.00 CHECK (satisfaccion_actual BETWEEN 0.00 AND 1.00),
    UNIQUE (id_partida, id_animal),
    UNIQUE (id_partida, id_especie, nombre),
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
    cantidad REAL NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_alimento),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE InventarioMedico (
    id_partida TEXT NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad REAL NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_insumo),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE MovimientoEconomico (
    id_movimiento TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('COMPRA', 'VENTA', 'GASTO', 'INGRESO', 'RECOMPENSA')),
    importe REAL NOT NULL CHECK (importe >= 0),
    origen TEXT NOT NULL,
    id_ciclo TEXT NULL,
    clave_idempotencia TEXT NOT NULL UNIQUE,
    fecha TEXT NOT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE BeneficioPendiente (
    id_beneficio TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    importe REAL NOT NULL CHECK (importe >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'RECLAMADO', 'ANULADO')),
    origen TEXT NOT NULL,
    id_ciclo TEXT NULL,
    clave_idempotencia TEXT NOT NULL UNIQUE,
    fecha_creacion TEXT NOT NULL,
    fecha_reclamacion TEXT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK ((estado = 'RECLAMADO') = (fecha_reclamacion IS NOT NULL))
);

CREATE TABLE PonderacionBeneficioRango (
    id_rango INTEGER PRIMARY KEY,
    satisfaccion_minima REAL NOT NULL CHECK (satisfaccion_minima BETWEEN 0.00 AND 1.00),
    multiplicador REAL NOT NULL CHECK (multiplicador BETWEEN 1.00 AND 5.00),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE AuditoriaEvento (
    id_evento TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL,
    entidad TEXT NOT NULL,
    id_entidad TEXT NULL,
    resultado TEXT NOT NULL CHECK (resultado IN ('CONFIRMADO', 'RECHAZADO', 'REVERTIDO')),
    id_ciclo TEXT NULL,
    clave_idempotencia TEXT UNIQUE,
    fecha TEXT NOT NULL,
    detalle TEXT NOT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Desbloqueos, misiones y recompensas
CREATE TABLE DesbloqueoEspecie (
    id_partida TEXT NOT NULL,
    id_especie TEXT NOT NULL,
    fecha TEXT NOT NULL,
    PRIMARY KEY (id_partida, id_especie),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE DesbloqueoHabitat (
    id_partida TEXT NOT NULL,
    id_habitat TEXT NOT NULL,
    fecha TEXT NOT NULL,
    PRIMARY KEY (id_partida, id_habitat),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE ProgresoMision (
    id_partida TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    progreso INTEGER NOT NULL DEFAULT 0 CHECK (progreso >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'CUMPLIDA', 'RECLAMADA')),
    PRIMARY KEY (id_partida, id_mision),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_mision) REFERENCES Mision(id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE RecompensaMision (
    id_recompensa TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('FONDOS', 'ALIMENTO', 'INSUMO', 'ESPECIE')),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'RECLAMADA', 'ANULADA')),
    clave_idempotencia TEXT NOT NULL UNIQUE,
    fecha TEXT NOT NULL,
    FOREIGN KEY (id_partida, id_mision) REFERENCES ProgresoMision(id_partida, id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Simulacion, reproduccion y avisos
CREATE TABLE CicloSimulacion (
    id_ciclo TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    numero INTEGER NOT NULL CHECK (numero >= 0),
    inicio TEXT NOT NULL,
    fin TEXT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'EJECUTANDO', 'CONFIRMADO', 'REVERTIDO')),
    desfase_offline INTEGER NOT NULL DEFAULT 0 CHECK (desfase_offline >= 0),
    UNIQUE (id_partida, id_ciclo),
    UNIQUE (id_partida, numero),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CHECK (estado <> 'CONFIRMADO' OR fin IS NOT NULL)
);

CREATE TABLE EstadoSimulacion (
    id_partida TEXT PRIMARY KEY,
    id_ciclo_ultimo TEXT NULL,
    instante_ultimo_ciclo TEXT NULL,
    remanente_temporizador INTEGER NOT NULL DEFAULT 0 CHECK (remanente_temporizador BETWEEN 0 AND 600),
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVO', 'PAUSADO', 'RECUPERACION')),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo_ultimo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

La simulación offline utiliza `R` como los segundos restantes del temporizador al cerrar la aplicación y `E` como los segundos transcurridos hasta reabrirla. Si `E < R`, no se ejecuta ningún ciclo y el nuevo remanente es `R - E`. Si `E >= R`, se ejecutan `1 + floor((E - R) / 600)` ciclos y el nuevo remanente es `600 - ((E - R) mod 600)`; un resto igual a cero se representa como `600`. Cada ciclo se identifica y se procesa de forma idempotente respetando RF-22 y RF-23.

CREATE TABLE ReproduccionCiclo (
    id_partida TEXT NOT NULL,
    id_ciclo TEXT NOT NULL,
    id_animal_a TEXT NOT NULL,
    id_animal_b TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('CONCEBIDA', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA')),
    ciclo_disponible INTEGER NOT NULL CHECK (ciclo_disponible >= 0),
    ciclo_expiracion INTEGER NOT NULL CHECK (ciclo_expiracion = ciclo_disponible + 2),
    clave_idempotencia TEXT NOT NULL UNIQUE,
    PRIMARY KEY (id_partida, id_ciclo, id_animal_a, id_animal_b),
    CHECK (id_animal_a < id_animal_b),
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_animal_a) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_animal_b) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE Notificacion (
    id_notificacion TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('INFORMATIVA', 'OPERATIVA', 'CRITICA')),
    severidad TEXT CHECK (severidad IS NULL OR severidad IN ('BAJA', 'MEDIA', 'ALTA')),
    entidad TEXT NULL,
    id_entidad TEXT NULL,
    id_ciclo TEXT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'VISTA', 'ACEPTADA', 'RESUELTA', 'DESCARTADA')),
    fecha TEXT NOT NULL,
    clave_idempotencia TEXT NOT NULL UNIQUE,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Metricas: exactamente un nivel por fila y FK real a la entidad medida
CREATE TABLE MetricaSatisfaccion (
    id_metrica TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_ciclo TEXT NULL,
    nivel TEXT NOT NULL CHECK (nivel IN ('ANIMAL', 'ESPACIO', 'HABITAT', 'PARTIDA')),
    id_animal TEXT NULL,
    id_espacio TEXT NULL,
    id_habitat TEXT NULL,
    valor REAL NOT NULL CHECK (valor BETWEEN 0.00 AND 1.00),
    fecha TEXT NOT NULL,
    CHECK (
        (nivel = 'PARTIDA' AND id_animal IS NULL AND id_espacio IS NULL AND id_habitat IS NULL)
        OR (nivel = 'ANIMAL' AND id_animal IS NOT NULL AND id_espacio IS NULL AND id_habitat IS NULL)
        OR (nivel = 'ESPACIO' AND id_animal IS NULL AND id_espacio IS NOT NULL AND id_habitat IS NULL)
        OR (nivel = 'HABITAT' AND id_animal IS NULL AND id_espacio IS NULL AND id_habitat IS NOT NULL)
    ),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_ciclo) REFERENCES CicloSimulacion(id_partida, id_ciclo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_animal) REFERENCES Animal(id_partida, id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_espacio) REFERENCES Espacio(id_partida, id_espacio)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Indices para las consultas de dominio y los paneles de seguimiento
CREATE INDEX idx_habitat_partida_bioma ON Habitat(id_partida, id_bioma);
CREATE INDEX idx_espacio_partida_habitat ON Espacio(id_partida, id_habitat, estado);
CREATE INDEX idx_animal_localizacion ON Animal(id_partida, id_espacio, estado_vital);
CREATE INDEX idx_animal_critico ON Animal(id_partida, nivel_alimentacion, estado_salud, ciclos_desnutricion, ciclos_enfermedad);
CREATE INDEX idx_animal_genealogia ON Animal(id_partida, id_padre, id_madre);
CREATE INDEX idx_inventario_alimento ON InventarioAlimento(id_partida, id_alimento);
CREATE INDEX idx_inventario_medico ON InventarioMedico(id_partida, id_insumo);
CREATE INDEX idx_movimiento_economico ON MovimientoEconomico(id_partida, fecha, tipo);
CREATE INDEX idx_beneficio_estado ON BeneficioPendiente(id_partida, estado, fecha_creacion);
CREATE INDEX idx_auditoria_partida_fecha ON AuditoriaEvento(id_partida, fecha, entidad);
CREATE INDEX idx_ciclo_partida_estado ON CicloSimulacion(id_partida, estado, numero);
CREATE INDEX idx_reproduccion_ciclo ON ReproduccionCiclo(id_partida, id_ciclo, estado);
CREATE INDEX idx_reproduccion_vigencia ON ReproduccionCiclo(id_partida, estado, ciclo_expiracion);
CREATE INDEX idx_notificacion_estado ON Notificacion(id_partida, tipo, estado, fecha);
CREATE INDEX idx_desbloqueo_partida ON DesbloqueoEspecie(id_partida, id_especie);
CREATE INDEX idx_desbloqueo_habitat_partida ON DesbloqueoHabitat(id_partida, id_habitat);
CREATE INDEX idx_mision_estado ON ProgresoMision(id_partida, estado);
CREATE INDEX idx_metrica_partida_nivel ON MetricaSatisfaccion(id_partida, id_ciclo, id_habitat, id_espacio, id_animal);
```

### 2.1. Validaciones transaccionales no expresables solo con `CHECK`

SQLite no puede expresar completamente estas reglas mediante restricciones de fila. La capa de dominio debe comprobarlas dentro de la misma transacción que modifica los datos:

1. El espacio destino pertenece a la partida, tiene estado activo, es compatible con la especie y no supera `capacidad_max` contando solo animales `VIVO`.
2. La capacidad solicitada para crear un espacio debe existir en `CosteEspacio`; no se permite crear espacios con capacidades fuera de esa tabla.
3. Un hábitat no se edita ni elimina durante la partida; solo se crea mediante la carga inicial autorizada.
4. Los progenitores y la pareja deben ser de la misma especie, estar en la misma partida y cumplir el estado vital exigido. `id_pareja` debe ser recíproco y ambos animales deben tener al menos 16 años; `id_mejor_amigo` puede ser interespecie, pero nunca autorreferente. La afinidad no familiar se consulta por el par de especies de los animales.
5. Una ficha solo se confirma con exactamente dos filas en `RasgoPersonalidad` y dos en `HabilidadSocial`; la satisfacción se calcula después de completar los datos del usuario y del sistema, antes de incorporar el animal al zoológico.
6. Una pareja de `ReproduccionCiclo` debe ser válida y única por ciclo; la aplicación normaliza el orden de sus identificadores antes de insertar.
7. Una concepción se hace disponible en el ciclo inmediatamente posterior al ciclo de concepción y expira tras tres ciclos disponibles; durante esa ventana no se vuelve a exigir el umbral de satisfacción de los progenitores.
8. La probabilidad total de reproducción se calcula como `max(0, probabilidad_reproduccion - penalizacion_hijo_reproduccion * hijos_vivos)`, usando los valores del rango de la especie. El sorteo del ciclo compara un valor aleatorio en `0..1` con esa probabilidad total; los hijos muertos no se cuentan.
9. El beneficio se calcula al finalizar cada ciclo y se vuelve a calcular tras cualquier mutación confirmada que altere la cantidad de animales vivos o la satisfacción global. Cada rango puede tener una fila de `PonderacionBeneficioRango`; si el individuo exclusivo cumple `satisfaccion_minima`, su `multiplicador` se aplica directamente al beneficio.
10. Una compra, venta, traslado, nacimiento, alimentación, tratamiento, baja o reclamación valida saldo, inventario, estado y capacidad antes de escribir.
11. Una especie exclusiva nunca se inserta como compra comercial. Un registro de `DesbloqueoEspecie` o `DesbloqueoHabitat` no se elimina ni se revoca; la ausencia del registro indica que la entidad sigue bloqueada.
12. Las relaciones sociales se limpian al morir o vender un animal, pero nunca se limpian `id_padre` ni `id_madre`.

## 3. Integridad transaccional e idempotencia

Cada operación comienza con `BEGIN IMMEDIATE`, valida sus precondiciones, aplica las mutaciones, registra `AuditoriaEvento`, recalcula las métricas afectadas y termina con `COMMIT`. Cualquier error ejecuta `ROLLBACK` y deja el estado anterior intacto.

| Operación | Escrituras atómicas mínimas | Clave de idempotencia |
|---|---|---|
| Compra o alta | `Animal`, rasgos, habilidades, `MovimientoEconomico`, `AuditoriaEvento` | solicitud de compra |
| Traslado o cambio de espacio | `Animal`, métricas, `AuditoriaEvento` | solicitud de traslado |
| Alimentación o tratamiento | inventario, `Animal`, avisos, métricas, auditoría | acción y ciclo |
| Ciclo periódico u offline | `CicloSimulacion`, animales, economía, reproducción, avisos, métricas, estado de simulación | `id_partida + numero` |
| Confirmación de reproducción | `ReproduccionCiclo`, `Animal`, rasgos, habilidades, auditoría | `id_partida + id_ciclo + pareja` |
| Beneficio o recompensa | saldo/inventario, fila de beneficio o recompensa, movimiento, auditoría | `clave_idempotencia` |

La reejecución de una clave ya confirmada devuelve el resultado persistido sin repetir efectos. La baja de animales es lógica: se cambia `estado_vital`, se anula `id_espacio` y se conservan genealogía, métricas y auditoría.

## 4. Regla formal de satisfacción

### 4.1. Disparadores

El recálculo se invoca exclusivamente al finalizar un ciclo periódico u offline, o tras consolidar una mutación de alta, baja, venta, traslado, edición de atributos, cambio de hábitat, alimentación, tratamiento o reproducción. La navegación, el renderizado y las consultas de lectura no lo ejecutan (RF-44, RES-10).

### 4.2. Ecuación individual

La especificación del proyecto establece la siguiente estructura para la satisfacción de un animal vivo $i$:

$$S_i = \Big(w_{bio}S_{bio} + w_{soc}S_{soc}\Big) \times P_{hab} \times M_{crit}$$

Los pesos `w_bio` y `w_soc` se almacenan en `Especie` y la restricción existente exige que sumen `1.00`. Esta sección no asigna ni modifica sus valores concretos.

La escala confirmada para `S_soc` y para las métricas de satisfacción es `0..1`. La fórmula social debe ser compatible con esta escala y no debe introducir una conversión adicional a `0..100`.

La afinidad social se define entre especies: todos los animales de una especie comparten la afinidad con una segunda especie. `AfinidadEspecies` y la matriz del JSON deben utilizar pares de especies; los valores concretos siguen pendientes.

Cuando no existe una relación social, su componente aporta `0` a `S_soc` y no se aplica ninguna penalización adicional.

En cada especie, `w_afinidad` debe ser mayor que la suma de los pesos de pareja, mejor amigo, padre, madre y hermanos.

`S_soc` se conserva sin redondear durante el cálculo. Solo se redondea la satisfacción final al transformarla en el porcentaje entero mostrado al usuario.

$$S_{bio} = \frac{nivel\_alimentacion}{100}$$

`nivel_alimentacion` está definido en el DDL en el rango `0..100`, por lo que esta operación produce un valor entre `0` y `1`; en particular, `0%` produce `0` y `100%` produce `1`. La alimentación y la salud no son editables directamente por el usuario (RES-03).

El factor crítico se calcula sin sumas mediante la combinación multiplicativa:

$$M_{crit} = M_{alimentacion} \times M_{enfermedad}$$

`M_alimentacion` utiliza una tabla específica por especie para los niveles `[0, 10, 20, ..., 90]`; con alimentación al `100%` su valor es `1`. `M_enfermedad` también es específico por especie y toma el valor `1` cuando el animal está sano. Los factores permanecen en `0..1` y `M_crit` no se redondea.

El cálculo interno de `S_i` permanece en `0..1`. Para mostrar la satisfacción individual se transforma a porcentaje `0..100` sin decimales; el criterio exacto de redondeo y la escala de persistencia se mantienen pendientes.

La documentación existente identifica las entradas de los demás componentes, pero no proporciona todavía sus fórmulas, ponderaciones ni todos sus valores:

| Componente | Información definida por el proyecto | Información pendiente de definir |
|---|---|---|
| `S_soc` | Debe considerar las referencias sociales contempladas por el modelo y respetar las relaciones válidas de RF-34, RF-48 y RES-13. Se expresa en `0..1`, la afinidad no familiar se consulta por el par de especies y no se redondea. | Contribución de cada relación, rango y valores concretos. |
| `P_hab` | Procede de la tabla de compatibilidad entre especie y bioma del JSON. Existe una fila para cada combinación especie-bioma, el valor es específico de cada especie y bioma, y se multiplica directamente en la ecuación. El JSON conserva los valores de configuración; no representa el esquema real de BBDD. | Completar las especies, biomas y valores concretos de la tabla. |
| `M_crit` | Se calcula como `M_alimentacion * M_enfermedad`, con factores específicos por especie en `0..1`; alimentación al `100%` y estado sano tienen factor `1`. | Valores concretos por especie y nivel de alimentación/enfermedad. `M_crit` no se redondea. |

Mientras estos datos no se definan en los documentos funcionales del proyecto, no se puede obtener un resultado numérico completo de `S_i` sin inventar reglas. Por tanto, `S_soc`, `P_hab`, `M_crit`, sus parámetros desconocidos y cualquier resultado dependiente quedan identificados como `PENDIENTE_DE_DEFINIR` en `BBDD/ValoresYFormulasPendientes.md`.

El cálculo solo puede tomar como entrada el estado confirmado de un animal vivo alojado en un espacio (`estado_vital = 'VIVO'` e `id_espacio` no nulo). Los animales muertos o vendidos se conservan para el histórico, la genealogía y la auditoría, pero no forman parte de una satisfacción activa.

No se fija en esta especificación ningún redondeo, saturación adicional, valor sustitutivo para datos ausentes ni comportamiento para casos vacíos que no esté definido en otro documento del proyecto. Esos puntos se mantienen pendientes y no deben resolverse mediante valores provisionales.

### 4.3. Persistencia y propagación

Una vez definidos todos los parámetros necesarios, el resultado individual se persiste en `Animal.satisfaccion_actual` y en `MetricaSatisfaccion` en la escala `0..1` cuando el evento o ciclo queda confirmado. Las métricas de espacio, hábitat y partida se calculan como la media directa de los animales vivos pertenecientes a cada nivel. Un nivel sin animales vivos queda sin métrica y no participa en las medias de niveles superiores.

Las métricas se escriben con el mismo `id_ciclo` del evento que las produjo. No se mantienen contadores derivados sin una fila de origen que permita reconstruirlos. El recálculo debe producirse solo tras una mutación confirmada o la finalización de un ciclo; no se ejecuta durante navegación, renderizado ni consultas de lectura (RF-15, RF-44, RES-10).

## 5. Soft delete y consistencia histórica

Un animal muerto o vendido conserva su fila, progenitores, especie y auditoría. Se establece `estado_vital` a `MUERTO` o `VENDIDO`, `id_espacio` a `NULL` y se anulan únicamente los vínculos sociales mutables de animales vivos. No se permite el borrado físico de animales con descendencia ni de registros referenciados por auditoría.

Un espacio vacío puede pasar a `INACTIVO` tras confirmación del usuario, pero no se elimina si existen animales históricos, métricas, operaciones o auditorías que lo referencien. La capacidad máxima permanece inmutable.

### 5.1. Cobertura pendiente del modelo

El modelo actual no representa de forma suficiente algunos datos que ya exigen los requisitos. Quedan identificados como insuficiencias, sin añadir estructuras ni valores:

- coste de adquisición de espacios;
- alimentos demandados por cada especie;
- fórmula estadística de longevidad máxima y su conversión temporal;
- costes de mantenimiento;
- cantidad y objeto concreto de las recompensas de misión;
- ciclos acumulados y restantes, contenido y origen de los avisos;
- clasificación y origen detallados de todos los eventos auditables.

La capa de dominio debe seguir aplicando las validaciones ya descritas para capacidad, compatibilidad, estados, reciprocidad, misma especie, fichas con exactamente dos rasgos y dos habilidades, solvencia, inventario e idempotencia. Los contadores de desnutrición y enfermedad son independientes; cada uno se reinicia al recuperarse su estado correspondiente y, si ambos alcanzan valores distintos, se utiliza el mayor para decidir la defunción. Esta lista no introduce reglas nuevas.

## 6. Matriz de cobertura

| Grupo de datos | Requisitos funcionales | No funcionales | Restricciones |
|---|---|---|---|
| Catálogo, hábitat y espacio | RF-02, RF-04, RF-07, RF-08, RF-25, RF-29, RF-31, RF-49 | RNF-01, RNF-04, RNF-13 | RES-04, RES-05, RES-06, RES-13 |
| Animal, rasgos y vínculos | RF-01, RF-03, RF-05, RF-06, RF-18, RF-19, RF-32, RF-33, RF-34, RF-48 | RNF-01, RNF-02, RNF-08, RNF-12, RNF-13 | RES-03, RES-06, RES-08, RES-11, RES-13 |
| Economía e inventario | RF-09, RF-10, RF-11, RF-12, RF-13, RF-26, RF-30, RF-31, RF-40, RF-50 | RNF-02, RNF-08, RNF-10, RNF-12, RNF-13 | RES-08, RES-12 |
| Ciclos, reproducción y offline | RF-14, RF-16, RF-17, RF-18, RF-21, RF-22, RF-23, RF-24, RF-44, RF-45, RF-46 | RNF-02, RNF-05, RNF-08, RNF-09, RNF-12, RNF-13 | RES-10, RES-13 |
| Métricas, prestigio y misiones | RF-15, RF-27, RF-35, RF-36, RF-37, RF-38, RF-39, RF-40 | RNF-01, RNF-02, RNF-10, RNF-13 | RES-07, RES-09, RES-10, RES-12 |
| Avisos y auditoría | RF-20, RF-28, RF-41, RF-42, RF-43, RF-47 | RNF-07, RNF-08, RNF-09, RNF-11, RNF-12 | RES-11, RES-12 |

Esta matriz implementa los requisitos existentes; no introduce requisitos funcionales nuevos. `Trazabilidad.md` permanece sin cambios porque no se han añadido ni eliminado identificadores del proyecto.
