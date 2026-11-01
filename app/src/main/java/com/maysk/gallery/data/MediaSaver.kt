package com.maysk.gallery.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Сохраняет файл в общую медиатеку (Pictures/<folder> или Movies/<folder>). */
object MediaSaver {

    fun save(
        context: Context,
        displayName: String,
        mime: String,
        isVideo: Boolean,
        folder: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        val rootDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cr = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$rootDir/$folder")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = cr.insert(base, values) ?: return null
                try {
                    cr.openOutputStream(uri)?.use(write) ?: error("no stream")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    cr.update(uri, values, null, null)
                    uri
                } catch (e: Exception) {
                    cr.delete(uri, null, null)
                    null
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(rootDir), folder).apply { mkdirs() }
                val file = File(dir, displayName)
                FileOutputStream(file).use(write)
                MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }
}
