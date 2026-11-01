package com.maysk.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun sectionTitle(millis: Long, today: LocalDate): String {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        d == today -> "Сегодня"
        d == today.minusDays(1) -> "Вчера"
        d.isAfter(today.minusDays(7)) -> "Эта неделя"
        else -> {
            val month = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
                .replaceFirstChar { it.uppercase() }
            "$month ${d.year}"
        }
    }
}

@Composable
fun GalleryScreen(
    items: List<MediaItem>,
    s: UiSettings,
    onOpen: (list: List<MediaItem>, index: Int) -> Unit,
) {
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    val shown = remember(items, s.favorites, onlyFav) {
        if (onlyFav) items.filter { it.key in s.favorites } else items
    }
    val sections = remember(shown, s.groupByDate, s.showDates) {
        if (!s.groupByDate) listOf("" to shown)
        else {
            val today = LocalDate.now()
            shown.groupBy { sectionTitle(it.dateMillis, today) }.toList()
        }
    }
    val indexOf = remember(shown) { shown.withIndex().associate { it.value.key to it.index } }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Maysk Gallery") {
            IconButton(onClick = { onlyFav = !onlyFav }) {
                Icon(
                    if (onlyFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = if (onlyFav) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (onlyFav) "В избранном пока пусто" else "Фото и видео не найдены",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((title, list) in sections) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$title") {
                    if (s.showDates && title.isNotBlank()) Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.key }) { item ->
                    MediaThumb(item, s, item.key in s.favorites) {
                        onOpen(shown, indexOf[item.key] ?: 0)
                    }
                }
            }
        }
    }
}
