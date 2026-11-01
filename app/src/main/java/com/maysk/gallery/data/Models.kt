package com.maysk.gallery.data

import android.net.Uri

/** Один снимок или видео из MediaStore. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val mime: String,
    val dateMillis: Long,
    val bucket: String,
    val durationMs: Long,
) {
    /** Уникален среди фото и видео (id у них могут совпадать). */
    val key: String get() = (if (isVideo) "v" else "i") + id
}

data class AlbumInfo(val name: String, val items: List<MediaItem>) {
    val cover: MediaItem get() = items.first()
    val count: Int get() = items.size
}

enum class ThemePreset(val title: String) {
    ORIGINAL("Original"),
    CLASSIC("Classic"),
    MODERN("Modern"),
    GLASS("Glass"),
    LUXURY("Luxury"),
    NEON("Neon"),
    MINIMAL("Minimal"),
    ISTYLE("iStyle"),
}

enum class AnimStyle(val title: String) {
    NONE("Нет"),
    FADE("Fade"),
    ZOOM("Zoom"),
    SLIDE("Slide"),
    ELASTIC("Elastic"),
}

enum class AlbumView(val title: String) {
    CARDS("Карточки"),
    TILES("Плитка"),
    LIST("Список"),
    CIRCLES("Круги"),
    GLASS("Стекло"),
    MINIMAL("Минимализм"),
}

/** Все настройки «Конструктора интерфейса». */
data class UiSettings(
    val preset: ThemePreset = ThemePreset.ORIGINAL,
    val columns: Int = 3,
    val cornerPercent: Int = 40,   // 0..80
    val glassAlpha: Float = 0.75f, // 0.2..1
    val shadow: Int = 1,           // 0 OFF, 1 LOW, 2 MEDIUM, 3 HIGH
    val animation: AnimStyle = AnimStyle.FADE,
    val albumView: AlbumView = AlbumView.CARDS,
    val navTop: Boolean = false,
    val fontScale: Float = 1f,
    val autoUpdate: Boolean = true,
    val favorites: Set<String> = emptySet(),
    /** Свои картинки: 0 = баннер, 1..4 = обложки альбомов. Пусто = брать из галереи. */
    val covers: List<String> = List(5) { "" },
    /** Акцентный цвет ARGB; 0 = взять из темы. */
    val accent: Int = 0,
    /** Матовость стекла 0..1 (светлая дымка на стеклянных панелях). */
    val frost: Float = 0f,
)
