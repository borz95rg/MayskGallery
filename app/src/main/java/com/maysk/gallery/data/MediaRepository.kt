package com.maysk.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MediaRepository {

    /** Читает все фото и видео устройства, новые сверху. */
    fun load(context: Context): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        result += query(context, isVideo = false)
        result += query(context, isVideo = true)
        result.sortByDescending { it.dateMillis }
        return result
    }

    /** Элементы системной корзины MediaStore (Android 11+). */
    fun loadTrashed(context: Context): List<MediaItem> {
        if (android.os.Build.VERSION.SDK_INT < 30) return emptyList()
        val result = ArrayList<MediaItem>()
        result += query(context, isVideo = false, trashedOnly = true)
        result += query(context, isVideo = true, trashedOnly = true)
        result.sortByDescending { it.dateMillis }
        return result
    }

    private fun query(context: Context, isVideo: Boolean, trashedOnly: Boolean = false): List<MediaItem> {
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
            if (trashedOnly && android.os.Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.DATE_EXPIRES)
        }.toTypedArray()

        val out = ArrayList<MediaItem>()
        try {
            val cursor = if (android.os.Build.VERSION.SDK_INT >= 30) {
                // На Android 11+ корзина НЕ попадает в обычные запросы, а условие
                // IS_TRASHED в selection игнорируется — нужен именно Bundle с MATCH_TRASHED.
                val args = android.os.Bundle().apply {
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_TRASHED,
                        if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                    )
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
                }
                context.contentResolver.query(base, projection, args, null)
            } else {
                context.contentResolver.query(base, projection, null, null, null)
            }
            cursor?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(
                    if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN
                )
                val iDur = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
                val iExpires = if (trashedOnly && android.os.Build.VERSION.SDK_INT >= 30)
                    c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken)
                    val date = if (taken > 0) taken else c.getLong(iAdded) * 1000L
                    out += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        isVideo = isVideo,
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: if (isVideo) "video/*" else "image/*",
                        dateMillis = date,
                        bucket = c.getString(iBucket)?.takeIf { it.isNotBlank() } ?: "Без альбома",
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        expiresAtMillis = if (iExpires >= 0 && !c.isNull(iExpires)) c.getLong(iExpires) * 1000L else 0L,
                        sizeBytes = if (!c.isNull(iSize)) c.getLong(iSize) else 0L,
                    )
                }
            }
        } catch (e: SecurityException) {
            // нет разрешения — вернём пустой список
        }
        return out
    }
}
