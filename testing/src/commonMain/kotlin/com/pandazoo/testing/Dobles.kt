package com.pandazoo.testing

import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj

/** Reloj controlado por el test. */
class RelojFalso(var segundos: Long = 1_700_000_000L) : Reloj {
    override fun ahoraSegundos(): Long = segundos

    fun avanzar(delta: Long) {
        segundos += delta
    }
}

/** Azar con secuencia fija; cuando se agota repite el último valor. */
class AzarFalso(vararg valores: Double) : Azar {
    private val secuencia = valores.toList().ifEmpty { listOf(0.0) }
    private var posicion = 0

    override fun siguienteDouble(): Double = secuencia[minOf(posicion++, secuencia.lastIndex)]
}

/** Identificadores deterministas: `prefijo-1`, `prefijo-2`... */
class GeneradorIdSecuencial(private val prefijo: String = "id") : GeneradorId {
    private var n = 0

    override fun nuevo(): String = "$prefijo-${++n}"
}
