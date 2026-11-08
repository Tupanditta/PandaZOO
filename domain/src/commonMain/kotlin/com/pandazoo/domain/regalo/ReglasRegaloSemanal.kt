package com.pandazoo.domain.regalo

/**
 * Reglas puras de los regalos semanales: qué día de la semana es y cuál es el identificador del regalo de ese día.
 * El reloj aporta los segundos Unix y el desplazamiento de la zona horaria del dispositivo; la regla no lee nada.
 */
object ReglasRegaloSemanal {
    private const val SEGUNDOS_DIA = 86_400L

    /** Días enteros desde 1970-01-01 en la hora local. */
    fun diasLocales(segundosUnix: Long, desplazamientoSegundos: Int): Long =
        divPiso(segundosUnix + desplazamientoSegundos, SEGUNDOS_DIA)

    /** Día de la semana ISO: 1 = lunes … 7 = domingo. El 1 de enero de 1970 fue jueves. */
    fun diaSemana(dias: Long): Int = (((dias + 3) % 7 + 7) % 7).toInt() + 1

    /** Fecha civil `aaaa-mm-dd` de un número de días desde 1970-01-01 (calendario gregoriano). */
    fun fecha(dias: Long): String {
        val z = dias + 719_468
        val era = divPiso(z, 146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = doy - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        val anio = yoe + era * 400 + if (mes <= 2) 1 else 0
        return "${anio.toString().padStart(4, '0')}-${mes.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}"
    }

    /** Identificador único del regalo de un día: no se concede dos veces el mismo. Ejemplo: `lunes:2026-11-09`. */
    fun idRegalo(idConfiguracion: String, fecha: String): String = "$idConfiguracion:$fecha"

    private fun divPiso(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }
}
