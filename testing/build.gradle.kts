import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "com.pandazoo.testing"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/configuracion"))
            dependencies {
                api(project(":application"))
            }
        }
    }
}

// El JSON de configuración se incrusta como código Kotlin común para que los tests de cualquier plataforma lo
// lean sin API de ficheros. La fuente única sigue siendo IngenieriaSoftware/Otros/valores_y_pesos.json.
val generarConfiguracionDePrueba = tasks.register("generarConfiguracionDePrueba") {
    val entrada = rootProject.layout.projectDirectory.file("IngenieriaSoftware/Otros/valores_y_pesos.json")
    val salida = layout.buildDirectory.dir("generated/configuracion")
    inputs.file(entrada)
    outputs.dir(salida)
    doLast {
        fun literal(s: String) = buildString {
            append('"')
            for (c in s) {
                when {
                    c == '\\' -> append("\\\\")
                    c == '"' -> append("\\\"")
                    c == '$' -> append("\\$")
                    c == '\n' -> append("\\n")
                    c == '\r' -> {}
                    c.code < 32 || c.code > 126 -> append("\\u" + c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }
        val partes = entrada.asFile.readText(Charsets.UTF_8).chunked(15_000)
        val destino = salida.get().asFile.resolve("com/pandazoo/testing/ConfiguracionDePrueba.kt")
        destino.parentFile.mkdirs()
        destino.writeText(
            buildString {
                appendLine("// Generado por la tarea generarConfiguracionDePrueba. No editar.")
                appendLine("package com.pandazoo.testing")
                appendLine()
                appendLine("/** Contenido de valores_y_pesos.json, disponible en todas las plataformas. */")
                appendLine("object ConfiguracionDePrueba {")
                appendLine("    private val partes: List<String> = listOf(")
                partes.forEach { appendLine("        ${literal(it)},") }
                appendLine("    )")
                appendLine()
                appendLine("    val texto: String get() = partes.joinToString(\"\")")
                appendLine("}")
            },
            Charsets.UTF_8,
        )
    }
}
tasks.matching { it.name.contains("Kotlin", ignoreCase = true) }.configureEach { dependsOn(generarConfiguracionDePrueba) }
