import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "com.pandazoo.data"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/esquema"))
            dependencies {
                api(project(":application"))
                api(libs.sqlite)
                implementation(libs.sqlite.bundled)
                implementation(libs.coroutines.core)
            }
        }
        commonTest.dependencies {
            implementation(project(":testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
            implementation(libs.coroutines.test)
        }
    }
}

tasks.withType<Test> { useJUnitPlatform() }

// El DDL (schema/esquema.sql) es la única definición del esquema. Se convierte en código Kotlin común para que
// las dos plataformas lo lean sin API de ficheros ni de recursos de ninguna de ellas (RES-14).
val generarEsquema by tasks.registering {
    val entrada = layout.projectDirectory.file("schema/esquema.sql")
    val salida = layout.buildDirectory.dir("generated/esquema")
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
        val sentencias = entrada.asFile.readText(Charsets.UTF_8)
            .split(Regex("(?m)^;;\\s*$"))
            .map { b -> b.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").trim() }
            .filter { it.isNotEmpty() }
        val destino = salida.get().asFile.resolve("com/pandazoo/data/db/EsquemaGenerado.kt")
        destino.parentFile.mkdirs()
        destino.writeText(
            buildString {
                appendLine("// Generado por la tarea generarEsquema a partir de data/schema/esquema.sql. No editar.")
                appendLine("package com.pandazoo.data.db")
                appendLine()
                appendLine("internal object EsquemaGenerado {")
                appendLine("    val sentencias: List<String> = listOf(")
                sentencias.forEach { appendLine("        ${literal(it)},") }
                appendLine("    )")
                appendLine("}")
            },
            Charsets.UTF_8,
        )
    }
}
tasks.matching { it.name.contains("Kotlin", ignoreCase = true) && it.name != "generarEsquema" }
    .configureEach { dependsOn(generarEsquema) }
