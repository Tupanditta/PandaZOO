# Diseño técnico de implementación

Este documento fija el lenguaje, la arquitectura, las tecnologías y las herramientas que usa el agente de IA para implementar la aplicación para **Android e iOS con una sola base de código** (iOS como PWA), y las reglas con las que trabaja. La migración desde la primera versión solo Android está definida en `Migracion/TareaMigracionMultiplataforma.md` y la de iOS nativo a PWA en `Migracion/MigracionPWAiOS.md`. Se complementa con `sprints.md` (qué se construye y cuándo) y con la especificación funcional (qué debe hacer).

Si una decisión de este documento choca con un requisito, gana el requisito (RF, RNF, RES) y se corrige este documento.

---

## 1. Decisiones de plataforma

| Aspecto | Decisión | Motivo |
|---|---|---|
| Plataformas | Android e iOS, solo teléfonos, orientación vertical | RES-02, RNF-06 |
| Código compartido | Kotlin Multiplatform: dominio, aplicación, datos y UI se escriben una vez | RES-14, RNF-15 |
| Lenguaje | Kotlin en todos los módulos y tests; la PWA añade solo HTML, un manifiesto y un service worker de unas 30 líneas | Una sola base de código |
| Persistencia | SQLite. Android y JVM: `androidx.sqlite` con el SQLite empaquetado (`sqlite-bundled`). Web (iOS): sql.js, el mismo SQLite compilado a WebAssembly, con la base entera copiada a IndexedDB tras cada confirmación. Ambos motores se ocultan tras la interfaz `ConexionSql` de `:data`, y `androidx.sqlite` no existe para JavaScript | `EspecificacionBBDD.md`, sección 2; RNF-16 |
| Interfaz | Compose Multiplatform con Material 3, solo componentes técnicos: tablas, listas, métricas y formularios | RES-01; sin renderizado 2D/3D ni animaciones espaciales. Identidad visual (paleta natural, tema claro y oscuro, fichas) definida en `Interfaz/EspecificacionInterfaz.md` |
| Configuración del juego | `valores_y_pesos.json`, empaquetado como recurso compartido de `:shared-ui` y leído con `kotlinx.serialization` | Es la fuente única de valores (`ValoresYFormulasPendientes.md`) |
| Android | SDK mínimo API 26; SDK de compilación y objetivo: el último estable disponible, fijado en el catálogo de versiones | Cubre el rango de 4.7" a 6.7" |
| iOS | PWA: Safari de iOS 15 o superior, target Kotlin/JS (`js`), alojada como sitio estático (GitHub Pages) e instalada en la pantalla de inicio. Se eligió JS y no Wasm porque Kotlin/Wasm exige WasmGC (Safari 18.2) | Gratuita, sin App Store ni Mac, cubre los iPhone de 4.7" a 6.7" en uso |
| Versiones de librerías | Estables vigentes, con antigüedad de al menos dos semanas, todas fijadas en `gradle/libs.versions.toml` | Reproducibilidad. Ninguna dependencia usa rangos ni `latest` |

Fuera de alcance por decisión: tablet, web de escritorio, orientación horizontal, servidor, red, cuentas de usuario, publicidad y analítica. La aplicación no necesita permiso de Internet y no se declara en el manifiesto de Android.

---

## 2. Arquitectura

### 2.1. Capas y módulos

Proyecto Gradle multi-módulo en Kotlin Multiplatform. La dirección de las dependencias es siempre hacia el dominio.

```text
:androidApp / iosApp   Contenedores de plataforma: arranque, ciclo de vida, manifiesto o Info.plist
   │
   ▼
:shared-ui    (KMP)  Compose Multiplatform: pantallas, ViewModels, navegación, cableado de dependencias
   │
   ▼
:data         (KMP)  SQLite (androidx.sqlite), repositorios, carga del JSON, reloj y azar reales
   │
   ▼
:application  (KMP)  Casos de uso transaccionales CU-01 a CU-21, puertos (interfaces)
   │
   ▼
:domain       (KMP)  Entidades, reglas y fórmulas puras, sin API de Android, iOS ni JVM
```

- `:domain`, `:application`, `:data` y `:shared-ui` son módulos Kotlin Multiplatform con los targets `android`, `iosArm64` e `iosSimulatorArm64`. Los tres primeros añaden además `jvm`, que sirve para ejecutar los tests en el ordenador en milisegundos, sin emulador ni simulador. Esto es lo que hace viable el desarrollo por TDD.
- `:domain` y `:application` no usan ninguna API de plataforma: ni `java.*`, ni `android.*`, ni `platform.*`. Una tarea de Gradle lo comprueba (RES-14).
- `:application` define los puertos (`Reloj`, `Azar`, `Transaccion`, repositorios…). `:data` los implementa. `:shared-ui` los conecta.
- Lo específico de plataforma se limita a `expect/actual` en `:data` (ruta de la base de datos y candado de la conexión) y a los contenedores `:androidApp` e `iosApp`.
- Un módulo de test compartido `:testing` (KMP) contiene los fixtures: constructores de partidas, la carga de referencia de 500 animales y 50 espacios, relojes y azares falsos.

### 2.2. Contenido de cada módulo

| Módulo | Paquetes principales |
|---|---|
| `:domain` | `modelo` (Animal, Espacio, Habitat, Especie, Partida…), `satisfaccion` (`S_i`, medias), `prestigio`, `ciclo` (etapas de simulación), `economia` (redondeo, costes), `reproduccion`, `mision` |
| `:application` | `caso` (un paquete por CU), `puerto`, `error` (errores de negocio tipados), `evento` (eventos de dominio) |
| `:data` | `db` (conexión, DDL, `expect/actual`), `repositorio`, `config` (JSON), `tiempo`, `purga` |
| `:shared-ui` | `ui/<pantalla>`, `navegacion`, `di`, `temporizador` |
| `:androidApp` | `Application`, `Activity`, manifiesto |
| `:webApp` | Contenedor web (PWA) de iOS: `index.html`, manifiesto, service worker, iconos y arranque que aloja la UI compartida |

### 2.3. Reglas de diseño obligatorias

1. **Reactivo, sin polling (RES-10, RF-44).** El recálculo de satisfacción, prestigio y misiones se lanza solo desde el resultado de un caso de uso que mutó estado o desde el cierre de un ciclo. Está prohibido recalcular en `Composable`, en `ViewModel` al abrir pantalla o en cualquier bucle. La UI solo lee datos ya persistidos mediante `Flow`, que el módulo de datos emite tras cada transacción confirmada.
2. **Un caso de uso mutante = una transacción SQLite** (RF-45). El caso de uso valida, muta, recalcula, audita y devuelve el resultado dentro de una sola transacción. Un error de negocio devuelve un resultado `Rechazado` con motivo tipado, registra el intento en auditoría y no deja cambios en el dominio (RES-08, RF-47).
3. **Idempotencia (RNF-12).** Toda operación mutante recibe una clave de idempotencia según la tabla de `EspecificacionBBDD.md`, sección 3. Repetir la clave devuelve el resultado persistido.
4. **Tiempo y azar inyectados.** El dominio nunca llama a `Clock.System`, `Random` ni a ninguna fuente de hora o azar del sistema. Usa los puertos `Reloj` y `Azar`. `Azar.siguienteDouble()` devuelve un valor en `[0,1)`; los tests lo sustituyen por una secuencia fija.
5. **Dinero y umbrales sin coma flotante binaria.** Los importes usan el tipo `Dinero`, un entero de céntimos (`Long`) con redondeo medio hacia arriba (RF-10, RF-15). Kotlin común no tiene `BigDecimal`, y un entero da el mismo resultado en las dos plataformas (RNF-15). Las probabilidades y satisfacciones usan `Double`; los umbrales de prestigio, aritmética decimal de escala fija propia, según la sección 4.3.
6. **Sin identificadores en la UI (RES-06).** Los modelos de presentación (`...UiModel`) no tienen campos de clave. La UI nunca recibe un `id`; las acciones se resuelven con una referencia opaca de navegación que no se muestra.
7. **Errores como valores.** Los fallos de negocio no se lanzan como excepciones; son un tipo sellado `Resultado<Exito, MotivoRechazo>`. Las excepciones se reservan para fallos técnicos y provocan el rollback.
8. **Jerarquía de entidades de solo lectura.** El código de aplicación no ofrece ninguna operación de inserción, edición ni borrado de hábitats (RES-05); el repositorio no expone esos métodos.

### 2.4. Flujo de un caso de uso mutante

```text
UI (evento de usuario)
  → ViewModel → CasoDeUso.ejecutar(comando, claveIdempotencia)
       → Transaccion.ejecutar {
            1. Comprobar idempotencia (devolver resultado previo si existe)
            2. Validar precondiciones (saldo, aforo, estado, ficha…)
            3. Aplicar cambios (dominio puro)
            4. Recalcular métricas y prestigio de las entidades afectadas
            5. Registrar avisos y auditoría
         }
       → Resultado (Exito | Rechazado(motivo))
  ← ViewModel actualiza el estado de pantalla
  ← La UI observa los Flows del módulo de datos y se repinta sola
```

### 2.5. Motor de ciclo y temporizador

- El ciclo es una función del dominio, `EjecutarCiclo(estado, numeroCiclo, reloj, azar)`, que ejecuta las etapas en el orden de RF-14. Cada etapa es una función separada y testeable.
- `:shared-ui/temporizador` mantiene un único temporizador de corrutina ligado al remanente `R`. Se mide con el reloj monótono común (`TimeSource.Monotonic`). Al vencer, invoca CU-11 y reprograma 600 s. No hay otro temporizador ni sondeo.
- Con la app en segundo plano el temporizador se cancela. Al volver a primer plano se ejecuta CU-12 (regla de reloj de `sprints.md`, sección 5.2). La señal de primer y segundo plano la aporta cada contenedor de plataforma a través de una interfaz común. No hay servicios, `WorkManager` ni tareas de fondo de iOS (decisión D7).
- El modo de depuración que adelanta ciclos existe solo en las compilaciones de depuración y llama al mismo caso de uso CU-11.

---

## 3. Tecnologías y librerías

Todas con versión fija en `libs.versions.toml`. Se usa una librería solo si resuelve una necesidad concreta de la lista; no se añaden otras sin justificarlo en el sprint.

| Necesidad | Tecnología |
|---|---|
| Construcción | Gradle con Kotlin DSL, Gradle Wrapper versionado, catálogo de versiones |
| Multiplataforma | Plugin Kotlin Multiplatform; plugin de biblioteca KMP de Android; targets `android`, `js` (web/PWA de iOS) y `jvm` (este último solo para tests y herramientas) |
| Android Gradle Plugin | La versión estable compatible con el SDK objetivo |
| JDK de compilación | El JDK incluido con Android Studio (ver sección 5); el bytecode se genera para Java 17 |
| Asincronía | Kotlin Coroutines y `Flow` |
| Inyección de dependencias | Por constructor, con un contenedor manual en `:shared-ui`. No se usa Hilt porque es solo de Android; el dominio y la aplicación no dependen de ningún framework |
| Base de datos | `androidx.sqlite` (`SQLiteDriver`, `SQLiteConnection`) con `sqlite-bundled`; el DDL versionado es la única definición del esquema |
| JSON | `kotlinx.serialization` |
| Navegación | Navigation Compose multiplataforma |
| Estado de UI | `ViewModel` multiplataforma + `StateFlow` |
| Fechas y horas | `kotlin.time` (`Clock`, `Instant`, `TimeSource.Monotonic`); no se usa `java.time` |
| Identificadores | `kotlin.uuid.Uuid` |
| Recursos compartidos | Recursos de Compose Multiplatform (`composeResources/`) |

### 3.1. Tests

| Tipo | Herramienta |
|---|---|
| Unitarios y de casos de uso | `kotlin.test` en `commonTest`: el mismo código se ejecuta en JVM, Android e iOS (RNF-15) |
| Aserciones | Kotest assertions (multiplataforma) |
| Dobles de test | Fakes escritos a mano para `Reloj`, `Azar` y repositorios en memoria; no se usa MockK porque no es multiplataforma |
| Pruebas de propiedades | Kotest property testing, para las invariantes (saldos, alimentación múltiplo de 10, `S_i` en `[0,1]`) |
| Base de datos | SQLite real, con el DDL de producción y el mismo motor empaquetado; en memoria o en fichero temporal. Sin Robolectric |
| Flows | Turbine |
| UI | Pruebas de UI de Compose Multiplatform sobre el código compartido, ejecutadas en el objetivo `jvm` de `:shared-ui`; pruebas instrumentadas de cada contenedor para el arranque |
| Tests de `:data` en Android | No hay tests de host de Android: el SQLite empaquetado carga una biblioteca nativa que solo existe en el dispositivo. El código común se prueba en JVM y en iOS, y en Android mediante pruebas instrumentadas |
| Rendimiento | Tests cronometrados sobre la carga de referencia y Macrobenchmark en Android; Instruments en iOS |

### 3.2. Calidad estática

| Necesidad | Herramienta |
|---|---|
| Formato | ktlint o `spotless` con ktlint |
| Análisis estático | detekt |
| Lint de Android | Android Lint, con advertencias tratadas como errores en los módulos propios |
| Independencia de plataforma | Tarea de Gradle que falla si `commonMain` de `:domain`, `:application`, `:data` o `:shared-ui` importa `java.*`, `javax.*`, `android.*` o `platform.*` (RES-14) |
| Cobertura | Kover (multiplataforma) sobre `:domain` y `:application` |
| Verificación de dependencias | `./gradlew dependencies` revisado en cada alta de librería |

---

## 4. Datos y persistencia

### 4.1. Base de datos

- El **DDL de `EspecificacionBBDD.md`, sección 2, es la fuente de verdad** del esquema, incluidos `CHECK`, claves compuestas por `id_partida`, `WITHOUT ROWID` e índices. Se mantiene como script SQL versionado en `:data` y se aplica al crear la base de datos.
- No hay entidades de mapeo ni segunda definición del esquema: los repositorios ejecutan SQL sobre la conexión. Un **test de esquema** (T0.3) comprueba que cada `CHECK` y cada clave foránea rechazan datos inválidos.
- El acceso a la base es una única conexión de `androidx.sqlite` con el SQLite empaquetado, protegida por un candado reentrante (`expect/actual`) y abierta con `PRAGMA foreign_keys = ON`. Las transacciones son `BEGIN IMMEDIATE` y mantienen el candado de principio a fin.
- La versión de la base de datos se guarda en `PRAGMA user_version`. Cualquier cambio de esquema posterior al Sprint 0 incluye migración y su test.

### 4.2. Configuración

- `valores_y_pesos.json` se copia a `:shared-ui/src/commonMain/composeResources/files/` como recurso compartido por ambas plataformas. Una tarea de Gradle comprueba que la copia del repositorio y la del recurso son idénticas.
- El validador del Sprint 0 rechaza configuración incompleta o incoherente (pesos que no suman 1.00, probabilidades que no suman 100, falta de precio, afinidades fuera de `0..1`, umbrales de prestigio inválidos, etc.).
- La configuración se carga una vez al crear la partida y se persiste en las tablas de catálogo; después la partida no depende del JSON.

### 4.3. Numérica

| Magnitud | Tipo | Regla |
|---|---|---|
| Dinero | `Dinero`: `Long` en céntimos | Redondeo medio hacia arriba al centésimo al construirlo desde un valor decimal; el valor redondeado es el que se persiste y usa. En la base de datos se guarda como `REAL` con dos decimales |
| Satisfacción, afinidad, probabilidades | `Double` | Sin redondeo interno; rango `0..1`. Los tests de frontera usan valores exactamente representables o `BigDecimal` en el caso concreto |
| Porcentaje mostrado | `Int` | `HALF_UP` de `valor * 100` |
| Umbral de animales de prestigio | Decimal propio de escala fija (`Long` en millonésimas) sin redondear para encadenar niveles | Comparación con el entero redondeado medio hacia arriba; se define en el Sprint 6 |
| Nivel del animal | `Int` | `>= 1`, `+1` por ciclo sano, acotado por el `nivel_maximo` del rango; el multiplicador de beneficio `M_nivel` usa `Double` sin redondeo interno |
| Instantes | `Long` en segundos desde la época Unix | Se obtienen del puerto `Reloj`; no se usa `java.time` |
| Remanente y tiempo transcurrido | `Long` en segundos | |

Cualquier comparación con `>=` o `<` estricto de RF-16 y RF-14 se prueba en su frontera exacta.

---

## 5. Herramientas del agente

### 5.1. Entorno de la máquina (comprobado)

| Elemento | Estado |
|---|---|
| Sistema | Windows 11 con shell bash (Git Bash) |
| Android Studio | Instalado en `C:\Program Files\Android\Android Studio` |
| macOS y Xcode | **No disponibles, y ya no hacen falta:** iOS se entrega como PWA, que se compila y prueba en Windows. El contenedor nativo `iosApp/` se borró (ver `Migracion/MigracionPWAiOS.md`) |
| Android SDK | Presente en `%LOCALAPPDATA%\Android\Sdk` (build-tools, platforms, platform-tools, emulator) |
| Java por defecto en `PATH` | **Java 1.8, insuficiente.** El agente usa el JDK de Android Studio (`jbr`) |
| `ANDROID_HOME` | No definido |
| `adb`, `gradle` | No están en `PATH` |
| Git | Disponible |

Preparación obligatoria en el Sprint 0, antes de escribir código:

1. Apuntar `JAVA_HOME` al JDK que incluye Android Studio (`...\Android Studio\jbr`) y configurarlo en `gradle.properties` con `org.gradle.java.home`, sin tocar el Java 1.8 del sistema.
2. Definir `ANDROID_HOME` (o `sdk.dir` en `local.properties`, que no se versiona) con la ruta del SDK.
3. Añadir `platform-tools` al `PATH` de la sesión para usar `adb`.
4. Generar el Gradle Wrapper y usar siempre `./gradlew`; no se instala Gradle global.
5. Crear un dispositivo virtual (AVD) con imagen de sistema reciente para pruebas instrumentadas y de humo.
6. Aceptar licencias del SDK (`sdkmanager --licenses`).

Si alguno de estos pasos requiere permisos o instalación manual, el agente se detiene y lo indica al usuario en lugar de improvisar.

### 5.2. Herramientas que usa el agente

| Tarea | Herramienta |
|---|---|
| Compilar Android | `./gradlew :androidApp:assembleDebug` |
| Compilar la PWA de iOS | `./gradlew :webApp:jsBrowserDistribution` (salida en `webApp/build/dist/js/productionExecutable`) |
| Tests de dominio, aplicación y datos | `./gradlew :domain:jvmTest :application:jvmTest :data:jvmTest` |
| Tests de la web (Node + sql.js) | `./gradlew :domain:jsNodeTest :application:jsNodeTest :data:jsNodeTest` |
| Tests instrumentados | `./gradlew :androidApp:connectedDebugAndroidTest` (emulador o dispositivo) |
| Lint y formato | `./gradlew ktlintCheck detekt lintDebug` |
| Verificación completa antes de cerrar un sprint | `./gradlew check` |
| Instalar y arrancar | `adb install`, `adb shell am start` |
| Capturas de pantalla para la demo | `adb exec-out screencap -p` |
| Registros | `adb logcat` filtrado por la etiqueta de la aplicación |
| Cambios y versiones | Git |
| Lectura y búsqueda de código | Herramientas de lectura y búsqueda del propio agente, no comandos de shell equivalentes |

### 5.3. Cómo trabaja el agente

1. **Un sprint, una rama.** Rama `sprint-N-nombre` a partir de `main`. Un commit por unidad de trabajo coherente (un CU, una regla, una migración), con mensaje en español que cite los identificadores (`CU-03`, `RF-02`). Se fusiona a `main` solo con la definición de hecho de `sprints.md` cumplida.
2. **TDD.** Para cada regla, primero el test que falla y luego el código mínimo que lo pasa. Los IDs de test de `sprints.md` (T1.1, T2.3…) se usan como nombre o comentario del test para mantener la trazabilidad.
3. **De dentro hacia fuera.** Dominio, luego aplicación, luego datos, luego UI. No se escribe UI de un CU antes de que su caso de uso esté probado.
4. **Fuente de verdad para cada duda.** En este orden: requisitos y restricciones, especificación del CU, `EspecificacionBBDD.md`, `Misiones/EspecificacionMisiones.md`, JSON. Si dos fuentes se contradicen o falta una regla, el agente no inventa: se detiene, lo anota y consulta al usuario.
5. **Alcance cerrado.** Solo se implementa lo del sprint en curso. Los puntos de extensión de sprints futuros se dejan como interfaces vacías, no como código a medias.
6. **Evidencia antes de afirmar.** Un sprint no se declara terminado sin haber ejecutado `./gradlew check` y mostrado su resultado. Si algo falla o se omite, se informa tal cual.
7. **No tocar la especificación sin permiso.** Los documentos de `IngenieriaSoftware/` son de solo lectura para el agente, salvo `sprints.md` (estado y trazabilidad) y este documento, y solo para reflejar decisiones ya acordadas.
8. **Cierre de sprint.** Informe breve: CU y RF cerrados, tests añadidos y su resultado, deuda técnica, riesgos nuevos y propuesta del siguiente paso.

### 5.4. Agentes auxiliares

El agente principal puede delegar, sin cambiar las reglas anteriores:

- Búsqueda amplia en la especificación o en el código, para no llenar su contexto.
- Revisión de código del sprint antes del cierre, con foco en reglas de negocio, atomicidad e idempotencia.
- Tareas independientes y sin estado compartido en paralelo (por ejemplo, tests de módulos distintos).

Un agente auxiliar nunca modifica la especificación ni fusiona ramas.

---

## 6. Estructura del repositorio

```text
PandaZOO/
├── IngenieriaSoftware/        Especificación (solo lectura salvo sprints.md y design.md)
├── gradle/libs.versions.toml  Versiones fijas
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew, gradlew.bat, gradle/wrapper/
├── domain/                    KMP: modelo y reglas puras (commonMain, commonTest, jvmTest)
├── application/               KMP: puertos y casos de uso
├── data/                      KMP: SQLite, repositorios, esquema.sql (commonMain/resources/db)
├── shared-ui/                 KMP + Compose Multiplatform: pantallas y ViewModels
│   └── src/commonMain/composeResources/files/   valores_y_pesos.json
├── androidApp/                Android application: arranque y manifiesto
├── webApp/                    Kotlin/JS: contenedor PWA (index.html, manifiesto, service worker, iconos)
├── testing/                   KMP: fixtures y dobles compartidos
├── config/detekt/             Reglas de análisis estático
└── .gitignore                 Excluye local.properties, build/, .gradle/, *.iml
```

---

## 7. Verificación continua

Sin servidor de integración continua: la puerta de calidad es local y obligatoria.

| Momento | Qué se ejecuta |
|---|---|
| Antes de cada commit | Tests del módulo afectado, formato y detekt |
| Antes de cerrar una unidad de trabajo | `./gradlew :domain:jvmTest :application:jvmTest :data:jvmTest` |
| Cierre de sprint | `./gradlew check` completo, más los tests instrumentados y la demo en emulador o dispositivo. En macOS, además, los tests de iOS y la demo en simulador; mientras no se haya ejecutado, el cierre de iOS consta como pendiente |
| Sprint 10 | Pruebas de rendimiento en el dispositivo de referencia (decisión D6 de `sprints.md`) |

Un test que falla bloquea el cierre. No se desactiva, no se marca como ignorado y no se relaja su aserción para hacerlo pasar sin acuerdo del usuario.

Umbrales de calidad:

- Cobertura de líneas en `:domain` y `:application` de al menos el 90%, y del 100% de las reglas con fórmula (satisfacción, beneficios, prestigio, ciclo, reproducción, misiones).
- Cero advertencias de detekt y de Android Lint sin justificar.
- Cada CU tiene al menos un test por cada flujo alternativo y situación de error de su especificación.

---

## 8. Decisiones cerradas y lo que queda por confirmar

Cerrado en este documento: lenguaje, plataformas (Android e iOS con código compartido), arquitectura por módulos, librerías por necesidad, política de tiempo y azar, tipos numéricos, estrategia de tests, herramientas y flujo de trabajo del agente.

Requiere acción del usuario:

- Disponer de un Mac con Xcode para compilar, probar y firmar la versión de iOS. Sin él, el código de iOS queda entregado pero sin verificar.
- Crear un AVD (o conectar un móvil Android) para la demo en Android.
- Indicar si se dispone de un dispositivo físico de gama media de cada plataforma para el Sprint 10 (D6); si no, el cierre de los RNF de rendimiento queda pendiente de ellos.
