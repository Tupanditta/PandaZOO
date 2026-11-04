package com.pandazoo.data.config

import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.config.ValidadorConfiguracion

/**
 * Interpreta y valida `valores_y_pesos.json`. El texto lo aporta el contenedor de cada plataforma
 * (recurso compartido de la interfaz), de modo que este módulo no depende de ninguna API de ficheros.
 */
object CargadorConfiguracion {
    /** Lanza [com.pandazoo.domain.config.ConfiguracionInvalidaException] si el contenido no es válido. */
    fun desdeTexto(texto: String): ConfiguracionJuego {
        val cfg = ConfiguracionJuego.desdeJson(texto)
        ValidadorConfiguracion.validarOExcepcion(cfg)
        return cfg
    }
}
