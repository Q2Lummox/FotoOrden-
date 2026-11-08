package com.fotoorden.app.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoInfo
import kotlinx.coroutines.yield

object ImageMath {

    /**
     * Hash perceptual (dHash, 64 bits). Dos fotos casi iguales dan hashes
     * que se diferencian en muy pocos bits.
     */
    fun dHash(src: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(src, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (lum(s.getPixel(x, y)) > lum(s.getPixel(x + 1, y))) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (s !== src) s.recycle()
        return hash
    }

    /**
     * Nitidez = varianza del laplaciano sobre una versión pequeña de la foto.
     * Valores bajos = foto borrosa, movida o casi vacía (p. ej. foto del bolsillo).
     */
    fun sharpness(src: Bitmap): Float {
        val maxSide = 400
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        val s = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(3),
                (src.height * scale).toInt().coerceAtLeast(3),
                true
            )
        } else src
        val w = s.width
        val h = s.height
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        if (s !== src) s.recycle()
        val g = FloatArray(w * h) { lum(px[it]) }

        var sum = 0.0
        var sq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val l = (g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4f * g[i]).toDouble()
                sum += l
                sq += l * l
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sum / n
        return (sq / n - mean * mean).toFloat()
    }

    private fun lum(c: Int): Float =
        0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    /**
     * Agrupa fotos casi idénticas (ráfagas, fotos repetidas, la misma imagen
     * guardada dos veces). Devuelve solo grupos de 2 o más.
     */
    suspend fun findDuplicateGroups(
        photos: List<Photo>,
        infos: Map<Long, PhotoInfo>,
        maxDistance: Int = 5,
    ): List<List<Photo>> {
        val items = photos.filter { (infos[it.id]?.hash ?: 0L) != 0L }
        val hashes = LongArray(items.size) { infos.getValue(items[it].id).hash }
        val n = items.size
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }

        for (i in 0 until n) {
            if (i % 200 == 0) yield() // permite cancelar
            val hi = hashes[i]
            for (j in i + 1 until n) {
                if (java.lang.Long.bitCount(hi xor hashes[j]) <= maxDistance) {
                    val a = find(i); val b = find(j)
                    if (a != b) parent[b] = a
                }
            }
        }

        return (0 until n)
            .groupBy { find(it) }
            .values
            .filter { it.size >= 2 }
            .map { idx -> idx.map { items[it] }.sortedByDescending { it.dateTaken } }
            .sortedByDescending { it.first().dateTaken }
    }

    /** En un grupo de repetidas, la que conviene conservar: la más nítida y, a igualdad, la más grande. */
    fun bestOf(group: List<Photo>, infos: Map<Long, PhotoInfo>): Photo =
        group.maxWith(compareBy<Photo>({ infos[it.id]?.sharpness ?: 0f }, { it.size }))
}
