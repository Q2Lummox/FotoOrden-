package com.fotoorden.app.data

import android.net.Uri

/** Una foto de la galería del móvil (fila de MediaStore). */
data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucket: String,        // nombre de la carpeta / álbum
    val relativePath: String,  // p. ej. "DCIM/Camera/"
    val dateTaken: Long,       // milisegundos
    val size: Long,            // bytes
    val width: Int,
    val height: Int,
    val dateExpires: Long = 0, // solo para la papelera (segundos)
)

enum class Category(val label: String) {
    PERSONAS("Personas"),
    DOCUMENTOS("Documentos"),
    PAISAJES("Paisajes"),
    CAPTURAS("Capturas"),
    COMIDA("Comida"),
    MASCOTAS("Mascotas"),
    OTRAS("Otras"),
}

/** Resultado del análisis de una foto. */
data class PhotoInfo(
    val category: Category,
    val sharpness: Float, // varianza del laplaciano; bajo = borrosa. -1 = desconocido
    val hash: Long,       // hash perceptual (dHash). 0 = no válido
    val faces: Int,
) {
    companion object {
        val FAILED = PhotoInfo(Category.OTRAS, -1f, 0L, 0)
    }
}

/** Un álbum = una carpeta de fotos del móvil. */
data class Album(
    val name: String,
    val relativePath: String,
    val photos: List<Photo>,
) {
    val cover: Photo get() = photos.first()
    val count: Int get() = photos.size

    /** Android solo permite colocar fotos en DCIM/ o Pictures/. */
    val canReceive: Boolean
        get() = relativePath.startsWith("DCIM/") || relativePath.startsWith("Pictures/")
}

data class PhotoSection(val key: String, val title: String?, val photos: List<Photo>)

data class AnalysisProgress(val done: Int, val total: Int)
