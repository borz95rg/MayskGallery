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
    /** Unix epoch millis when MediaStore plans to permanently remove a trashed item; 0 if unknown. */
    val expiresAtMillis: Long = 0L,
    val sizeBytes: Long = 0L,
) {
    /** Уникален среди фото и видео (id у них могут совпадать). */
    val key: String get() = (if (isVideo) "v" else "i") + id
}

data class AlbumInfo(val name: String, val items: List<MediaItem>) {
    val cover: MediaItem get() = items.first()
    val count: Int get() = items.size
}

enum class ThemePreset(val title: String) {
    DEEP_BLUE("Глубокий Синий"),
    ARCTIC("Арктический Свет"),
    VIOLET("Фиолетовое Поле"),
    MINT_COAL("Мята и Уголь"),
    COPPER("Медный Вечер"),
    ROYAL_BLUE("Королевский Синий"),
    ICE_WIND("Ледяной Ветер"),
    DESERT("Пустынная Дюна"),
    EMERALD("Emerald Haven"),
    DARK_MAGIC("Тёмная магия"),
    // Совместимость со старыми сохранёнными настройками
    ORIGINAL("Original"), CLASSIC("Classic"), MODERN("Modern"),
    GLASS("Glass"), LUXURY("Luxury"), NEON("Neon"), MINIMAL("Minimal"), ISTYLE("iStyle")
}

enum class DockStyle(val title: String) {
    HIDDEN("Скрытая"),
    FLOATING("Стекло · капсула"),      // оригинал с большой круглой кнопкой
    PILL("Стекло · пилюля"),           // ровная полупрозрачная полоса
    UNDERLINE("Подчёркивание"),        // тонкая линия под активным
    OUTLINE("Обводка"),                // активный в рамке
    GLOW("Свечение"),                  // активный с glow
    MINIMAL("Минимализм"),             // только иконки + тонкая линия
    FLAT("Плоская"),
    VOLUMETRIC("Объёмная"),
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
    val dockStyle: DockStyle = DockStyle.FLOATING,
    val dockColor: Int = 0xFF25252B.toInt(),
    val dockShowLabels: Boolean = true,
    val dockHeight: Int = 110,
    val autoUpdate: Boolean = true,
    val favorites: Set<String> = emptySet(),
    /** Свои картинки: 0 = баннер, 1..4 = обложки альбомов. Пусто = брать из галереи. */
    val covers: List<String> = List(5) { "" },
    /** Акцентный цвет ARGB; 0 = взять из темы. */
    val accent: Int = 0,
    /** Матовость стекла 0..1 (светлая дымка на стеклянных панелях). */
    val frost: Float = 0f,
    val showDates: Boolean = true,
    val groupByDate: Boolean = true,
    val confirmDelete: Boolean = true,
    val lockOnBackground: Boolean = true,
    val biometricUnlock: Boolean = true,
    val hideNotificationContent: Boolean = true,
    val trashRetentionDays: Int = 30,
    val memoriesEnabled: Boolean = true,
    val memoriesNotifications: Boolean = false,
    val widgetRandom: Boolean = false,
    val widgetBlur: Boolean = true,
    val slideshowSeconds: Int = 4,
    val duplicateScanEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val language: String = "system",
    val dateFormat: String = "system",
)
