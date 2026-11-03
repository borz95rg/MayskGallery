package com.maysk.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maysk.gallery.BuildConfig
import com.maysk.gallery.MainViewModel
import com.maysk.gallery.data.AlbumView
import com.maysk.gallery.data.AnimStyle
import com.maysk.gallery.data.Keys
import com.maysk.gallery.data.ThemePreset
import com.maysk.gallery.data.DockStyle
import com.maysk.gallery.data.UiSettings
import kotlin.math.roundToInt

private val SettingsEmerald = Color(0xFF16C79A)
private val SettingsGold = Color(0xFFFFC83D)

/** Современные настройки: сгруппированные карточки, понятные подписи и живые значения. */
@Composable
fun SettingsScreen(vm: MainViewModel, s: UiSettings) {
    val cs = MaterialTheme.colorScheme
    val pickCover = rememberCoverPicker(vm)
    var customHue by remember { mutableStateOf(330f) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        ScreenTitle("Настройки")

        // Верхняя карточка вместо старого «конструктора»: показывает, что именно настраивается.
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = .72f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Твоя галерея. Твои правила.", color = cs.onSurface, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Настрой внешний вид и поведение приложения под себя.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(SettingsEmerald, SettingsGold))),
                    contentAlignment = Alignment.Center,
                ) { Text("✦", color = Color(0xFF07110E), fontSize = 25.sp) }
            }
        }

        SettingsGroup("ТЕМА И ОФОРМЛЕНИЕ", "Новая система оформления Maysk Gallery") {
            SettingsLabel("Готовые темы", "Цвета карточек, фона и текста меняются одновременно")
            val presets = listOf(
                ThemePreset.DEEP_BLUE, ThemePreset.ARCTIC, ThemePreset.VIOLET, ThemePreset.MINT_COAL,
                ThemePreset.COPPER, ThemePreset.ROYAL_BLUE, ThemePreset.ICE_WIND, ThemePreset.DESERT,
                ThemePreset.EMERALD, ThemePreset.DARK_MAGIC
            )
            presets.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { preset ->
                        val chosen = s.preset == preset
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(when (preset) {
                                    ThemePreset.ARCTIC -> Color(0xFFF8F9FA)
                                    ThemePreset.ICE_WIND -> Color(0xFFE0F2FE)
                                    ThemePreset.DEEP_BLUE -> Color(0xFF0D1B2A)
                                    ThemePreset.VIOLET -> Color(0xFF1A0B2E)
                                    ThemePreset.MINT_COAL -> Color(0xFF12181B)
                                    ThemePreset.COPPER -> Color(0xFF2B1B17)
                                    ThemePreset.ROYAL_BLUE -> Color(0xFF0A192F)
                                    ThemePreset.DESERT -> Color(0xFF2A2421)
                                    ThemePreset.EMERALD -> Color(0xFF0F2027)
                                    else -> Color(0xFF050505)
                                })
                                .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else cs.outline.copy(alpha=.35f), RoundedCornerShape(16.dp))
                                .clickable { vm.set(Keys.PRESET, preset.name); vm.set(Keys.ACCENT, 0) }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text((if (chosen) "✓ " else "") + preset.title,
                                color = if (preset == ThemePreset.ARCTIC || preset == ThemePreset.ICE_WIND) Color(0xFF121212) else Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SettingsLabel("Акцентный цвет", "Быстрые цвета для кнопок и выделений")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF16C79A,0xFFB88CFF,0xFFFF5FB0,0xFFFF7A59,0xFFFFC83D,0xFF4ADE80,0xFF36D1DC,0xFF5B86E5,0xFF007AFF,0xFFE63946).forEach { c ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (s.accent == c.toInt()) 3.dp else 0.dp, cs.onSurface, CircleShape)
                        .clickable { vm.set(Keys.ACCENT,c.toInt()) }, contentAlignment=Alignment.Center) {
                        if (s.accent == c.toInt()) Text("✓", color=Color.Black, fontWeight=FontWeight.Bold)
                    }
                }
                TextButton(onClick={vm.set(Keys.ACCENT,0)}) { Text("Авто") }
            }
            SettingsLabel("Свой цвет", "Передвигай ползунок, чтобы выбрать оттенок")
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color.hsv(customHue, .78f, .95f)))
                Slider(value=customHue, onValueChange={customHue=it}, valueRange=0f..360f, modifier=Modifier.weight(1f))
                TextButton(onClick={vm.set(Keys.ACCENT,Color.hsv(customHue,.78f,.95f).toArgb())}) { Text("Применить") }
            }
            SettingsSlider("Матовость стекла", "${(s.frost*100).toInt()}%", s.frost, 0f..1f) { vm.set(Keys.FROST,it) }
            SettingsSlider("Прозрачность панелей", "${(s.glassAlpha*100).toInt()}%", s.glassAlpha, .2f..1f) { vm.set(Keys.GLASS,it) }
            SettingsSlider("Закругление карточек", "${s.cornerPercent}%", s.cornerPercent.toFloat(), 0f..80f) { vm.set(Keys.CORNER,it.roundToInt()) }
            SettingsLabel("Тень карточек")
            ChipRow(listOf(0,1,2,3),s.shadow,{listOf("Нет","Мягкая","Средняя","Сильная")[it]}) { vm.set(Keys.SHADOW,it) }
        }

        SettingsGroup("НИЖНЯЯ ПАНЕЛЬ", "Плавающее полупрозрачное меню. Контент виден сквозь него") {
            ChipRow(DockStyle.values().toList(), s.dockStyle, { it.title }) { vm.set(Keys.DOCK_STYLE,it.name) }
            SettingsLabel("Цвет панели", "Оттенок стекла (прозрачность зависит от стиля)")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf(0xFF17171C,0xFF25252B,0xFF263B55,0xFF472A5D,0xFF17483F,0xFF6A3544,0xFF8A5A32,0xFFE8EDF3).forEach { c ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (s.dockColor == c.toInt()) 3.dp else 1.dp, cs.onSurface, CircleShape)
                        .clickable { vm.set(Keys.DOCK_COLOR,c.toInt()) })
                }
            }
            SettingsSwitch("Текст на кнопках", "Показывать подписи фото, альбомы и видео", s.dockShowLabels) { vm.set(Keys.DOCK_LABELS,it) }
            val dockBg = Color(s.dockColor).copy(alpha = 0.50f)
            val onDockPreview = if (Color(s.dockColor).luminance() > 0.5f) Color(0xFF121212) else Color.White
            Card(colors=CardDefaults.cardColors(containerColor=dockBg), shape=RoundedCornerShape(28.dp)) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(12.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                    listOf("фото","альбомы","видео").forEach { label ->
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(when(label){"фото"->"▧";"альбомы"->"✦";else->"▷"}, color=onDockPreview, fontSize=18.sp)
                            if (s.dockShowLabels) Text(label, color=onDockPreview, fontSize=11.sp)
                        }
                    }
                }
            }
        }


        SettingsGroup("РАЗМЕР ТЕКСТА", "Изменяй шрифт и проверяй результат в реальном времени") {
            SettingsSlider("Размер шрифта", "${(s.fontScale*100).toInt()}%", s.fontScale, .8f..1.6f) { vm.set(Keys.FONT,it) }
            Card(colors=CardDefaults.cardColors(containerColor=cs.surfaceVariant), shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Предварительный просмотр", color=cs.onSurface, fontSize=11.sp)
                    Text("Maysk Gallery", color=cs.onSurface, fontSize=22.sp, fontWeight=FontWeight.Bold)
                    Text("Красивые моменты всегда рядом", color=cs.onSurfaceVariant, fontSize=14.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={}, enabled=false) { Text("Кнопка", fontSize=14.sp) }
                        Text("Фото  •  Альбомы  •  Видео", color=cs.primary, fontSize=12.sp)
                    }
                }
            }
        }

        SettingsGroup("ФОТО И АЛЬБОМЫ", "Как отображаются воспоминания") {
            SettingsLabel("Обложки главного экрана", "Можно выбрать свои изображения. Если сбросить — используются фото из галереи.")
            listOf("Главный баннер", "Альбом 1", "Альбом 2", "Альбом 3", "Альбом 4").forEachIndexed { i, title ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (s.covers[i].isBlank()) "Автоматическая обложка" else "Своё изображение выбрано", color = cs.onSurfaceVariant, fontSize = 11.sp)
                    }
                    TextButton(onClick = { pickCover(i) }) { Text("Выбрать") }
                    if (s.covers[i].isNotBlank()) TextButton(onClick = { vm.set(Keys.COVERS[i], "") }) { Text("Сброс") }
                }
            }
            SettingsSlider("Колонки в сетке", "${s.columns}", s.columns.toFloat(), 2f..8f) { vm.set(Keys.COLUMNS, it.roundToInt()) }
            SettingsSwitch("Показывать даты", "Отображать дату съёмки рядом с группами фотографий", s.showDates) { vm.set(Keys.SHOW_DATES, it) }
            SettingsSwitch("Группировать по дате", "Разделять общую галерею на дни и периоды", s.groupByDate) { vm.set(Keys.GROUP_BY_DATE, it) }
            SettingsLabel("Стиль альбомов", "Выбери удобный вид списка альбомов")
            ChipRow(AlbumView.values().toList(), s.albumView, { it.title }) { vm.set(Keys.ALBUM_VIEW, it.name) }
        }

        SettingsGroup("БЕЗОПАСНОСТЬ", "Приватность и защита личных файлов") {
            SettingsSwitch("Блокировать сейф при сворачивании", "При возвращении потребуется повторная разблокировка", s.lockOnBackground) { vm.set(Keys.LOCK_ON_BACKGROUND, it) }
            SettingsSwitch("Отпечаток пальца", "Использовать биометрию, если устройство поддерживает её", s.biometricUnlock) { vm.set(Keys.BIOMETRIC_UNLOCK, it) }
            SettingsSwitch("Скрывать содержимое уведомлений", "Не показывать имена и превью личных файлов", s.hideNotificationContent) { vm.set(Keys.HIDE_NOTIFICATION_CONTENT, it) }
        }

        SettingsGroup("КОРЗИНА", "Управление удалёнными файлами") {
            SettingsSwitch("Подтверждать удаление", "Показывать дополнительное подтверждение перед удалением", s.confirmDelete) { vm.set(Keys.CONFIRM_DELETE, it) }
            SettingsLabel("Предпочтительный срок хранения", "На Android срок системной корзины может определяться самой системой")
            ChipRow(listOf(7, 15, 30, 60, 90), s.trashRetentionDays, { "$it дн." }) { vm.set(Keys.TRASH_RETENTION, it) }
        }

        SettingsGroup("ХРАНИЛИЩЕ", "Контроль места и файлов") {
            SettingsSwitch("Подготавливать поиск дубликатов", "Разрешить анализ файлов для поиска совпадений", s.duplicateScanEnabled) { vm.set(Keys.DUPLICATE_SCAN, it) }
            SettingsSwitch("Подготовить автокопирование", "Сохранить предпочтение; перед запуском нужно выбрать безопасное место для копий", s.autoBackupEnabled) { vm.set(Keys.AUTO_BACKUP, it) }
            SettingsLabel("Очистка кэша", "Кэш миниатюр можно очищать отдельно от оригиналов фотографий")
        }

        SettingsGroup("ВОСПОМИНАНИЯ", "Подборки по датам и прошлым годам") {
            SettingsSwitch("Показывать воспоминания", "Раздел воспоминаний на главной странице", s.memoriesEnabled) { vm.set(Keys.MEMORIES_ENABLED, it) }
            SettingsLabel("Уведомления о воспоминаниях", "Планирование уведомлений будет добавлено отдельно; сейчас воспоминания доступны на главной странице.")
        }

        SettingsGroup("ВИДЖЕТ", "Фотографии на главном экране телефона") {
            SettingsSwitch("Случайные фотографии", "Менять изображение виджета при обновлении", s.widgetRandom) { vm.set(Keys.WIDGET_RANDOM, it) }
            SettingsSwitch("Размывать миниатюру", "Показывать мягко размытое изображение в виджете", s.widgetBlur) { vm.set(Keys.WIDGET_BLUR, it) }
        }

        SettingsGroup("ДОПОЛНИТЕЛЬНО", "Язык, даты и воспроизведение") {
            SettingsLabel("Язык приложения", "Выбор сохраняется; полная локализация интерфейса подключается отдельно.")
            ChipRow(listOf("system", "ru", "kk", "en"), s.language, { when (it) { "system" -> "Системный"; "ru" -> "Русский"; "kk" -> "Қазақша"; else -> "English" } }) { vm.set(Keys.LANGUAGE, it) }
            SettingsLabel("Формат даты", "Предпочтение сохраняется; применение формата ко всем экранам дат требует отдельной локализации.")
            ChipRow(listOf("system", "dmy", "mdy"), s.dateFormat, { when (it) { "dmy" -> "ДД.ММ.ГГГГ"; "mdy" -> "ММ/ДД/ГГГГ"; else -> "Системный" } }) { vm.set(Keys.DATE_FORMAT, it) }
            SettingsSlider("Интервал слайд-шоу", "${s.slideshowSeconds} сек.", s.slideshowSeconds.toFloat(), 2f..15f) { vm.set(Keys.SLIDESHOW_SECONDS, it.roundToInt()) }
            SettingsLabel("Анимация появления")
            ChipRow(AnimStyle.values().toList(), s.animation, { it.title }) { vm.set(Keys.ANIM, it.name) }
        }

        SettingsGroup("ОБНОВЛЕНИЯ", "Версия и актуальность приложения") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Автоматически проверять", color = cs.onSurface, fontWeight = FontWeight.Medium)
                    Text("Проверка новых версий при запуске", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(checked = s.autoUpdate, onCheckedChange = { vm.set(Keys.AUTO_UPDATE, it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Текущая версия", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = { vm.checkUpdate(manual = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить обновления")
            }
        }

        TextButton(
            onClick = { vm.resetLook() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text("Вернуть оригинальное оформление", color = cs.onSurfaceVariant) }

        Text(
            "Настройки сохраняются автоматически",
            color = cs.onSurfaceVariant.copy(alpha = .75f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = cs.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = .48f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = SettingsEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(subtitle, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsLabel(title: String, description: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (description != null) Text(description, color = cs.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = cs.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = SettingsGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}
