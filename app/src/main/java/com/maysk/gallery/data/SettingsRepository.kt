package com.maysk.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "maysk_settings")

object Keys {
    val PRESET = stringPreferencesKey("preset")
    val COLUMNS = intPreferencesKey("columns")
    val CORNER = intPreferencesKey("corner")
    val GLASS = floatPreferencesKey("glass")
    val SHADOW = intPreferencesKey("shadow")
    val ANIM = stringPreferencesKey("anim")
    val ALBUM_VIEW = stringPreferencesKey("album_view")
    val NAV_TOP = booleanPreferencesKey("nav_top")
    val FONT = floatPreferencesKey("font")
    val AUTO_UPDATE = booleanPreferencesKey("auto_update")
    val FAVORITES = stringSetPreferencesKey("favorites")
    val ACCENT = intPreferencesKey("accent")
    val FROST = floatPreferencesKey("frost")
    val COVERS = List(5) { stringPreferencesKey("cover$it") }
}

class SettingsRepository(private val context: Context) {

    val flow: Flow<UiSettings> = context.dataStore.data.map { p ->
        val d = UiSettings()
        UiSettings(
            preset = enumOr(p[Keys.PRESET], d.preset),
            columns = (p[Keys.COLUMNS] ?: d.columns).coerceIn(2, 8),
            cornerPercent = (p[Keys.CORNER] ?: d.cornerPercent).coerceIn(0, 80),
            glassAlpha = (p[Keys.GLASS] ?: d.glassAlpha).coerceIn(0.2f, 1f),
            shadow = (p[Keys.SHADOW] ?: d.shadow).coerceIn(0, 3),
            animation = enumOr(p[Keys.ANIM], d.animation),
            albumView = enumOr(p[Keys.ALBUM_VIEW], d.albumView),
            navTop = p[Keys.NAV_TOP] ?: d.navTop,
            fontScale = (p[Keys.FONT] ?: d.fontScale).coerceIn(0.8f, 1.4f),
            autoUpdate = p[Keys.AUTO_UPDATE] ?: d.autoUpdate,
            favorites = p[Keys.FAVORITES] ?: d.favorites,
            covers = Keys.COVERS.map { p[it] ?: "" },
            accent = p[Keys.ACCENT] ?: d.accent,
            frost = (p[Keys.FROST] ?: d.frost).coerceIn(0f, 1f),
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    /** «Сбросить до оригинала»: внешний вид и свои картинки; избранное и автообновление остаются. */
    suspend fun resetLook() {
        context.dataStore.edit { p ->
            listOf(Keys.PRESET, Keys.COLUMNS, Keys.CORNER, Keys.GLASS, Keys.SHADOW, Keys.ANIM,
                Keys.ALBUM_VIEW, Keys.NAV_TOP, Keys.FONT, Keys.ACCENT, Keys.FROST).forEach { p.remove(it) }
            Keys.COVERS.forEach { p.remove(it) }
        }
    }

    suspend fun toggleFavorite(key: String) {
        context.dataStore.edit { p ->
            val cur = p[Keys.FAVORITES] ?: emptySet()
            p[Keys.FAVORITES] = if (key in cur) cur - key else cur + key
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
