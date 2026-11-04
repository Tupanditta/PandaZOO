package com.pandazoo.data.db

/** Candado reentrante que serializa el acceso a la única conexión SQLite. Lo único dependiente de plataforma del módulo. */
expect class Candado() {
    fun <T> conCandado(bloque: () -> T): T
}
