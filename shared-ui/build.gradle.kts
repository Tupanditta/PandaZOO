import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "com.pandazoo.ui"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
    }
    js {
        browser {
            // Los tests de interfaz (Compose UI Test) se ejecutan en JVM sobre el mismo código común. En un navegador
            // headless Skiko no llega a cargarse en esta versión (ReferenceError en SkikoComposeUiTest), así que la
            // tarea de test del navegador se desactiva; la web real se verifica con una prueba de extremo a extremo.
            testTask { enabled = false }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.cmp.lifecycle.runtime)
            implementation(libs.cmp.navigation)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "com.pandazoo.ui.generated.resources"
    generateResClass = always
}

tasks.withType<Test> { useJUnitPlatform() }

// La copia del JSON de configuración como recurso compartido debe ser idéntica a la del repositorio.
val verificarConfiguracion = tasks.register("verificarConfiguracion") {
    val original = rootProject.layout.projectDirectory.file("IngenieriaSoftware/Otros/valores_y_pesos.json")
    val copia = layout.projectDirectory.file("src/commonMain/composeResources/files/valores_y_pesos.json")
    inputs.files(original, copia)
    doLast {
        check(original.asFile.readBytes().contentEquals(copia.asFile.readBytes())) {
            "El recurso valores_y_pesos.json de shared-ui difiere de ${original.asFile.path}"
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(verificarConfiguracion) }

// Esa comprobación solo tiene sentido si se ejecutan los tests de interfaz en el navegador (desactivados arriba).
tasks.matching { it.name == "checkComposeUiTestConfigurationForJs" }.configureEach { enabled = false }
