package com.maysk.gallery.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.maysk.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Индекс «что на фото». Распознавание идёт на устройстве (ML Kit),
 * результат сохраняется в файл, чтобы не анализировать снимки повторно.
 */
class AiIndex(private val context: Context) {

    private val file = File(context.filesDir, "ai_labels.json")
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()
    )

    suspend fun loadCache(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        try {
            val json = JSONObject(file.readText())
            val map = HashMap<String, List<String>>()
            for (k in json.keys()) {
                val arr = json.getJSONArray(k)
                map[k] = List(arr.length()) { arr.getString(it) }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun save(map: Map<String, List<String>>) = withContext(Dispatchers.IO) {
        val json = JSONObject()
        for ((k, v) in map) json.put(k, JSONArray(v))
        file.writeText(json.toString())
    }

    /** Возвращает метки снимка или null, если не удалось его прочитать. */
    suspend fun labelsFor(item: MediaItem): List<String>? = withContext(Dispatchers.Default) {
        val bmp = loadBitmap(item) ?: return@withContext null
        try {
            labeler.process(InputImage.fromBitmap(bmp, 0)).await().map { it.text }
        } catch (_: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun loadBitmap(item: MediaItem): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(384, 384), null)
        } else if (!item.isVideo) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } else null
    } catch (_: Exception) {
        null
    }

    fun close() = labeler.close()
}
