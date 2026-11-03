package com.maysk.gallery.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Результат разбора EXIF одного фото. lat/lon = NaN, если геопозиции нет. */
data class MediaMeta(val lat: Double, val lon: Double, val selfie: Boolean) {
    val hasGeo: Boolean get() = !lat.isNaN() && !lon.isNaN()
}

/**
 * Читает метаданные (GPS и признак фронтальной камеры) и кэширует их в файл,
 * чтобы не разбирать EXIF тысяч снимков при каждом открытии экрана.
 */
class MediaMetaIndex(private val context: Context) {

    private val file = File(context.filesDir, "media_meta.json")

    fun loadCache(): Map<String, MediaMeta> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val out = HashMap<String, MediaMeta>()
            for (k in json.keys()) {
                val a = json.getJSONArray(k)
                out[k] = MediaMeta(
                    if (a.isNull(0)) Double.NaN else a.getDouble(0),
                    if (a.isNull(1)) Double.NaN else a.getDouble(1),
                    a.getBoolean(2),
                )
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(map: Map<String, MediaMeta>) {
        try {
            val json = JSONObject()
            for ((k, m) in map) {
                val a = org.json.JSONArray()
                a.put(if (m.hasGeo) m.lat else JSONObject.NULL)
                a.put(if (m.hasGeo) m.lon else JSONObject.NULL)
                a.put(m.selfie)
                json.put(k, a)
            }
            file.writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    /** Читает EXIF. null — файл не удалось открыть (повторим попытку в следующий раз). */
    fun read(item: MediaItem, canReadLocation: Boolean): MediaMeta? {
        val nameSelfie = looksLikeSelfieByName(item)
        return try {
            // Android 10+ скрывает GPS из EXIF, если не запросить оригинал
            // (нужно разрешение ACCESS_MEDIA_LOCATION).
            val uri: Uri = if (canReadLocation && Build.VERSION.SDK_INT >= 29)
                MediaStore.setRequireOriginal(item.uri) else item.uri
            val stream = (try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                context.contentResolver.openInputStream(item.uri)
            }) ?: return null
            stream.use {
                val exif = ExifInterface(it)
                val ll = FloatArray(2)
                val geo = exif.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)
                val exifHints = listOf(
                    exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                    exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                    exif.getAttribute(ExifInterface.TAG_USER_COMMENT),
                    exif.getAttribute(ExifInterface.TAG_MAKE),
                    exif.getAttribute(ExifInterface.TAG_MODEL),
                ).filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
                val lensSelfie = listOf("front", "selfie", "фронт", "前置", "前摄", "user camera")
                    .any { it in exifHints }
                MediaMeta(
                    if (geo) ll[0].toDouble() else Double.NaN,
                    if (geo) ll[1].toDouble() else Double.NaN,
                    nameSelfie || lensSelfie,
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            // повреждённый EXIF и т.п. — считаем, что метаданных нет
            MediaMeta(Double.NaN, Double.NaN, nameSelfie)
        }
    }

    companion object {
        /** Селфи по альбому/имени файла (разные производители камер называют по-разному). */
        fun looksLikeSelfieByName(item: MediaItem): Boolean {
            val b = item.bucket.lowercase(Locale.ROOT)
            val n = item.name.lowercase(Locale.ROOT)
            val pathHints = listOf("selfies", "selfie", "селфи", "front camera", "front_camera", "frontcamera")
            return pathHints.any { it in b || it in n } ||
                "_front" in n || "front_" in n || "-front" in n || "фронт" in n
        }
    }
}
