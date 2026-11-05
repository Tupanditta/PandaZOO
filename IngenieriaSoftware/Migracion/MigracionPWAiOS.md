# Migración de iOS nativo a PWA

**Decisión:** iOS deja de ser una aplicación nativa (Kotlin/Native + Xcode, sin Mac ni App Store disponibles) y pasa a ser una **aplicación web progresiva (PWA)**. Se abre en Safari, se instala en la pantalla de inicio y se aloja gratis como sitio estático (GitHub Pages). Android no cambia.

**Alcance:** solo lo que necesita iOS. Ninguna regla de negocio cambia. Cubre los sprints 0 a 5.

## 1. Qué se hizo

| Pieza | Cambio |
|---|---|
| Targets | `iosArm64` e `iosSimulatorArm64` se sustituyen por `js` (Kotlin/JS) en `:domain`, `:application`, `:testing`, `:data` y `:shared-ui`. Se eligió JS y no Wasm porque Kotlin/Wasm exige WasmGC (Safari 18.2 o superior) |
| `:webApp` (nuevo) | Contenedor PWA: `index.html`, `manifest.webmanifest`, `sw.js` (service worker), iconos y `Main.kt` |
| Motor SQL | `androidx.sqlite` no existe para JavaScript. `:data` define `ConexionSql`/`SentenciaSql`; Android y JVM la implementan con el SQLite empaquetado (`sqliteMain`) y la web con **sql.js 1.14.1** (`data/vendor/sqljs/`, SQLite en WebAssembly). El esquema y todo el SQL son los mismos |
| Errores SQL | Los tests ya no usan `androidx.sqlite.SQLiteException` sino `ErrorSql`, igual en todas las plataformas |
| Persistencia | `BaseDatosWeb` carga la partida desde IndexedDB al abrir y copia la base entera tras cada confirmación (agrupadas en 200 ms; al instante si la página está oculta). También se guarda lo escrito fuera de transacción |
| Primer y segundo plano | Al ocultar o cerrar la pestaña se detiene el temporizador del ciclo y se guarda el remanente (RF-24). `PandaZooRaiz` recibe `registrarVisibilidad`, y `abrirBaseDatos` pasó a `suspend` |
| Interfaz | `✓` pasa a `✔`: la fuente de reserva de la web no tiene U+2713 y se veía un cuadrado vacío |
| Gradle | `settings.gradle.kts` usa `PREFER_SETTINGS` y declara los repositorios de Node.js y Yarn, porque Kotlin/JS los añade al proyecto y `FAIL_ON_PROJECT_REPOS` lo impedía |
| Documentos | `RES-02`, `design.md`, `sprints.md` e `Interfaz/EspecificacionInterfaz.md` actualizados |

Se borraron `iosApp/`, `data/src/iosMain`, `data/src/iosTest` y `shared-ui/src/iosMain`. Siguen en el historial de git.

## 2. Verificación por sprint

Los sprints 0 a 5 comparten código, así que cada uno se prueba en la web con sus mismos tests, ahora sobre Kotlin/JS con sql.js en Node:

| Sprint | Tests | Resultado en web |
|---|---|---|
| 0 Cimientos | `Sprint0Test`, `DineroTest`, `RedondeoTest` | Pasan. T0.3 espera `delete` y no `wal` como modo de diario, porque sql.js no admite WAL |
| 1 Economía y espacios | `Sprint1Test` | Pasan |
| 2 Satisfacción | `Sprint2Test` | Pasan, incluido el humo de rendimiento T2.9 |
| 3 Especímenes | `Sprint3Test`, `AnimalTest` | Pasan |
| 4 Cuidado | `Sprint4Test`, `CuidadoTest` (en `AnimalTest.kt`) | Pasan |
| 5 Ciclo y avisos | `Sprint5Test`, `CicloTest` | Pasan, incluido el humo de rendimiento T5.15 |
| Web (nuevo) | `PersistenciaWebTest` | Guardado tras confirmar y agrupado, reapertura desde los bytes guardados, claves foráneas tras guardar, escritura suelta, base en memoria sin respaldo |

`ValidadorConfiguracionTest`, `SatisfaccionTest` e `IndependenciaDePlataformaTest` siguen siendo solo de JVM (usan APIs de JVM). El validador y la fórmula de satisfacción sí se ejercitan en la web: cada `Entorno` carga y valida el JSON, y `Sprint2Test` recalcula la satisfacción.

Comando: `./gradlew check :androidApp:assembleDebug :webApp:jsBrowserDistribution`.

**Prueba de extremo a extremo** (Edge sin pantalla, viewport de iPhone, no automatizada en el repositorio): la app arranca con saldo `2000.00`; se compró bambú (saldo `1950.00`, `130 kg`); se cerró el navegador y, al reabrir con el mismo perfil, el estado seguía igual; tras 27 s abierta y cerrar la pestaña el remanente del temporizador quedó en 573 s.

## 3. Limitaciones conocidas

- **Tests de interfaz solo en JVM.** Los 22 tests de Compose UI se ejecutan en JVM sobre el mismo código común. En un navegador sin pantalla fallan con `ReferenceError` porque Skiko no llega a cargarse, así que la tarea `jsBrowserTest` de `:shared-ui` está desactivada.
- **No se probó en un iPhone ni en Safari.** Solo en Edge (Chromium). Pendiente de una prueba manual en el dispositivo.
- **Borrado de datos por Safari.** Safari puede borrar los datos de un sitio web que no se usa durante 7 días. Una PWA **instalada en la pantalla de inicio** queda exenta. La app pide almacenamiento persistente, pero conviene instalarla.
- **Una sola pestaña.** Dos pestañas abiertas a la vez sobrescriben la partida una de otra. No hay protección.
- **Sin ciclos con la app cerrada.** Igual que en Android hasta el Sprint 8 (CU-12).

## 4. Cómo instalarla en un iPhone

1. Abrir la dirección de la PWA en Safari.
2. Compartir, y después «Añadir a pantalla de inicio».

## 5. Publicar en GitHub Pages

1. `./gradlew :webApp:jsBrowserDistribution`.
2. Publicar el contenido de `webApp/build/dist/js/productionExecutable` como sitio estático. Todas las rutas son relativas, así que funciona en `https://usuario.github.io/repositorio/`.
3. Al publicar una versión nueva, el service worker usa primero la red, de modo que se recibe al recargar. Sin conexión sirve la última copia guardada.
