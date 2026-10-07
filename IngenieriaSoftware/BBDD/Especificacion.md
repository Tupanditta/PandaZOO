# Especificación Técnica: Modelo de Datos y Cálculo de Satisfacción

## 1. Especificación del Modelo de Datos Persistente (Base de Datos)

El esquema persigue la tercera forma normal (3FN), previene la redundancia destructiva, incorpora soporte de borrado lógico (*soft delete*) y asegura consultas de satisfacción en tiempo $O(1)$[cite: 8].

```
  +-------------------+       +-------------------+       +---------------------+
  |      HABITAT      | 1   N |      ESPACIO      | 1   N |       ANIMAL        |
  |-------------------|-------|-------------------|-------|---------------------|
  | id_habitat (PK)   |       | id_espacio (PK)   |       | id_animal (PK)      |
  | nombre            |       | id_habitat (FK)   |       | id_espacio (FK,NULL)|
  | tipo_bioma        |       | nombre            |       | id_especie (FK)     |
  +-------------------+       | capacidad_max     |       | nombre              |
            |                 +-------------------+       | edad_ciclos         |
            | 1                                           | nivel_alimentacion  |
            |                                             | estado_salud        |
            | N                                           | estado_vital        |
  +---------------------+                                 | id_pareja (FK)      |
  | COMPATIBILIDAD_BIOMA|                                 | id_mejor_amigo (FK) |
  |---------------------|                                 | id_padre (FK)       |
  | id_especie (PK, FK) |                                 | id_madre (FK)       |
  | tipo_bioma (PK)     |                                 | satisfaccion_actual |
  | factor_bioma        |                                 +---------------------+
  +---------------------+                                            | 1
            | N                                                      |
            |                                                        | N
            | 1                                           +---------------------+
  +---------------------+ 1   N +--------------------+    | RASGO_PERSONALIDAD  |
  |      ESPECIE        |-------|  AFINIDAD_ESPECIES |    | HABILIDAD_SOCIAL    |
  |---------------------|       |--------------------|    +---------------------+
  | id_especie (PK)     |       | id_especie_a (PK)  |
  | nombre_comun        |       | id_especie_b (PK)  |
  | tope_hermanos       |       | coeficiente        |
  | peso_bio            |       +--------------------+
  | peso_soc            |
  | peso_esp            |
  +---------------------+
```

### 1.1. Diccionario de Datos DDL (SQLite / Room)

```sql
-- TABLA 1: Hábitats físicos del zoológico (Entidades de solo lectura, RES-05)
CREATE TABLE Habitat (
    id_habitat TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    tipo_bioma TEXT NOT NULL CHECK (tipo_bioma IN ('SABANA', 'BOSQUE_TEMPLADO', 'SELVA_TROPICAL', 'TUNDRA', 'DESIERTO', 'ACUATICO'))
);

-- TABLA 2: Espacios de alojamiento contenidos en hábitats (RF-02, RES-04)
CREATE TABLE Espacio (
    id_espacio TEXT PRIMARY KEY,
    id_habitat TEXT NOT NULL,
    nombre TEXT NOT NULL,
    capacidad_max INTEGER NOT NULL CHECK (capacidad_max > 0),
    FOREIGN KEY (id_habitat) REFERENCES Habitat(id_habitat) ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- TABLA 3: Catálogo taxonómico de especies biológicas
CREATE TABLE Especie (
    id_especie TEXT PRIMARY KEY,
    nombre_comun TEXT NOT NULL UNIQUE,
    tope_hermanos INTEGER NOT NULL DEFAULT 2 CHECK (tope_hermanos > 0),
    peso_bio REAL NOT NULL DEFAULT 0.35,
    peso_soc REAL NOT NULL DEFAULT 0.30,
    peso_esp REAL NOT NULL DEFAULT 0.35,
    CONSTRAINT chk_suma_pesos CHECK (ROUND(peso_bio + peso_soc + peso_esp, 2) = 1.00)
);

-- TABLA 4: Compatibilidad asimétrica entre especie y bioma ambiental
CREATE TABLE CompatibilidadBioma (
    id_especie TEXT NOT NULL,
    tipo_bioma TEXT NOT NULL,
    factor_bioma REAL NOT NULL CHECK (factor_bioma IN (1.00, 0.75, 0.30)), -- 1.0=Idóneo, 0.75=Neutral, 0.30=Hostil
    PRIMARY KEY (id_especie, tipo_bioma),
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie) ON UPDATE CASCADE ON DELETE RESTRICT
);

-- TABLA 5: Matriz de afinidad interespecífica (convivencia en el mismo espacio)
CREATE TABLE AfinidadEspecies (
    id_especie_a TEXT NOT NULL,
    id_especie_b TEXT NOT NULL,
    coeficiente REAL NOT NULL CHECK (coeficiente BETWEEN -1.00 AND 1.00),
    PRIMARY KEY (id_especie_a, id_especie_b),
    FOREIGN KEY (id_especie_a) REFERENCES Especie(id_especie) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (id_especie_b) REFERENCES Especie(id_especie) ON UPDATE CASCADE ON DELETE CASCADE
);

-- TABLA 6: Entidad Animal individual con soporte de Borrado Lógico (RF-01, RF-14, RF-19, RES-03)
CREATE TABLE Animal (
    id_animal TEXT PRIMARY KEY,
    id_especie TEXT NOT NULL,
    id_espacio TEXT NULL, -- NULL si el espécimen ha fallecido (estado_vital = 'MUERTO')
    nombre TEXT NOT NULL,
    edad_ciclos INTEGER NOT NULL DEFAULT 0 CHECK (edad_ciclos >= 0),
    nivel_alimentacion INTEGER NOT NULL DEFAULT 100 CHECK (nivel_alimentacion BETWEEN 0 AND 100),
    estado_salud TEXT NOT NULL DEFAULT 'SANO' CHECK (estado_salud IN ('SANO', 'ENFERMO')),
    estado_vital TEXT NOT NULL DEFAULT 'VIVO' CHECK (estado_vital IN ('VIVO', 'MUERTO', 'VENDIDO')),
    ciclos_criticos INTEGER NOT NULL DEFAULT 0 CHECK (ciclos_criticos >= 0),
    
    -- Vínculos de linaje inmutables (no se anulan al morir el progenitor para preservar parentesco)
    id_pareja TEXT NULL,
    id_mejor_amigo TEXT NULL,
    id_padre TEXT NULL,
    id_madre TEXT NULL,
    
    -- Snapshot precalculado del indicador (RF-15, RF-27, RNF-01)
    satisfaccion_actual REAL NOT NULL DEFAULT 1.00 CHECK (satisfaccion_actual BETWEEN 0.00 AND 1.00),
    
    FOREIGN KEY (id_especie) REFERENCES Especie(id_especie) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_espacio) REFERENCES Espacio(id_espacio) ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_pareja) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_mejor_amigo) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE SET NULL,
    FOREIGN KEY (id_padre) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (id_madre) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uk_nombre_por_especie UNIQUE (id_especie, nombre)
);

-- TABLAS 7 y 8: Rasgos psicológicos obligatorios (Exactamente 2 por espécimen, RF-33)
CREATE TABLE RasgoPersonalidad (
    id_animal TEXT NOT NULL,
    rasgo TEXT NOT NULL,
    PRIMARY KEY (id_animal, rasgo),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE HabilidadSocial (
    id_animal TEXT NOT NULL,
    habilidad TEXT NOT NULL,
    PRIMARY KEY (id_animal, habilidad),
    FOREIGN KEY (id_animal) REFERENCES Animal(id_animal) ON UPDATE CASCADE ON DELETE CASCADE
);

-- Índices de consulta acelerada para cálculo de cohabitantes (RNF-02)
CREATE INDEX idx_animal_localizacion ON Animal(id_espacio, estado_vital);
CREATE INDEX idx_animal_genealogia ON Animal(id_espacio, id_padre, id_madre, id_pareja);
```

---

## 2. Especificación Formal de la Regla de Negocio

```
================================================================================
REGLA DE NEGOCIO: RN-BIEN-01
Nombre: Modelo Procedimental Asimétrico de Satisfacción Animal y Dispersión
Módulos Asociados: Simulación, Gestión de Espacios, Monitor de Prestigio
Requisitos Referenciados: RF-14, RF-15, RF-19, RF-27, RF-34, RF-35, RF-44, RES-10
================================================================================
```

### 2.1. Alcance y Disparadores (Triggers de Ejecución)
En cumplimiento del procesamiento reactivo desacoplado (RF-44, RES-10)[cite: 7, 10], el cálculo de satisfacción **no debe ejecutarse mediante sondeo continuo (polling) ni vincularse al refresco visual del dispositivo**[cite: 10].  
El recálculo se invoca exclusivamente ante los siguientes eventos:
1. **Pérdida/Ganancia Biológica:** Ejecución periódica o en diferido de un ciclo de simulación (RF-14, RF-21)[cite: 4, 5].
2. **Acción Operativa Directa:** Alimentación (RF-12) o administración de tratamiento médico (RF-13)[cite: 4].
3. **Mutación Censal o Espacial:** Adquisición de animal (RF-01), defunción por ciclo crítico o longevidad (RF-19), venta (RF-06) o traslado de espécimen (RF-05)[cite: 2, 4, 11].
4. **Mutación de Vínculos:** Edición de vínculos de pareja o mejor amigo (RF-03, RF-34)[cite: 2, 6].

---

### 2.2. Ecuación Maestra de Satisfacción Individual ($S_i$)

La satisfacción consolidada de un ejemplar vivo $i$ se define como:

$$S_i = \Big( w_{bio} \cdot S_{bio} + w_{soc} \cdot S_{soc} + w_{esp} \cdot S_{esp} \Big) \times P_{hab} \times M_{crit}$$

Donde:
* $w_{bio} + w_{soc} + w_{esp} = 1.00$ (pesos tipificados por cada especie biológica).
* $S_i \in [0.00, 1.00]$ (representado de $0\%$ a $100\%$ en las interfaces)[cite: 4, 5].

---

### 2.3. Desglose de Componentes

#### A. Componente Biológico ($S_{bio} \in [0.00, 1.00]$)
Cuantifica el nivel de soporte vital del individuo[cite: 2, 4]:
$$S_{bio} = \frac{\text{nivel\_alimentacion}}{100}$$

#### B. Componente Social y Familiar ($S_{soc} \in [0.00, 1.00]$)
Recompensa el emparejamiento y la socialización, pero penaliza el estancamiento consanguíneo para forzar la dispersión de linajes hacia otros recintos:

$$S_{soc} = \Big( 0.40 \cdot P + 0.30 \cdot A + 0.20 \cdot L_{padres} + 0.10 \cdot f(H) \Big) \times P_{clan}$$

##### 1. Pareja y Amistad Conviviente ($P, A$)
* **Pareja Conviviente ($P$):** $1.00$ si el espécimen asignado como pareja cohabita vivo en el mismo espacio; $0.00$ en caso de ausencia, viudez o separación física[cite: 14].
* **Mejor Amigo Conviviente ($A$):** $1.00$ si el espécimen designado como mejor amigo cohabita vivo en el espacio; $0.00$ en caso de ausencia[cite: 14].

##### 2. Factor de Progenitores: Separación Física vs. Orfandad ($L_{padres} \in [0.00, 1.00]$)
Distingue formalmente la desvinculación por separación en vida respecto a la orfandad definitiva:

$$L_{padres} = \begin{cases} 0.00 & \text{si al menos un progenitor vive pero no reside en su mismo espacio (abandono físico)} \\ 0.00 & \text{si el ejemplar fue adquirido en tienda (sin linaje registrado)} \\ B_{origen} + B_{legado} & \text{si ambos progenitores han fallecido (estado\_vital = 'MUERTO')} \\ 1.00 & \text{si ambos progenitores residen y cohabitan con él en el mismo espacio} \\ 0.50 & \text{si un progenitor cohabita y el otro ha fallecido} \end{cases}$$

Donde, ante la orfandad total confirmada:
* **$B_{origen} = 0.40$ (Inmutable):** Impronta residual por haber nacido y sido criado en el parque por progenitores registrados[cite: 4].
* **$B_{legado} = 0.60 \times \left[ \frac{\min(C, 2)}{2} \right]$:** Recuperación del valor filial si el huérfano convive con sus propias crías ($C$) en el espacio[cite: 4].

##### 3. Aporte Marginal de Hermandad ($f(H)$) y Límite de Clan ($P_{clan}$)
El peso de los hermanos cohabitantes se reduce al $10\%$ ($0.10$) y satura rápidamente a $H_{max} = 2$:

$$f(H) = \frac{\min(H, 2)}{2}$$

* **Conteo de Hermanos en Convivencia ($H$):** Número de especímenes vivos en el mismo espacio que comparten al menos un `id_padre` o `id_madre`[cite: 4]. El parentesco persiste aun tras la muerte de los padres gracias al borrado lógico[cite: 4].
* **Penalizador de Apiñamiento Familiar / Endogamia ($P_{clan}$):**  
  Desincentiva la saturación de especímenes del mismo linaje en un único espacio:
  $$P_{clan} = \begin{cases}
  1.00 & \text{si } H \le 1 \quad (\text{convivencia normal o dispersión}) \\
  0.75 & \text{si } H = 2 \quad (\text{presión de clan familiar}) \\
  0.50 & \text{si } H \ge 3 \quad (\text{penalización severa por saturación consanguínea})
  \end{cases}$$

#### C. Componente de Espacio y Cohesión Multiespecie ($S_{esp} \in [0.00, 1.00]$)
Premia la integración de especies compatibles frente a recintos monoespecie:

$$S_{esp} = \text{clamp}\Big( A_{inter} \times (1.00 + B_{fam}),\ 0.00,\ 1.00 \Big)$$

Sea $N$ el número de cohabitantes vivos en el espacio (excluyendo al animal $i$).  
Si $N = 0$, el animal se encuentra aislado: $S_{esp} = 0.50$.  
Para $N > 0$:

1. **Afinidad Interespecífica Asimétrica ($A_{inter}$):**  
   Calculada a partir de la afinidad media $\overline{\alpha} = \frac{1}{N} \sum_{j=1}^{N} \text{Afinidad}[E_i, E_j]$:
   $$A_{inter} = \begin{cases} 
   0.50 + (\overline{\alpha} \times 0.50) & \text{si } \overline{\alpha} \ge 0 \quad (\text{rango: } [0.50, 1.00]) \\
   0.50 + (\overline{\alpha} \times 0.85) & \text{si } \overline{\alpha} < 0 \quad (\textbf{penalización asimétrica severa: } [0.00, 0.50))
   \end{cases}$$

2. **Bono de Estructura Familiar Multiespecie ($B_{fam}$):**  
   Premia que el espacio esté ocupado por unidades familiares funcionales en lugar de ejemplares inconexos[cite: 14]:
   $$B_{fam} = \left(\frac{N_{fam}}{N}\right) \times 0.30 \times D_{esp}$$
   * $N_{fam}$: Número de cohabitantes que conviven con su pareja o con descendencia/ascendencia directa en el recinto[cite: 14].
   * $D_{esp} = 1.00$ si todos los animales del espacio pertenecen a la misma especie.
   * $D_{esp} = 1.35$ si conviven familias estructuradas de **dos o más especies biológicas distintas con afinidad no negativa ($\overline{\alpha} \ge 0$)**.

---

### 2.4. Multiplicadores y Filtros Globales

#### A. Penalizador Asimétrico de Hábitat / Condición Higiénica ($P_{hab}$)
Ubicar al espécimen en su bioma adecuado es una condición básica que no genera bonificaciones artificiales; equivocarse destruye el bienestar del animal:

$$P_{hab} = \text{factor\_bioma} \text{ según tabla } \text{CompatibilidadBioma}$$

* **Bioma Idóneo:** $P_{hab} = 1.00$ (sin penalización).
* **Bioma Neutral/Tolerable:** $P_{hab} = 0.75$ (reducción de un 25%).
* **Bioma Hostil/Incompatible:** $P_{hab} = 0.30$ (reducción directa e inamortizable del 70%).

#### B. Modificador de Colapso Crítico ($M_{crit}$)
Penaliza de forma multiplicativa las situaciones biológicas graves (RF-19, RF-20)[cite: 4, 5]:
$$M_{crit} = M_{salud} \times M_{hambre}$$
* $M_{salud} = 0.50$ si `estado_salud = 'ENFERMO'`; caso contrario $1.00$[cite: 4].
* $M_{hambre} = 0.40$ si `nivel_alimentacion = 0`; caso contrario $1.00$[cite: 5].

---

### 2.5. Protocolo de Borrado Lógico (*Soft Delete*) en Defunciones (RF-19)

Para preservar la genealogía y el reconocimiento de hermandades sin violar la integridad referencial (RNF-08)[cite: 4, 8]:
1. **Inmutabilidad Genealógica:** Se prohíbe el borrado físico (`DELETE`) de registros en `Animal` que figuren como `id_padre` o `id_madre` de otros individuos[cite: 4].
2. **Transición por Defunción:** Al fallecer un animal (RF-19)[cite: 4]:
   * Su columna `estado_vital` cambia a `'MUERTO'`[cite: 4].
   * Su campo `id_espacio` se establece en `NULL` (queda excluido del aforo, gastos y cálculos del espacio)[cite: 2, 4].
   * Se anulan sus vínculos mutables de `id_pareja` e `id_mejor_amigo` en las fichas de los animales vivos[cite: 2, 4].
3. **Persistencia Fraternal:** La consulta de hermandad opera comparando los identificadores de linaje (`id_padre` / `id_madre`) entre animales vivos dentro del mismo recinto, garantizando que sigan reconociéndose como hermanos de por vida aunque sus progenitores hayan fallecido[cite: 4].

---

### 2.6. Propagación y Consistencia Jerárquica

Tras consolidar la satisfacción individual ($S_i$) en `Animal.satisfaccion_actual`, el motor propaga la métrica de forma reactiva y ascendente (RF-15)[cite: 4]:

1. **Satisfacción del Espacio ($S_{espacio}$):**  
   $$S_{espacio} = \frac{1}{K}\sum_{i=1}^{K} S_i \quad (K = \text{total de animales vivos en el espacio})$$
2. **Satisfacción del Hábitat ($S_{habitat}$):**  
   Media aritmética de los espacios pertenecientes a dicho hábitat[cite: 1, 4].
3. **Satisfacción Global del Parque ($S_{global}$):**  
   Media aritmética de todos los especímenes vivos del zoológico, la cual modula de forma directa el nivel de Prestigio Actual (RF-35) y la probabilidad reproductiva de las parejas (RF-16)[cite: 4, 6].