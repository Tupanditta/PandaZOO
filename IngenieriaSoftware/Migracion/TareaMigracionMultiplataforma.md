# Tarea M: migración a código compartido Android + iOS

**Origen:** RES-02 (Android e iOS), RES-14 (código de negocio independiente de plataforma), RNF-06, RNF-15 y RNF-16.
**Estado de partida:** Sprint 0 y Sprint 1 implementados solo para Android (`:domain`, `:application`, `:data` y `:app` con Android, SQLite del sistema, Robolectric y `java.math.BigDecimal`).
**Objetivo:** que dominio, aplicación, datos y UI se escriban una sola vez en Kotlin Multiplatform y se ejecuten en Android e iOS con los mismos resultados, sin cambiar ninguna regla de negocio ni ningún comportamiento ya probado.

## Principios

1. **Un paso, un módulo.** Cada paso toca un solo módulo y deja el repositorio compilable y con todos los tests en verde. No se avanza con tests rojos.
2. **De dentro hacia fuera**, como en `design.md`: `:domain`, `:application`, `:testing`, `:data`, `:shared-ui` y, por último, los contenedores de plataforma.
3. **Los tests no se relajan.** Se migran de ubicación y de herramienta, pero cada aserción de los sprints 0 y 1 se conserva. El número de tests de cada módulo no puede bajar.
4. **Sin cambios funcionales.** Si una regla cambia por la migración (por ejemplo, el tipo del dinero), el resultado observable debe ser idéntico y se prueba.
5. **Lo específico de plataforma, aislado.** Solo se admite `expect/actual` o interfaz en: ruta y apertura de la base de datos, candado de acceso a la conexión, reloj monótono, ciclo de vida de primer plano y arranque de la aplicación.

## Pasos

| Paso | Módulo | Qué se hace | Criterio de hecho |
|---|---|---|---|
| M0 | Especificación | RES-02, RES-14, RNF-06, RNF-15, RNF-16, `design.md`, `sprints.md` (Sprint M), trazabilidad. | Documentos coherentes entre sí. |
| M1 | Raíz del proyecto | Catálogo de versiones con Kotlin Multiplatform, Compose Multiplatform, `androidx.sqlite` con SQLite empaquetado y plugin KMP de Android. `settings.gradle.kts` con los módulos nuevos. Opción `kotlin.native.ignoreDisabledTargets`. | `./gradlew help` configura todos los módulos. |
| M2 | `:domain` | Pasa a KMP (`android`, `jvm`, `iosArm64`, `iosSimulatorArm64`). El dinero deja `java.math.BigDecimal` y pasa a un tipo propio `Dinero` en céntimos (`Long`) con redondeo medio hacia arriba. Redondeos de porcentaje y entero con aritmética propia. Tests a `commonTest`; los que leen el JSON, a `jvmTest`. | Mismos 17 tests en verde y resultados idénticos; ninguna API `java.*` en `commonMain`. |
| M3 | `:application` | Pasa a KMP. Puertos y casos de uso usan `Dinero`. | Compila en todos los targets declarados. |
| M4 | `:testing` | Pasa a KMP: relojes, azar y generador de ids falsos, usables desde cualquier plataforma. | Compila y lo consumen `:data` y `:shared-ui`. |
| M5 | `:data` | Sustituye la API de SQLite de Android por `androidx.sqlite` (`SQLiteDriver`, `SQLiteConnection`) con SQLite empaquetado (RNF-16). Mismo `esquema.sql`. Candado, ruta de la base de datos, reloj y UUID por `expect/actual` o por la biblioteca estándar. Se elimina Robolectric: los tests corren en JVM puro. | Los 51 tests de datos en verde en JVM sobre el mismo código; `esquema.sql` sin cambios. |
| M6 | `:shared-ui` (nuevo, sustituye a `:app`) | Compose Multiplatform: ViewModels, pantallas, navegación y textos compartidos. Sin `android.net.Uri`. El JSON de configuración pasa a recursos compartidos. | Los 4 tests de UI en verde; ningún `import android.*` en `commonMain`. |
| M7 | `:androidApp` | Contenedor Android: `Application`, `Activity`, manifiesto (vertical, sin Internet), instalación del APK. | `assembleDebug` correcto; la app arranca igual que antes. |
| M8 | `iosApp/` | Contenedor iOS: fuentes Swift de arranque, definición del proyecto Xcode y framework `SharedUi` generado por Gradle. Orientación vertical. | Fuentes y configuración entregadas. Compilarlo requiere macOS con Xcode (ver Limitaciones). |
| M9 | Cierre | `./gradlew check`, retirada de código y dependencias obsoletas (`:app`, Robolectric, `BigDecimal`), actualización de `sprints.md` y de la trazabilidad. | Informe de cierre con tests por módulo. |

## Mapa de módulos tras la migración

```text
:androidApp  ─┐                                  (Android, application)
iosApp (Xcode)┤→ :shared-ui (CMP) → :data → :application → :domain
              │                          ↘ :testing (solo tests)
```

| Módulo | Targets | Reutilización |
|---|---|---|
| `:domain` | android, jvm, iosArm64, iosSimulatorArm64 | 100 % común |
| `:application` | ídem | 100 % común |
| `:data` | ídem | Común salvo `expect/actual` de base de datos |
| `:shared-ui` | android, iosArm64, iosSimulatorArm64 (más jvm solo si hace falta para tests) | 100 % común salvo arranque |

## Qué no cambia

- El DDL (`esquema.sql`) y el modelo de datos.
- Los casos de uso, los motivos de rechazo, la auditoría y la idempotencia.
- Los textos de la interfaz y la regla RES-06.
- La política de reloj y azar inyectados.

## Riesgos y limitaciones

| Riesgo | Mitigación |
|---|---|
| El código de iOS no se puede compilar ni ejecutar en Windows. | Todo lo común se verifica en JVM y Android. La parte iOS se entrega como fuentes y configuración, y se declara como **no verificada** hasta ejecutar `./gradlew :shared-ui:iosSimulatorArm64Test` y abrir el proyecto en un Mac. |
| `BigDecimal` no existe en Kotlin común. | `Dinero` en céntimos (`Long`). Se añaden tests de equivalencia con los valores que ya probaba el Sprint 1 (por ejemplo `0.005` a `0.01` y `10 kg × 0.50`). |
| Una sola conexión SQLite compartida entre hilos. | Candado reentrante por `expect/actual`; las transacciones lo mantienen de principio a fin. |
| Versiones nuevas de AGP, Compose Multiplatform y `androidx.sqlite` con API que puede variar. | Versiones fijadas en el catálogo; cada paso se compila antes de seguir. |
| El plugin KMP de Android no admite el módulo de aplicación. | `:androidApp` es un módulo Android aparte; los módulos compartidos son bibliotecas KMP. |

## Criterio de hecho de la tarea

- Todos los tests de los sprints 0 y 1 pasan sobre el código compartido (como mínimo 72 tests; no se pueden perder).
- `./gradlew check assembleDebug` correcto en Windows.
- Ningún `import android.*`, `java.*` ni `javax.*` en `commonMain` de `:domain`, `:application`, `:data` y `:shared-ui`, comprobado por una tarea de Gradle.
- `iosApp/` entregado con instrucciones y marcado como pendiente de verificación en macOS.

## Informe de ejecución (Windows, sin macOS)

| Paso | Estado | Evidencia |
|---|---|---|
| M0 Especificación | Hecho | RES-02, RES-14, RNF-06, RNF-15, RNF-16, `design.md`, `sprints.md` (Sprint M) y trazabilidad actualizados. |
| M1 Build | Hecho | Catálogo de versiones, plugins KMP y Compose Multiplatform, módulos nuevos en `settings.gradle.kts`. |
| M2 `:domain` | Hecho | `Dinero` en céntimos y redondeo propio. 23 tests en JVM y 8 en Android (host); los 17 originales se conservan. |
| M3 `:application` | Hecho | `Dinero` en puertos y casos de uso; compila en JVM, Android e iOS. |
| M4 `:testing` | Hecho | Dobles comunes y configuración de prueba generada como código común. |
| M5 `:data` | Hecho | `androidx.sqlite` con SQLite empaquetado; `schema/esquema.sql` convertido en código común; 52 tests en JVM (los 51 originales más la reapertura de fichero). |
| M6 `:shared-ui` | Hecho | Compose Multiplatform; 4 tests de UI sobre el código compartido (objetivo `jvm`). |
| M7 `:androidApp` | Hecho | `assembleDebug` correcto. |
| M8 `iosApp/` | Entregado, **no verificado** | Fuentes Swift, `Info.plist`, `project.yml` y README. |
| M9 Cierre | Hecho | `./gradlew check :androidApp:assembleDebug` correcto; `:app` y Robolectric retirados. |

**Criterios cumplidos:** 79 tests en JVM sobre código compartido (más de los 72 de partida); `check` y `assembleDebug` correctos; tarea de independencia de plataforma como test (`IndependenciaDePlataformaTest`, TM.4).

**Lo que sí se ha comprobado de iOS en Windows:** el compilador de Kotlin/Native compila a klib todo el código de iOS de los cuatro módulos y sus tests (`compileKotlinIosArm64` e `iosSimulatorArm64`). Eso ya detectó y permitió corregir errores reales (nombres de tests con comas, `@JvmInline`, reflexión de JVM en tests comunes).

**Lo que no se ha podido comprobar:** el enlazado del framework (`linkDebugFramework…` se omite fuera de macOS), la ejecución de los tests en el simulador (TM.5) y la compilación y ejecución de `iosApp/`. El estado de iOS sigue siendo «entregado, no verificado».

**Desviación documentada:** los tests de `:data` no se ejecutan como tests de host de Android porque el SQLite empaquetado carga una biblioteca nativa de Android que no existe en la JVM del ordenador. El mismo código se prueba en JVM (con el SQLite empaquetado de escritorio) y en Android se valida arrancando la aplicación y, más adelante, con pruebas instrumentadas.
