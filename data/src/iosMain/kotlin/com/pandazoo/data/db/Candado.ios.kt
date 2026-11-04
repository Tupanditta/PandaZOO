package com.pandazoo.data.db

import platform.Foundation.NSRecursiveLock

actual class Candado actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> conCandado(bloque: () -> T): T {
        lock.lock()
        try {
            return bloque()
        } finally {
            lock.unlock()
        }
    }
}
