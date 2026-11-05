package com.pandazoo.data.depuracion

import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.modelo.IdPartida

/** Contenido de la carga de referencia generada. */
data class ResumenCarga(val espacios: Int, val animales: Int)

/**
 * Generador de datos de prueba. La compra de animales llega en el Sprint 3: hasta entonces, y para medir
 * rendimiento después, los animales solo se crean desde aquí. Se usa en tests y en el modo de depuración; la
 * aplicación de usuario no lo expone (Sprint 2, "Generador de la carga de referencia").
 *
 * Escribe directamente en la base de datos, sin pasar por casos de uso: no cobra, no audita y no recalcula la
 * satisfacción. Tras generar, quien lo usa invoca el recálculo.
 */
class DatosDePrueba(private val bd: BaseDatos, private val ids: GeneradorId) {
    fun espacio(idPartida: IdPartida, idHabitat: String, nombre: String, capacidad: Int = 10): String {
        val id = ids.nuevo()
        bd.escribir(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max, estado) VALUES (?, ?, ?, ?, ?, 'ACTIVO')",
            id, idPartida, idHabitat, nombre, capacidad,
        )
        return id
    }

    /** Alta de un animal vivo con la edad máxima media de su especie y satisfacción persistida `0` (sin calcular). */
    fun animal(
        idPartida: IdPartida,
        idEspacio: String,
        idEspecie: String,
        nombre: String,
        alimentacion: Int = 100,
        enfermo: Boolean = false,
        idPadre: String? = null,
        idMadre: String? = null,
    ): String {
        val id = ids.nuevo()
        val edadMedia = bd.uno("SELECT edad_media_anios FROM Especie WHERE id_especie = ?", idEspecie) { it.entero(0) }
            ?: error("Especie inexistente")
        bd.escribir(
            "INSERT INTO Animal (id_animal, id_partida, id_especie, id_espacio, nombre, edad_anios, edad_maxima_anios, " +
                "edad_maxima_meses, edad_maxima_dias, nivel_alimentacion, estado_salud, id_padre, id_madre, satisfaccion_actual) " +
                "VALUES (?, ?, ?, ?, ?, 1, ?, 0, 0, ?, ?, ?, ?, 0)",
            id, idPartida, idEspecie, idEspacio, nombre, edadMedia, alimentacion, if (enfermo) "ENFERMO" else "SANO", idPadre, idMadre,
        )
        return id
    }

    fun parejas(idPartida: IdPartida, a: String, b: String) {
        bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", b, idPartida, a)
        bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", a, idPartida, b)
    }

    fun amigos(idPartida: IdPartida, a: String, b: String) {
        bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", b, idPartida, a)
        bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", a, idPartida, b)
    }

    /** `true` si la partida ya tiene animales: la carga no se mezcla con datos existentes. */
    fun hayAnimales(idPartida: IdPartida): Boolean =
        bd.uno("SELECT 1 FROM Animal WHERE id_partida = ? LIMIT 1", idPartida) { true } ?: false

    /**
     * Carga de referencia (`sprints.md`, sección 1): 50 espacios de capacidad 10 repartidos por todos los
     * hábitats con 500 animales vivos. Cada espacio tiene una pareja con dos crías, una amistad y cuatro animales
     * más de otra especie, con alimentación y salud variadas. Es determinista. Devuelve `null` si ya había animales.
     */
    fun cargaReferencia(idPartida: IdPartida): ResumenCarga? {
        if (hayAnimales(idPartida)) return null
        return bd.ejecutar {
            val especies = bd.leer("SELECT id_especie FROM Especie ORDER BY id_especie") { it.texto(0) }
            val habitats = bd.leer("SELECT id_habitat FROM Habitat WHERE id_partida = ? ORDER BY codigo", idPartida) { it.texto(0) }
            check(especies.isNotEmpty() && habitats.isNotEmpty()) { "Falta el catálogo" }
            var n = 0
            fun siguiente(): Int = ++n

            repeat(ESPACIOS) { i ->
                val espacio = espacio(idPartida, habitats[i % habitats.size], "Espacio de prueba ${i + 1}")
                val a = especies[(i * 3) % especies.size]
                val b = especies[(i * 3 + 1) % especies.size]
                val c = especies[(i * 3 + 2) % especies.size]

                fun alta(especie: String, padre: String? = null, madre: String? = null): String {
                    val k = siguiente()
                    return animal(idPartida, espacio, especie, "Ejemplar $k", alimentacion = ((k * 7) % 11) * 10, enfermo = k % 13 == 0, idPadre = padre, idMadre = madre)
                }

                val padre = alta(a)
                val madre = alta(a)
                val amigo1 = alta(b)
                val amigo2 = alta(b)
                alta(a, padre, madre)
                alta(a, padre, madre)
                repeat(4) { alta(c) }
                parejas(idPartida, padre, madre)
                amigos(idPartida, amigo1, amigo2)
            }
            ResumenCarga(ESPACIOS, n)
        }
    }

    companion object {
        const val ESPACIOS = 50
    }
}
