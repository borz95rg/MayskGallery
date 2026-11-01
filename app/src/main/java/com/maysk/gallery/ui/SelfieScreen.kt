package com.maysk.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings

/**
 * Раздел «Селфи» — только просмотр уже сохранённых фронтальных снимков.
 * Камера и разрешение CAMERA здесь намеренно не запрашиваются.
 */
@Composable
fun SelfieScreen(
    items: List<MediaItem>,
    settings: UiSettings,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    // Снимки, сделанные встроенной фронтальной камерой приложения, сохраняются
    // в отдельный альбом Selfies и получают префикс SELFIE_.
    val selfies = items.filter { item ->
        !item.isVideo && (
            item.bucket.equals("Selfies", ignoreCase = true) ||
                item.name.startsWith("SELFIE_", ignoreCase = true)
            )
    }.sortedByDescending { it.dateMillis }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Селфи", actions = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        })
        if (selfies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Селфи пока нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns.coerceIn(2, 4)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(selfies, key = { _, item -> item.key }) { index, item ->
                    MediaThumb(item, settings, false) { onOpen(selfies, index) }
                }
            }
        }
    }
}
