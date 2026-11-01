package com.maysk.gallery.ui

import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaRepository
import com.maysk.gallery.data.UiSettings

@Composable
fun TrashScreen(s: UiSettings, onOpen: (List<MediaItem>, Int) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(emptyList<MediaItem>()) }
    var pending by remember { mutableStateOf<MediaItem?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MediaRepository.loadTrashed(ctx)
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Корзина")
        if (Build.VERSION.SDK_INT < 30) {
            Text(
                "Системная корзина доступна на Android 11 и новее. На этой версии Android удалённые файлы обрабатываются системой.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else if (items.isEmpty()) {
            Text("Корзина пуста", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else {
            Text(
                "Файлы временно хранятся в системной корзине Android. Срок окончательного удаления задаёт система.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(s.columns.coerceIn(2, 4)),
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    Column {
                        MediaThumb(item, s, false) { onOpen(items, items.indexOf(item)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = {
                                val values = android.content.ContentValues().apply {
                                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                                }
                                ctx.contentResolver.update(item.uri, values, null, null)
                                refresh++
                            }) { Text("Вернуть") }
                            TextButton(onClick = { pending = item }) { Text("Удалить") }
                        }
                        Text(
                            "Срок хранения контролирует Android",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Удалить навсегда?") },
            text = { Text("Файл «${item.name}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    ctx.contentResolver.delete(item.uri, null, null)
                    pending = null
                    refresh++
                }) { Text("Удалить навсегда") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}
