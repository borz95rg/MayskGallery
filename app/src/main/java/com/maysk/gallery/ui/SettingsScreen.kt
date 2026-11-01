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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maysk.gallery.BuildConfig
import com.maysk.gallery.MainViewModel
import com.maysk.gallery.data.AlbumView
import com.maysk.gallery.data.AnimStyle
import com.maysk.gallery.data.Keys
import com.maysk.gallery.data.ThemePreset
import com.maysk.gallery.data.UiSettings
import kotlin.math.roundToInt

private val SettingsEmerald = Color(0xFF16C79A)
private val SettingsGold = Color(0xFFFFC83D)

/** Современные настройки: сгруппированные карточки, понятные подписи и живые значения. */
@Composable
fun SettingsScreen(vm: MainViewModel, s: UiSettings) {
    val cs = MaterialTheme.colorScheme
    val pickCover = rememberCoverPicker(vm)

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

        SettingsGroup("ВНЕШНИЙ ВИД", "Тема, цвета и оформление") {
            SettingsLabel("Тема галереи", "Структура оригинальной галереи сохраняется во всех темах")
            ChipRow(ThemePreset.values().toList(), s.preset, { it.title }) { vm.set(Keys.PRESET, it.name) }

            SettingsLabel("Акцентный цвет", "Выбери цвет кнопок и выделений")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    0xFF16C79A, 0xFFB88CFF, 0xFFFF5FB0, 0xFFFF7A59,
                    0xFFFFC83D, 0xFF4ADE80, 0xFF36D1DC, 0xFF5B86E5,
                ).forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .then(if (s.accent == c.toInt()) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable { vm.set(Keys.ACCENT, c.toInt()) },
                        contentAlignment = Alignment.Center,
                    ) { if (s.accent == c.toInt()) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = { vm.set(Keys.ACCENT, 0) }) { Text("Авто") }
            }
            SettingsSlider("Матовость стекла", "${(s.frost * 100).toInt()}%", s.frost, 0f..1f) { vm.set(Keys.FROST, it) }
            SettingsSlider("Прозрачность панелей", "${(s.glassAlpha * 100).toInt()}%", s.glassAlpha, .2f..1f) { vm.set(Keys.GLASS, it) }
            SettingsSlider("Закругление карточек", "${s.cornerPercent}%", s.cornerPercent.toFloat(), 0f..80f) { vm.set(Keys.CORNER, it.roundToInt()) }
            SettingsLabel("Тень карточек", "Насколько выражен объём элементов")
            ChipRow(listOf(0, 1, 2, 3), s.shadow, { listOf("Нет", "Мягкая", "Средняя", "Сильная")[it] }) { vm.set(Keys.SHADOW, it) }
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
            SettingsLabel("Стиль альбомов", "Выбери удобный вид списка альбомов")
            ChipRow(AlbumView.values().toList(), s.albumView, { it.title }) { vm.set(Keys.ALBUM_VIEW, it.name) }
        }

        SettingsGroup("УДОБСТВО", "Навигация и анимация") {
            SettingsLabel("Положение панели навигации", "Выбери, где удобнее переключать разделы")
            ChipRow(listOf(false, true), s.navTop, { if (it) "Сверху" else "Снизу" }) { vm.set(Keys.NAV_TOP, it) }
            SettingsLabel("Анимация появления")
            ChipRow(AnimStyle.values().toList(), s.animation, { it.title }) { vm.set(Keys.ANIM, it.name) }
            SettingsSlider("Размер текста", "${(s.fontScale * 100).toInt()}%", s.fontScale, .8f..1.4f) { vm.set(Keys.FONT, it) }
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
