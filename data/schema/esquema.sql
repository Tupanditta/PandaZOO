-- Esquema de PandaZOO. Fuente de verdad: IngenieriaSoftware/BBDD/EspecificacionBBDD.md, seccion 2.
-- Los PRAGMA se aplican desde codigo (BaseDatos). Un bloque por sentencia, separados por una linea con ;;
CREATE TABLE Bioma (
    id_bioma TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
)
;;
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
)
;;
CREATE TABLE CompatibilidadBioma (
    id_especie TEXT NOT NULL,
    id_bioma TEXT NOT NULL,
    factor_bioma REAL NOT NULL CHECK (factor_bioma BETWEEN 0.00 AND 1.00),
    PRIMARY KEY (id_especie, id_bioma),
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_bioma) REFERENCES Bioma(id_bioma)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
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
)
;;
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
)
;;
CREATE TABLE Alimento (
    id_alimento TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
)
;;
CREATE TABLE CosteAlimento (
    id_alimento TEXT PRIMARY KEY,
    importe_por_kilogramo REAL NOT NULL CHECK (importe_por_kilogramo >= 0 AND importe_por_kilogramo = ROUND(importe_por_kilogramo, 2)),
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE InsumoMedico (
    id_insumo TEXT PRIMARY KEY,
    nombre TEXT NOT NULL UNIQUE
)
;;
CREATE TABLE CosteInsumo (
    id_insumo TEXT PRIMARY KEY,
    importe_por_unidad REAL NOT NULL CHECK (importe_por_unidad >= 0 AND importe_por_unidad = ROUND(importe_por_unidad, 2)),
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE ConsumoMedicoRango (
    id_rango INTEGER NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad_unidades INTEGER NOT NULL CHECK (cantidad_unidades > 0),
    PRIMARY KEY (id_rango, id_insumo),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE Mision (
    id_mision TEXT PRIMARY KEY,
    tipo_objetivo TEXT NOT NULL UNIQUE,
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVA', 'INACTIVA')),
    naturaleza_progreso TEXT NOT NULL CHECK (naturaleza_progreso IN ('ACUMULATIVO', 'DINAMICO')),
    tipo_recompensa TEXT NOT NULL CHECK (tipo_recompensa IN ('FONDOS', 'ALIMENTO', 'INSUMO', 'ESPECIE')),
    id_item_recompensa TEXT NULL,
    cantidad_recompensa REAL NOT NULL CHECK (cantidad_recompensa > 0),
    CHECK ((tipo_recompensa IN ('ALIMENTO', 'INSUMO')) = (id_item_recompensa IS NOT NULL))
)
;;
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
)
;;
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
    espacio_gratuito_disponible INTEGER NOT NULL DEFAULT 1 CHECK (espacio_gratuito_disponible IN (0, 1)),
    CHECK (
        (prestigio_maximo_nivel IS NULL AND animales_vivos_maximos IS NULL
            AND satisfaccion_global_maxima IS NULL)
        OR (prestigio_maximo_nivel IS NOT NULL AND animales_vivos_maximos IS NOT NULL
            AND satisfaccion_global_maxima IS NOT NULL)
    )
)
;;
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
)
;;
CREATE TABLE Espacio (
    id_espacio TEXT PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_habitat TEXT NOT NULL,
    nombre TEXT NOT NULL,
    capacidad_max INTEGER NOT NULL CHECK (capacidad_max > 0),
    estado TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    coste_pagado REAL NOT NULL DEFAULT 0 CHECK (coste_pagado >= 0 AND coste_pagado = ROUND(coste_pagado, 2)),
    UNIQUE (id_partida, id_espacio),
    UNIQUE (id_partida, id_habitat, nombre),
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE CosteEspacio (
    capacidad_max INTEGER PRIMARY KEY CHECK (capacidad_max > 0),
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2))
)
;;
CREATE TABLE CosteRango (
    id_rango INTEGER PRIMARY KEY,
    importe REAL NOT NULL CHECK (importe >= 0 AND importe = ROUND(importe, 2)),
    FOREIGN KEY (id_rango) REFERENCES RangoEspecie(id_rango)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
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
)
;;
CREATE TABLE RasgoPersonalidad (
    id_animal TEXT NOT NULL,
    rasgo TEXT NOT NULL,
    PRIMARY KEY (id_animal, rasgo),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE HabilidadSocial (
    id_animal TEXT NOT NULL,
    habilidad TEXT NOT NULL,
    PRIMARY KEY (id_animal, habilidad),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
CREATE TABLE InventarioAlimento (
    id_partida TEXT NOT NULL,
    id_alimento TEXT NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_alimento),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_alimento) REFERENCES Alimento(id_alimento)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID
;;
CREATE TABLE InventarioMedico (
    id_partida TEXT NOT NULL,
    id_insumo TEXT NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad >= 0),
    PRIMARY KEY (id_partida, id_insumo),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_insumo) REFERENCES InsumoMedico(id_insumo)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID
;;
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
)
;;
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
)
;;
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
)
;;
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
) WITHOUT ROWID
;;
CREATE TABLE DesbloqueoHabitat (
    id_partida TEXT NOT NULL,
    id_habitat TEXT NOT NULL,
    fecha INTEGER NOT NULL,
    PRIMARY KEY (id_partida, id_habitat),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_partida, id_habitat) REFERENCES Habitat(id_partida, id_habitat)
        ON UPDATE CASCADE ON DELETE RESTRICT
) WITHOUT ROWID
;;
CREATE TABLE ProgresoMision (
    id_partida TEXT NOT NULL,
    id_instancia_mision TEXT NOT NULL,
    id_mision TEXT NOT NULL,
    progreso REAL NOT NULL DEFAULT 0 CHECK (progreso >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE', 'CUMPLIDA', 'AGOTADA')),
    orden_cumplida INTEGER,
    PRIMARY KEY (id_partida, id_instancia_mision),
    UNIQUE (id_partida, id_mision),
    UNIQUE (id_partida, id_instancia_mision, id_mision),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_mision) REFERENCES Mision(id_mision)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
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
)
;;
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
)
;;
CREATE TABLE UnidadGratuita (
    id_unidad INTEGER PRIMARY KEY,
    id_partida TEXT NOT NULL,
    id_especie TEXT NOT NULL,
    origen TEXT NOT NULL CHECK (origen IN ('BIENVENIDA')),
    unidad_pendiente INTEGER NOT NULL DEFAULT 1 CHECK (unidad_pendiente IN (0, 1)),
    fecha INTEGER NOT NULL,
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
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
) WITHOUT ROWID
;;
CREATE TABLE EstadoSimulacion (
    id_partida TEXT PRIMARY KEY,
    numero_ciclo_ultimo INTEGER NULL CHECK (numero_ciclo_ultimo IS NULL OR numero_ciclo_ultimo >= 0),
    instante_ultimo_ciclo INTEGER NULL,
    remanente_temporizador INTEGER NOT NULL DEFAULT 0 CHECK (remanente_temporizador BETWEEN 0 AND 600),
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVO', 'PAUSADO', 'RECUPERACION')),
    FOREIGN KEY (id_partida) REFERENCES Partida(id_partida)
        ON UPDATE CASCADE ON DELETE RESTRICT
)
;;
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
) WITHOUT ROWID
;;
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
)
;;
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
)
;;
CREATE INDEX idx_habitat_partida_bioma ON Habitat(id_partida, id_bioma)
;;
CREATE INDEX idx_espacio_partida_habitat ON Espacio(id_partida, id_habitat, estado)
;;
CREATE INDEX idx_animal_localizacion ON Animal(id_partida, id_espacio) WHERE estado_vital = 'VIVO'
;;
CREATE INDEX idx_animal_critico ON Animal(id_partida, nivel_alimentacion, estado_salud, ciclos_desnutricion, ciclos_enfermedad) WHERE estado_vital = 'VIVO'
;;
CREATE INDEX idx_animal_genealogia ON Animal(id_partida, id_padre, id_madre)
;;
CREATE INDEX idx_movimiento_economico ON MovimientoEconomico(id_partida, numero_ciclo, tipo)
;;
CREATE INDEX idx_beneficio_estado ON BeneficioPendiente(id_partida, estado, numero_ciclo)
;;
CREATE INDEX idx_auditoria_partida_fecha ON AuditoriaEvento(id_partida, numero_ciclo, entidad)
;;
CREATE INDEX idx_ciclo_partida_estado ON CicloSimulacion(id_partida, estado, numero)
;;
CREATE INDEX idx_reproduccion_ciclo ON ReproduccionCiclo(id_partida, numero_ciclo, estado)
;;
CREATE INDEX idx_reproduccion_vigencia ON ReproduccionCiclo(id_partida, estado, ciclo_expiracion)
;;
CREATE INDEX idx_notificacion_estado ON Notificacion(id_partida, tipo, estado, fecha)
;;
CREATE UNIQUE INDEX uq_notificacion_critica_activa ON Notificacion(id_partida, id_entidad)
    WHERE tipo = 'CRITICA' AND estado IN ('PENDIENTE', 'VISTA')
;;
CREATE UNIQUE INDEX uq_notificacion_operativa ON Notificacion(id_partida, id_entidad) WHERE tipo = 'OPERATIVA'
;;
CREATE UNIQUE INDEX uq_notificacion_informativa ON Notificacion(id_partida, codigo, id_entidad) WHERE tipo = 'INFORMATIVA'
;;
CREATE INDEX idx_mision_estado ON ProgresoMision(id_partida, estado)
;;
CREATE UNIQUE INDEX uq_metrica_partida ON MetricaSatisfaccion(id_partida) WHERE nivel = 'PARTIDA'
;;
CREATE UNIQUE INDEX uq_metrica_habitat ON MetricaSatisfaccion(id_partida, id_habitat) WHERE nivel = 'HABITAT'
;;
CREATE UNIQUE INDEX uq_metrica_espacio ON MetricaSatisfaccion(id_partida, id_espacio) WHERE nivel = 'ESPACIO'
