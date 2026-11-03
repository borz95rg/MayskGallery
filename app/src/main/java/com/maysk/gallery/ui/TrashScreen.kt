package com.maysk.gallery.ui

import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    var confirmClearAll by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refresh) {
        items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MediaRepository.loadTrashed(ctx)
        }
    }

    val systemActionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Перечитываем MediaStore после подтверждения/отмены системного диалога.
        refresh++
        if (result.resultCode == android.app.Activity.RESULT_OK) message = null
    }

    fun launchSystemRequest(sender: IntentSender) {
        systemActionLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Корзина")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("В корзине: ${items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (items.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                TextButton(onClick = { confirmClearAll = true }) { Text("Очистить всё") }
            }
        }

        if (Build.VERSION.SDK_INT < 30) {
            Text(
                "На этой версии Android системная корзина MediaStore не поддерживается. Удалённые ранее файлы здесь не отображаются.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else if (items.isEmpty()) {
            Text("Корзина пуста", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else {
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
                                try {
                                    // Используем системный запрос восстановления: Android сам
                                    // показывает разрешение на изменение чужого MediaStore-файла.
                                    val request = MediaStore.createTrashRequest(
                                        ctx.contentResolver, listOf(item.uri), false
                                    )
                                    launchSystemRequest(request.intentSender)
                                } catch (e: Exception) {
                                    message = "Не удалось восстановить файл: ${e.message ?: "ошибка доступа"}"
                                }
                            }) { Text("Восстановить") }
                            TextButton(onClick = { pending = item }) { Text("Удалить") }
                        }
                        val remaining = item.expiresAtMillis - System.currentTimeMillis()
                        val remainingText = if (item.expiresAtMillis <= 0L) "Срок хранения задаёт Android"
                            else if (remaining <= 0L) "Скоро будет удалено системой"
                            else "Осталось: ${remaining / 86_400_000L} дн. ${(remaining % 86_400_000L) / 3_600_000L} ч."
                        Text(
                            remainingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Очистить корзину полностью?") },
            text = { Text("Будут безвозвратно удалены все ${items.size} фото и видео из корзины. Android может запросить подтверждение.") },
            confirmButton = {
                TextButton(onClick = {
                    val uris = items.map { it.uri }
                    confirmClearAll = false
                    try {
                        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
                            val request = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                            launchSystemRequest(request.intentSender)
                        }
                    } catch (e: Exception) {
                        message = "Не удалось очистить корзину: ${e.message ?: "ошибка доступа"}"
                        refresh++
                    }
                }) { Text("Удалить всё") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Отмена") } },
        )
    }

    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Удалить навсегда?") },
            text = { Text("Файл «${item.name}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val request = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(item.uri))
                            pending = null
                            launchSystemRequest(request.intentSender)
                        } else {
                            ctx.contentResolver.delete(item.uri, null, null)
                            pending = null
                            refresh++
                        }
                    } catch (e: Exception) {
                        message = "Не удалось удалить файл: ${e.message ?: "ошибка доступа"}"
                        pending = null
                        refresh++
                    }
                }) { Text("Удалить навсегда") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}
