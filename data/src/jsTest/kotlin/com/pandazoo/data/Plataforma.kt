package com.pandazoo.data

import com.pandazoo.data.db.AlmacenWeb
import kotlin.random.Random

actual fun rutaTemporal(nombre: String): String = "prueba-${Random.nextInt()}-$nombre"

actual fun borrarArchivoDeBd(ruta: String) {
    AlmacenWeb.cache.remove(ruta)
}

actual val modoDiarioEsperado: String = "delete"
