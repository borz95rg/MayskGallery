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

    /** Элементы, помещённые в системную корзину Android 11+. */
    fun loadTrashed(context: Context): List<MediaItem> {
        if (android.os.Build.VERSION.SDK_INT < 30) return emptyList()
        val result = ArrayList<MediaItem>()
        result += queryTrashed(context, isVideo = false)
        result += queryTrashed(context, isVideo = true)
        return result.sortedByDescending { it.dateMillis }
    }

    private fun queryTrashed(context: Context, isVideo: Boolean): List<MediaItem> {
        if (android.os.Build.VERSION.SDK_INT < 30) return emptyList()
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val out = ArrayList<MediaItem>()
        try {
            context.contentResolver.query(
                base, projection,
                "${MediaStore.MediaColumns.IS_TRASHED}=1", null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val expires = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val mediaId = c.getLong(id)
                    out += MediaItem(
                        id = mediaId,
                        uri = ContentUris.withAppendedId(base, mediaId),
                        isVideo = isVideo,
                        name = c.getString(name) ?: "",
                        mime = c.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                        dateMillis = c.getLong(added) * 1000L,
                        bucket = c.getString(bucket) ?: "Корзина",
                        durationMs = 0L,
                        trashExpiresMillis = if (expires >= 0 && !c.isNull(expires)) c.getLong(expires) * 1000L else null,
                    )
                }
            }
        } catch (_: Exception) { }
        return out
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
        } catch (e: SecurityException) {
            // нет разрешения — вернём пустой список
        }
        return out
    }
}
