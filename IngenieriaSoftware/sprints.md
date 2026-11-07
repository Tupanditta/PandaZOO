# Plan de implementación por sprints

Este documento divide la implementación de la aplicación en sprints incrementales. Cada sprint entrega un resultado funcional revisable, cubre casos de uso, requisitos y restricciones concretos, e incluye sus propios tests. Un sprint no empieza hasta que el anterior cumple su criterio de hecho.

Fuentes: `Requisitos/Funcionales.md` (RF), `Requisitos/NoFuncionales.md` (RNF), `Restricciones/EspecificacionRES.md` (RES), `CasosUso/` (CU), `BBDD/EspecificacionBBDD.md`, `Misiones/EspecificacionMisiones.md` y `Otros/valores_y_pesos.json`.

**Decisiones técnicas:** lenguaje, arquitectura, tecnologías y herramientas están definidos en `design.md`. Resumen: Android e iOS con una sola base de código en Kotlin Multiplatform, SQLite (`androidx.sqlite`), Compose Multiplatform, arquitectura por capas con dominio puro (RES-01, RES-02, RES-14, RNF-06, RNF-15: solo interfaz de tablas, métricas y formularios, solo vertical). La migración del código de los sprints 0 y 1 está en el Sprint M. iOS se entrega como PWA en lugar de aplicación nativa; los sprints 0 a 5 ya funcionan en la web (`Migracion/MigracionPWAiOS.md`), y todos los sprints posteriores deben pasar también los tests de la web (`jsNodeTest`). Las decisiones sobre reloj, reproducción, dispositivo de referencia y tamaño del Sprint 5 están en la sección 5.

---

## 1. Estrategia común

### 1.1. Principios de escalado

1. **De dentro hacia fuera.** Primero reglas puras de dominio (testeables sin dispositivo), luego persistencia, luego casos de uso y al final la interfaz.
2. **Determinismo.** El reloj (`Clock`) y el generador aleatorio (`Random`) se inyectan. Ningún test depende del tiempo real ni de azar no controlado. Esto es obligatorio para RF-14, RF-16, RF-21 y RNF-12.
3. **Cada sprint deja la app ejecutable.** Al final de cada sprint hay una demo en dispositivo o emulador con lo nuevo funcionando.
4. **Un caso de uso = una transacción.** Cada CU que modifica estado se implementa como una única transacción SQLite con auditoría (RF-45, RF-47). Los intentos rechazados no cambian el dominio (RES-08).
5. **Sin polling.** Ningún sprint introduce bucles de evaluación, recálculos por renderizado ni cómputo al navegar (RES-10, RF-44). Se verifica con un test transversal desde el Sprint 2.

### 1.2. Pirámide de tests

| Nivel | Qué prueba | Herramienta sugerida | Cuándo corre |
|---|---|---|---|
| Unitario (dominio) | Fórmulas, validaciones, redondeos, máquinas de estado | JUnit | Cada commit |
| Integración (datos) | Repositorios, restricciones `CHECK`/FK, transacciones y rollback | SQLite real en memoria o fichero temporal + `kotlin.test` | Cada commit |
| Casos de uso | Flujos completos CU-xx con base de datos real en memoria, reloj y azar fijos | JUnit | Cada commit |
| Instrumentados / UI | Navegación, formularios, accesibilidad | Compose UI Test multiplataforma | Fin de sprint |
| Rendimiento | RNF-01 a RNF-05, RNF-10 sobre carga de referencia | Macrobenchmark / test cronometrado | Sprint 10, y humo desde Sprint 5 |

### 1.3. Carga de referencia (RNF-04)

Fixture reutilizable desde el Sprint 2: 50 espacios y 500 animales vivos repartidos en todos los hábitats. Se construye una sola vez como generador de datos de test y se reutiliza en todos los sprints para detectar regresiones de rendimiento pronto.

### 1.4. Definición de hecho (DoD) común

Un sprint está hecho cuando:

- Todos los tests del sprint y de los sprints anteriores pasan.
- Cada CU del sprint cubre su flujo principal, todas las excepciones y alternativas de su especificación en `EspecificacionesCU/`.
- Cada operación que muta estado deja registro de auditoría y es atómica (RF-45, RF-47).
- Ningún identificador técnico aparece en la interfaz (RES-06).
- La demo del sprint se ha ejecutado y revisado.
- La trazabilidad (sección 4) está actualizada.

### 1.5. Ceremonia de revisión

Al cierre de cada sprint: demo corta, lista de CU/RF cerrados, informe de tests, deuda técnica anotada y confirmación del alcance del siguiente sprint.

---

## 2. Visión general

| Sprint | Resultado entregable | CU | Depende de |
|---|---|---|---|
| 0 | Cimientos: proyecto, base de datos, configuración JSON, semilla de partida | — | — |
| 1 | Economía, inventario y espacios | CU-02, CU-03, CU-07, CU-16 | 0 |
| M | Migración a código compartido Android + iOS | — | 0, 1 |
| 2 | Motor de satisfacción y monitor | CU-17 | 0, 1, M |
| 3 | Gestión de especímenes y tienda de animales | CU-01, CU-04, CU-05, CU-06, CU-14 | 1, 2 |
| 4 | Cuidado animal: alimentación y tratamiento | CU-13, CU-09 | 3 |
| 5 | Ciclo de simulación, beneficios, defunción y avisos | CU-11, CU-08, CU-18, CU-21 | 4 |
| 6 | Prestigio, desbloqueos y avisos informativos | CU-19 | 5 |
| 7 | Reproducción | CU-10 | 5, 6 |
| 8 | Simulación en diferido y recuperación | CU-12 | 5, 7 |
| 9 | Misiones y recompensas | CU-15, CU-20 | 6, 7 |
| 10 | Endurecimiento: rendimiento, retención, accesibilidad y aceptación | — | todos |

Hitos de revisión sugeridos:

- **Hito A (tras Sprint 3):** el usuario ya puede montar un zoo y gestionar animales. Se valida el modelo de datos y la usabilidad de las fichas.
- **Hito B (tras Sprint 5):** el zoo evoluciona solo. Se valida el motor de simulación, que es el mayor riesgo técnico.
- **Hito C (tras Sprint 8):** simulación completa y robusta, incluida la recuperación tras cierre.
- **Hito D (tras Sprint 10):** versión candidata.

---

## 3. Sprints

### Sprint 0 — Cimientos

**Objetivo:** proyecto compilable con base de datos, configuración cargada desde JSON y una partida nueva inicializada, sin pantallas de negocio.

**Alcance**

- Proyecto Android, módulos/paquetes por capas, inyección de dependencias, CI local que ejecuta los tests.
- Esquema SQLite completo según `EspecificacionBBDD.md`, sección 2 (todas las tablas, `CHECK`, claves compuestas con `id_partida`, `PRAGMA foreign_keys = ON`). Se crea el esquema entero ahora para no migrar entre sprints; las tablas se usan según avancen.
- Cargador y validador de `valores_y_pesos.json` (tablas de rangos, costes, alimentos por especie, afinidades, compatibilidad bioma, prestigio, misiones, recompensas aleatorias). La validación rechaza configuración incompleta (por ejemplo, pesos sociales que no suman 1.00, probabilidades que no suman 100).
- Utilidades de dominio: redondeo al centésimo con medios hacia arriba (dinero, porcentajes, umbrales), abstracciones `Clock` y `Random`.
- Inicialización de partida (sección 2.4 de la BBDD): catálogo, hábitats precargados de solo lectura (RES-05), fondos iniciales `2000.00`, inventarios vacíos, `EstadoSimulacion`, desbloqueos iniciales.
- Mecanismo común de transacción + auditoría + idempotencia (sección 3 de la BBDD) como base reutilizable por todos los CU.

**Tests**

- T0.1 Redondeo: `0.005 → 0.01`, `0.004 → 0.00`, medios hacia arriba en dinero, porcentaje y umbral de prestigio.
- T0.2 El validador acepta el JSON actual y rechaza variantes rotas: pesos sociales ≠ 1.00, peso de afinidad no mayor que la suma familiar, probabilidades ≠ 100, falta de precio de bambú, falta `M_crit`.
- T0.3 Esquema: cada `CHECK` y cada FK rechaza un dato inválido (alimentación no múltiplo de 10, capacidad fuera de tabla, referencia a otra partida, etc.).
- T0.4 Una partida nueva tiene fondos `2000.00`, prestigio `0`, sin máximos históricos y hábitats precargados.
- T0.5 Rollback: si una escritura falla a mitad de una transacción de prueba, no quedan cambios parciales.
- T0.6 Idempotencia base: repetir la misma clave de operación devuelve el resultado original sin duplicar efectos.
- T0.7 Los hábitats no admiten inserción, edición ni borrado por la capa de aplicación (RES-05).

**Criterio de hecho:** la app arranca, crea una partida y los tests de infraestructura pasan. Demo: inspección del estado inicial de la base de datos.

---

### Sprint 1 — Economía, inventario y espacios

**Objetivo:** el usuario ve el zoo, compra insumos y espacios y los configura. Primera pantalla navegable.

**Alcance**

- **CU-16** Consultar inventario y finanzas (RF-09, RF-26).
- **CU-07** Adquirir insumos: bambú e insumos médicos, coste = cantidad × precio, saldo suficiente (RF-10, RF-31, RF-50, RES-12).
- **CU-03** Adquirir espacios: capacidad de la tabla de costes, denominación única en el hábitat, hábitat desbloqueado (RF-02).
- **CU-02** Configurar espacio: cambia nombre y/o hábitat, no cambia capacidad, valida capacidad del hábitat destino (RF-04, RES-04).
- Navegación jerárquica Hábitats → Espacios (el nivel Animales llega en el Sprint 3) con etiquetas legibles (RF-08, RF-25, RES-06).
- Pantalla de inicio básica con saldo.

**Tests**

- T1.1 Compra de bambú: coste correcto, saldo e inventario actualizados en una sola transacción; cantidad `0` o negativa rechazada.
- T1.2 Saldo insuficiente: sin cambios y motivo exacto (RF-31). Cubre también el límite exacto (saldo = coste).
- T1.3 No pueden existir saldos ni inventarios negativos tras ninguna secuencia de operaciones (propiedad sobre secuencias aleatorias; RES-12, RF-50).
- T1.4 Compra de espacio: capacidades válidas `2, 4, 6, 8, 10, 15, 20`; capacidad fuera de tabla rechazada; nombre duplicado en el mismo hábitat rechazado, mismo nombre en otro hábitat aceptado.
- T1.5 Espacio en hábitat bloqueado rechazado.
- T1.6 Configurar espacio: renombrar, cambiar de hábitat, ambos; la capacidad queda intacta; hábitat destino sin capacidad rechazado sin cambios.
- T1.7 Reintentar la misma compra con la misma clave de operación no cobra dos veces (RNF-12).
- T1.8 Auditoría: compra confirmada y compra rechazada generan registro con el motivo; la consulta de inventario no genera registro (RF-47).
- T1.9 UI: ninguna pantalla muestra identificadores técnicos (RES-06); navegación Hábitats → Espacios funciona.

**Criterio de hecho:** se puede comprar bambú, insumos y espacios y reorganizar espacios entre hábitats. Demo en dispositivo.

---

### Sprint M — Migración a código compartido Android + iOS

**Objetivo:** que todo lo construido en los sprints 0 y 1 funcione igual en Android e iOS desde una sola base de código, sin cambiar ninguna regla de negocio. Es obligatorio antes del Sprint 2 para que ningún sprint posterior se escriba dos veces. Definición completa, pasos y criterios en `Migracion/TareaMigracionMultiplataforma.md`.

**Alcance**

- Pasos M1 a M9 del documento de migración: build multiplataforma, `:domain`, `:application`, `:testing`, `:data`, `:shared-ui`, `:androidApp`, `iosApp/` y cierre.
- El dinero pasa de `BigDecimal` a `Dinero` en céntimos (RES-14, RNF-15).
- SQLite empaquetado y común a ambas plataformas (RNF-16).
- Tarea de Gradle que prohíbe APIs de plataforma en el código común (RES-14).

**Tests**

- TM.1 Los 72 tests de los sprints 0 y 1 se ejecutan sobre el código compartido y pasan, sin perder ninguno.
- TM.2 Equivalencia del dinero: los casos de redondeo y coste del Sprint 1 (`0.005` a `0.01`, `10 kg × 0.50 = 5.00`, límite exacto saldo = coste) dan el mismo resultado con `Dinero`.
- TM.3 El mismo `esquema.sql` crea la base de datos en JVM y en Android; todas las pruebas de `CHECK` y claves foráneas siguen rechazando lo mismo.
- TM.4 La tarea de independencia de plataforma falla si se añade un `import java.*` o `android.*` en `commonMain` (se prueba con un caso de prueba).
- TM.5 En macOS: la suite de `:domain`, `:application` y `:data` pasa en `iosSimulatorArm64`.

**Criterio de hecho:** `./gradlew check assembleDebug` correcto en Windows con todos los tests migrados, `iosApp/` entregado y la verificación en macOS registrada. Hasta que se ejecute TM.5 en un Mac, el estado de iOS consta como «entregado, no verificado».

---

### Sprint 2 — Motor de satisfacción y monitor

**Objetivo:** tener el cálculo central del juego, probado de forma aislada y con carga de referencia, antes de que existan animales gestionables por el usuario.

**Alcance**

- Dominio puro: `S_i = (w_bio·S_bio + w_soc·S_soc) · P_hab · M_crit` (RF-15, `EspecificacionBBDD.md` sección 4).
  - `S_bio = alimentación / 100`.
  - `S_soc` con pareja, mejor amigo, padre, madre, hermanos vivos y afinidad no familiar del espacio.
  - `P_hab` por combinación especie-bioma; `M_crit = M_alimentación · M_enfermedad`.
  - Sin redondeo interno; porcentaje mostrado entero con medios hacia arriba.
- Medias jerárquicas: espacio, hábitat, parque. Un nivel sin animales vivos queda sin métrica y no entra en las medias superiores.
- Orquestador de recálculo disparable únicamente por evento de dominio y persistencia en `MetricaSatisfaccion` (RF-44).
- **CU-17** Consultar monitor de satisfacción: parque, hábitat, espacio, individuo (RF-27).
- Generador de la carga de referencia (500 animales, 50 espacios) y de datos de prueba para animales, usado por los siguientes sprints. La creación manual de animales para este fin existe solo en tests y en un modo de depuración.

**Tests**

- T2.1 Fórmula con casos calculados a mano: alimentación 100% y sano → `M_crit = 1`; alimentación 0% reduce `S_i`; animal enfermo reduce `S_i`.
- T2.2 `S_soc`: relación inexistente aporta `0` sin penalización adicional; pareja, amigo, familia y afinidad con los pesos de la especie; pares de la misma especie.
- T2.3 `S_i` siempre en `[0,1]` para entradas extremas (propiedad).
- T2.4 `P_hab` modifica `S_i` pero no impide ubicar animales (se comprobará de nuevo en el Sprint 3).
- T2.5 Redondeo de presentación: `0.495 → 50`, `0.494 → 49`; el valor interno no se redondea.
- T2.6 Medias: espacio vacío sin métrica; hábitat con un solo espacio con animales usa solo ese; parque sin animales vale `0`.
- T2.7 El recálculo corre solo ante eventos y no ante navegación ni lectura: contador de invocaciones del orquestador igual a `0` tras abrir pantallas (RES-10, RF-44).
- T2.8 Afectados: tras un cambio en un espacio se recalculan los animales del espacio, no los de espacios no afectados.
- T2.9 Rendimiento de humo: recálculo completo sobre la carga de referencia por debajo de 1000 ms incluyendo persistencia (RNF-02).
- T2.10 UI: el monitor muestra los cuatro niveles con etiquetas legibles y porcentajes enteros.

**Criterio de hecho:** el motor de satisfacción coincide con los casos calculados a mano y cumple el presupuesto de tiempo sobre la carga de referencia. Demo: monitor con datos generados.

**Estado: implementado (verificado en Windows/JVM y compilación Android; iOS sin verificar).** `./gradlew check :androidApp:assembleDebug` correcto. T2.1 a T2.10 cubiertos: fórmula y medias en `domain` (`SatisfaccionTest`), persistencia, recálculo por alcance, monitor y rendimiento en `data` (`Sprint2Test`), pantalla y no recálculo al navegar en `shared-ui` (`Sprint2UiTest`). La carga de 500 animales y 50 espacios se recalcula y persiste muy por debajo de 1000 ms en JVM; la medición en dispositivo queda para el Sprint 10 (D6). Decisiones de interpretación:

- Un vínculo (pareja, amigo, padre, madre) solo cuenta si el animal vinculado sigue vivo y alojado; no se exige que comparta espacio. `num_hermanos` cuenta animales vivos que comparten padre o madre, estén donde estén.
- Familia a efectos de `C_afinidad`: pareja, padre, madre, hermanos e hijos vivos. El mejor amigo no es familiar y sí entra en la afinidad.
- Las medias de espacio, hábitat y parque son medias directas sobre los animales vivos de cada nivel, no medias de medias (`EspecificacionBBDD.md`, 4.3).
- El orquestador `RecalcularSatisfaccion` admite alcance `Parque` o `Espacios`. Los casos de uso de los sprints 3 a 8 deben incluir en el alcance todos los espacios afectados, incluidos los de familiares alojados en otros espacios cuando cambie el número de hermanos o una relación.
- Generador de datos de prueba (`DatosDePrueba`): solo en tests y en modo de depuración (Android con `FLAG_DEBUGGABLE`; en iOS permanece desactivado hasta que haya contenedor de depuración).

---

### Sprint 3 — Gestión de especímenes y tienda de animales

**Objetivo:** el usuario compra y gestiona animales. Se cierra el ciclo manual del zoo. **Hito A.**

**Alcance**

- Tienda de animales con estados de catálogo: bloqueada, comercial desbloqueada, exclusiva (RF-10, RF-49, RES-09). Los desbloqueos de este sprint son los iniciales; la lógica dinámica por prestigio llega en el Sprint 6.
- **CU-01** Adquirir espécimen: hábitat → espacio con cupo → ficha completa → alta atómica con 20 años, alimentación 100% y sano; edad máxima fijada al comprar (edad media × factor `0.90..1.10`); satisfacción tras la ficha; compatibilidad solo afecta a la satisfacción (RF-01, RF-29, RF-32, RF-33, RES-08).
- **CU-05** Editar ficha: nombre, dos habilidades, dos rasgos, pareja y mejor amigo; pareja recíproca, misma especie, ambos ≥ 16 años; sin autorreferencias; variables biológicas no editables (RF-03, RF-34, RF-48, RES-03).
- **CU-04** Reubicar espécimen: espacio destino activo con capacidad; recálculo de origen y destino (RF-05, RF-29).
- **CU-06** Vender espécimen: no enfermo, alimentación > 0%, acredita `0`, estado `VENDIDO`, conserva genealogía (RF-06, RES-11).
- **CU-14** Dar de baja espacio vacío: baja lógica sin romper referencias; oferta opcional tras traslado o baja (RF-07).
- Nivel Animales en la navegación y ficha técnica del animal con rango visible y sin edición de campos protegidos (RF-25).

**Tests**

- T3.1 Compra atómica: sin saldo, sin cupo, especie bloqueada o ficha incompleta no cambia nada. Con todo válido: saldo, aforo, animal, satisfacción y auditoría cambian juntos.
- T3.2 Valores iniciales del animal: 20 años, 100%, sano, edad máxima en `[0.90, 1.10] × edad media` (con `Random` fijo en los extremos).
- T3.3 Nombre vacío o de más de 40 caracteres rechazado; nombre repetido, en la misma especie o en otra, aceptado (RF-32). Los listados desambiguan los homónimos sin mostrar identificadores.
- T3.4 Ficha exige exactamente dos habilidades y dos rasgos (RF-33).
- T3.5 Especie exclusiva no comprable en la tienda ordinaria (RES-09).
- T3.6 Comprar un animal en un hábitat incompatible está permitido y baja la satisfacción de él y de sus compañeros de espacio.
- T3.7 Vínculos: pareja no recíproca, distinta especie, menor de 16 o autorreferencia rechazadas; mejor amigo inter-especie aceptado; vínculos coherentes tras editar (RF-34, RF-48).
- T3.8 Intento de editar edad, alimentación, salud, satisfacción o especie rechazado (RES-03).
- T3.9 Reubicar: aforo lleno rechazado; traslado correcto actualiza ambos espacios; destino inactivo rechazado.
- T3.10 Vender: enfermo rechazado, alimentación 0% rechazada, venta correcta no suma fondos, libera aforo y mantiene genealogía.
- T3.11 No se puede borrar físicamente un animal con descendencia (RES-11).
- T3.12 Baja de espacio: con animales rechazada; con referencias históricas o pendientes rechazada; vacío y limpio aceptada como baja lógica.
- T3.13 Reintento idempotente de compra y de traslado (RNF-12).
- T3.14 UI: flujo de compra de punta a punta, la ficha no muestra claves (RES-06).

**Criterio de hecho:** se pueden comprar, editar, mover y vender animales con todas las reglas. Demo en dispositivo.

**Estado: implementado (verificado en Windows/JVM y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew check :androidApp:assembleDebug` correcto. T3.1 a T3.14 cubiertos: reglas de edad y catálogo cerrado en `domain` (`AnimalTest`), casos de uso con base de datos real en `data` (`Sprint3Test`) y flujos de pantalla en `shared-ui` (`Sprint3UiTest`). Decisiones de interpretación:

- La satisfacción del compañero de espacio cambia al llegar un animal nuevo, pero no siempre baja: un animal solo no tiene afinidad (`C_afinidad = 0`) y el compañero la aporta. T3.6 se verifica como «la satisfacción de él es menor en un hábitat incompatible y la de los compañeros se recalcula».
- El nombre no es único (RF-32): se eliminaron la restricción `UNIQUE (id_partida, id_especie, nombre)` y el rechazo por duplicado. Si en un listado dos animales tienen la misma etiqueta, se añade la edad y, si aún coinciden, un número de orden de alta.
- El mejor amigo no es recíproco (cada animal guarda el suyo); la pareja sí. Quitar la pareja libera a los dos. No se puede pasar directamente de una pareja a otra: hay que quitar la primera (CU-05, 3.c).
- Un vínculo que no cambia en una edición no se vuelve a validar, porque una pareja trasladada a otro espacio sigue siéndolo (regla 5.3, Sprint 7).
- Un traslado conserva pareja y mejor amigo. Al vender se anulan los vínculos mutables en ambos sentidos y se conservan `id_padre` e `id_madre`.
- Los espacios afectados por una venta o edición se calculan con `espaciosRelacionados` (vínculos, padres, hijos y hermanos vivos alojados), de modo que la satisfacción de un familiar en otro espacio también se recalcula. CU-02 recalcula el espacio cuando cambia de hábitat.
- Dependencias que impiden la baja lógica de un espacio (CU-14): un aviso abierto (`PENDIENTE` o `VISTA`) que lo referencia y una concepción `CONCEBIDA` de un progenitor alojado en él. El histórico de auditoría no bloquea la baja lógica, porque no se borra nada. Los sprints 5 y 7 añadirán sus propias referencias a esta comprobación si las crean.
- La unidad a coste cero de una recompensa de misión (CU-01, paso 7) llega en el Sprint 9; por ahora todas las compras cobran el coste de `CosteRango`.
- Navegación: Hábitats → Espacios → Animales → Ficha. La compra de animales parte de la tienda (botón «Comprar animales» de la tienda de recursos). La venta y el traslado ofrecen la baja del espacio que queda vacío.

---

### Sprint 4 — Cuidado animal

**Objetivo:** alimentación y tratamiento médico manuales, que son las respuestas del usuario a la simulación.

**Alcance**

- **CU-13** Alimentar espécimen: individual y grupal por espacio, +10 puntos por incremento, consumo de bambú por especie, tope 100%, total mostrado antes de confirmar, sin consumo parcial (RF-12, RF-30).
- **CU-09** Aplicar tratamiento médico: solo animal vivo enfermo, todos los insumos del rango disponibles, consumo atómico, restablece a sano (RF-13).
- Recálculo de satisfacción tras cada operación (RF-15).
- Pantalla de ficha con acciones de alimentar y tratar.

**Tests**

- T4.1 Alimentación individual: de 60% a 70%; consumo según especie; límite 100%; animal al 100% o muerto rechazado.
- T4.2 Falta de bambú o de configuración para la especie: rechazo con motivo exacto, sin cambios.
- T4.3 Grupal: el total mostrado coincide con el consumo real; inventario insuficiente no alimenta a nadie (todo o nada); animales al 100% y no vivos excluidos del lote.
- T4.4 La alimentación siempre es `0` o múltiplo de 10 tras cualquier secuencia (propiedad).
- T4.5 Tratamiento: sano rechazado; insumo parcial rechazado; correcto consume todo y deja sano.
- T4.6 La satisfacción sube tras alimentar o curar y se recalcula solo en esos eventos.
- T4.7 Fallo simulado a mitad de la operación grupal revierte inventario y animales (RF-45).
- T4.8 Idempotencia de una operación repetida.

**Criterio de hecho:** el usuario puede mantener sanos y alimentados a sus animales. Demo en dispositivo.

**Estado: implementado (verificado en Windows/JVM y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew allTests :androidApp:assembleDebug` correcto. T4.1 a T4.8 cubiertos: reglas puras en `domain` (`CuidadoTest`), casos de uso con base de datos real en `data` (`Sprint4Test`) y flujos de pantalla en `shared-ui` (`Sprint4UiTest`). Decisiones de interpretación:

- Auditoría: `ALIMENTACION_INDIVIDUAL` (entidad `ANIMAL`), `ALIMENTACION_GRUPAL` (entidad `ESPACIO`) y `TRATAMIENTO` (entidad `ANIMAL`).
- La acción grupal recibe el total de kilos que se mostró al usuario. Si al confirmar el conjunto requiere otro total, se rechaza todo con `ConjuntoCambiado` (CU-13, 4.a).
- Un rechazo por bambú o insumos insuficientes informa de la cantidad necesaria, la disponible y el faltante; nunca consume parcialmente.
- Alimentar con resultado mayor que 0 reinicia `ciclos_desnutricion`; tratar reinicia `ciclos_enfermedad`.
- Hay un único aviso `ALERTA_SALUD` por animal. Se cierra como `RESUELTA` solo cuando, tras alimentar o tratar, ambos contadores están a 0; si el animal sigue enfermo y desnutrido, el aviso se conserva. Los avisos los crea el Sprint 5; este sprint solo los cierra.
- Recálculo de satisfacción limitado al espacio del animal o del lote, tras confirmar la mutación (RF-15).
- Interfaz: ficha con «Alimentar» y «Aplicar tratamiento» (solo si está enfermo), y «Alimentar a todos» en la pantalla del espacio; todas con diálogo de confirmación que muestra las cantidades.

---

### Sprint 5 — Ciclo de simulación, beneficios, defunción y avisos

**Objetivo:** el zoo evoluciona solo cada 10 minutos. Es el sprint de mayor riesgo. **Hito B.**

**División (decisión D5).** Este sprint se ejecuta en dos entregas, cada una con demo y revisión:

- **5A, motor del ciclo:** CU-11 con las etapas edad, alimentación, salud, ciclos críticos, defunciones, métricas y persistencia; identificador de ciclo, temporizador y remanente; defunción. Tests T5.1 a T5.6, T5.10, T5.13, T5.14 y T5.15. Demo: el zoo envejece, enferma y pierde animales con el modo de depuración que adelanta ciclos.
- **5B, economía y avisos del ciclo:** etapa de beneficios, CU-08, alertas de desnutrición, CU-18 y CU-21. Tests T5.7, T5.8, T5.9, T5.11, T5.12 y T5.16. Demo: beneficios, reclamación, panel crítico e historial.

5B no empieza hasta que 5A cumple su criterio de hecho. Las etapas de reproducción y misiones del ciclo quedan como puntos de extensión vacíos hasta los sprints 7 y 9.

**Alcance**

- **CU-11** Ejecutar ciclo, en el orden definido por RF-14: edad (+6 días, calendario 30×12), alimentación (−20 puntos, mínimo 0), salud (enfermar si `aleatorio < prob_rango · (1 − S)`), ciclos críticos, defunciones (longevidad o 3 ciclos críticos), reproducción (solo se deja el punto de extensión), beneficios, misiones (punto de extensión), métricas y persistencia.
- Identificador único de ciclo y control de duplicados (RNF-12).
- Temporizador de 10 minutos con persistencia del remanente en segundos (RF-24). Ejecución en primer plano con un único temporizador ligado al fin de ciclo, sin polling (RES-10).
- **CU-08** Reclamar beneficios: una sola vez, acredita el total pendiente y lo deja a `0` (RF-11, RES-12). Beneficio por ciclo según la fórmula de la BBDD, sección 1.4.
- Defunción: estado `MUERTO`, desvinculado del espacio, anula vínculos mutables en otras fichas, conserva genealogía (RF-19).
- **CU-18** Panel de seguimiento crítico con ciclos acumulados y restantes, y acceso directo a la ficha (RF-20, RF-41, RF-42).
- **CU-21** Historial de avisos con severidades baja, media y alta y retención acotada (RF-28).
- Alerta crítica única por animal, actualizada cada ciclo y resuelta al recuperarse (RF-20).

**Tests**

- T5.1 Orden de etapas: cada etapa ve el resultado de la anterior (test con trazas por etapa).
- T5.2 Edad: +6 días por ciclo; 5 ciclos = 30 días = 1 mes; transición de año correcta.
- T5.3 Alimentación: 100→80→…→0, nunca negativa.
- T5.4 Enfermedad: con `Random` fijo, probabilidad `0` nunca enferma, `1 − S = 1` y prob. 1 siempre; solo animales vivos, alojados y sanos; frontera `aleatorio == umbral` no enferma (`<` estricto).
- T5.5 Contadores críticos: desnutrición y enfermedad independientes, se reinician al recuperarse, con ambos se usa el mayor, 3 ciclos continuos provocan muerte.
- T5.6 Muerte por longevidad al alcanzar la edad máxima; el animal sale del aforo y las referencias mutables de otros se anulan; genealogía conservada.
- T5.7 Beneficio: fórmula con casos a mano (varios rangos, satisfacción fraccionaria), `0` sin animales vivos y sin fila en `BeneficioPendiente` cuando vale `0`; se calcula tras las defunciones.
- T5.8 Mutaciones entre ciclos no alteran el acumulado reclamable; solo el cierre de ciclo lo suma.
- T5.9 Reclamar: acredita el total, marca registros, pendiente `0`; segunda reclamación seguida no acredita nada y no duplica (RES-12).
- T5.10 Mismo identificador de ciclo ejecutado dos veces produce un único efecto (RNF-12).
- T5.11 Alerta de desnutrición: una sola por animal, se actualiza cada ciclo, desaparece al alimentar; si sigue enfermo la alerta continúa con la causa restante.
- T5.12 Panel crítico: ciclos restantes = `3 − max(contadores)`; identifica por nombre, nunca por clave.
- T5.13 Recuperación de un cierre forzado a mitad de ciclo: se restaura el último estado consistente, sin ciclo parcial (RNF-08, RNF-09).
- T5.14 Temporizador: el remanente persiste y continúa donde corresponde (RF-24); sin sondeo en reposo (RES-10, RNF-05).
- T5.15 Ciclo completo sobre la carga de referencia por debajo de 1000 ms incluyendo persistencia (RNF-02, prueba de humo).
- T5.16 Historial de avisos filtrado por severidad y acotado por retención.

**Criterio de hecho:** se puede dejar la app abierta y el zoo envejece, enferma, genera beneficios y mata animales descuidados; el usuario recibe avisos y reclama beneficios. Demo en dispositivo con un ciclo de 10 minutos y con un modo de depuración que adelanta ciclos.

**Estado: implementado, 5A y 5B (verificado en Windows/JVM y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew allTests :androidApp:assembleDebug` correcto. T5.1 a T5.16 cubiertos: reglas puras en `domain` (`CicloTest`), ciclo, beneficios, avisos y temporizador con base de datos real en `data` (`Sprint5Test`) y pantallas en `shared-ui` (`Sprint5UiTest`). Decisiones de interpretación:

- Numeración: el primer ciclo es el `1`; el número es la clave de idempotencia (`ciclo-N`). Un número que no es el siguiente se rechaza como `CicloFueraDeOrden`; una partida o simulación en pausa, como `SimulacionNoActiva`.
- Beneficio: se calcula tras las defunciones con la satisfacción persistida al inicio del ciclo de los animales que siguen vivos (la «vigente al inicio de la etapa»). La satisfacción nueva se calcula en la etapa de métricas y alimenta el beneficio del siguiente ciclo.
- Salud: se hace un sorteo por animal vivo, alojado y sano, en orden de identificador; la frontera `aleatorio == umbral` no enferma.
- Auditoría por ciclo: `CICLO_CONFIRMADO` (entidad `CICLO`, clave `id_partida:CICLO_CONFIRMADO:ciclo-N`), y por cada `DEFUNCION`, `CAMBIO_ESTADO_SALUD` (sano a enfermo) y `BENEFICIO_GENERADO`, con clave derivada del ciclo y el animal.
- Avisos críticos: se abren al entrar un animal en estado crítico (un único aviso abierto por animal), se conservan mientras persista alguna causa y se resuelven cuando no queda ninguna o el animal muere. CU-13 y CU-09 los resuelven al alimentar o tratar.
- Reproducción y misiones son puntos de extensión (`EtapaCiclo`) que no hacen nada hasta los sprints 7 y 9.
- Temporizador (`MotorCiclos`): una sola espera hasta el fin del ciclo con el reloj monótono, sin sondeo. Se inicia en primer plano y al pasar a segundo plano guarda el remanente. Un ciclo revertido o rechazado no reinicia el temporizador en bucle: espera un ciclo completo antes de reintentar. No hay servicio en segundo plano (D7).
- Retención: `PurgarRetencion` se ejecuta tras cada ciclo en su propia transacción y acota auditoría, movimientos, beneficios cerrados, ciclos y avisos cerrados (por ventana y por número máximo de filas); los avisos `PENDIENTE` no se purgan. La poda de animales inactivos (BBDD 5.3) queda para el Sprint 10.
- CU-08: la reclamación recibe el importe mostrado; si al confirmar el pendiente es otro se rechaza (`BeneficiosCambiados`). Un reintento con la misma `id_solicitud` devuelve el resultado persistido.
- CU-21: el historial se muestra con el aviso más reciente primero; listar no cambia ningún estado y abrir el detalle de uno `PENDIENTE` lo marca `VISTA`.
- Modo de depuración: botón «Adelantar ciclo» en la pantalla de hábitats, junto a la generación de datos de prueba.

**Cambio de fondos iniciales.** La partida nueva empieza con `2000.00` monedas (`partida_inicial.fondos`). Las partidas creadas antes (versión de base de datos 1) reciben `1000.00` monedas una sola vez al migrar a la versión 2, registradas como movimiento `INGRESO` con origen `COMPENSACION_INICIAL`.

---

### Sprint 6 — Prestigio, desbloqueos y avisos informativos

**Objetivo:** progresión del parque. Los desbloqueos pasan de fijos a dinámicos.

**Alcance**

- Cálculo de prestigio actual con umbrales por nivel (RF-35): animales vivos redondeados al entero con medios hacia arriba, satisfacción sin redondeo, siguiente umbral calculado desde el valor sin redondear.
- Máximos históricos independientes: nivel, animales vivos y satisfacción global (RF-36).
- Desbloqueos permanentes de especies y hábitats por nivel (RES-07, RF-43).
- Avisos informativos de ascenso y desbloqueo, sin repeticiones y con un único aviso al subir varios niveles.
- **CU-19** Consultar prestigio y panel de estadísticas desde la pantalla inicial (RF-37).
- Disparo del cálculo solo tras mutación o fin de ciclo (RF-35, RF-44).

**Tests**

- T6.1 Tabla de umbrales: cada nivel con valores por encima, iguales y por debajo del umbral en ambas condiciones (se exigen las dos a la vez).
- T6.2 Redondeo de animales sin acumular error: el nivel `n+1` se calcula desde el umbral `n` sin redondear.
- T6.3 Ningún nivel cumplido da prestigio `0`.
- T6.4 Los máximos son independientes y no disminuyen si baja el prestigio actual (RF-36). Una partida nueva no tiene máximos.
- T6.5 Desbloqueo persistente: tras descender de nivel la especie sigue comprable (RES-07).
- T6.6 Subir varios niveles en un recálculo emite un solo aviso, el del más alto; no se repite un nivel ya alcanzado (RF-43).
- T6.7 Las especies y hábitats desbloqueados generan un aviso informativo no interactivo, una sola vez.
- T6.8 Tras desbloquear un hábitat, se puede crear un espacio en él (integración con CU-03).
- T6.9 Verificación de prestigio sobre la carga de referencia por debajo de 150 ms (RNF-10).
- T6.10 Navegar al panel de prestigio no dispara cálculos (RES-10).

**Criterio de hecho:** el parque asciende de nivel, desbloquea contenido y lo comunica. Demo en dispositivo.

**Estado: implementado (verificado en Windows/JVM, web y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew check :androidApp:assembleDebug` correcto. T6.1 a T6.10 cubiertos: umbrales, redondeo y máximos en `domain` (`PrestigioTest`), evaluación, desbloqueos, avisos, CU-19 y rendimiento con base de datos real en `data` (`Sprint6Test`) y panel en `shared-ui` (`Sprint6UiTest`). Decisiones de interpretación:

- El prestigio no tiene disparador propio: `RecalcularPrestigio` se invoca al final de `RecalcularSatisfaccion.alConsolidar`, dentro de la misma transacción. Así corre exactamente en los eventos de RF-35/RF-44 (mutaciones y fin de ciclo) y nivel, máximos, desbloqueos, avisos y auditoría se confirman o revierten con la mutación.
- Censo: animales `VIVO` con espacio; satisfacción global = métrica `PARTIDA` recién persistida (`0` sin animales). Los umbrales se leen de `tablas.niveles_prestigio` (la tabla es la expansión de la fórmula; el test T6.1 la regenera y la compara).
- Máximos: la terna se inicializa la primera vez que algún valor es positivo (una partida sin animales sigue sin máximos) y después sube componente a componente.
- Aviso de ascenso solo si el nivel supera el máximo previo; un salto de varios niveles emite uno solo, el del más alto. Los desbloqueos de todos los niveles menores o iguales se insertan con origen `PRESTIGIO`. Los avisos informativos no se repiten (índice único `uq_notificacion_informativa` más comprobación previa).
- Auditoría nueva: `PRESTIGIO_ASCENSO` (entidad `PARTIDA`), `ESPECIE_DESBLOQUEADA` (entidad `ESPECIE`) y `HABITAT_DESBLOQUEADO` (entidad `HABITAT`), origen `SISTEMA`, con clave `idPartida:tipo:idEntidad`. El `numero_ciclo` es el del ciclo en curso o, en una mutación, el último confirmado.
- CU-19: `ConsultarPrestigio` lee solo valores persistidos y aísla cada dato (nivel, máximos, censo, satisfacción); uno no disponible se informa sin sustituirlo. Muestra además los requisitos del nivel siguiente.
- Interfaz: indicador «Nivel N» en la cabecera global (pantalla inicial y resto de pantallas) que abre el panel «Prestigio».

---

### Sprint 7 — Reproducción

**Objetivo:** ciclo reproductivo completo, desde la concepción hasta el registro de la cría.

**Alcance**

- Evaluación en cada ciclo (RF-16): parejas válidas de la misma especie, ambos progenitores con satisfacción sin redondear ≥ umbral de rango, probabilidad `max(0, p_inicial − penalización × hijos vivos comunes)`, una sola concepción por pareja y ciclo.
- Notificación interactiva única, con ventana de tres ciclos y caducidad (RF-17).
- **CU-10** Aceptar reproducción: validación de vigencia y aforo, ficha de la cría (personalidad, habilidades, mejor amigo opcional), padre, madre y hermanos automáticos, edad 0, alimentación 100%, sano, satisfacción antes del alojamiento, operación idempotente y definitiva (RF-18, RES-08).
- Integración en la etapa de reproducción del ciclo (Sprint 5).

**Tests**

- T7.1 Umbral: satisfacción exactamente igual al umbral reproduce, un punto inferior no.
- T7.2 Probabilidad: con `0` nunca concibe, con `1` siempre; penalización por hijos nunca produce probabilidad negativa; cuenta hijos vivos comunes, alojados o no.
- T7.3 Sin vínculo de pareja válido no hay evaluación (RF-34).
- T7.4 Una pareja no concibe dos veces en el mismo ciclo.
- T7.5 Notificación emitida al inicio del ciclo posterior; sigue aceptable durante tres ciclos aunque baje la satisfacción; caduca después y conserva su estado.
- T7.6 Aforo lleno: aceptación deshabilitada; con aforo libre, habilitada.
- T7.7 Aceptar dos veces seguidas crea una sola cría (idempotencia); aceptar tras caducar es rechazado sin cambios.
- T7.8 La cría hereda especie, tiene 0 años, 100%, sana, con padre, madre y hermanos correctos; ficha incompleta no consolida.
- T7.9 Atomicidad: un fallo durante el alta de la cría revierte todo.
- T7.10 Regla 5.3: si un progenitor muere, se vende, se traslada o se rompe la pareja durante la ventana, la concepción sigue `CONCEBIDA`, la aceptación se deshabilita con su motivo y una aceptación forzada se rechaza sin cambios. Si la causa se revierte antes de vencer, se vuelve a habilitar. Al vencer pasa a `EXPIRADA` y la notificación a `DESCARTADA`.

**Criterio de hecho:** parejas válidas se reproducen y el usuario registra a la descendencia. Demo en dispositivo con azar controlado.

**Estado: implementado (verificado en Windows/JVM, web y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew check :androidApp:assembleDebug` correcto. T7.1 a T7.10 cubiertos: reglas puras en `domain` (`ReproduccionTest`), etapa del ciclo, CU-10 y regla 5.3 con base de datos real en `data` (`Sprint7Test`) y flujo de pantalla en `shared-ui` (`Sprint7UiTest`). Decisiones de interpretación:

- Una pareja es evaluable si es recíproca, de la misma especie, con ambos vivos y alojados en el mismo espacio (misma condición que la regla 5.3). La edad mínima de 16 años solo se exige al crear el vínculo (CU-05), no en cada ciclo.
- Un sorteo por pareja evaluable que llega al umbral, en orden de identificadores, con el mismo `Azar` del ciclo. Se usa la satisfacción persistida al inicio del ciclo, como el beneficio.
- Una pareja con una concepción `CONCEBIDA` abierta no se vuelve a evaluar hasta que se acepte o expire. Así no se acumulan ofertas para la misma pareja.
- Ventana: concibe en el ciclo `N`, disponible desde `N+1`, `ciclo_expiracion = N+3`. Es aceptable mientras el último ciclo confirmado no supere `N+3`; al confirmarse un ciclo mayor, la etapa de reproducción la pasa a `EXPIRADA` y su aviso a `DESCARTADA` (con `ciclo_cierre` = ese ciclo). La expiración se ejecuta antes de evaluar nuevas parejas, de modo que una pareja puede volver a concebir en el mismo ciclo en que caduca su oferta anterior.
- Hijos vivos comunes: animales `VIVO` cuyo `id_padre` e `id_madre` son los dos progenitores, estén alojados o no; los vendidos o muertos no cuentan.
- CU-10: `id_padre` es el progenitor de identificador menor e `id_madre` el mayor. Los hermanos no se guardan: salen de compartir padre y madre. La cría no puede tener pareja al nacer; solo se admite mejor amigo del mismo espacio. Los espacios a recalcular incluyen los de los hermanos alojados en otros espacios.
- Una aceptación rechazada no cambia nada, salvo la auditoría del rechazo. La caducidad la registra la etapa del ciclo (no la aceptación rechazada).
- Auditoría nueva: `CONCEPCION` (entidad `REPRODUCCION`, origen `CICLO_PERIODICO`) y `REPRODUCCION_CONFIRMADA` (entidad `REPRODUCCION`, origen `CU-10`).
- No se implementa el rechazo explícito de una concepción (`RECHAZADA`): CU-10 no lo define como flujo; la oferta solo termina aceptándose o caducando.
- Interfaz: el aviso operativo muestra los nombres de la pareja y su especie; abrirlo ofrece «Registrar cría», que lleva al formulario (nombre, dos habilidades, dos rasgos y mejor amigo opcional). Con la aceptación deshabilitada se muestra el motivo.

---

### Sprint 8 — Simulación en diferido y recuperación

**Objetivo:** el zoo sigue evolucionando de forma justa mientras la app está cerrada. **Hito C.**

**Alcance**

- **CU-12** Procesar simulación en diferido al reiniciar: calcula tiempo transcurrido desde la última desconexión, aplica el remanente persistido, ejecuta el ciclo pendiente y los completos posteriores hasta `retencion.max_ciclos_offline` y descarta el exceso (RF-21, RF-24).
- Restricción de hambruna offline: alimentación no desciende más de dos tramos por sesión diferida (RF-22).
- Restricción de enfermedad offline: un animal que enferma computa un ciclo; uno ya enfermo acumula como máximo dos adicionales (RF-23).
- Restauración del último estado consistente tras cierre inesperado en menos de 3 segundos (RNF-09).
- Ningún ciclo se duplica entre ejecución en vivo y diferida (RNF-12).

**Tests**

- T8.1 Tiempo menor que el remanente: ningún ciclo. Igual al remanente: un ciclo. Remanente más 3 ciclos completos: cuatro ciclos.
- T8.2 Más tiempo que el máximo configurado: se ejecuta el máximo y se descarta el exceso.
- T8.3 Hambruna offline: `100/80/60 → 40`, `40/20 → 0`, y un animal ya en 0 solo computa un ciclo mortal (tabla completa de transiciones).
- T8.4 Enfermedad offline: casos de enfermar, ya enfermo y dos ciclos adicionales máximo.
- T8.5 Reabrir dos veces seguidas no repite ciclos (idempotencia).
- T8.6 Remanente persistido en segundos y reanudación exacta del temporizador (RF-24).
- T8.7 Cierre forzado en cada etapa del ciclo (inyección de fallo por etapa): al reabrir, el estado es el último consistente y se completa en menos de 3 s.
- T8.8 Escenario combinado: ausencia larga con animales a punto de morir, reproducción pendiente y caducada, y misiones.
- T8.9 Regla de reloj 5.2: salto hacia atrás da `E = 0`, ningún ciclo, `R` intacto, marca reescrita y evento de auditoría; atrás y luego adelante no regala ciclos. Salto hacia delante grande queda limitado por `max_ciclos_offline`. Marca ausente o mayor que `ahora` sigue el flujo 2.b de CU-12.
- T8.10 CU-12 se ejecuta también al volver a primer plano con el proceso vivo, con el mismo resultado que un arranque en frío. Un cambio de hora con la app abierta no altera el ciclo en vivo (reloj monótono).

**Criterio de hecho:** cerrar y reabrir la app produce estados coherentes, sin trampas ni duplicados. Demo con el reloj de test.

**Estado: implementado (verificado en Windows/JVM, web y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew check :androidApp:assembleDebug` correcto. T8.1 a T8.10 cubiertos: plan de ciclos y límites offline en `domain` (`DiferidoTest`) y CU-12 con base de datos real, reloj y azar fijos en `data` (`Sprint8Test`). Decisiones de interpretación:

- Marca de última actividad: no hay columna nueva ni migración; se usa `EstadoSimulacion.instante_ultimo_ciclo`. Se escribe en cada ciclo confirmado, al pasar a segundo plano (junto con el remanente, en la misma sentencia) y al terminar una recuperación.
- Cada ciclo vencido es una transacción de CU-11 con su propio número. Su marca es el instante virtual en que termina (`marca + R + k·600`) y deja el remanente en 600; así un cierre inesperado a mitad conserva el último ciclo confirmado y la siguiente apertura reanuda desde ahí, sin perder ni regalar tiempo. Una transacción final fija remanente y marca con la hora actual y registra un único `RECUPERACION_DIFERIDA` (clave `id_partida:RECUPERACION_DIFERIDA:último_ciclo`); la purga va después, en su propia transacción.
- Hambruna offline (RF-22), medida contra el estado del animal al empezar la sesión: desde 100, 80 o 60 baja como mucho a 40; desde 40, 20 o 0, a 0. Solo se computa un ciclo mortal por sesión (el contador de desnutrición sube como máximo 1).
- Enfermedad offline (RF-23): el animal que enferma en la sesión computa un solo ciclo enfermo; el que ya estaba enfermo, como máximo dos adicionales. El sorteo de un animal se detiene al enfermar, como en el ciclo ordinario.
- Salto de reloj hacia atrás y marca mayor que `ahora` son el mismo caso: `E = 0`, ningún ciclo, `R` intacto, marca reescrita y auditoría `RELOJ_ANOMALO`. Una marca ausente reinicializa la marca sin auditar (flujo 2.b). Con la simulación en pausa el tiempo ausente no cuenta y solo se renueva la marca.
- CU-12 corre en cada paso a primer plano (`AppContainer.alPrimerPlano`) antes de arrancar el temporizador, para que ambos no ejecuten el mismo ciclo. `alSegundoPlano` cancela la recuperación en curso entre ciclos y guarda remanente y marca.
- Desviaciones respecto a CU-12 7: la satisfacción y las métricas se escriben en cada ciclo, no solo al final (el resultado final es el mismo; escribir solo al final exige un recálculo en memoria). No se usa el estado `RECUPERACION` de `EstadoSimulacion`: la recuperación es reanudable por construcción. Tras un cierre a mitad de recuperación, los límites de RF-22 y RF-23 se miden desde el estado ya confirmado al reabrir.
- Pendiente: no hay aviso en pantalla del resumen de la ausencia; `AppContainer.ultimoDiferido` lo expone para cuando se diseñe.

---

### Sprint 9 — Misiones y recompensas

**Objetivo:** capa de objetivos y recompensas, incluida la tienda a coste cero para exclusivas.

**Alcance**

- Una misión vigente por tipo configurado, sin duplicados (RF-39).
- Todos los tipos de `EspecificacionMisiones.md`: compras por especie, edad alcanzada, cantidad total, por hábitat, ocupación de espacios, prestigio, ventas por especie o edad, composición familiar y satisfacción mínima.
- Progreso acumulativo (compras, ventas, edades) frente a dinámico (hábitat, satisfacción, prestigio); cumplida al alcanzar el objetivo.
- **CU-20** Consultar misiones y progreso.
- **CU-15** Reclamar recompensa: crédito único, retirada de la instancia cumplida y creación atómica de la nueva con progresión (constante, operación, mínimos, máximos, redondeo) (RF-40).
- Recompensas: alimento o insumos, dinero o animal aleatorio ponderado persistido para ser idempotente; desbloqueo de especie exclusiva y unidad a coste cero para una compra (RF-38, RES-09, `EspecificacionBBDD.md` sección 1.3).
- Integración con la etapa de misiones del ciclo y con los eventos de compra, venta y edad.

**Tests**

- T9.1 Hay exactamente una instancia vigente por tipo tras inicializar y tras reclamar.
- T9.2 Cada tipo de misión: casos de progreso, cumplimiento exacto en el límite y no cumplimiento.
- T9.3 Progreso acumulativo no disminuye al vender o morir; las condiciones dinámicas sí pueden bajar.
- T9.4 Progresión: cada operación, mínimo y máximo, y redondeo por parámetro; los parámetros categóricos no cambian.
- T9.5 Reclamar dos veces la misma misión no acredita dos veces (RES-12, RNF-12).
- T9.6 Reclamación atómica: crédito, retirada y creación de la nueva instancia ocurren juntos o ninguno.
- T9.7 Recompensa de animal: distribución ponderada con `Random` fijo; la especie persistida se reutiliza en reintentos sin volver a sortear; se permite repetir especie.
- T9.8 Unidad exclusiva: aparece en la tienda a coste cero, es comprable una vez, flujo normal de CU-01 y `unidad_pendiente` pasa a `0` en la misma transacción; no caduca y no se purga mientras esté pendiente.
- T9.9 Una misión cumplida permanece visible y reclamable hasta reclamarse.
- T9.10 El recálculo de misiones sobre la carga de referencia cumple 150 ms (RNF-10).

**Criterio de hecho:** el bucle completo de juego (comprar, cuidar, simular, progresar, recompensar) funciona. Demo en dispositivo.

**Estado: implementado (verificado en Windows/JVM, web y compilación Android; iOS sin verificar; demo en dispositivo pendiente).** `./gradlew check :androidApp:assembleDebug` correcto. T9.1 a T9.10 cubiertos: reglas puras en `domain` (`MisionTest`), misiones, reclamación, unidad gratuita y rendimiento con base de datos real en `data` (`Sprint9Test`) y pantalla en `shared-ui` (`Sprint9UiTest`). Decisiones de interpretación:

- Las instancias de la semilla ya existían (`ProgresoMision`, una por tipo); este sprint solo las evalúa y las sustituye. Objetivo de cada tipo: el parámetro indicado en `ReglasMision` (las dos misiones de venta valen `1`).
- Eventos: compra (CU-01) y venta (CU-06) actualizan los contadores acumulativos en su propia transacción; la etapa de misiones del ciclo cuenta a los animales que cruzan la edad requerida (años completos, `edad_previa/360 < requerida ≤ edad/360`) y solo ve a los supervivientes del ciclo, así que un animal que muere en el mismo ciclo en que cruzaría la edad no cuenta. Las misiones dinámicas se evalúan al final de cada recálculo de satisfacción (`RecalcularSatisfaccion`), es decir, en cada mutación y en cada ciclo, tras el prestigio.
- Una instancia `CUMPLIDA` no vuelve a `PENDIENTE` aunque el valor dinámico baje; el progreso mostrado se queda en el último valor evaluado hasta que se reclama.
- Familia: «hermanos» son los animales vivos con el mismo padre **o** la misma madre (EspecificacionMisiones 3.9); no hace falta que estén alojados. Padre y madre del animal evaluado deben estar vivos y alojados.
- CU-15: valida instancia, contenido y progresión antes de escribir (un rechazo no cambia nada). La clave de idempotencia es la instancia (`id_partida:RECOMPENSA_RECLAMADA:id_instancia`), así que repetir la reclamación devuelve el resultado persistido; con la clave ya purgada, la instancia sustituida se rechaza como `MisionInexistente`. Todo parámetro numérico sin operación y constante se rechaza como `ProgresionInvalida`; fuera de `[mínimo, máximo]` la instancia pasa a `AGOTADA` y no se vuelve a crear.
- La aritmética de progresión es exacta a seis decimales para `SUMAR` y `RESTAR`; el parámetro `satisfaccion_minima` se redondea al centésimo y el resto al entero (medios hacia arriba).
- Animal aleatorio: sorteo con el `Azar` del contenedor sobre los tramos acumulados de `recompensas_animales_aleatorios`, persistido en `RecompensaMision.id_especie_seleccionada` con `unidad_pendiente = 1`. La especie se desbloquea con origen `RECOMPENSA` y emite el aviso informativo `ESPECIE_DESBLOQUEADA` (sin duplicar).
- Unidad gratuita: una unidad pendiente tiene prioridad sobre el estado de catálogo; la compra cuesta 0, sigue el flujo de CU-01 (ficha, aforo, satisfacción) y consume la unidad en la misma transacción. Un rechazo de la compra no la consume. Cuenta como compra para las misiones de compra de esa especie.
- Retención: las recompensas cerradas (`unidad_pendiente = 0`) se purgan tras `retencion.recompensa_mision_cerrada` ciclos; una unidad pendiente nunca se purga.
- Interfaz: la pestaña Misiones lista una misión por tipo (las agotadas no se muestran) con descripción, progreso y recompensa; la cumplida ofrece «Reclamar recompensa» con confirmación. La tienda de animales muestra la unidad concedida como «Gratis».

---

### Sprint 10 — Endurecimiento y aceptación

**Objetivo:** cumplir los requisitos no funcionales sobre la carga de referencia y cerrar la versión candidata. **Hito D.**

**Alcance**

- Retención y purga de todas las tablas de histórico, incluyendo la poda de animales inactivos (`EspecificacionBBDD.md` secciones 5.2 y 5.3; RNF-14, RES-11).
- Pruebas de rendimiento de RNF-01 a RNF-05 y RNF-10 en un dispositivo real de gama media.
- Accesibilidad de estados críticos: color, icono y texto a la vez (RNF-07), y separación visual de avisos interactivos e informativos (RNF-11).
- Adaptabilidad en pantallas de 4.7" a 6.7", solo vertical, en Android e iOS (RNF-06).
- Paridad entre plataformas: la suite completa se ejecuta en ambas y la recorrida de aceptación se repite en cada una (RNF-15).
- Auditoría de RES-06: ningún identificador técnico en la interfaz.
- Pruebas de extremo a extremo, de regresión completa y de sesiones largas.
- Revisión final de trazabilidad y limpieza de deuda técnica.

**Tests**

- T10.1 RNF-01: pintado de ficha < 200 ms, medido sobre la carga de referencia.
- T10.2 RNF-02: propagación y persistencia < 1000 ms tras mutación y tras ciclo.
- T10.3 RNF-03: transición entre cualquier par de vistas < 200 ms.
- T10.4 RNF-04: memoria ≤ 250 MB con 500 animales y 50 espacios.
- T10.5 RNF-05: CPU en reposo ≤ 15% en el hilo principal, con el intervalo de medida documentado.
- T10.6 RNF-14: simulación acelerada de miles de ciclos; el tamaño de la base de datos se estabiliza; la purga no rompe el límite de RNF-02 ni borra filas aún necesarias (genealogía, recompensas pendientes).
- T10.7 Auditoría: se conservan origen, fecha, ciclo y resultado de cada transacción, incluidos los rechazos; no se auditan consultas ni navegación (RF-47).
- T10.8 Barrido de UI: tamaños de pantalla extremos, contraste y presencia de icono, color y texto en estados críticos.
- T10.9 Recorrido de aceptación: una partida completa guiada por el catálogo de CU, con todos los CU ejecutados al menos una vez.
- T10.10 Regresión: suite completa de todos los sprints sin fallos.

**Criterio de hecho:** todos los RNF cumplen su umbral, todas las pruebas pasan y el recorrido de aceptación se completa. Versión candidata.

---

## 4. Trazabilidad

### 4.1. Casos de uso por sprint

| CU | Nombre | Sprint |
|---|---|---|
| CU-01 | Adquirir espécimen | 3 |
| CU-02 | Configurar espacio | 1 |
| CU-03 | Adquirir espacios | 1 |
| CU-04 | Reubicar espécimen | 3 |
| CU-05 | Editar ficha espécimen | 3 |
| CU-06 | Vender espécimen | 3 |
| CU-07 | Adquirir insumos | 1 |
| CU-08 | Reclamar beneficios | 5 |
| CU-09 | Aplicar tratamiento médico | 4 |
| CU-10 | Aceptar reproducción | 7 |
| CU-11 | Ejecutar ciclo de simulación | 5 |
| CU-12 | Procesar simulación en diferido | 8 |
| CU-13 | Alimentar espécimen | 4 |
| CU-14 | Dar de baja espacio vacío | 3 |
| CU-15 | Reclamar recompensa de misión | 9 |
| CU-16 | Consultar inventario y finanzas | 1 |
| CU-17 | Consultar monitor de satisfacción | 2 |
| CU-18 | Gestionar alertas y seguimiento crítico | 5 |
| CU-19 | Consultar prestigio | 6 |
| CU-20 | Consultar misiones y progreso | 9 |
| CU-21 | Consultar historial de avisos | 5 |

### 4.2. Requisitos funcionales por sprint

| Sprint | RF |
|---|---|
| 0 | RF-45, RF-46, RF-47 (infraestructura) |
| 1 | RF-02, RF-04, RF-08, RF-09, RF-10 (insumos), RF-26, RF-31, RF-50 |
| 2 | RF-15, RF-27, RF-44 |
| 3 | RF-01, RF-03, RF-05, RF-06, RF-07, RF-10 (animales), RF-25, RF-29, RF-32, RF-33, RF-34, RF-48, RF-49 |
| 4 | RF-12, RF-13, RF-30 |
| 5 | RF-11, RF-14, RF-19, RF-20, RF-24, RF-28, RF-41, RF-42 |
| 6 | RF-35, RF-36, RF-37, RF-43 |
| 7 | RF-16, RF-17, RF-18 |
| 8 | RF-21, RF-22, RF-23 |
| 9 | RF-38, RF-39, RF-40 |
| 10 | Verificación de todos |

### 4.3. Requisitos no funcionales y restricciones

| Elemento | Dónde se construye | Dónde se verifica |
|---|---|---|
| RNF-01, 03, 04, 05 | Todos los sprints (disciplina de diseño) | Sprint 10 (humo desde el 2) |
| RNF-02 | Sprints 2 y 5 | Sprints 2, 5 y 10 |
| RNF-08, 09 | Sprints 0, 5 y 8 | Sprints 5 y 8 |
| RNF-10 | Sprints 6 y 9 | Sprints 6, 9 y 10 |
| RNF-12 | Sprint 0 (mecanismo) | Cada sprint con operaciones mutantes |
| RNF-13 | Sprint 0 | Cada sprint |
| RNF-14 | Sprint 10 | Sprint 10 |
| RNF-06, 07, 11 | UI de cada sprint | Sprint 10 |
| RES-03 | Sprint 3 | Sprint 3 |
| RES-02, RES-14, RNF-15, RNF-16 | Sprint M | Sprint M y cada sprint posterior |
| RES-04 | Sprint 1 | Sprint 1 |
| RES-05 | Sprint 0 | Sprint 0 |
| RES-06 | UI de cada sprint | Cada sprint y auditoría en Sprint 10 |
| RES-07 | Sprint 6 | Sprint 6 |
| RES-08, 09 | Sprints 3 y 9 | Sprints 3 y 9 |
| RES-10 | Todos | Test transversal desde Sprint 2 |
| RES-11 | Sprints 3 y 10 | Sprints 3 y 10 |
| RES-12, 13 | Sprint 0 | Cada sprint |

---

## 5. Decisiones cerradas y riesgos

### 5.1. Decisiones cerradas

Todos los puntos que estaban abiertos quedan resueltos con estas decisiones. Si alguna se cambia, hay que actualizar también el sprint indicado.

| Id | Punto | Decisión | Sprint |
|---|---|---|---|
| D1 | Plataforma y lenguaje | **Revisada:** Android e iOS con una sola base de código en Kotlin Multiplatform, SQLite con `androidx.sqlite`, interfaz con Compose Multiplatform. Sustituye a la decisión inicial de Android nativo con Room. Detalle en `design.md`. | 0, M |
| D2 | Reloj del dispositivo en la simulación diferida | Ver 5.2. | 8 |
| D3 | Progenitor que deja de ser válido durante la ventana de reproducción | Ver 5.3. | 7 |
| D4 | Valores y fórmulas pendientes en la documentación de BBDD | Revisado: `BBDD/ValoresYFormulasPendientes.md` declara todos los elementos como `DEFINIDO` y no contiene ningún `PENDIENTE_DE_DEFINIR` ni `DEBE_CONFIRMARSE`. No queda nada por resolver. El validador del JSON del Sprint 0 sigue siendo la red de seguridad ante cualquier hueco. | 0 |
| D5 | Tamaño del Sprint 5 | Se divide de forma definitiva en 5A y 5B (ver el propio sprint), cada una con su demo y su revisión. | 5 |
| D6 | Dispositivo de referencia para RNF-01 a RNF-05 y RNF-10 | Un dispositivo físico Android de gama media (4 GB de RAM, pantalla de 6.0" a 6.5", Android 10 o superior) **y** un iPhone de gama media (iOS 15 o superior, pantalla de 6.0" a 6.5"). Cada RNF se cierra en ambos. Emulador y simulador sirven para pruebas funcionales; sus mediciones de rendimiento son orientativas y no cierran ningún RNF. | 10 |
| D7 | Servicio en segundo plano | No existe en ninguna plataforma. El ciclo corre solo con la app en primer plano y el tiempo ausente se resuelve con CU-12 (ver 5.2). No se usa `WorkManager`, ni servicios, ni tareas de fondo de iOS. | 5, 8 |

### 5.2. Regla del reloj (D2)

La simulación diferida usa el reloj de pared del dispositivo, que es la única fuente disponible con la app cerrada.

1. Se persiste una marca de última actividad (segundos desde la época Unix) en cada ciclo confirmado, al pasar a segundo plano y al cerrar la app. El remanente `R` se persiste junto con la marca, en la misma transacción.
2. CU-12 se ejecuta en cada paso a primer plano, no solo en el arranque en frío. El proceso puede seguir vivo con el temporizador detenido en segundo plano, y el resultado debe ser el mismo.
3. `E = ahora − marca`, en segundos.
4. **Salto hacia atrás** (`E < 0`): se trata como `E = 0`. No se ejecuta ningún ciclo, `R` no cambia, la marca se reescribe con el valor de `ahora` y se registra un evento de auditoría de reloj anómalo. Un salto hacia atrás seguido de otro hacia delante no regala tiempo, porque se cuenta desde la marca nueva.
5. **Salto hacia delante**: no se puede distinguir de una ausencia real y no se detecta. El daño posible queda acotado por `retencion.max_ciclos_offline = 1008` (7 días de ciclos) y por los límites de hambruna y enfermedad de RF-22 y RF-23. Se acepta como riesgo del producto, al ser un juego monojugador sin ranking.
6. **Ciclos en primer plano**: el temporizador se mide con reloj monótono (`SystemClock.elapsedRealtime`), por lo que cambiar la hora del dispositivo con la app abierta no afecta al ciclo.
7. Una marca ausente o inválida (por ejemplo, un valor mayor que `ahora` por corrupción) sigue el flujo 2.b de CU-12: no se procesan ciclos y se reinicializa la marca.

### 5.3. Regla de progenitor no válido en reproducción (D3)

CU-10 (flujo alternativo 4.b) rechaza la aceptación cuando los progenitores dejan de ser válidos, pero no indica en qué estado queda la concepción. Se define así, usando solo los estados ya existentes en `EspecificacionBBDD.md` (sección 2.3.2):

1. La concepción es válida para aceptarse solo si los dos progenitores están vivos, alojados en el mismo espacio y siguen siendo pareja recíproca. Se comprueba al abrir el detalle de la notificación y otra vez, dentro de la transacción, al confirmar.
2. Si un progenitor muere, se vende, se traslada a otro espacio o se rompe la pareja, la concepción **sigue en `CONCEBIDA`**, pero la acción de aceptar queda deshabilitada y el motivo se muestra al usuario. No se crea ninguna transición nueva.
3. Si la causa desaparece antes de que venza la ventana (por ejemplo, se devuelve al progenitor al espacio original y se restablece la pareja), la aceptación vuelve a habilitarse.
4. Al vencer los tres ciclos pasa a `EXPIRADA` y la notificación a `DESCARTADA`, como en el caso normal.
5. El umbral de satisfacción no se vuelve a comprobar dentro de la ventana, tal y como indica CU-10.

### 5.4. Riesgos que permanecen

| Riesgo | Sprint afectado | Mitigación |
|---|---|---|
| La fórmula de satisfacción concentra muchas reglas y es la base de casi todo. | 2 | Aislarla como dominio puro y cerrarla con casos calculados a mano antes de construir sobre ella. |
| El DDL escrito a mano (con `CHECK` y `WITHOUT ROWID`) podría divergir de otra definición del esquema; por eso el DDL es la única definición y no hay entidades de mapeo. | 0 | Test de esquema desde el Sprint 0 (T0.3) y exportación del esquema de Room versionada en el repositorio. Ver `design.md`, sección 5. |
| Rendimiento con 500 animales. | 2 en adelante | Carga de referencia disponible desde el Sprint 2 y prueba de humo en cada sprint, no solo al final. |
| Acumulación de deuda en las pantallas por avanzar primero en dominio. | 1 a 9 | Cada sprint entrega UI funcional, aunque sea básica; el pulido visual no es un objetivo (RES-01). |
