package com.pandazoo.data

import com.pandazoo.application.puerto.ZooRepositorio
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T0.7 (RES-05): la capa de aplicación no ofrece escritura sobre hábitats. Usa reflexión, solo disponible en JVM. */
class HabitatsSoloLecturaTest {
    @Test
    fun `T0_7 la capa de aplicacion no ofrece operaciones de escritura sobre habitats`() {
        val prohibidos = Regex("(?i)(crear|insertar|editar|actualizar|reconfigurar|eliminar|borrar|dar)(Habitat|Habitats)")
        ZooRepositorio::class.java.methods.map { it.name }.filter { prohibidos.containsMatchIn(it) } shouldBe emptyList()
        // La reconfiguración y la creación del repositorio operan solo sobre espacios.
        ZooRepositorio::class.java.methods.map { it.name }.filter { it.contains("Habitat", ignoreCase = true) }
            .toSet() shouldBe setOf("habitat", "habitats")
    }
}
