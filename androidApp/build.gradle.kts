import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pandazoo.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pandazoo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // Solo móviles reales: sin x86/x86_64 (emuladores), que añadían ~2,4 MB de bibliotecas nativas.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // La firma sale de ~/.pandazoo/firma.properties (fuera del repositorio) o de la variable PANDAZOO_FIRMA.
    val firma = Properties().apply {
        val ruta = System.getenv("PANDAZOO_FIRMA") ?: "${System.getProperty("user.home")}/.pandazoo/firma.properties"
        val archivo = file(ruta)
        if (archivo.exists()) archivo.inputStream().use { load(it) }
    }
    signingConfigs {
        if (firma.containsKey("storeFile")) {
            create("publicacion") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("publicacion")?.let { signingConfig = it }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared-ui"))
    implementation(libs.activity.compose)
}
