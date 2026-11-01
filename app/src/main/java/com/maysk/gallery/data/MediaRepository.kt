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

    private fun query(context: Context, isVideo: Boolean): List<MediaItem> {
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val out = ArrayList<MediaItem>()
        try {
            context.contentResolver.query(base, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(
                    if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN
                )
                val iDur = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

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
                    )
                }
            }
        } catch (_: SecurityException) {
            // нет разрешения — вернём пустой список
        }
        return out
    }
}
