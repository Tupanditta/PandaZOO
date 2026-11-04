package com.pandazoo.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.pandazoo.data.db.BaseDatos
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/** Contenedor iOS: solo decide dónde vive la base de datos; toda la interfaz es código compartido. */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        PandaZooRaiz(abrirBaseDatos = { BaseDatos.enArchivo(rutaBaseDatos()) })
    }

@OptIn(ExperimentalForeignApi::class)
private fun rutaBaseDatos(): String {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(base, withIntermediateDirectories = true, attributes = null, error = null)
    return "$base/pandazoo.db"
}
