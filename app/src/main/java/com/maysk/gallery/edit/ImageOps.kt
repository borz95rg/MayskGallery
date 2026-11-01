package com.maysk.gallery.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

enum class EditFilter(val title: String) {
    ORIGINAL("Оригинал"),
    VIVID("Vivid"),
    NATURAL("Natural"),
    FILM("Film"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    SUNSET("Sunset"),
    BW("B&W");

    fun matrix(): ColorMatrix = when (this) {
        ORIGINAL -> ColorMatrix()
        VIVID -> ColorMatrix().also { it.setSaturation(1.45f) }.also { it.postConcat(contrast(1.12f)) }
        NATURAL -> ColorMatrix().also { it.setSaturation(1.1f) }
        FILM -> ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0f, 0f, -6f,
                0f, 1.0f, 0f, 0f, -4f,
                0f, 0f, 0.9f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f,
            )
        ).also { it.postConcat(contrast(1.1f)) }
        WARM -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 6f,
                0f, 1.02f, 0f, 0f, 0f,
                0f, 0f, 0.88f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        COOL -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, -4f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.14f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        VINTAGE -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 8f,
                0.349f, 0.686f, 0.168f, 0f, 4f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        SUNSET -> ColorMatrix(
            floatArrayOf(
                1.18f, 0f, 0f, 0f, 4f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 0.82f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f,
            )
        ).also { it.postConcat(ColorMatrix().apply { setSaturation(1.2f) }) }
        BW -> ColorMatrix().also { it.setSaturation(0f) }
    }
}

enum class CropRatio(val title: String, val w: Int, val h: Int) {
    FREE("Без обрезки", 0, 0),
    SQUARE("1:1", 1, 1),
    R43("4:3", 4, 3),
    R34("3:4", 3, 4),
    R169("16:9", 16, 9),
}

data class EditState(
    val filter: EditFilter = EditFilter.ORIGINAL,
    val brightness: Float = 0f,   // -1..1
    val contrast: Float = 1f,     // 0.5..1.5
    val saturation: Float = 1f,   // 0..2
    val rotation: Int = 0,        // 0, 90, 180, 270
    val flip: Boolean = false,
    val crop: CropRatio = CropRatio.FREE,
) {
    val isDefault get() = this == EditState()
}

private fun contrast(c: Float): ColorMatrix {
    val t = (1f - c) * 128f
    return ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}

object ImageOps {

    /** Читает картинку, уменьшая до maxSide по длинной стороне, с учётом поворота из EXIF. */
    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val orientation = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                else -> Unit
            }
            if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Применяет все правки к исходной картинке и возвращает новую. */
    fun render(src: Bitmap, st: EditState): Bitmap {
        // 1. поворот и отражение
        var bmp = src
        if (st.rotation != 0 || st.flip) {
            val m = Matrix()
            if (st.flip) m.postScale(-1f, 1f)
            m.postRotate(st.rotation.toFloat())
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        // 2. обрезка по центру
        if (st.crop != CropRatio.FREE) {
            val target = st.crop.w.toFloat() / st.crop.h
            val cur = bmp.width.toFloat() / bmp.height
            val cw: Int
            val ch: Int
            if (cur > target) {
                ch = bmp.height
                cw = min(bmp.width, (ch * target).toInt())
            } else {
                cw = bmp.width
                ch = min(bmp.height, (cw / target).toInt())
            }
            bmp = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
        }
        // 3. цвет: фильтр -> насыщенность -> контраст -> яркость
        val cm = st.filter.matrix()
        cm.postConcat(ColorMatrix().apply { setSaturation(st.saturation) })
        cm.postConcat(contrast(st.contrast))
        val b = st.brightness * 255f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(bmp, 0f, 0f, paint)
        return out
    }

    fun thumbnail(src: Bitmap, side: Int): Bitmap {
        val k = side.toFloat() / max(src.width, src.height)
        return if (k >= 1f) src
        else Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
    }
}
