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

    /** Alta de un animal vivo del nivel indicado y satisfacción persistida `0` (sin calcular). */
    fun animal(
        idPartida: IdPartida,
        idEspacio: String,
        idEspecie: String,
        nombre: String,
        alimentacion: Int = 100,
        enfermo: Boolean = false,
        idPadre: String? = null,
        idMadre: String? = null,
        nivel: Int = 1,
        ciclosNivel: Int = 0,
    ): String {
        val id = ids.nuevo()
        bd.escribir(
            "INSERT INTO Animal (id_animal, id_partida, id_especie, id_espacio, nombre, nivel, ciclos_nivel, " +
                "nivel_alimentacion, estado_salud, id_padre, id_madre, satisfaccion_actual) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
            id, idPartida, idEspecie, idEspacio, nombre, nivel, ciclosNivel, alimentacion, if (enfermo) "ENFERMO" else "SANO",
            idPadre, idMadre,
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
                    return animal(idPartida, espacio, especie, "Ejemplar $k", alimentacion = ((k * 7) % 11) * 10, enfermo = k % 13 == 0, idPadre = padre, idMadre = madre, nivel = 1 + (k * 3) % 12)
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

    /**
     * Borra la carga de referencia sin tocar lo creado por el usuario: solo animales llamados "Ejemplar N" y
     * espacios llamados "Espacio de prueba N" que, tras borrar esos animales, queden vacíos. Los animales de
     * usuario que descendían de uno borrado conservan su ficha sin padre/madre. Devuelve lo eliminado.
     */
    fun eliminarCargaReferencia(idPartida: IdPartida): ResumenCarga = bd.ejecutar {
        val animalesPrueba = "SELECT id_animal FROM Animal WHERE id_partida = ? AND nombre GLOB 'Ejemplar [0-9]*'"
        val animales = bd.leer("SELECT COUNT(*) FROM Animal WHERE id_partida = ? AND nombre GLOB 'Ejemplar [0-9]*'", idPartida) { it.entero(0) }.firstOrNull() ?: 0
        bd.escribir("DELETE FROM RasgoPersonalidad WHERE id_animal IN ($animalesPrueba)", idPartida)
        bd.escribir("DELETE FROM HabilidadSocial WHERE id_animal IN ($animalesPrueba)", idPartida)
        bd.escribir("DELETE FROM ReproduccionCiclo WHERE id_partida = ? AND (id_animal_a IN ($animalesPrueba) OR id_animal_b IN ($animalesPrueba))", idPartida, idPartida, idPartida)
        bd.escribir("DELETE FROM Notificacion WHERE id_partida = ? AND id_entidad IN ($animalesPrueba)", idPartida, idPartida)
        for (columna in listOf("id_pareja", "id_mejor_amigo", "id_padre", "id_madre")) {
            bd.escribir("UPDATE Animal SET $columna = NULL WHERE id_partida = ? AND $columna IN ($animalesPrueba)", idPartida, idPartida)
        }
        bd.escribir("DELETE FROM Animal WHERE id_partida = ? AND nombre GLOB 'Ejemplar [0-9]*'", idPartida)

        val vacios = "SELECT id_espacio FROM Espacio WHERE id_partida = ? AND nombre GLOB 'Espacio de prueba [0-9]*' " +
            "AND NOT EXISTS (SELECT 1 FROM Animal a WHERE a.id_partida = Espacio.id_partida AND a.id_espacio = Espacio.id_espacio)"
        val espacios = bd.leer("SELECT COUNT(*) FROM ($vacios)", idPartida) { it.entero(0) }.firstOrNull() ?: 0
        bd.escribir("DELETE FROM MetricaSatisfaccion WHERE id_partida = ? AND id_espacio IN ($vacios)", idPartida, idPartida)
        bd.escribir("DELETE FROM Notificacion WHERE id_partida = ? AND id_entidad IN ($vacios)", idPartida, idPartida)
        bd.escribir("DELETE FROM Espacio WHERE id_partida = ? AND id_espacio IN ($vacios)", idPartida, idPartida)
        ResumenCarga(espacios, animales)
    }

    companion object {
        const val ESPACIOS = 50
    }
}
