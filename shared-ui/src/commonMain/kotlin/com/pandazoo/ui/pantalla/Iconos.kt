package com.pandazoo.ui.pantalla

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Iconos vectoriales propios (rejilla 24x24, trazados al estilo Material); se tiñen con el color del contenido. */
internal object Iconos {
    private fun icono(nombre: String, trazado: String) =
        ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(trazado).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

    val Tienda = icono("tienda", "M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 -0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2L7.42,15c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63h7.45c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49c0.08,-0.14 0.12,-0.31 0.12,-0.48 0,-0.55 -0.45,-1 -1,-1L5.21,4l-0.94,-2L1,2zM17,18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    val Satisfaccion = icono("satisfaccion", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    // Moneda: disco con anillo interior hueco y centro macizo.
    val Saldo = icono("saldo", "M12,2a10,10 0 1,1 0,20a10,10 0 1,1 0,-20zM12,5a7,7 0 1,0 0,14a7,7 0 1,0 0,-14zM12,9a3,3 0 1,1 0,6a3,3 0 1,1 0,-6z")
    val Bambu = icono("bambu", "M10,2h4v5h-4zM10,8h4v6h-4zM10,15h4v7h-4zM14,9c3,0 5,-1 6,-3 -3,0 -5,1 -6,3zM10,13c-3,0 -5,-1 -6,-3 3,0 5,1 6,3z")
    val InsumosMedicos = icono("insumos", "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM17,13h-4v4h-2v-4H7v-2h4V7h2v4h4V13z")
    val Guia = icono("guia", "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5 -1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5 0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5 1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05 0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z")
    val Prestigio = icono("prestigio", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    val Habitats = icono("habitats", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Misiones = icono("misiones", "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z")
    val Avisos = icono("avisos", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
    val Volver = icono("volver", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Mas = icono("mas", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Menos = icono("menos", "M19,13H5v-2h14v2z")
    val Check = icono("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Cerrar = icono("cerrar", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Papelera = icono("papelera", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    // Huella: cuatro dedos (círculos) y la almohadilla.
    val Pata = icono("pata", "M4.5,7a2.5,2.5 0 1,1 0,5a2.5,2.5 0 1,1 0,-5zM9,3a2.5,2.5 0 1,1 0,5a2.5,2.5 0 1,1 0,-5zM15,3a2.5,2.5 0 1,1 0,5a2.5,2.5 0 1,1 0,-5zM19.5,7a2.5,2.5 0 1,1 0,5a2.5,2.5 0 1,1 0,-5zM12,12c-3,0 -6,3.5 -6,6.5 0,2 1.5,3 3,3 1.2,0 2,-0.5 3,-0.5s1.8,0.5 3,0.5c1.5,0 3,-1 3,-3 0,-3 -3,-6.5 -6,-6.5z")
}
