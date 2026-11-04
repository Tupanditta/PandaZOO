package com.pandazoo.application.caso

import com.pandazoo.application.puerto.CatalogoRepositorio
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.InventarioRepositorio
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.InventarioFinanzas
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Seccion

/**
 * CU-16 Consultar inventario y finanzas (RF-09, RF-26). Es de solo lectura: no abre transacción ni
 * audita (RF-47). Si una sección falla, las demás se conservan y la fallida no se presenta como cero.
 */
class ConsultarInventarioFinanzas(
    private val economia: EconomiaRepositorio,
    private val inventario: InventarioRepositorio,
) {
    fun ejecutar(idPartida: IdPartida): InventarioFinanzas =
        InventarioFinanzas(
            fondos = seccion("el balance") { economia.fondos(idPartida) },
            existencias = seccion("las existencias") { inventario.existencias(idPartida) },
            beneficiosPendientes = seccion("los beneficios pendientes") { economia.beneficiosPendientes(idPartida) },
        )

    private fun <T> seccion(nombre: String, lectura: () -> T): Seccion<T> =
        try {
            Seccion.Disponible(lectura())
        } catch (e: Exception) {
            Seccion.NoDisponible("No se pudo consultar $nombre")
        }
}

/** Oferta de la tienda de recursos y de espacios (CU-07 paso 2, CU-03 paso 3). Solo lectura. */
class ConsultarTienda(private val catalogo: CatalogoRepositorio) {
    fun recursos(): List<OfertaRecurso> = catalogo.recursosElegibles()

    fun capacidades(): List<OpcionCapacidad> = catalogo.opcionesCapacidad()
}

/** Navegación jerárquica Hábitats a Espacios (RF-08). Solo lectura. */
class ConsultarZoo(private val zoo: ZooRepositorio) {
    fun habitats(idPartida: IdPartida): List<Habitat> = zoo.habitats(idPartida)

    fun espacios(idPartida: IdPartida, idHabitat: String): List<Espacio> = zoo.espacios(idPartida, idHabitat)

    fun espacio(idPartida: IdPartida, idEspacio: String): Espacio? = zoo.espacio(idPartida, idEspacio)
}
