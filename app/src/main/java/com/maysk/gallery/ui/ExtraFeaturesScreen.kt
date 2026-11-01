package com.maysk.gallery.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
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
import androidx.core.content.ContextCompat
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
        "slideshow" -> SlideshowFeature(items.filter { !it.isVideo }, s, onBack)
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

private data class CollageLayoutSpec(val id: Int, val title: String, val subtitle: String)

private fun collageRects(count: Int, layout: Int): List<android.graphics.RectF> {
    if (count <= 0) return emptyList()
    if (layout == 1 && count > 1) {
        val result = mutableListOf(android.graphics.RectF(0f, 0f, .62f, 1f))
        val h = 1f / (count - 1)
        for (i in 1 until count) result += android.graphics.RectF(.635f, (i - 1) * h, 1f, i * h)
        return result
    }
    if (layout == 2 && count >= 3) {
        val result = mutableListOf(android.graphics.RectF(0f, 0f, .58f, .58f))
        result += android.graphics.RectF(.595f, 0f, 1f, .39f)
        result += android.graphics.RectF(.595f, .405f, 1f, .58f)
        val rest = count - 3
        if (rest > 0) {
            val cols = 2
            val rows = (rest + cols - 1) / cols
            val cellW = 1f / cols
            val cellH = .39f / rows
            for (i in 0 until rest) {
                val row = i / cols
                val col = i % cols
                result += android.graphics.RectF(col * cellW, .61f + row * cellH, (col + 1) * cellW - .012f, .61f + (row + 1) * cellH - .012f)
            }
        }
        return result.take(count)
    }
    val cols = when {
        count <= 2 -> 2
        count <= 4 -> 2
        count <= 6 -> 3
        else -> 4
    }
    val rows = (count + cols - 1) / cols
    return (0 until count).map { i ->
        val col = i % cols
        val row = i / cols
        val gap = .012f
        android.graphics.RectF(
            col.toFloat() / cols + gap, row.toFloat() / rows + gap,
            (col + 1f) / cols - gap, (row + 1f) / rows - gap,
        )
    }
}

@Composable
private fun CollageFeature(items: List<MediaItem>, s: UiSettings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableIntStateOf(0) } // count -> layout -> photos -> preview
    var photoCount by remember { mutableIntStateOf(4) }
    var layout by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var replaceIndex by remember { mutableIntStateOf(-1) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun saveCurrentCollage() {
        if (busy || selected.size != photoCount) return
        busy = true
        val snapshot = selected.toList()
        val text = caption.trim()
        val chosenLayout = layout
        scope.launch {
            val saved = withContext(Dispatchers.IO) { saveCollage(ctx, snapshot, chosenLayout, text) }
            busy = false
            message = if (saved) "Коллаж сохранён в Pictures/MayskGallery" else "Не удалось сохранить коллаж. Проверь доступ к фотографиям."
        }
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveCurrentCollage() else message = "Для сохранения коллажа нужно разрешить доступ к файлам."
    }
    val layouts = remember { listOf(
        CollageLayoutSpec(0, "Ровная сетка", "Все фотографии одинакового размера"),
        CollageLayoutSpec(1, "Главный кадр", "Одна большая фотография и остальные рядом"),
        CollageLayoutSpec(2, "Мозаика", "Асимметричная композиция"),
    ) }
    androidx.activity.compose.BackHandler(enabled = stage > 0) { stage = (stage - 1).coerceAtLeast(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Коллаж", actions = { TextButton(onClick = onBack) { Text("Закрыть") } })
        when (stage) {
            0 -> {
                Text("Сколько фотографий будет в коллаже?", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (2..8).forEach { n ->
                        TextButton(onClick = { photoCount = n; stage = 1 }) {
                            Text(n.toString(), color = if (photoCount == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Text("Выбери количество от 2 до 8 — затем подберём подходящий макет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { stage = 0 }) { Text("← Количество") }
                    Text("$photoCount фотографий", color = MaterialTheme.colorScheme.primary)
                }
                Text("Выбери расположение фотографий", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(layouts, key = { it.id }) { spec ->
                        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).clickable { layout = spec.id; stage = 2 }.padding(10.dp)) {
                            TemplateSketch(photoCount, spec.id, Modifier.fillMaxWidth().aspectRatio(1.2f))
                            Text(spec.title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                            Text(spec.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
            2 -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = { stage = 1 }) { Text("← Макеты") }
                    Text("Выбери $photoCount фото · ${selected.size}/$photoCount", color = MaterialTheme.colorScheme.primary)
                }
                if (replaceIndex >= 0) Text("Выбери замену для фото №${replaceIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(s.columns.coerceIn(2, 4)), modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.key }) { item ->
                        val selectedIndex = selected.indexOfFirst { it.key == item.key }
                        Box {
                            MediaThumb(item, s, false) {
                                if (replaceIndex >= 0) {
                                    if (selected.none { it.key == item.key }) selected = selected.toMutableList().also { it[replaceIndex] = item }
                                    replaceIndex = -1
                                } else if (selectedIndex >= 0) selected = selected.filterNot { it.key == item.key }
                                else if (selected.size < photoCount) selected = selected + item
                            }
                            if (selectedIndex >= 0) Text("${selectedIndex + 1}", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(30.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
                        }
                    }
                }
                Button(enabled = selected.size == photoCount && replaceIndex < 0, onClick = { stage = 3 }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Показать результат") }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = { stage = 2 }) { Text("← Изменить фото") }
                    Text(layouts.first { it.id == layout }.title, color = MaterialTheme.colorScheme.primary)
                }
                CollageComposition(selected, layout, caption, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = caption, onValueChange = { caption = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    label = { Text("Подпись к коллажу (необязательно)") }, singleLine = true,
                )
                Text("Порядок фотографий", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    selected.forEachIndexed { index, item ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(item.uri, item.name, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Row(horizontalArrangement = Arrangement.Center) {
                                TextButton(enabled = index > 0, onClick = { selected = selected.toMutableList().also { val t = it[index - 1]; it[index - 1] = it[index]; it[index] = t } }) { Text("←") }
                                TextButton(onClick = { replaceIndex = index; stage = 2 }) { Text("↻") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { stage = 1 }) { Text("Другой макет") }
                    Button(enabled = !busy, onClick = {
                        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else saveCurrentCollage()
                    }, modifier = Modifier.weight(1f)) { Text(if (busy) "Сохранение…" else "Сохранить коллаж") }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
        }
    }
}

@Composable
private fun TemplateSketch(count: Int, layout: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        val rects = collageRects(count, layout)
        rects.forEachIndexed { index, r ->
            drawRoundRect(
                color = if (index == 0) Color(0xFF8FA9C5) else Color(0xFF48596B),
                topLeft = androidx.compose.ui.geometry.Offset(r.left * size.width, r.top * size.height),
                size = androidx.compose.ui.geometry.Size(r.width() * size.width, r.height() * size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CollageComposition(items: List<MediaItem>, layout: Int, caption: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
        val rects = collageRects(items.size, layout)
        items.forEachIndexed { index, item ->
            val r = rects[index]
            AsyncImage(
                model = item.uri, contentDescription = item.name,
                modifier = Modifier.offset(x = maxWidth * r.left, y = maxHeight * r.top)
                    .size(width = maxWidth * r.width(), height = maxHeight * r.height())
                    .padding(2.dp).clip(RoundedCornerShape(7.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        if (caption.isNotBlank()) {
            Text(caption, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().background(Color.Black.copy(alpha = .65f)).padding(10.dp), maxLines = 2)
        }
    }
}

private fun decodeForCollage(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun saveCollage(context: android.content.Context, items: List<MediaItem>, layout: Int, caption: String): Boolean {
    if (items.size !in 2..8) return false
    var outputUri: Uri? = null
    var bitmap: Bitmap? = null
    return try {
        val size = 1600
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap = outputBitmap
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rects = collageRects(items.size, layout)
        var decodedCount = 0
        items.forEachIndexed { index, item ->
            val source = decodeForCollage(context, item.uri) ?: return@forEachIndexed
            val r = rects[index]
            val dst = android.graphics.Rect(
                (r.left * size).toInt(), (r.top * size).toInt(),
                (r.right * size).toInt(), (r.bottom * size).toInt(),
            )
            val targetRatio = dst.width().toFloat() / dst.height().coerceAtLeast(1)
            val sourceRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
            val src = if (sourceRatio > targetRatio) {
                val w = (source.height * targetRatio).toInt().coerceAtMost(source.width)
                android.graphics.Rect((source.width - w) / 2, 0, (source.width + w) / 2, source.height)
            } else {
                val h = (source.width / targetRatio).toInt().coerceAtMost(source.height)
                android.graphics.Rect(0, (source.height - h) / 2, source.width, (source.height + h) / 2)
            }
            canvas.drawBitmap(source, src, dst, paint)
            source.recycle()
            decodedCount++
        }
        if (decodedCount != items.size) return false
        if (caption.isNotBlank()) {
            val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(175, 0, 0, 0) }
            canvas.drawRect(0f, size - 150f, size.toFloat(), size.toFloat(), strip)
            val safeCaption = caption.take(100)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = minOf(58f, 1300f / safeCaption.length.coerceAtLeast(1)); textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            canvas.drawText(safeCaption, size / 2f, size - 58f, textPaint)
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "MayskCollage_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MayskGallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val ok = context.contentResolver.openOutputStream(outputUri!!)?.use { outputBitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) } ?: false
        if (ok && Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(outputUri!!, done, null, null)
        }
        if (!ok) context.contentResolver.delete(outputUri!!, null, null)
        ok
    } catch (_: Exception) {
        outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        false
    } finally { bitmap?.recycle() }
}

@Composable
private fun SlideshowFeature(items: List<MediaItem>, s: UiSettings, onBack: () -> Unit) {
    val albums = remember(items) { listOf("Все фотографии") + items.map { it.bucket }.distinct().sorted() }
    var selectedAlbum by remember(items) { mutableStateOf("Все фотографии") }
    val selectedItems = remember(items, selectedAlbum) {
        (if (selectedAlbum == "Все фотографии") items else items.filter { it.bucket == selectedAlbum }).sortedByDescending { it.dateMillis }
    }
    var active by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var index by remember(items, selectedAlbum) { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val interval = s.slideshowSeconds.coerceIn(2, 15)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle("Слайд-шоу", actions = { TextButton(onClick = onBack) { Text("Закрыть") } })
        Text("Выбери альбом или покажи всю галерею", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        Box {
            Button(onClick = { menuExpanded = true }) { Text(selectedAlbum + "  ▾") }
            androidx.compose.material3.DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                albums.forEach { album ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(album) }, onClick = { selectedAlbum = album; index = 0; menuExpanded = false })
                }
            }
        }
        Text("${selectedItems.size} фотографий · смена каждые $interval сек.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
        Button(enabled = selectedItems.isNotEmpty(), onClick = { index = 0; playing = true; active = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("Начать показ на весь экран") }
    }

    if (active && selectedItems.isNotEmpty()) {
        androidx.activity.compose.BackHandler { active = false }
        LaunchedEffect(active, playing, interval, selectedItems) {
            while (active && playing) {
                delay(interval * 1000L)
                index = (index + 1) % selectedItems.size
            }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { active = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val dialogView = androidx.compose.ui.platform.LocalView.current
                androidx.compose.runtime.DisposableEffect(dialogView) {
                    val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                    val decor = window?.decorView
                    val previousFlags = decor?.systemUiVisibility ?: 0
                    decor?.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    onDispose { if (decor != null) decor.systemUiVisibility = previousFlags }
                }
                AsyncImage(
                    model = selectedItems[index.coerceIn(selectedItems.indices)].uri,
                    contentDescription = selectedItems[index.coerceIn(selectedItems.indices)].name,
                    modifier = Modifier.fillMaxSize().clickable { playing = !playing },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text("${index + 1} / ${selectedItems.size}", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
                TextButton(onClick = { active = false }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("Закрыть", color = Color.White) }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .58f)).padding(horizontal = 10.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index = (index - 1 + selectedItems.size) % selectedItems.size }) { Text("‹ Назад", color = Color.White) }
                    Button(onClick = { playing = !playing }) { Text(if (playing) "Ⅱ Пауза" else "▶ Продолжить") }
                    TextButton(onClick = { index = (index + 1) % selectedItems.size }) { Text("Вперёд ›", color = Color.White) }
                }
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
