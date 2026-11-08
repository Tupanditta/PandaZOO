package com.pandazoo.data.tiempo

import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Reloj de pared real. Es el único lugar del código que consulta la hora del sistema. */
@OptIn(ExperimentalTime::class)
class RelojSistema : Reloj {
    override fun ahoraSegundos(): Long = Clock.System.now().epochSeconds

    override fun desplazamientoLocalSegundos(): Int = desplazamientoZonaSegundos(ahoraSegundos())
}

class AzarSistema(private val random: Random = Random.Default) : Azar {
    override fun siguienteDouble(): Double = random.nextDouble()
}

@OptIn(ExperimentalUuidApi::class)
class GeneradorUuid : GeneradorId {
    override fun nuevo(): String = Uuid.random().toString()
}
