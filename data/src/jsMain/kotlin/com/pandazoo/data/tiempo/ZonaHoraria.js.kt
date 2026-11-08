package com.pandazoo.data.tiempo

import kotlin.js.Date

// `getTimezoneOffset` devuelve los minutos que UTC se adelanta a la hora local (positivo al oeste de Greenwich).
internal actual fun desplazamientoZonaSegundos(segundosUnix: Long): Int =
    -(Date(segundosUnix.toDouble() * 1_000.0).getTimezoneOffset() * 60).toInt()
