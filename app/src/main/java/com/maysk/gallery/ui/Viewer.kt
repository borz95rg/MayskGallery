package com.maysk.gallery.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Полноэкранный просмотр.
 * Свайп влево/вправо — следующее фото, вниз — закрыть, вверх — информация,
 * двойной тап — увеличение.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Viewer(
    items: List<MediaItem>,
    startIndex: Int,
    s: UiSettings,
    labels: Map<String, List<String>>,
    onClose: () -> Unit,
    onFavorite: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    secretMode: Boolean = false,
    onEdit: (MediaItem) -> Unit = {},
    onHide: (MediaItem) -> Unit = {},
    onRestore: (MediaItem) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    val current = items.getOrNull(pager.currentPage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ViewerPage(
                item = items[page],
                zoomed = zoomed && page == pager.currentPage,
                secretMode = secretMode,
                onZoom = { zoomed = it },
                onClose = onClose,
                onInfo = { showInfo = true },
            )
        }

        Row(
            Modifier.systemBarsPadding().fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть", tint = Color.White)
            }
            Text(
                "${pager.currentPage + 1} из ${items.size}",
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (current != null) {
            val fav = current.key in s.favorites
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = 8.dp)
                    .glass(s, CircleShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secretMode) {
                    IconButton(onClick = { onRestore(current) }) {
                        Icon(Icons.Filled.LockOpen, "Вернуть в галерею", tint = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    IconButton(onClick = { share(ctx, current) }) {
                        Icon(Icons.Filled.Share, "Поделиться", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { onFavorite(current) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Избранное",
                            tint = if (fav) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { if (current.isVideo) edit(ctx, current) else onEdit(current) }) {
                        Icon(Icons.Filled.Edit, "Изменить", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { onHide(current) }) {
                        Icon(Icons.Filled.Lock, "Спрятать в сейф", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = { onDelete(current) }) {
                    Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (showInfo && current != null) {
            // тап по затемнению закрывает панель
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showInfo = false }
            )
            InfoPanel(current, labels[current.key].orEmpty(), s, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ViewerPage(
    item: MediaItem,
    secretMode: Boolean,
    zoomed: Boolean,
    onZoom: (Boolean) -> Unit,
    onClose: () -> Unit,
    onInfo: () -> Unit,
) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(zoomed) {
        if (!zoomed) {
            scale = 1f
            offset = Offset.Zero
        } else if (scale == 1f) {
            scale = 2.5f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // scale читается в момент тапа, поэтому значение всегда свежее
                detectTapGestures(onDoubleTap = { onZoom(scale <= 1f) })
            }
            .pointerInput(zoomed) {
                if (zoomed) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offset += drag
                    }
                } else {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 250f) onClose() else if (dragY < -150f) onInfo()
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { _, amount -> dragY += amount }
                }
            }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        if (item.isVideo) {
            IconButton(
                onClick = {
                    if (secretMode) {
                        android.widget.Toast.makeText(ctx, "Верните видео из сейфа, чтобы воспроизвести", android.widget.Toast.LENGTH_SHORT).show()
                    } else playVideo(ctx, item)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.Filled.PlayArrow, "Воспроизвести", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun InfoPanel(item: MediaItem, labels: List<String>, s: UiSettings, modifier: Modifier) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(12.dp)
            .glass(s)
            .padding(18.dp)
    ) {
        Text(item.name, color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Дата: ${fmt.format(Date(item.dateMillis))}", color = cs.onSurfaceVariant)
        Text("Альбом: ${item.bucket}", color = cs.onSurfaceVariant)
        Text("Тип: ${item.mime}", color = cs.onSurfaceVariant)
        if (item.isVideo) Text("Длительность: ${formatDuration(item.durationMs)}", color = cs.onSurfaceVariant)
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("ИИ видит: ${labels.joinToString(", ")}", color = cs.primary)
        }
    }
}

private fun share(ctx: Context, item: MediaItem) {
    val i = Intent(Intent.ACTION_SEND)
        .setType(item.mime)
        .putExtra(Intent.EXTRA_STREAM, item.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun edit(ctx: Context, item: MediaItem) {
    val i = Intent(Intent.ACTION_EDIT)
        .setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(i, "Изменить").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(ctx, "Нет приложения для редактирования", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun playVideo(ctx: Context, item: MediaItem) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(ctx, "Нет видеоплеера", android.widget.Toast.LENGTH_SHORT).show()
    }
}
