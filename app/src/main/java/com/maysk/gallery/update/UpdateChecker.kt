package com.maysk.gallery.update

import com.maysk.gallery.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
)

/**
 * Смотрит последний релиз в GitHub:
 * https://github.com/<owner>/<repo>/releases/latest
 * и сравнивает тег (v1.2.0) с версией установленного приложения.
 */
object UpdateChecker {

    /** Успех с null — значит, обновлений нет. */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MayskGallery-Updater")
            }
            try {
                when (conn.responseCode) {
                    404 -> null // релизов ещё нет (или репозиторий закрыт)
                    200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                    else -> error("HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parse(body: String): UpdateInfo? {
        val json = JSONObject(body)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.getString("tag_name")
        if (!isNewer(tag, BuildConfig.VERSION_NAME)) return null

        val assets = json.optJSONArray("assets") ?: return null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apk = a.getString("browser_download_url")
                break
            }
        }
        if (apk == null) return null // релиз без APK — пока не предлагаем

        return UpdateInfo(
            version = tag.trimStart('v', 'V'),
            notes = json.optString("body").take(1500),
            apkUrl = apk,
            pageUrl = json.optString("html_url"),
        )
    }

    /** "v1.10.0" новее "1.9.3"; "-dev" и прочие хвосты игнорируются. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = numbers(remote)
        val l = numbers(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(v: String): List<Int> =
        v.trimStart('v', 'V').substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
