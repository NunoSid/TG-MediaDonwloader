package pt.nunosid.tgmedialibrary.data

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-memory-only thumbnail cache.
 * Nothing is persisted to disk by this cache. It disappears when the process ends
 * and is explicitly cleared whenever the private library is locked.
 */
object ThumbnailMemoryCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 16L)
        .coerceIn(8L * 1024L, 48L * 1024L)
        .toInt()

    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun get(fileId: Int): Bitmap? = cache.get(fileId)

    @Synchronized
    fun put(fileId: Int, bitmap: Bitmap) {
        cache.put(fileId, bitmap)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
