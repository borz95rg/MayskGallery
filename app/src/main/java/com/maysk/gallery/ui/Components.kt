package com.maysk.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maysk.gallery.data.AnimStyle
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Превью фото/видео. Скругление, тень и анимация берутся из настроек. */
@Composable
fun MediaThumb(
    item: MediaItem,
    s: UiSettings,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = s.shape()
    val progress = remember { Animatable(if (s.animation == AnimStyle.NONE) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            if (s.animation == AnimStyle.ELASTIC) {
                progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            } else {
                progress.animateTo(1f, tween(350))
            }
        }
    }
    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val p = progress.value
                when (s.animation) {
                    AnimStyle.NONE -> Unit
                    AnimStyle.FADE -> alpha = p
                    AnimStyle.ZOOM -> {
                        alpha = p
                        scaleX = 0.8f + 0.2f * p
                        scaleY = 0.8f + 0.2f * p
                    }
                    AnimStyle.SLIDE -> {
                        alpha = p
                        translationY = (1f - p) * 60f
                    }
                    AnimStyle.ELASTIC -> {
                        scaleX = p
                        scaleY = p
                    }
                }
            }
            .shadow(s.shadowDp(), shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 11.sp)
            }
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, null,
                tint = Color(0xFFFF4D8D),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
            )
        }
    }
}

/** Простая сетка без заголовков: для альбома и результатов поиска. */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    s: UiSettings,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(s.columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
        }
        items(items, key = { it.key }) { item ->
            MediaThumb(item, s, item.key in s.favorites) { onOpen(items.indexOf(item)) }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val serif = MaterialTheme.typography.headlineSmall.fontFamily == androidx.compose.ui.text.font.FontFamily.Serif
        Text(
            if (serif) text.lowercase() else text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (serif) FontWeight.Normal else FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(options) { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}
