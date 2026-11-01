package com.maysk.gallery.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** Дополнительные инструменты в визуальном языке исходной галереи. */
@Composable
fun ExtraFeaturesScreen(
    feature: String,
    items: List<MediaItem>,
    s: UiSettings,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onBack: () -> Unit,
) {
    when (feature) {
        "memories" -> MemoriesFeature(items, s, onOpen, onBack)
        "duplicates" -> DuplicatesFeature(items, s, s.duplicateScanEnabled, onOpen, onBack)
        "collage" -> CollageFeature(items.filter { !it.isVideo }, s, onBack)
        "slideshow" -> SlideshowFeature(items.filter { !it.isVideo }, s, onOpen, onBack)
        "map" -> MapFeature(items.filter { !it.isVideo }, s, onBack)
        else -> MemoriesFeature(items, s, onOpen, onBack)
    }
}

@Composable
private fun MemoriesFeature(items: List<MediaItem>, s: UiSettings, onOpen: (List<MediaItem>, Int) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val years = remember(items) { items.map { java.time.Instant.ofEpochMilli(it.dateMillis).atZone(java.time.ZoneId.systemDefault()).year }.distinct().sortedDescending() }
    var year by remember(items) { mutableIntStateOf(years.firstOrNull() ?: java.time.LocalDate.now().year) }
    val selected = remember(items, year) { items.filter { java.time.Instant.ofEpochMilli(it.dateMillis).atZone(java.time.ZoneId.systemDefault()).year == year } }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Воспоминания")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            years.forEach { y -> TextButton(onClick = { year = y }) { Text(y.toString(), color = if (y == year) cs.primary else cs.onSurfaceVariant) } }
        }
        Text("${selected.size} фото и видео за $year год", color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        MediaGrid(selected, s, onOpen = { onOpen(selected, it) })
    }
}

@Composable
private fun DuplicatesFeature(items: List<MediaItem>, s: UiSettings, enabled: Boolean, onOpen: (List<MediaItem>, Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<List<MediaItem>>>(emptyList()) }
    LaunchedEffect(items, enabled) {
        if (!enabled) { groups = emptyList(); scanning = false; return@LaunchedEffect }
        scanning = true
        groups = withContext(Dispatchers.IO) {
            // SHA-256 по содержимому: одинаковые имена не считаются доказательством дубликата.
            val candidates = items.filter { it.sizeBytes > 0L }.groupBy { "${it.sizeBytes}:${it.mime}" }.values.filter { it.size > 1 }.flatten()
            val hashes = candidates.mapNotNull { item ->
                val digest = runCatching {
                    val md = MessageDigest.getInstance("SHA-256")
                    ctx.contentResolver.openInputStream(item.uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
                    } ?: error("stream unavailable")
                    item to md.digest().joinToString("") { b -> "%02x".format(b) }
                }.getOrNull()
                digest
            }
            hashes.groupBy { it.second }.values.filter { it.size > 1 }.map { group -> group.map { it.first } }
        }
        scanning = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Дубликаты")
        Text(when { !enabled -> "Сканирование отключено в настройках хранилища."; scanning -> "Проверяем содержимое файлов… Это может занять время."; else -> "Найдено групп точных совпадений: ${groups.size}" }, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (enabled && !scanning && groups.isEmpty()) Text("Одинаковых файлов не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(s.columns.coerceIn(2, 4)), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEachIndexed { index, group ->
                item(key = "duplicate_$index", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text("Совпадение ${index + 1} · ${group.size} копии", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(8.dp))
                }
                items(group, key = { it.key }) { item -> MediaThumb(item, s, false) { onOpen(group, group.indexOf(item)) } }
            }
        }
    }
}

@Composable
private fun CollageFeature(items: List<MediaItem>, s: UiSettings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Коллаж")
        Text("Выбери от 2 до 6 фотографий. Коллаж сохранится отдельным файлом; оригиналы не изменяются.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text("Выбрано: ${selected.size} / 6", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(s.columns.coerceIn(2, 4)), modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.key }) { item ->
                Box {
                    MediaThumb(item, s, false) { selected = if (item.key in selected) selected - item.key else if (selected.size < 6) selected + item.key else selected }
                    if (item.key in selected) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
        }
        Button(enabled = selected.size in 2..6 && !busy, onClick = {
            busy = true
            val chosen = items.filter { it.key in selected }
            scope.launch {
                val saved = withContext(Dispatchers.IO) { saveCollage(ctx, chosen) }
                busy = false
                message = if (saved) "Коллаж сохранён в Pictures/MayskGallery" else "Не удалось создать коллаж"
            }
        }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(if (busy) "Создание…" else "Создать коллаж") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
    }
}


private fun decodeForCollage(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun saveCollage(context: android.content.Context, items: List<MediaItem>): Boolean = try {
    val size = 1200
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val cells = when (items.size) { 2 -> listOf(0 to 0, 1 to 0); 3 -> listOf(0 to 0, 1 to 0, 0 to 1); 4 -> listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1); 5 -> listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1); else -> (0..5).map { it % 3 to it / 3 } }
    val cols = if (items.size == 2) 2 else if (items.size == 5) 3 else if (items.size == 3) 2 else 2
    val rows = if (items.size <= 2) 1 else 2
    val cellW = size / cols
    val cellH = size / rows
    items.forEachIndexed { index, item ->
        val source = decodeForCollage(context, item.uri) ?: return@forEachIndexed
        val (x, y) = cells[index]
        val scale = maxOf(cellW.toFloat() / source.width, cellH.toFloat() / source.height)
        val w = (source.width * scale).toInt(); val h = (source.height * scale).toInt()
        val left = x * cellW + (cellW - w) / 2; val top = y * cellH + (cellH - h) / 2
        canvas.drawBitmap(source, null, android.graphics.Rect(left, top, left + w, top + h), paint)
        source.recycle()
    }
    val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "MayskCollage_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MayskGallery") }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    val ok = context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: false
    bitmap.recycle(); ok
} catch (_: Exception) { false }

@Composable
private fun SlideshowFeature(items: List<MediaItem>, s: UiSettings, onOpen: (List<MediaItem>, Int) -> Unit, onBack: () -> Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(items, playing, s.slideshowSeconds) {
        while (playing && items.isNotEmpty()) { delay(s.slideshowSeconds * 1000L); index = (index + 1) % items.size }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ScreenTitle("Слайд-шоу")
        if (items.isEmpty()) Text("Нет фотографий для показа", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            AsyncImage(items[index.coerceIn(items.indices)].uri, null, Modifier.fillMaxWidth().weight(1f).padding(12.dp).clip(RoundedCornerShape(24.dp)).clickable { onOpen(items, index) }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Text("${index + 1} / ${items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(16.dp)) {
                TextButton(onClick = { index = (index - 1 + items.size) % items.size }) { Text("Назад") }
                Button(onClick = { playing = !playing }) { Text(if (playing) "Пауза" else "Продолжить") }
                TextButton(onClick = { index = (index + 1) % items.size }) { Text("Далее") }
            }
        }
    }
}

@Composable
private fun MapFeature(items: List<MediaItem>, s: UiSettings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var locations by remember(items) { mutableStateOf<List<Pair<MediaItem, Pair<Double, Double>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(items) {
        locations = withContext(Dispatchers.IO) {
            items.mapNotNull { item ->
                runCatching {
                    val stream = ctx.contentResolver.openInputStream(item.uri) ?: return@runCatching null
                    val exif = stream.use { ExifInterface(it) }
                    val coords = FloatArray(2)
                    if (exif.getLatLong(coords)) item to (coords[0].toDouble() to coords[1].toDouble()) else null
                }.getOrNull()
            }
        }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Карта фотографий")
        Text(if (loading) "Ищем координаты в метаданных…" else "Фотографий с сохранённой геопозицией: ${locations.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        if (locations.isEmpty() && !loading) Text("У фотографий нет GPS-координат. Карта появится для снимков, в которых камера сохранила местоположение.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(locations, key = { it.first.key }) { (item, coords) ->
                Column(Modifier.clickable { val uri = Uri.parse("geo:${coords.first},${coords.second}?q=${coords.first},${coords.second}(${Uri.encode(item.name)})"); runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }) {
                    MediaThumb(item, s, false) {}
                    Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1)
                    Text("${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)} · открыть карту", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}
