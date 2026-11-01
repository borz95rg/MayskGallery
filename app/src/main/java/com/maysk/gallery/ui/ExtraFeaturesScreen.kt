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

/** 100 curated collage compositions: 20 distinct layout families × 5 deliberate variations. */
private fun collageRects(count: Int, layout: Int): List<android.graphics.RectF> {
    if (count <= 0) return emptyList()
    val family = Math.floorMod(layout, 20)
    val variant = Math.floorDiv(layout, 20).coerceIn(0, 4)
    val gap = .012f + variant * .003f
    fun r(l: Float, t: Float, rr: Float, b: Float) = android.graphics.RectF(l, t, rr, b)
    fun grid(n: Int, cols: Int, rowsOffset: Int = 0): List<android.graphics.RectF> {
        if (n <= 0) return emptyList()
        val rows = (n + cols - 1) / cols
        return (0 until n).map { i ->
            val j = (i + rowsOffset) % n
            val row = j / cols
            val col = j % cols
            val w = 1f / cols
            val h = 1f / rows
            r(col * w + gap, row * h + gap, (col + 1) * w - gap, (row + 1) * h - gap)
        }
    }
    fun heroLeft(): List<android.graphics.RectF> {
        val split = (.52f + variant * .035f).coerceAtMost(.66f)
        val side = 1f - split
        return listOf(r(gap, gap, split-gap, 1f-gap)) + (0 until count-1).map { i ->
            r(split+gap, i.toFloat()/(count-1)+gap, 1f-gap, (i+1f)/(count-1)-gap)
        }
    }
    fun heroRight(): List<android.graphics.RectF> = heroLeft().map { r(1f-it.right, it.top, 1f-it.left, it.bottom) }
    fun heroTop(): List<android.graphics.RectF> {
        val h = (.42f + variant*.035f).coerceAtMost(.58f)
        return listOf(r(gap,gap,1f-gap,h-gap)) + grid(count-1, if (count <= 3) 2 else 3).map { r(it.left,h+it.top*(1f-h),it.right,h+it.bottom*(1f-h)) }
    }
    fun heroBottom(): List<android.graphics.RectF> = heroTop().map { r(it.left,1f-it.bottom,it.right,1f-it.top) }
    val result = when (family) {
        0 -> grid(count, if (count <= 4) 2 else 3, variant % 3)
        1 -> heroLeft()
        2 -> heroRight()
        3 -> heroTop()
        4 -> heroBottom()
        5 -> { // cinematic centre with small surrounding frames
            val hero = r(.20f+variant*.015f,.20f,.80f-variant*.015f,.80f)
            val ring = listOf(r(0f,0f,.30f,.18f),r(.35f,0f,.65f,.18f),r(.70f,0f,1f,.18f),r(0f,.22f,.16f,.48f),r(.84f,.22f,1f,.48f),r(0f,.52f,.16f,.78f),r(.84f,.52f,1f,.78f),r(0f,.82f,.30f,1f),r(.35f,.82f,.65f,1f),r(.70f,.82f,1f,1f))
            listOf(hero) + ring.take(count-1)
        }
        6 -> { // film-strip: large middle image, thin frames above and below
            val middle = r(gap,.22f+variant*.02f,1f-gap,.78f-variant*.02f)
            val n = count-1; val topN = (n+1)/2; val bottomN = n-topN
            listOf(middle) + (0 until topN).map { i -> r(i.toFloat()/topN+gap,gap,(i+1f)/topN-gap,.18f) } + (0 until bottomN).map { i -> r(i.toFloat()/bottomN+gap,.82f,(i+1f)/bottomN-gap,1f-gap) }
        }
        7 -> { // honeycomb-inspired offset mosaic
            val cols = if (count <= 3) 2 else 3
            grid(count, cols, variant % 2).mapIndexed { i, q -> r((q.left + if ((i/cols)%2==1) .035f else 0f).coerceAtMost(.75f), q.top, (q.right + if ((i/cols)%2==1) .035f else 0f).coerceAtMost(1f), q.bottom) }
        }
        8 -> { // editorial 2-up header with varied lower cards
            val topH = .34f + variant*.025f
            val first = r(gap,gap,.58f-gap,topH-gap)
            val second = r(.60f+gap,gap,1f-gap,topH-gap)
            listOf(first,second) + grid(count-2, if (count <= 4) 2 else 3).map { r(it.left,topH+it.top*(1f-topH),it.right,topH+it.bottom*(1f-topH)) }
        }
        9 -> { // staggered scrapbook rows
            val rows = count
            (0 until count).map { i ->
                val h=1f/rows
                val shift=if ((i+variant)%2==0) .02f+variant*.008f else .12f+variant*.008f
                r((shift).coerceAtMost(.22f),i*h+gap,(1f-shift*.45f), (i+1f)*h-gap)
            }
        }
        10 -> { // circle-gallery arrangement represented as square cells, circle mask applied in renderer
            grid(count, if (count <= 4) 2 else 3, variant%2).mapIndexed { i,q ->
                val scale=if(i==0) 1f else .94f
                val cx=(q.left+q.right)/2; val cy=(q.top+q.bottom)/2
                val w=q.width()*scale; val h=q.height()*scale
                r(cx-w/2,cy-h/2,cx+w/2,cy+h/2)
            }
        }
        11 -> { // alternating magazine columns
            val leftW=.38f+variant*.025f
            (0 until count).map { i ->
                val col=i%2; val row=i/2; val rows=(count+1)/2
                val h=1f/rows
                if(col==0) r(gap,row*h+gap,leftW-gap,(row+1f)*h-gap)
                else r(leftW+gap,row*h+gap,1f-gap,(row+1f)*h-gap)
            }
        }
        12 -> { // panorama bands
            val heroH=.30f+variant*.025f
            listOf(r(gap,gap,1f-gap,heroH-gap)) + grid(count-1,2).map { r(it.left,heroH+it.top*(1f-heroH),it.right,heroH+it.bottom*(1f-heroH)) }
        }
        13 -> { // diagonal-flow / offset cards
            val base=grid(count,2,variant%2)
            base.mapIndexed { i,q ->
                val dx=if(i%2==0) -.025f else .025f
                r((q.left+dx).coerceAtLeast(gap),(q.top+if(i%2==0) .025f else 0f).coerceAtMost(.95f),(q.right+dx).coerceAtMost(1f-gap),(q.bottom+if(i%2==0) .025f else 0f).coerceAtMost(1f-gap))
            }
        }
        14 -> { // central column with side stories
            val heroW=.42f+variant*.02f
            val sideW=(1f-heroW)/2f
            val hero=r(sideW+gap,gap,sideW+heroW-gap,1f-gap)
            val sides=(0 until count-1).map { i ->
                val left=i%2==0; val row=i/2; val rows=((count-1)+1)/2; val h=1f/rows
                if(left) r(gap,row*h+gap,sideW-gap,(row+1f)*h-gap) else r(sideW+heroW+gap,row*h+gap,1f-gap,(row+1f)*h-gap)
            }
            listOf(hero)+sides
        }
        15 -> { // vertical photo-story with alternating inset
            (0 until count).map { i ->
                val h=1f/count; val inset=if(i%2==0) .02f+variant*.01f else .15f+variant*.012f
                r(inset,i*h+gap,1f-inset,(i+1f)*h-gap)
            }
        }
        16 -> { // triangle-inspired diagonal rhythm
            val cols=if(count<=4) 2 else 3
            grid(count,cols).mapIndexed { i,q ->
                val shift=if(i%2==0) .025f else -.025f
                r((q.left+shift).coerceAtLeast(gap),q.top,(q.right+shift).coerceAtMost(1f-gap),q.bottom)
            }
        }
        17 -> { // large portrait + two small side rails
            val hero=r(.22f+variant*.015f,gap,.78f-variant*.015f,1f-gap)
            val rail=(0 until count-1).map { i ->
                val side=i%2; val row=i/2; val rows=((count-1)+1)/2; val h=1f/rows
                if(side==0) r(gap,row*h+gap,.19f,(row+1f)*h-gap) else r(.81f,row*h+gap,1f-gap,(row+1f)*h-gap)
            }
            listOf(hero)+rail
        }
        18 -> { // asymmetrical gallery with hero at varying corner
            val heroW=.56f+variant*.025f; val heroH=.56f+variant*.025f
            val corner=variant%4
            val hero=when(corner){0->r(gap,gap,heroW,heroH);1->r(1f-heroW,gap,1f-gap,heroH);2->r(gap,1f-heroH,heroW,1f-gap);else->r(1f-heroW,1f-heroH,1f-gap,1f-gap)}
            val rest=grid(count-1,2,variant%2).map { q ->
                val x=if(corner%2==0) .48f else 0f; val y=if(corner<2) .48f else 0f
                r((q.left*.48f+x).coerceIn(0f,1f), (q.top*.48f+y).coerceIn(0f,1f), (q.right*.48f+x).coerceIn(0f,1f), (q.bottom*.48f+y).coerceIn(0f,1f))
            }
            listOf(hero)+rest
        }
        else -> { // clean editorial mosaic with a shifting hero tile
            val cols=if(count<5) 2 else 3
            val base=grid(count,cols,variant%2)
            base.mapIndexed { i,q -> if(i==variant%count) r(q.left,q.top*.88f,q.right,q.bottom*.88f) else q }
        }
    }
    return result.take(count).map { q ->
        android.graphics.RectF(q.left.coerceIn(0f, .98f), q.top.coerceIn(0f,.98f), q.right.coerceIn(.02f,1f), q.bottom.coerceIn(.02f,1f))
    }
}

private fun collageMask(layout: Int): Int = when (Math.floorMod(layout, 20)) {
    5 -> 5  // heart-inspired romantic frames
    7 -> 2  // honeycomb hexagons
    10 -> 1 // circular gallery
    13 -> 3 // diamond mosaic
    16 -> 4 // triangular art collage
    else -> 0 // editorial rounded rectangles
}

private fun collageAndroidPath(rect: android.graphics.RectF, mask: Int): android.graphics.Path {
    val p = android.graphics.Path()
    val l=rect.left; val t=rect.top; val r=rect.right; val b=rect.bottom
    when(mask) {
        1 -> p.addOval(rect, android.graphics.Path.Direction.CW)
        2 -> { p.moveTo((l+r)/2,t); p.lineTo(r,t+(b-t)*.25f); p.lineTo(r,b-(b-t)*.25f); p.lineTo((l+r)/2,b); p.lineTo(l,b-(b-t)*.25f); p.lineTo(l,t+(b-t)*.25f); p.close() }
        3 -> { p.moveTo((l+r)/2,t); p.lineTo(r,(t+b)/2); p.lineTo((l+r)/2,b); p.lineTo(l,(t+b)/2); p.close() }
        4 -> { p.moveTo((l+r)/2,t); p.lineTo(r,b); p.lineTo(l,b); p.close() }
        5 -> { p.moveTo((l+r)/2,t); p.cubicTo(r,t,r,(t+b)/2,(l+r)/2,b); p.cubicTo(l,(t+b)/2,l,t,(l+r)/2,t); p.close() }
        else -> p.addRoundRect(rect, 24f, 24f, android.graphics.Path.Direction.CW)
    }
    return p
}

private fun collageComposeShape(layout: Int): androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val path = androidx.compose.ui.graphics.Path()
    val w=size.width; val h=size.height
    when(collageMask(layout)) {
        1 -> path.addOval(androidx.compose.ui.geometry.Rect(0f,0f,w,h))
        2 -> { path.moveTo(w*.5f,0f); path.lineTo(w,h*.25f); path.lineTo(w,h*.75f); path.lineTo(w*.5f,h); path.lineTo(0f,h*.75f); path.lineTo(0f,h*.25f); path.close() }
        3 -> { path.moveTo(w*.5f,0f); path.lineTo(w,h*.5f); path.lineTo(w*.5f,h); path.lineTo(0f,h*.5f); path.close() }
        4 -> { path.moveTo(w*.5f,0f); path.lineTo(w,h); path.lineTo(0f,h); path.close() }
        5 -> { path.moveTo(w*.5f,0f); path.cubicTo(w,0f,w,h*.5f,w*.5f,h); path.cubicTo(0f,h*.5f,0f,0f,w*.5f,0f); path.close() }
        else -> path.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f,0f,w,h,androidx.compose.ui.geometry.CornerRadius(14f,14f)))
    }
    addPath(path)
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
        val families = listOf(
            "Редакционная сетка", "Обложка слева", "Обложка справа", "Панорама сверху", "Панорама снизу",
            "Центральная история", "Киноплёнка", "Соты", "Журнал", "Скрапбук", "Круговая галерея",
            "Две колонки", "Путевой дневник", "Динамика", "Центральный портрет", "Фото-лента",
            "Треугольный ритм", "Портрет в центре", "Асимметричный угол", "Арт-мозаика"
        )
        val categories = listOf("Классика", "Журнальные", "Мозаика", "Асимметрия", "Истории", "Креатив", "Романтика", "Путешествия", "Портреты", "Геометрия")
        (0 until 100).map { id ->
            val family = id % families.size
            val variation = id / families.size + 1
            val category = when (family) {
                1,2,8 -> "Журнальные"
                5,10 -> "Романтика"
                6,15 -> "Истории"
                7,13,16 -> "Геометрия"
                9 -> "Семья"
                3,4,12 -> "Путешествия"
                14,17 -> "Портреты"
                19 -> "Природа"
                18 -> "Асимметрия"
                else -> categories[(family + variation) % categories.size]
            }
            CollageLayoutSpec(id, "${families[family]} · ${listOf("I","II","III","IV","V")[variation-1]}", "${photoCount} фото · пример композиции", category)
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
                Text("После выбора количества увидишь готовые композиции с примерами фотографий. Все действия выполняются на телефоне.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { stage = 0 }) { Text("← Количество") }
                    Text("$photoCount фотографий", color = MaterialTheme.colorScheme.primary)
                }
                Text("Выбери готовую композицию. В превью показаны фотографии из твоей галереи.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                            CollageTemplatePreview(items, photoCount, spec.id, Modifier.fillMaxWidth().aspectRatio(1.2f))
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
private fun CollageTemplatePreview(
    galleryItems: List<MediaItem>,
    count: Int,
    layout: Int,
    modifier: Modifier = Modifier,
) {
    // Each card uses real local photos from the device gallery, clipped to the
    // exact same geometry that is used by the collage renderer and exporter.
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF171717))
    ) {
        val rects = collageRects(count, layout)
        rects.forEachIndexed { index, r ->
            val tileModifier = Modifier
                .offset(x = maxWidth * r.left, y = maxHeight * r.top)
                .size(width = maxWidth * r.width(), height = maxHeight * r.height())
                .padding(1.5.dp)
                .clip(collageComposeShape(layout))
            val photo = galleryItems.getOrNull(index)
            if (photo != null) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = "Пример фотографии ${index + 1}",
                    modifier = tileModifier,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = tileModifier.background(Color(0xFF303036)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
            }
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
                    .padding(2.dp).clip(collageComposeShape(layout)),
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
        canvas.drawColor(android.graphics.Color.rgb(24, 23, 25))
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
            val saveCount = canvas.save()
            val normalized = android.graphics.RectF(
                dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()
            )
            canvas.clipPath(collageAndroidPath(normalized, collageMask(layout)))
            canvas.drawBitmap(source, src, dst, paint)
            canvas.restoreToCount(saveCount)
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
