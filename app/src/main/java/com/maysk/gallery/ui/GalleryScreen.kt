package com.maysk.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private val ru = Locale("ru")
private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", ru)
private val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
private fun monthTitle(month: YearMonth) = month.atDay(1).format(monthFormat).replaceFirstChar { it.uppercase(ru) }
private fun dayTitle(day: LocalDate): String = when (day) {
    LocalDate.now() -> "Сегодня"
    LocalDate.now().minusDays(1) -> "Вчера"
    else -> day.format(dayFormat).replaceFirstChar { it.uppercase(ru) }
}

@Composable
fun GalleryScreen(
    items: List<MediaItem>,
    s: UiSettings,
    onOpen: (list: List<MediaItem>, index: Int) -> Unit,
    onDeleteSelected: (List<MediaItem>) -> Unit = {},
) {
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val shown = remember(items, s.favorites, onlyFav) {
        if (onlyFav) items.filter { it.key in s.favorites } else items
    }
    val selected = remember(shown, selectedKeys) { shown.filter { it.key in selectedKeys } }
    val months = remember(shown) {
        shown.groupBy { YearMonth.from(localDate(it.dateMillis)) }.toList().sortedByDescending { it.first }
    }
    val indexOf = remember(shown) { shown.withIndex().associate { it.value.key to it.index } }

    fun toggleGroup(group: List<MediaItem>) {
        val keys = group.map { it.key }.toSet()
        selectedKeys = if (selectedKeys.containsAll(keys)) selectedKeys - keys else selectedKeys + keys
    }
    fun toggleItem(item: MediaItem) {
        selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
        if (selectedKeys.isEmpty()) selecting = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(if (selecting) "Выбрано: ${selected.size}" else "Maysk Gallery") {
            if (selecting) {
                TextButton(onClick = { selectedKeys = shown.map { it.key }.toSet() }) { Text("Выбрать всё") }
                TextButton(onClick = { selectedKeys = emptySet(); selecting = false }) { Text("Отмена") }
            } else {
                TextButton(onClick = { selecting = true }) { Text("Выбрать") }
                IconButton(onClick = { onlyFav = !onlyFav }) {
                    Icon(if (onlyFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (onlyFav) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Выбирайте отдельные файлы, дни или целые месяцы",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Button(onClick = { onDeleteSelected(selected) }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Удалить ${selected.size}")
                }
            }
        }
        if (shown.isEmpty()) {
            Text(if (onlyFav) "В избранном пока пусто" else "Фото и видео не найдены",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.columns), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (s.groupByDate || selecting) {
                months.forEach { (month, monthItems) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "month_$month") {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(monthTitle(month), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f))
                            if (selecting) TextButton(onClick = { toggleGroup(monthItems) }) {
                                Text(if (monthItems.all { it.key in selectedKeys }) "Снять месяц" else "Выбрать месяц")
                            }
                        }
                    }
                    val days = monthItems.groupBy { localDate(it.dateMillis) }.toList().sortedByDescending { it.first }
                    days.forEach { (day, dayItems) ->
                        if (s.showDates) item(span = { GridItemSpan(maxLineSpan) }, key = "day_$day") {
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 5.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(dayTitle(day), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                if (selecting) TextButton(onClick = { toggleGroup(dayItems) }) {
                                    Text(if (dayItems.all { it.key in selectedKeys }) "Снять день" else "Выбрать день")
                                }
                            }
                        }
                        items(dayItems, key = { it.key }) { item ->
                            val checked = item.key in selectedKeys
                            Box {
                                MediaThumb(item, s, item.key in s.favorites) {
                                    if (selecting) toggleItem(item) else onOpen(shown, indexOf[item.key] ?: 0)
                                }
                                if (selecting) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .65f))
                                        .border(1.dp, Color.White, CircleShape).padding(3.dp)) {
                                        Icon(if (checked) Icons.Filled.CheckCircle else Icons.Filled.Check,
                                            contentDescription = if (checked) "Выбрано" else "Выбрать", tint = Color.White)
                                    }
                                    if (checked) Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, s.shape()))
                                }
                            }
                        }
                    }
                }
            } else {
                items(shown, key = { it.key }) { item ->
                    val checked = item.key in selectedKeys
                    Box {
                        MediaThumb(item, s, item.key in s.favorites) {
                            if (selecting) toggleItem(item) else onOpen(shown, indexOf[item.key] ?: 0)
                        }
                        if (selecting) {
                            if (checked) Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, s.shape()))
                            Icon(if (checked) Icons.Filled.CheckCircle else Icons.Filled.Check, null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}
