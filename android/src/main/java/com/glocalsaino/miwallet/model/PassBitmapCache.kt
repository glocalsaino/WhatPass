package com.glocalsaino.miwallet.model

import android.graphics.Bitmap
import android.util.LruCache

object PassBitmapCache {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun get(passId: String, bitmapType: String): Bitmap? =
        cache.get("${passId}_$bitmapType")

    fun put(passId: String, bitmapType: String, bitmap: Bitmap) {
        cache.put("${passId}_$bitmapType", bitmap)
    }

    fun evict(passId: String) {
        cache.snapshot().keys
            .filter { it.startsWith("${passId}_") }
            .forEach { cache.remove(it) }
    }
}
