package com.maysk.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maysk.gallery.data.FaceInfo
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaMeta
import com.maysk.gallery.data.SelfieIndex
import com.maysk.gallery.data.UiSettings

/**
 * Раздел «Селфи»: фото, где ML Kit нашёл крупное лицо, а также снимки с «селфи»-признаками в имени/EXIF.
 * Любое фото можно отметить вручную (кнопка «+») или убрать из раздела (крестик): ручная отметка главнее.
 */
@Composable
fun SelfieScreen(
    items: List<MediaItem>,
    meta: Map<String, MediaMeta>,
    faces: Map<String, FaceInfo>,
    yes: Set<String>,
    no: Set<String>,
    progress: Pair<Int, Int>?,
    settings: UiSettings,
    onBack: () -> Unit,
    onMark: (MediaItem, Boolean) -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val photos = remember(items) { items.filter { !it.isVideo } }
    val selfies = remember(photos, meta, faces, yes, no) {
        photos.filter { SelfieIndex.isSelfie(it, meta[it.key], faces[it.key], yes, no) }.sortedByDescending { it.dateMillis }
    }
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Селфи", actions = {
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Отметить фото как селфи")
            }
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        })
        progress?.let { (done, total) ->
            Text(
                "Ищу лица на фото: $done из $total…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (selfies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (progress != null) "Идёт поиск лиц, селфи появятся здесь" else "Селфи пока нет. Нажмите «+», чтобы отметить фото вручную",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns.coerceIn(2, 4)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(selfies, key = { _, item -> item.key }) { index, item ->
                    Box {
                        MediaThumb(item, settings, false) { onOpen(selfies, index) }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onMark(item, false) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Это не селфи", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        val selfieKeys = remember(selfies) { selfies.mapTo(HashSet()) { it.key } }
        val candidates = remember(photos, selfieKeys) { photos.filter { it.key !in selfieKeys }.take(400) }
        Dialog(onDismissRequest = { picking = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    ScreenTitle("Выберите селфи", actions = {
                        TextButton(onClick = { picking = false }) { Text("Готово") }
                    })
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(candidates, key = { it.key }) { item ->
                            MediaThumb(item, settings, false) { onMark(item, true) }
                        }
                    }
                }
            }
        }
    }
}
