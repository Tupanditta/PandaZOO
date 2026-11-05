pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Kotlin/JS añade por su cuenta los repositorios de Node.js y Yarn al proyecto; con FAIL_ON_PROJECT_REPOS la
    // sincronización falla, así que se prefieren los del ajuste y se declaran aquí las dos distribuciones.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Distribuciones de Node.js"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Distribuciones de Yarn"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
    }
}
rootProject.name = "PandaZOO"
include(":domain", ":application", ":testing", ":data", ":shared-ui", ":androidApp", ":webApp")
