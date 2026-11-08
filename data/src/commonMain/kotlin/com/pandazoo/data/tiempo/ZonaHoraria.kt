package com.pandazoo.data.tiempo

/** Segundos que la hora local del dispositivo se adelanta a UTC en el instante [segundosUnix] (negativo al oeste de Greenwich). */
internal expect fun desplazamientoZonaSegundos(segundosUnix: Long): Int
