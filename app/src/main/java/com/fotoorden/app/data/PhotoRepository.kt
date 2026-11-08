package com.fotoorden.app.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log

/** Todo el acceso a las fotos del móvil pasa por aquí (MediaStore). */
class PhotoRepository(context: Context) {

    private val resolver: ContentResolver = context.contentResolver
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun loadPhotos(): List<Photo> = query(onlyTrashed = false)

    fun loadTrash(): List<Photo> = query(onlyTrashed = true)

    private fun query(onlyTrashed: Boolean): List<Photo> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            if (onlyTrashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }

        val result = ArrayList<Photo>()
        try {
            resolver.query(collection, projection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iExp = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                    val added = c.getLong(iAdded) * 1000L
                    result += Photo(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(iName) ?: "",
                        bucket = c.getString(iBucket) ?: "Sin nombre",
                        relativePath = c.getString(iPath) ?: "",
                        dateTaken = if (taken > 0) taken else added,
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateExpires = if (c.isNull(iExp)) 0L else c.getLong(iExp),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo fotos", e)
        }
        result.sortByDescending { it.dateTaken }
        return result
    }

    // --- Peticiones que el usuario debe aprobar (Android muestra un diálogo del sistema) ---

    fun writeRequest(uris: List<Uri>): PendingIntent = MediaStore.createWriteRequest(resolver, uris)

    fun trashRequest(uris: List<Uri>, trash: Boolean): PendingIntent =
        MediaStore.createTrashRequest(resolver, uris, trash)

    fun deleteRequest(uris: List<Uri>): PendingIntent = MediaStore.createDeleteRequest(resolver, uris)

    /** Mueve una foto a otra carpeta. Requiere permiso de escritura ya concedido. */
    fun move(uri: Uri, relativePath: String): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        resolver.update(uri, values, null, null) > 0
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo mover $uri", e)
        false
    }

    companion object {
        private const val TAG = "PhotoRepository"

        fun newAlbumPath(name: String): String = "Pictures/${sanitize(name)}/"

        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60)
    }
}
