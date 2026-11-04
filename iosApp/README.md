# iosApp

Contenedor iOS de PandaZOO. Solo arranca la interfaz compartida (`:shared-ui`, Compose Multiplatform); no tiene lógica de negocio.

**Estado: entregado, no verificado.** Kotlin/Native para iOS solo compila en macOS. Este contenedor se escribió en Windows y nunca se ha compilado ni ejecutado. Hasta que se haga la verificación de abajo, iOS consta como «pendiente» (`IngenieriaSoftware/Migracion/TareaMigracionMultiplataforma.md`).

## Verificación en macOS (TM.5)

1. Requisitos: macOS con Xcode 15 o superior, JDK 17 o superior y XcodeGen (`brew install xcodegen`).
2. Tests compartidos sobre el simulador:
   ```
   ./gradlew :domain:iosSimulatorArm64Test :application:iosSimulatorArm64Test :data:iosSimulatorArm64Test :shared-ui:iosSimulatorArm64Test
   ```
3. Aplicación:
   ```
   cd iosApp
   xcodegen generate
   open iosApp.xcodeproj
   ```
   Elegir un simulador de iPhone y ejecutar. La fase de compilación llama a `./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode`.
4. Anotar el resultado en el informe de cierre de la tarea de migración.

## Qué hay que comprobar a mano

- La app arranca en vertical y muestra el saldo `1000.00`.
- Comprar bambú, comprar un espacio y renombrarlo funcionan igual que en Android.
- La base de datos se crea en el directorio de soporte de la aplicación y los datos persisten al reabrir.
