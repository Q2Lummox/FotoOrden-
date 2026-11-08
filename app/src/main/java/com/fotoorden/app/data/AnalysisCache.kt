package com.fotoorden.app.data

import android.content.Context
import java.io.File

/**
 * Guarda el resultado del análisis para no repetirlo.
 * Formato: una línea por foto -> id \t categoría \t nitidez \t hash \t caras
 */
class AnalysisCache(context: Context) {

    private val file = File(context.filesDir, "analisis_v1.tsv")

    fun load(): Map<Long, PhotoInfo> {
        val map = HashMap<Long, PhotoInfo>()
        if (!file.exists()) return map
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size < 5) return@forEachLine
            val id = p[0].toLongOrNull() ?: return@forEachLine
            val cat = runCatching { Category.valueOf(p[1]) }.getOrDefault(Category.OTRAS)
            map[id] = PhotoInfo(
                category = cat,
                sharpness = p[2].toFloatOrNull() ?: -1f,
                hash = p[3].toLongOrNull() ?: 0L,
                faces = p[4].toIntOrNull() ?: 0,
            )
        }
        return map
    }

    fun append(batch: Map<Long, PhotoInfo>) {
        if (batch.isEmpty()) return
        file.appendText(batch.entries.joinToString("") { line(it.key, it.value) })
    }

    /** Reescribe el fichero solo con las fotos que siguen existiendo. */
    fun rewrite(all: Map<Long, PhotoInfo>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(all.entries.joinToString("") { line(it.key, it.value) })
        tmp.renameTo(file)
    }

    private fun line(id: Long, i: PhotoInfo) =
        "$id\t${i.category.name}\t${i.sharpness}\t${i.hash}\t${i.faces}\n"
}
