package com.maysk.gallery.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Секретный альбом. Файлы копируются в закрытую папку приложения
 * (недоступна галереям и файловым менеджерам), PIN хранится только в виде хэша.
 */
class SecretVault(private val context: Context) {

    private val prefs = context.getSharedPreferences("maysk_vault", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "secret").apply { mkdirs() }

    val hasPin: Boolean get() = prefs.contains(K_HASH)

    var biometric: Boolean
        get() = prefs.getBoolean(K_BIO, true)
        set(v) { prefs.edit().putBoolean(K_BIO, v).apply() }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(K_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(K_FAILS, 0)
            .putLong(K_LOCK_UNTIL, 0L)
            .apply()
    }

    fun lockRemainingMs(): Long = (prefs.getLong(K_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Проверка PIN. После 5 ошибок подряд вход блокируется на растущее время. */
    fun verify(pin: String): Boolean {
        if (lockRemainingMs() > 0) return false
        val salt = Base64.decode(prefs.getString(K_SALT, "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(K_HASH, "") ?: "", Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        if (ok) {
            prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0L).apply()
        } else {
            val fails = prefs.getInt(K_FAILS, 0) + 1
            val e = prefs.edit().putInt(K_FAILS, fails)
            if (fails >= 5) e.putLong(K_LOCK_UNTIL, System.currentTimeMillis() + 30_000L * (fails - 4))
            e.apply()
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 100_000, 256))
            .encoded

    // ---------- файлы ----------

    fun list(): List<MediaItem> =
        dir.walkTopDown().filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val display = f.name.substringAfter("__", f.name)
                val ext = display.substringAfterLast('.', "").lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/*"
                val album = f.parentFile?.takeIf { it != dir }?.name ?: "Сейф"
                MediaItem(
                    id = f.absolutePath.hashCode().toLong() and 0x7fffffffL,
                    uri = Uri.fromFile(f),
                    isVideo = mime.startsWith("video"),
                    name = display,
                    mime = mime,
                    dateMillis = f.lastModified(),
                    bucket = album,
                    durationMs = 0L,
                    sizeBytes = f.length(),
                )
            }.toList()

    /** Копирует файл в сейф. Оригинал удаляет вызывающий код (нужно подтверждение системы). */
    fun hide(item: MediaItem): Boolean = try {
        val albumName = item.bucket.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(60).ifBlank { "Без альбома" }
        val albumDir = File(dir, albumName).apply { mkdirs() }
        val target = File(albumDir, "${item.key}__${item.name}")
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("no stream")
        target.setLastModified(item.dateMillis)
        true
    } catch (e: Exception) {
        false
    }

    /** Возвращает файл в общую галерею (Pictures/Restored) и удаляет из сейфа. */
    fun restore(item: MediaItem): Boolean {
        val file = File(item.uri.path ?: return false)
        if (!file.exists()) return false
        val uri = MediaSaver.save(context, item.name, item.mime, item.isVideo, "Restored") { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (uri != null) file.delete()
        return uri != null
    }

    fun delete(item: MediaItem): Boolean {
        val path = item.uri.path ?: return false
        return File(path).delete()
    }

    private companion object {
        const val K_HASH = "hash"
        const val K_SALT = "salt"
        const val K_BIO = "bio"
        const val K_FAILS = "fails"
        const val K_LOCK_UNTIL = "lock_until"
    }
}
