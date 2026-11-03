package com.maysk.gallery.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** Итог поиска лиц на фото: сколько лиц и какую долю кадра занимает самое крупное. */
data class FaceInfo(val faces: Int, val maxShare: Float)

/**
 * Поиск лиц (ML Kit, работает на устройстве) для раздела «Селфи».
 * Результаты кэшируются в файл, ручные отметки «селфи / не селфи» хранятся отдельно и важнее автоопределения.
 */
class SelfieIndex(private val context: Context) {

    private val file = File(context.filesDir, "face_index.json")
    private val prefs = context.getSharedPreferences("selfie_marks", Context.MODE_PRIVATE)

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.1f)
                .build()
        )
    }

    fun loadCache(): Map<String, FaceInfo> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val out = HashMap<String, FaceInfo>()
            for (k in json.keys()) {
                val a = json.getJSONArray(k)
                out[k] = FaceInfo(a.getInt(0), a.getDouble(1).toFloat())
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(map: Map<String, FaceInfo>) {
        try {
            val json = JSONObject()
            for ((k, f) in map) json.put(k, JSONArray().put(f.faces).put(f.maxShare.toDouble()))
            file.writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    fun marksYes(): Set<String> = prefs.getStringSet(YES, emptySet()).orEmpty().toSet()
    fun marksNo(): Set<String> = prefs.getStringSet(NO, emptySet()).orEmpty().toSet()

    /** Ручная отметка: true — это селфи, false — это не селфи. */
    fun mark(key: String, selfie: Boolean) {
        val yes = marksYes().toMutableSet()
        val no = marksNo().toMutableSet()
        if (selfie) { yes += key; no -= key } else { no += key; yes -= key }
        prefs.edit().putStringSet(YES, yes).putStringSet(NO, no).apply()
    }

    /** Ищет лица. Если файл не читается — возвращает «лиц нет», чтобы не пробовать вечно. */
    suspend fun detect(item: MediaItem): FaceInfo {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return FaceInfo(0, 0f)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
            val bmp = resolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return FaceInfo(0, 0f)
            val rotation = try {
                resolver.openInputStream(item.uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            } catch (e: Exception) {
                0
            }
            val sideways = rotation % 180 != 0
            val w = (if (sideways) bmp.height else bmp.width).toFloat()
            val h = (if (sideways) bmp.width else bmp.height).toFloat()
            val faces = detector.process(InputImage.fromBitmap(bmp, rotation)).await()
            bmp.recycle()
            val share = faces.maxOfOrNull { it.boundingBox.width() * it.boundingBox.height() / (w * h) } ?: 0f
            FaceInfo(faces.size, share)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FaceInfo(0, 0f)
        }
    }

    fun close() {
        try { detector.close() } catch (_: Exception) {}
    }

    companion object {
        private const val YES = "yes"
        private const val NO = "no"

        /**
         * Правило «селфи»: ручная отметка важнее всего; затем имя/альбом и EXIF; затем лицо(а) крупным планом.
         * Лицо на фото само по себе не доказывает, что снимали фронтальной камерой — поэтому есть ручная отметка.
         */
        fun isSelfie(
            item: MediaItem,
            meta: MediaMeta?,
            face: FaceInfo?,
            yes: Set<String>,
            no: Set<String>,
        ): Boolean {
            if (item.isVideo) return false
            if (item.key in yes) return true
            if (item.key in no) return false
            if (MediaMetaIndex.looksLikeSelfieByName(item) || meta?.selfie == true) return true
            return face != null && face.faces in 1..3 && face.maxShare >= 0.06f
        }
    }
}
