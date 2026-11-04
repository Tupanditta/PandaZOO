package com.pandazoo.domain

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

/**
 * RES-14 y TM.4: el código común no puede usar API de Android, iOS ni de la JVM. Se comprueba sobre el código
 * fuente de `commonMain` de los cuatro módulos compartidos.
 */
class IndependenciaDePlataformaTest {
    private val modulos = listOf("domain", "application", "data", "shared-ui")
    private val prohibido = Regex("""^\s*import\s+(java|javax|android|platform)\.""")
    private val calificado = Regex("""\b(java|javax|android)\.(util|math|io|nio|time|text|lang|net)\.""")

    private fun infracciones(texto: String): List<String> =
        texto.lines().withIndex()
            .filter { (_, l) -> prohibido.containsMatchIn(l) || (!l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") && calificado.containsMatchIn(l)) }
            .map { (i, l) -> "línea ${i + 1}: ${l.trim()}" }

    @Test
    fun `TM_4 el codigo comun de los modulos compartidos no importa APIs de plataforma`() {
        val raiz = File("..").canonicalFile
        val violaciones = mutableListOf<String>()
        var analizados = 0
        modulos.forEach { m ->
            File(raiz, "$m/src/commonMain").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                analizados++
                infracciones(f.readText(Charsets.UTF_8)).forEach { violaciones += "${f.relativeTo(raiz).path} $it" }
            }
        }
        (analizados > 20) shouldBe true
        violaciones.shouldBeEmpty()
    }

    @Test
    fun `TM_4 el detector reconoce las APIs prohibidas`() {
        infracciones("import java.math.BigDecimal").size shouldBe 1
        infracciones("import android.content.Context").size shouldBe 1
        infracciones("import platform.Foundation.NSLock").size shouldBe 1
        infracciones("val x = java.util.UUID.randomUUID()").size shouldBe 1
        infracciones("import kotlin.uuid.Uuid\nimport androidx.sqlite.SQLiteConnection").size shouldBe 0
        infracciones("// usa java.util.UUID en otra plataforma").size shouldBe 0
    }
}
