package com.pandazoo.domain.economia

/**
 * Aritmética decimal exacta sobre la representación decimal más corta de un `Double`, sin `BigDecimal`
 * (que no existe en Kotlin común). Garantiza el mismo resultado en todas las plataformas (RNF-15).
 *
 * Ejemplo: `0.005` se lee como el decimal exacto `0.005` (no como `0.005000000000000000104…`) y por tanto
 * se redondea a `0.01`, igual que hacía `BigDecimal.valueOf(0.005)`.
 */
object Decimales {
    /** Número decimal exacto: `signo * digitos * 10^-escala`. */
    class Exacto internal constructor(val negativo: Boolean, val digitos: String, val escala: Int)

    /** Lee un `Double` finito como su decimal más corto (`Double.toString`), con o sin notación científica. */
    fun leer(valor: Double): Exacto {
        require(valor.isFinite()) { "Valor no finito: $valor" }
        var texto = valor.toString()
        val negativo = texto.startsWith("-")
        if (negativo) texto = texto.substring(1)
        var exponente = 0
        val posE = texto.indexOfFirst { it == 'E' || it == 'e' }
        if (posE >= 0) {
            exponente = texto.substring(posE + 1).toInt()
            texto = texto.substring(0, posE)
        }
        val punto = texto.indexOf('.')
        val enteros = if (punto >= 0) texto.substring(0, punto) else texto
        val fraccion = if (punto >= 0) texto.substring(punto + 1) else ""
        var digitos = (enteros + fraccion).trimStart('0')
        var escala = fraccion.length - exponente
        // Normaliza ceros finales de la parte decimal para que la escala sea la mínima.
        while (escala > 0 && digitos.endsWith("0")) {
            digitos = digitos.dropLast(1)
            escala--
        }
        if (digitos.isEmpty()) {
            digitos = "0"
            escala = 0
        }
        return Exacto(negativo, digitos, escala)
    }

    /** Número de decimales significativos del valor (`0.5` tiene 1, `10.0` tiene 0). */
    fun decimales(valor: Double): Int = maxOf(0, leer(valor).escala)

    /** Redondea a `decimales` cifras con los medios alejándose de cero (equivale a `HALF_UP`). Devuelve el entero escalado. */
    fun redondearEscalado(valor: Double, decimales: Int): Long {
        val d = leer(valor)
        // Cifras a conservar tras mover la coma: digitos * 10^-(escala) * 10^decimales.
        val desplazamiento = decimales - d.escala
        val magnitud: Long = if (desplazamiento >= 0) {
            d.digitos.toLong() * potencia10(desplazamiento)
        } else {
            val cortar = -desplazamiento
            if (cortar > d.digitos.length) {
                0L
            } else {
                val parte = d.digitos.substring(0, d.digitos.length - cortar)
                val base = if (parte.isEmpty()) 0L else parte.toLong()
                val siguiente = d.digitos[d.digitos.length - cortar] - '0'
                if (siguiente >= 5) base + 1 else base
            }
        }
        return if (d.negativo) -magnitud else magnitud
    }

    fun potencia10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
