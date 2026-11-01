package com.maysk.gallery.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maysk.gallery.BuildConfig
import com.maysk.gallery.MainViewModel
import com.maysk.gallery.data.AlbumView
import com.maysk.gallery.data.AnimStyle
import com.maysk.gallery.data.Keys
import com.maysk.gallery.data.ThemePreset
import com.maysk.gallery.data.UiSettings
import kotlin.math.roundToInt

/** «Конструктор интерфейса»: всё, что меняет внешний вид, тут. */
@Composable
fun SettingsScreen(vm: MainViewModel, s: UiSettings) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Конструктор интерфейса")

        // Живой предпросмотр
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val colors = listOf(
                listOf(Color(0xFFFF7A59), Color(0xFFB84DFF)),
                listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
                listOf(Color(0xFFFFC83D), Color(0xFFFF3D9A)),
            )
            for (c in colors) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(90.dp)
                        .shadow(s.shadowDp(), s.shape(), clip = false)
                        .clip(s.shape())
                        .background(Brush.linearGradient(c))
                )
            }
        }

        Section("Мои фото по умолчанию")
        val pick = rememberCoverPicker(vm)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Выберите картинки из галереи телефона для главного экрана. Пусто — берутся ваши последние фото. На главной их можно менять и долгим нажатием.",
                color = cs.onSurfaceVariant,
            )
            listOf("Главный баннер", "Альбом 1", "Альбом 2", "Альбом 3", "Альбом 4").forEachIndexed { i, title ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$title: " + if (s.covers[i].isBlank()) "из галереи" else "своё фото",
                        color = cs.onBackground, modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(onClick = { pick(i) }) { Text("Выбрать") }
                    if (s.covers[i].isNotBlank()) androidx.compose.material3.TextButton(
                        onClick = { vm.set(Keys.COVERS[i], "") }
                    ) { Text("Сброс") }
                }
            }
        }

        Section("Тема")
        ChipRow(ThemePreset.values().toList(), s.preset, { it.title }) { vm.set(Keys.PRESET, it.name) }

        Section("Размер карточек (колонок: ${s.columns})")
        SliderRow(s.columns.toFloat(), 2f..8f, 5) { vm.set(Keys.COLUMNS, it.roundToInt()) }

        Section("Закругление: ${s.cornerPercent}%")
        SliderRow(s.cornerPercent.toFloat(), 0f..80f, 0) { vm.set(Keys.CORNER, it.roundToInt()) }

        Section("Прозрачность стекла: ${(s.glassAlpha * 100).toInt()}%")
        SliderRow(s.glassAlpha, 0.2f..1f, 0) { vm.set(Keys.GLASS, it) }

        Section("Тень")
        ChipRow(listOf(0, 1, 2, 3), s.shadow, { listOf("OFF", "LOW", "MEDIUM", "HIGH")[it] }) { vm.set(Keys.SHADOW, it) }

        Section("Анимация появления")
        ChipRow(AnimStyle.values().toList(), s.animation, { it.title }) { vm.set(Keys.ANIM, it.name) }

        Section("Вид альбомов")
        ChipRow(AlbumView.values().toList(), s.albumView, { it.title }) { vm.set(Keys.ALBUM_VIEW, it.name) }

        Section("Панель навигации")
        ChipRow(listOf(false, true), s.navTop, { if (it) "Сверху" else "Снизу" }) { vm.set(Keys.NAV_TOP, it) }

        Section("Размер шрифта: ${(s.fontScale * 100).toInt()}%")
        SliderRow(s.fontScale, 0.8f..1.4f, 0) { vm.set(Keys.FONT, it) }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.resetLook() },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) { Text("Сбросить до оригинала") }

        Section("Обновления")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Проверять обновления при запуске",
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = s.autoUpdate, onCheckedChange = { vm.set(Keys.AUTO_UPDATE, it) })
            }
            Spacer(Modifier.height(8.dp))
            Text("Версия ${BuildConfig.VERSION_NAME}", color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.checkUpdate(manual = true) }) { Text("Проверить обновления") }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SliderRow(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    // steps в Slider — число промежуточных делений
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
