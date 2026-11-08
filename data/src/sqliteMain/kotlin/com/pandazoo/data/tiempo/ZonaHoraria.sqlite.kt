package com.pandazoo.data.tiempo

internal actual fun desplazamientoZonaSegundos(segundosUnix: Long): Int =
    java.util.TimeZone.getDefault().getOffset(segundosUnix * 1_000L) / 1_000
