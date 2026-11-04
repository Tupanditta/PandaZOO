package com.pandazoo.data.db

import java.util.concurrent.locks.ReentrantLock

actual class Candado actual constructor() {
    private val lock = ReentrantLock()

    actual fun <T> conCandado(bloque: () -> T): T {
        lock.lock()
        try {
            return bloque()
        } finally {
            lock.unlock()
        }
    }
}
