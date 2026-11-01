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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

private data class CollageLayoutSpec(val id: Int, val title: String, val subtitle: String, val category: String)

/** 120 editable layouts. Each layout is generated from a distinct composition family and variation. */
private fun collageRects(count: Int, layout: Int): List<android.graphics.RectF> {
    if (count <= 0) return emptyList()
    val family = Math.floorMod(layout, 15)
    val variant = Math.floorDiv(layout, 15)
    val gap = .008f + (variant % 4) * .004f
    fun grid(n: Int, cols: Int, reverse: Boolean = false, offset: Int = 0): List<android.graphics.RectF> {
        val rows = (n + cols - 1) / cols
        return (0 until n).map { i ->
            val j = if (reverse) n - 1 - i else i
            val col = (j + offset) % cols
            val row = j / cols
            val cellW = 1f / cols
            val cellH = 1f / rows
            android.graphics.RectF(col * cellW + gap, row * cellH + gap, (col + 1) * cellW - gap, (row + 1) * cellH - gap)
        }
    }
    if (count == 2) {
        return when (family % 6) {
            0 -> listOf(android.graphics.RectF(gap, gap, .5f-gap, 1f-gap), android.graphics.RectF(.5f+gap, gap, 1f-gap, 1f-gap))
            1 -> listOf(android.graphics.RectF(gap, gap, 1f-gap, .5f-gap), android.graphics.RectF(gap, .5f+gap, 1f-gap, 1f-gap))
            2 -> listOf(android.graphics.RectF(gap, gap, .64f-gap, 1f-gap), android.graphics.RectF(.66f+gap, gap, 1f-gap, 1f-gap))
            3 -> listOf(android.graphics.RectF(gap, gap, 1f-gap, .64f-gap), android.graphics.RectF(gap, .66f+gap, 1f-gap, 1f-gap))
            4 -> listOf(android.graphics.RectF(gap, gap, .5f-gap, .5f-gap), android.graphics.RectF(.5f+gap, .5f+gap, 1f-gap, 1f-gap))
            else -> listOf(android.graphics.RectF(.18f, gap, .82f, .48f-gap), android.graphics.RectF(.18f, .52f+gap, .82f, 1f-gap))
        }
    }
    val rest = count - 1
    return when (family) {
        0 -> grid(count, if (count < 5) 2 else 3, variant % 2 == 1, variant % 3)
        1 -> listOf(android.graphics.RectF(gap, gap, .62f-gap, 1f-gap)) + (0 until rest).map { i -> android.graphics.RectF(.64f+gap, i.toFloat()/rest+gap, 1f-gap, (i+1f)/rest-gap) }
        2 -> listOf(android.graphics.RectF(.38f+gap, gap, 1f-gap, 1f-gap)) + (0 until rest).map { i -> android.graphics.RectF(gap, i.toFloat()/rest+gap, .36f-gap, (i+1f)/rest-gap) }
        3 -> listOf(android.graphics.RectF(gap, gap, 1f-gap, .60f-gap)) + grid(rest, if (rest <= 2) 2 else 3).map { r -> android.graphics.RectF(r.left, .62f+r.top*.38f, r.right, .62f+r.bottom*.38f) }
        4 -> grid(rest, if (rest <= 2) 2 else 3).map { r -> android.graphics.RectF(r.left, r.top*.38f, r.right, r.bottom*.38f) } + listOf(android.graphics.RectF(gap, .40f+gap, 1f-gap, 1f-gap))
        5 -> {
            val result = mutableListOf(android.graphics.RectF(.25f, .22f, .75f, .78f))
            val ring = listOf(android.graphics.RectF(0f,0f,.24f,.21f), android.graphics.RectF(.26f,0f,.74f,.20f), android.graphics.RectF(.76f,0f,1f,.21f), android.graphics.RectF(0f,.23f,.23f,.77f), android.graphics.RectF(.77f,.23f,1f,.77f), android.graphics.RectF(0f,.79f,.24f,1f), android.graphics.RectF(.26f,.80f,.74f,1f), android.graphics.RectF(.76f,.79f,1f,1f))
            result + ring.take(rest)
        }
        6 -> { val cols = if (variant % 2 == 0) 2 else 3; grid(count, cols, variant % 3 == 1, variant % 2) }
        7 -> { val cols = if (count < 5) count else 3; grid(count, cols, variant % 2 == 0, variant % 3) }
        8 -> { val result = mutableListOf<android.graphics.RectF>(); val heroH = .42f; result += android.graphics.RectF(gap,gap,1f-gap,heroH-gap); result + grid(rest, 2, variant % 2 == 1).map { r -> android.graphics.RectF(r.left, heroH+r.top*(1f-heroH), r.right, heroH+r.bottom*(1f-heroH)) } }
        9 -> { val cols = if (count <= 4) 2 else 3; grid(count, cols, variant % 2 == 0, variant % 3) }
        10 -> { val rows = count; (0 until count).map { i -> val top = i.toFloat()/rows; android.graphics.RectF(if (i%2==0) gap else .18f, top+gap, if (i%2==0) .82f else 1f-gap, (i+1f)/rows-gap) } }
        11 -> {
            val top = listOf(android.graphics.RectF(gap,gap,.49f-gap,.48f-gap), android.graphics.RectF(.51f+gap,gap,1f-gap,.48f-gap))
            val bottomCount = count - 2
            top + grid(bottomCount, if (bottomCount <= 2) 2 else 3, variant % 2 == 0).map { r -> android.graphics.RectF(r.left, .52f+r.top*.48f, r.right, .52f+r.bottom*.48f) }
        }
        12 -> { val base = grid(count, 2, variant%2==1); base.mapIndexed { i,r -> if (i%2==0) android.graphics.RectF(r.left,r.top*.92f,r.right,r.bottom*.92f) else android.graphics.RectF(r.left,r.top*.92f+.08f,r.right,r.bottom*.92f+.08f) } }
        13 -> {
            val result = mutableListOf(android.graphics.RectF(gap,.19f,1f-gap,.81f))
            val perRow = (rest + 1) / 2
            result + (0 until rest).map { i ->
                val slot = i / 2
                val left = slot.toFloat() / perRow
                val right = (slot + 1f) / perRow
                if (i % 2 == 0) android.graphics.RectF(left+gap, gap, right-gap, .16f-gap)
                else android.graphics.RectF(left+gap, .84f+gap, right-gap, 1f-gap)
            }
        }
        else -> grid(count, if (variant%2==0) 2 else 3, variant%2==1, variant%4)
    }.take(count).map { r -> android.graphics.RectF(r.left.coerceIn(0f,1f), r.top.coerceIn(0f,1f), r.right.coerceIn(0f,1f), r.bottom.coerceIn(0f,1f)) }
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
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCategory by remember { mutableStateOf("Все") }
    fun saveCurrentCollage() {
        if (busy || selected.size != photoCount) return
        busy = true
        val snapshot = selected.toList()
        val text = caption.trim()
        val chosenLayout = layout
        scope.launch {
            val uri = withContext(Dispatchers.IO) { saveCollage(ctx, snapshot, chosenLayout, text) }
            busy = false
            if (uri != null) { savedUri = uri; message = "Коллаж успешно сохранён"; stage = 4 }
            else message = "Не удалось сохранить коллаж. Проверь доступ к фотографиям и свободное место."
        }
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveCurrentCollage() else message = "Для сохранения коллажа нужно разрешить доступ к файлам."
    }
    val layouts = remember(photoCount) {
        val families = listOf("Классическая сетка", "Главный кадр слева", "Главный кадр справа", "Большое фото сверху", "Большое фото снизу", "Центральная композиция", "Журнальная сетка", "Мозаика", "Фотоистория", "Асимметрия", "Вертикальная лента", "Редакционный стиль", "Динамика", "Киноплёнка", "Свободная композиция")
        val categories = listOf("Классика", "Журнальные", "Мозаика", "Асимметрия", "Истории", "Креатив")
        (0 until 120).map { id ->
            val family = id % families.size
            val variation = id / families.size + 1
            CollageLayoutSpec(id, "${families[family]} №$variation", "${photoCount} фото · вариант $variation", categories[(family + variation) % categories.size])
        }
    }
    val visibleLayouts = remember(layouts, selectedCategory) { if (selectedCategory == "Все") layouts else layouts.filter { it.category == selectedCategory } }
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
                Text("120 шаблонов для $photoCount фотографий · выбери подходящую композицию", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("Все") + layouts.map { it.category }.distinct()).forEach { category ->
                        TextButton(onClick = { selectedCategory = category }) {
                            Text(category, color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleLayouts, key = { it.id }) { spec ->
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
            4 -> {
                ScreenTitle("Коллаж сохранён", actions = { TextButton(onClick = onBack) { Text("Готово") } })
                Text("Готовое изображение уже находится в галерее телефона · Pictures/MayskGallery", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                savedUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = "Сохранённый коллаж", modifier = Modifier.fillMaxWidth().weight(1f).padding(14.dp).clip(RoundedCornerShape(18.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { stage = 3 }, modifier = Modifier.weight(1f)) { Text("Редактировать") }
                    TextButton(onClick = { savedUri = null; stage = 0; selected = emptyList(); caption = "" }) { Text("Новый коллаж") }
                }
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

private fun saveCollage(context: android.content.Context, items: List<MediaItem>, layout: Int, caption: String): Uri? {
    if (items.size !in 2..8) return null
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
        if (decodedCount != items.size) return null
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
        outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = context.contentResolver.openOutputStream(outputUri!!)?.use { stream -> outputBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream).also { stream.flush() } } ?: false
        if (ok && Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(outputUri!!, done, null, null)
        }
        if (!ok) { context.contentResolver.delete(outputUri!!, null, null); null }
        else {
            val sizeBytes = context.contentResolver.query(outputUri!!, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
            if (sizeBytes > 0L) outputUri else { context.contentResolver.delete(outputUri!!, null, null); null }
        }
    } catch (_: Exception) {
        outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        null
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
