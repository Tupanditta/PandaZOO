package com.pandazoo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.ui.PandaZooRaiz

/** Contenedor Android: solo decide dónde vive la base de datos; toda la interfaz es código compartido. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PandaZooRaiz(abrirBaseDatos = {
                val archivo = getDatabasePath("pandazoo.db")
                archivo.parentFile?.mkdirs()
                BaseDatos.enArchivo(archivo.path)
            })
        }
    }
}
