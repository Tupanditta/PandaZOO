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
        namespace = "com.pandazoo.application"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest { }
    }
    js { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions)
        }
    }
}

tasks.withType<Test> { useJUnitPlatform() }
