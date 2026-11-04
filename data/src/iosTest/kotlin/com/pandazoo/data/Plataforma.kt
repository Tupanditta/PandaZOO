package com.pandazoo.data

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

actual fun rutaTemporal(nombre: String): String = NSTemporaryDirectory() + NSUUID().UUIDString + "-" + nombre

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun borrarArchivoDeBd(ruta: String) {
    listOf("", "-wal", "-shm", "-journal").forEach { NSFileManager.defaultManager.removeItemAtPath(ruta + it, null) }
}
