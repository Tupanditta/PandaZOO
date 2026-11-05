package com.pandazoo.data

import java.io.File

actual fun rutaTemporal(nombre: String): String {
    val carpeta = File(System.getProperty("java.io.tmpdir"), "pandazoo-tests-${System.nanoTime()}")
    carpeta.mkdirs()
    return File(carpeta, nombre).path
}

actual fun borrarArchivoDeBd(ruta: String) {
    listOf("", "-wal", "-shm", "-journal").forEach { File(ruta + it).delete() }
    File(ruta).parentFile?.delete()
}

actual val modoDiarioEsperado: String = "wal"
