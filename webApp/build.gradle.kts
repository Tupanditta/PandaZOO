plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Contenedor web (PWA) para iPhone: solo arranca la interfaz compartida (`:shared-ui`) y decide dónde vive la base
// de datos (IndexedDB). Sustituye al contenedor nativo `iosApp`.
kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "pandazoo.js" }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared-ui"))
            implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.get()}")
            implementation("org.jetbrains.compose.ui:ui:${libs.versions.compose.get()}")
        }
    }
}

// sql.js (SQLite en WebAssembly) se sirve junto a la aplicación; la página lo carga con una etiqueta <script>.
tasks.named<Copy>("jsProcessResources") {
    from(rootProject.layout.projectDirectory.dir("data/vendor/sqljs")) {
        include("sql-wasm-browser.js", "sql-wasm-browser.wasm")
    }
}
