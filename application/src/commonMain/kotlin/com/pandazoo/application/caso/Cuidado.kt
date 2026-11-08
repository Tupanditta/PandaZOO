package com.pandazoo.application.caso

import com.pandazoo.application.error.FaltanteInsumo
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AnimalResumen
import com.pandazoo.application.puerto.CuidadoRepositorio
import com.pandazoo.application.puerto.InventarioRepositorio
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.animal.ReglasCuidado
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso

private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)

private val BAMBU = Recurso(TipoRecurso.ALIMENTO, "bambu")

// ---------------------------------------------------------------------------------------------------- CU-13 individual

data class ComandoAlimentarAnimal(val idPartida: IdPartida, val idAnimal: String, val idSolicitud: String)

data class AnimalAlimentado(val idAnimal: String, val nivel: Int, val kilos: Int, val bambuRestante: Int)

/**
 * CU-13 Alimentar espécimen, acción individual (RF-12, RF-30): suma 10 puntos con tope en el 100 % y consume la
 * cantidad entera de bambú de su especie. Atómico: sin bambú o sin configuración no cambia nada.
 */
class AlimentarAnimal(
    private val ejecutor: EjecutorOperacion,
    private val animales: AnimalRepositorio,
    private val cuidado: CuidadoRepositorio,
    private val inventario: InventarioRepositorio,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoAlimentarAnimal): Resultado<AnimalAlimentado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "ALIMENTACION_INDIVIDUAL",
            entidad = "ANIMAL",
            origen = "CU-13",
            idSolicitud = c.idSolicitud,
            huella = "animal=${c.idAnimal}",
            codificar = { "${it.idAnimal}|${it.nivel}|${it.kilos}|${it.bambuRestante}" },
            decodificar = {
                val (id, nivel, kilos, restante) = it.split("|")
                AnimalAlimentado(id, nivel.toInt(), kilos.toInt(), restante.toInt())
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { alimentar(c) }
    }

    private fun alimentar(c: ComandoAlimentarAnimal): Resultado<AnimalAlimentado> {
        val animal = animales.animal(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)
        val espacio = animal.idEspacio
        if (!animal.vivo || espacio == null) return rechazo(MotivoRechazo.AnimalNoVivo)
        if (animal.alimentacion >= ReglasCuidado.ALIMENTACION_MAXIMA) return rechazo(MotivoRechazo.AlimentacionCompleta)
        val kilos = cuidado.kilosPorIncremento(animal.idEspecie)?.let { ReglasCuidado.kilosPorRacion(it, animal.nivel) }
            ?: return rechazo(MotivoRechazo.RacionNoConfigurada)
        val disponible = inventario.cantidad(c.idPartida, BAMBU) ?: 0
        if (disponible < kilos) return rechazo(MotivoRechazo.BambuInsuficiente(kilos, disponible))

        val nivel = ReglasCuidado.nivelTrasRacion(animal.alimentacion)
        inventario.incrementar(c.idPartida, BAMBU, -kilos)
        cuidado.fijarAlimentacion(c.idPartida, c.idAnimal, nivel)
        cuidado.resolverAlertaSalud(c.idPartida, c.idAnimal)
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(setOf(espacio)))
        return Resultado.Exito(AnimalAlimentado(c.idAnimal, nivel, kilos, disponible - kilos))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-13 grupal

/**
 * @property kilosEsperados total que se mostró al usuario antes de confirmar; si al validar el conjunto ya
 * requiere otro total, se rechaza todo (CU-13, 4.a). `null` no comprueba el total mostrado.
 */
data class ComandoAlimentarEspacio(
    val idPartida: IdPartida,
    val idEspacio: String,
    val kilosEsperados: Int?,
    val idSolicitud: String,
)

data class EspacioAlimentado(val idEspacio: String, val animales: Int, val kilos: Int, val bambuRestante: Int)

/** CU-13 Alimentar espécimen, acción grupal: lleva al 100 % a todos los animales vivos del espacio por debajo de ese nivel. */
class AlimentarEspacio(
    private val ejecutor: EjecutorOperacion,
    private val zoo: ZooRepositorio,
    private val animales: AnimalRepositorio,
    private val cuidado: CuidadoRepositorio,
    private val inventario: InventarioRepositorio,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoAlimentarEspacio): Resultado<EspacioAlimentado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "ALIMENTACION_GRUPAL",
            entidad = "ESPACIO",
            origen = "CU-13",
            idSolicitud = c.idSolicitud,
            huella = "espacio=${c.idEspacio};kilos=${c.kilosEsperados}",
            codificar = { "${it.idEspacio}|${it.animales}|${it.kilos}|${it.bambuRestante}" },
            decodificar = {
                val (id, n, kilos, restante) = it.split("|")
                EspacioAlimentado(id, n.toInt(), kilos.toInt(), restante.toInt())
            },
            idEntidad = { it.idEspacio },
        )
        return ejecutor.ejecutar(op) { alimentar(c) }
    }

    private fun alimentar(c: ComandoAlimentarEspacio): Resultado<EspacioAlimentado> {
        zoo.espacio(c.idPartida, c.idEspacio) ?: return rechazo(MotivoRechazo.EspacioInexistente)
        val objetivo = animales.animalesDeEspacio(c.idPartida, c.idEspacio)
            .filter { it.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA }
        if (objetivo.isEmpty()) return rechazo(MotivoRechazo.NadaQueAlimentar)

        var total = 0
        for (a in objetivo) {
            val kilos = cuidado.kilosPorIncremento(a.idEspecie) ?: return rechazo(MotivoRechazo.RacionNoConfigurada)
            total += ReglasCuidado.kilosHastaMaximo(a.alimentacion, kilos, a.nivel)
        }
        if (c.kilosEsperados != null && c.kilosEsperados != total) return rechazo(MotivoRechazo.ConjuntoCambiado(total))
        val disponible = inventario.cantidad(c.idPartida, BAMBU) ?: 0
        if (disponible < total) return rechazo(MotivoRechazo.BambuInsuficiente(total, disponible))

        inventario.incrementar(c.idPartida, BAMBU, -total)
        objetivo.forEach {
            cuidado.fijarAlimentacion(c.idPartida, it.id, ReglasCuidado.ALIMENTACION_MAXIMA)
            cuidado.resolverAlertaSalud(c.idPartida, it.id)
        }
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(setOf(c.idEspacio)))
        return Resultado.Exito(EspacioAlimentado(c.idEspacio, objetivo.size, total, disponible - total))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-13 hábitat

/** Como [ComandoAlimentarEspacio], pero para todos los espacios activos de un hábitat. */
data class ComandoAlimentarHabitat(
    val idPartida: IdPartida,
    val idHabitat: String,
    val kilosEsperados: Int?,
    val idSolicitud: String,
)

data class HabitatAlimentado(val idHabitat: String, val animales: Int, val kilos: Int, val bambuRestante: Int)

/**
 * CU-13 Alimentar espécimen, acción por hábitat: lleva al 100 % a todos los animales vivos por debajo de ese
 * nivel en los espacios activos del hábitat. Atómico: si el bambú no alcanza para todos, no cambia nada.
 */
class AlimentarHabitat(
    private val ejecutor: EjecutorOperacion,
    private val zoo: ZooRepositorio,
    private val animales: AnimalRepositorio,
    private val cuidado: CuidadoRepositorio,
    private val inventario: InventarioRepositorio,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoAlimentarHabitat): Resultado<HabitatAlimentado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "ALIMENTACION_HABITAT",
            entidad = "HABITAT",
            origen = "CU-13",
            idSolicitud = c.idSolicitud,
            huella = "habitat=${c.idHabitat};kilos=${c.kilosEsperados}",
            codificar = { "${it.idHabitat}|${it.animales}|${it.kilos}|${it.bambuRestante}" },
            decodificar = {
                val (id, n, kilos, restante) = it.split("|")
                HabitatAlimentado(id, n.toInt(), kilos.toInt(), restante.toInt())
            },
            idEntidad = { it.idHabitat },
        )
        return ejecutor.ejecutar(op) { alimentar(c) }
    }

    private fun alimentar(c: ComandoAlimentarHabitat): Resultado<HabitatAlimentado> {
        zoo.habitat(c.idPartida, c.idHabitat) ?: return rechazo(MotivoRechazo.HabitatNoDisponible)
        val espacios = zoo.espacios(c.idPartida, c.idHabitat).map { it.id }
        val objetivo = espacios.flatMap { e ->
            animales.animalesDeEspacio(c.idPartida, e).filter { it.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA }
        }
        if (objetivo.isEmpty()) return rechazo(MotivoRechazo.NadaQueAlimentar)

        var total = 0
        for (a in objetivo) {
            val kilos = cuidado.kilosPorIncremento(a.idEspecie) ?: return rechazo(MotivoRechazo.RacionNoConfigurada)
            total += ReglasCuidado.kilosHastaMaximo(a.alimentacion, kilos, a.nivel)
        }
        if (c.kilosEsperados != null && c.kilosEsperados != total) return rechazo(MotivoRechazo.ConjuntoCambiado(total))
        val disponible = inventario.cantidad(c.idPartida, BAMBU) ?: 0
        if (disponible < total) return rechazo(MotivoRechazo.BambuInsuficiente(total, disponible))

        inventario.incrementar(c.idPartida, BAMBU, -total)
        objetivo.forEach {
            cuidado.fijarAlimentacion(c.idPartida, it.id, ReglasCuidado.ALIMENTACION_MAXIMA)
            cuidado.resolverAlertaSalud(c.idPartida, it.id)
        }
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(objetivo.mapNotNull { it.idEspacio }.toSet()))
        return Resultado.Exito(HabitatAlimentado(c.idHabitat, objetivo.size, total, disponible - total))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-09

data class ComandoTratarAnimal(val idPartida: IdPartida, val idAnimal: String, val idSolicitud: String)

data class AnimalTratado(val idAnimal: String, val unidades: Int)

/** CU-09 Aplicar tratamiento médico (RF-13): consume todos los insumos del rango o ninguno y deja al animal sano. */
class TratarAnimal(
    private val ejecutor: EjecutorOperacion,
    private val animales: AnimalRepositorio,
    private val cuidado: CuidadoRepositorio,
    private val inventario: InventarioRepositorio,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoTratarAnimal): Resultado<AnimalTratado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "TRATAMIENTO",
            entidad = "ANIMAL",
            origen = "CU-09",
            idSolicitud = c.idSolicitud,
            huella = "animal=${c.idAnimal}",
            codificar = { "${it.idAnimal}|${it.unidades}" },
            decodificar = {
                val (id, unidades) = it.split("|")
                AnimalTratado(id, unidades.toInt())
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { tratar(c) }
    }

    private fun tratar(c: ComandoTratarAnimal): Resultado<AnimalTratado> {
        val animal = animales.animal(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)
        val espacio = animal.idEspacio
        if (!animal.vivo || espacio == null) return rechazo(MotivoRechazo.AnimalNoVivo)
        if (!animal.enfermo) return rechazo(MotivoRechazo.AnimalSano)
        val consumo = cuidado.consumoMedico(c.idPartida, c.idAnimal).map { it.copy(cantidad = ReglasCuidado.unidadesMedicina(it.cantidad, animal.nivel)) }
        if (consumo.isEmpty()) return rechazo(MotivoRechazo.ConsumoMedicoNoConfigurado)

        val existencias = consumo.map { it to (inventario.cantidad(c.idPartida, Recurso(TipoRecurso.INSUMO, it.idInsumo)) ?: 0) }
        val faltantes = existencias.filter { (m, disponible) -> disponible < m.cantidad }
            .map { (m, disponible) -> FaltanteInsumo(m.nombre, m.cantidad, disponible) }
        if (faltantes.isNotEmpty()) return rechazo(MotivoRechazo.InsumosInsuficientes(faltantes))

        consumo.forEach { inventario.incrementar(c.idPartida, Recurso(TipoRecurso.INSUMO, it.idInsumo), -it.cantidad) }
        cuidado.curar(c.idPartida, c.idAnimal)
        cuidado.resolverAlertaSalud(c.idPartida, c.idAnimal)
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(setOf(espacio)))
        return Resultado.Exito(AnimalTratado(c.idAnimal, consumo.sumOf { it.cantidad }))
    }
}

// ---------------------------------------------------------------------------------------------------- Consultas

/** Vista previa de una ración individual. `kilos` es `null` si la especie no tiene cantidad configurada. */
data class PreviaRacion(val kilos: Int?, val bambuDisponible: Int)

/** Vista previa de la acción grupal (CU-13, paso 2). `kilos` es `null` si alguna especie no tiene cantidad configurada. */
data class PreviaGrupal(val animales: Int, val kilos: Int?, val bambuDisponible: Int)

data class LineaTratamiento(val nombre: String, val necesario: Int, val disponible: Int)

/** Consultas de solo lectura que alimentan los diálogos de confirmación; no mutan ni recalculan nada. */
class ConsultarCuidado(
    private val animales: AnimalRepositorio,
    private val cuidado: CuidadoRepositorio,
    private val inventario: InventarioRepositorio,
    private val zoo: ZooRepositorio,
) {
    fun racion(idPartida: IdPartida, idAnimal: String): PreviaRacion? {
        val a = animales.animal(idPartida, idAnimal) ?: return null
        return PreviaRacion(cuidado.kilosPorIncremento(a.idEspecie)?.let { ReglasCuidado.kilosPorRacion(it, a.nivel) }, inventario.cantidad(idPartida, BAMBU) ?: 0)
    }

    fun grupal(idPartida: IdPartida, idEspacio: String): PreviaGrupal =
        previa(idPartida, animales.animalesDeEspacio(idPartida, idEspacio))

    /** Como [grupal], sobre todos los espacios activos del hábitat. */
    fun grupalHabitat(idPartida: IdPartida, idHabitat: String): PreviaGrupal =
        previa(idPartida, zoo.espacios(idPartida, idHabitat).flatMap { animales.animalesDeEspacio(idPartida, it.id) })

    private fun previa(idPartida: IdPartida, candidatos: List<AnimalResumen>): PreviaGrupal {
        val objetivo = candidatos.filter { it.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA }
        var total: Int? = 0
        for (a in objetivo) {
            val kilos = cuidado.kilosPorIncremento(a.idEspecie)
            total = if (kilos == null || total == null) null else total + ReglasCuidado.kilosHastaMaximo(a.alimentacion, kilos, a.nivel)
        }
        return PreviaGrupal(objetivo.size, total, inventario.cantidad(idPartida, BAMBU) ?: 0)
    }

    fun tratamiento(idPartida: IdPartida, idAnimal: String): List<LineaTratamiento> {
        val nivel = animales.animal(idPartida, idAnimal)?.nivel ?: 1
        return cuidado.consumoMedico(idPartida, idAnimal).map {
            LineaTratamiento(it.nombre, ReglasCuidado.unidadesMedicina(it.cantidad, nivel), inventario.cantidad(idPartida, Recurso(TipoRecurso.INSUMO, it.idInsumo)) ?: 0)
        }
    }
}
