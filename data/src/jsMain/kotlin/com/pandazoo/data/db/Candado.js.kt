package com.pandazoo.data.db

/** JavaScript tiene un solo hilo y ninguna transacción se suspende a mitad: no hace falta bloquear. */
actual class Candado actual constructor() {
    actual fun <T> conCandado(bloque: () -> T): T = bloque()
}
