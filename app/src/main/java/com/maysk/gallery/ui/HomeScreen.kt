package com.maysk.gallery.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maysk.gallery.MainViewModel
import com.maysk.gallery.data.AlbumInfo
import com.maysk.gallery.data.Keys
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings
import com.maysk.gallery.data.DockStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


private val Serif = FontFamily.Serif
private val Soft = Color(0xFFEDEDED)

/** Волнистая асимметричная маска главного баннера. */
private val BlobShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.08f * w, 0.30f * h)
    cubicTo(0.04f * w, 0.12f * h, 0.26f * w, 0.02f * h, 0.45f * w, 0.07f * h)
    cubicTo(0.60f * w, 0.11f * h, 0.70f * w, 0.00f * h, 0.88f * w, 0.09f * h)
    cubicTo(1.03f * w, 0.20f * h, 0.96f * w, 0.45f * h, 0.85f * w, 0.60f * h)
    cubicTo(0.76f * w, 0.74f * h, 0.84f * w, 0.90f * h, 0.66f * w, 1.00f * h)
    lineTo(0.22f * w, 1.00f * h)
    cubicTo(0.12f * w, 0.80f * h, 0.19f * w, 0.66f * h, 0.06f * w, 0.50f * h)
    close()
}

private val months = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun MediaItem.date(): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Выбор своей картинки из галереи телефона.
 * slot: 0 = баннер, 1..4 = обложки альбомов. Возвращает функцию «открыть выбор для slot».
 */
@Composable
fun rememberCoverPicker(vm: MainViewModel): (Int) -> Unit {
    val ctx = LocalContext.current
    var slot by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            vm.set(Keys.COVERS[slot], uri.toString())
        }
    }
    return { s ->
        slot = s
        launcher.launch(arrayOf("image/*"))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    items: List<MediaItem>,
    s: UiSettings,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onFilter: (String, List<MediaItem>) -> Unit,
    onTab: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onSelfie: () -> Unit,
    meta: Map<String, com.maysk.gallery.data.MediaMeta> = emptyMap(),
    selfieCountOverride: Int = -1,
) {
    val cs = MaterialTheme.colorScheme
    val photos = remember(items) { items.filter { !it.isVideo } }
    val albums = remember(items) {
        items.groupBy { it.bucket }.map { AlbumInfo(it.key, it.value) }.sortedByDescending { it.count }
    }
    val placesCount = remember(items, meta) {
        items.filter { !it.isVideo }.mapNotNull { i -> meta[i.key]?.takeIf { it.hasGeo } }
            .map { Math.round(it.lat * 10) to Math.round(it.lon * 10) }.distinct().size
    }
    val selfieCount0 = remember(items, meta) {
        items.count { !it.isVideo && (com.maysk.gallery.data.MediaMetaIndex.looksLikeSelfieByName(it) || meta[it.key]?.selfie == true) }
    }
    val selfieCount = if (selfieCountOverride >= 0) selfieCountOverride else selfieCount0
    val favs = remember(items, s.favorites) { items.filter { it.key in s.favorites } }
    val recent = remember(items) {
        val from = LocalDate.now().minusDays(30)
        items.filter { it.date().isAfter(from) }
    }
    var menu by remember { mutableStateOf(false) }

    // Отступ внутри скролла, чтобы последние карточки можно было поднять выше панели.
    // Глобального прямоугольника/шторки снизу больше нет — панель плавает поверх.
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
        // ---- шапка ----
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {

                    Text("галерея", fontFamily = Serif, fontSize = 46.sp, color = Color.White)
                    Text("✦", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp))
                }
                Text("твои воспоминания.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            RoundIcon(Icons.Filled.Search) { onTab(2) }
            Spacer(Modifier.size(10.dp))
            Box {
                RoundIcon(Icons.Filled.Menu) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        "Все альбомы" to 1, "AI Поиск" to 2, "Сейф" to 3,
                        "Корзина" to 8, "Воспоминания" to 9, "Дубликаты" to 10,
                        "Коллаж" to 11, "Слайд-шоу" to 12, "Места" to 13,
                        "Настройки" to 4,
                    ).forEach { (t, i) ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { menu = false; onTab(i) })
                    }
                }
            }
        }

        // ---- баннер с органической маской ----
        val bannerPages = if (s.covers[0].isNotBlank()) listOf(s.covers[0]) else photos.take(5).map { it.uri.toString() }
        val bannerState = androidx.compose.foundation.pager.rememberPagerState { bannerPages.size.coerceAtLeast(1) }
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(330.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .height(330.dp)
                    .clip(BlobShape)
                    .background(cs.surfaceVariant)
                    .combinedClickable(
                        onClick = { if (photos.isNotEmpty()) onOpen(photos, 0) },
                        onLongClick = { onPick(0) },
                    ),
            ) {
                androidx.compose.foundation.pager.HorizontalPager(bannerState, Modifier.fillMaxWidth().height(330.dp)) { pg ->
                    bannerPages.getOrNull(pg)?.let {
                        AsyncImage(it, null, Modifier.fillMaxWidth().height(330.dp), contentScale = ContentScale.Crop)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(330.dp).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))
                    )
                )
            }
            Column(Modifier.align(Alignment.CenterEnd).offset(y = 60.dp), horizontalAlignment = Alignment.End) {
                Text("✦", fontSize = 16.sp, color = Color.White)
                Text(
                    "фото —\nэто способ\nостановить время,\nне останавливая его.",
                    textAlign = TextAlign.End,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = Color.White,
                )
            }
        }

        // ---- быстрые категории ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickCard(Icons.Filled.Star, "избранное", favs.size) { onFilter("избранное", favs) }
            QuickCard(Icons.Filled.History, "недавние", recent.size) { onFilter("недавние", recent) }
            QuickCard(Icons.Filled.Place, "места", placesCount) { onTab(13) }
            QuickCard(Icons.Filled.CameraAlt, "селфи", selfieCount) { onSelfie() }
        }

        // ---- альбомы ----
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("альбомы ✦", fontFamily = Serif, fontSize = 26.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text("все альбомы →", fontSize = 13.sp, color = Color.White, modifier = Modifier.clickable { onTab(1) })
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0 until 4) {
                val a = albums.getOrNull(i)
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.78f)
                            .clip(RoundedCornerShape(percent = 34))
                            .background(cs.surfaceVariant)
                            .combinedClickable(
                                onClick = { a?.let { onFilter(it.name, it.items) } },
                                onLongClick = { onPick(i + 1) },
                            ),
                    ) {
                        val cover = s.covers[i + 1].ifBlank { null } ?: a?.items?.firstOrNull { !it.isVideo }?.uri?.toString()
                        if (cover != null) AsyncImage(cover, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        if (a != null) Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .clip(CircleShape).background(cs.primary)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text("${a.count}", fontSize = 12.sp, color = cs.onPrimary) }
                    }
                    Text(
                        (a?.name ?: "—") + " •",
                        fontSize = 12.sp, color = Soft, maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                    )
                }
            }
        }

        // ---- воспоминания ----
        if (s.memoriesEnabled) {
        Text(
            "воспоминания ✦", fontFamily = Serif, fontSize = 26.sp, color = Color.White,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp),
        )
        val years = remember(items) {
            items.map { it.date().year }.distinct().sortedDescending().ifEmpty { listOf(LocalDate.now().year) }
        }
        var yearIdx by remember { mutableIntStateOf(0) }
        val year = years[yearIdx.coerceIn(0, years.lastIndex)]
        var yearMenu by remember { mutableStateOf(false) }
        var month by remember { mutableIntStateOf(LocalDate.now().monthValue - 1) }
        val monthItems = remember(items, month, year) {
            items.filter { val d = it.date(); d.year == year && d.monthValue == month + 1 }
                .sortedByDescending { it.dateMillis }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Text(
                    "$year ▾", fontSize = 12.sp, color = Color.White,
                    modifier = Modifier.clickable { yearMenu = true }.padding(vertical = 4.dp),
                )
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    years.forEachIndexed { index, value ->
                        DropdownMenuItem(
                            text = { Text(value.toString()) },
                            onClick = { yearIdx = index; yearMenu = false },
                        )
                    }
                }
            }
            months.forEachIndexed { i, m ->
                val sel = i == month
                Text(
                    m, fontSize = 12.sp,
                    color = if (sel) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (sel) cs.primary else Color.Transparent)
                        .clickable {
                            month = i
                            val selected = items.filter { val d = it.date(); d.year == year && d.monthValue == i + 1 }
                                .sortedByDescending { it.dateMillis }
                            if (selected.isNotEmpty()) onFilter("$m $year", selected)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        if (monthItems.isEmpty()) {
            Text("в этом месяце пока пусто", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 6.dp))
        } else ThumbRow(monthItems.take(12), s, onOpen)
        if (monthItems.size > 12) {
            Text(
                "весь месяц · ${monthItems.size} фото и видео →",
                fontSize = 12.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.clickable { onFilter("${months[month]} $year", monthItems) }
                    .padding(start = 22.dp, top = 8.dp),
            )
        }

        // ---- в этот день ----
        val today = LocalDate.now()
        val onThisDay = remember(items) {
            items.filter { val d = it.date(); d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year }
        }
        if (onThisDay.isNotEmpty()) {
            Text(
                "в этот день ✦", fontFamily = Serif, fontSize = 26.sp, color = Color.White,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp),
            )
            ThumbRow(onThisDay.take(40), s, onOpen)
        }
        }
    }
}

@Composable
private fun ThumbRow(list: List<MediaItem>, s: UiSettings, onOpen: (List<MediaItem>, Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(list) { idx, m ->
            Box(
                Modifier.size(width = 84.dp, height = 104.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surfaceVariant)
                    .clickable { onOpen(list, idx) },
            ) {
                AsyncImage(m.uri, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                if (m.key in s.favorites) Icon(
                    Icons.Filled.Favorite, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickCard(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(title, fontSize = 12.sp, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Text("$count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Плавающая полупрозрачная нижняя панель.
 * Стили: капсула, macOS Dock, iOS Tab Bar, пилюля, подчёркивание, обводка, свечение…
 * selected: 0=фото, 1=альбомы, 2=видео.
 */
@Composable
fun OriginalDock(selected: Int, s: UiSettings, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    if (s.dockStyle == DockStyle.HIDDEN) return

    val base = Color(s.dockColor)
    val glassAlpha = when (s.dockStyle) {
        DockStyle.VOLUMETRIC -> 0.62f
        DockStyle.FLAT -> 0.42f
        DockStyle.MINIMAL, DockStyle.IOS -> 0.30f
        DockStyle.MACOS -> 0.40f
        else -> 0.36f
    }
    val bg = base.copy(alpha = glassAlpha)
    val onDock = if (base.luminance() > 0.5f) Color(0xFF121212) else Color.White
    val muted = onDock.copy(alpha = 0.65f)
    val borderColor = Color.White.copy(alpha = 0.20f)
    val accent = MaterialTheme.colorScheme.primary
    val style = s.dockStyle

    when (style) {
        DockStyle.FLOATING, DockStyle.VOLUMETRIC -> DockCapsule(
            selected, s, base, glassAlpha, onDock, muted, borderColor, accent, modifier, onSelect
        )
        DockStyle.MACOS -> DockMacOS(
            selected, s, bg, onDock, muted, borderColor, accent, modifier, onSelect
        )
        DockStyle.IOS -> DockIOS(
            selected, s, bg, onDock, muted, borderColor, accent, modifier, onSelect
        )
        else -> DockGeneric(
            selected, s, style, bg, onDock, muted, borderColor, accent, modifier, onSelect
        )
    }
}

/** Стекло · капсула — большая круглая кнопка по центру */
@Composable
private fun DockCapsule(
    selected: Int, s: UiSettings, base: Color, glassAlpha: Float,
    onDock: Color, muted: Color, borderColor: Color, accent: Color,
    modifier: Modifier, onSelect: (Int) -> Unit
) {
    val barHeight = 62.dp
    val shape = RoundedCornerShape(38.dp)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .shadow(18.dp, shape)
                .clip(shape)
                .background(base.copy(alpha = glassAlpha))
                .background(Color.White.copy(alpha = 0.05f + s.frost * 0.10f))
                .border(1.dp, borderColor, shape)
        )
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DockSimpleItem(Icons.Filled.Landscape, "фото", selected == 0, s.dockShowLabels, onDock, muted, accent, Modifier.weight(1f)) { onSelect(0) }
            Spacer(Modifier.weight(1f))
            DockSimpleItem(Icons.Outlined.VideoLibrary, "видео", selected == 2, s.dockShowLabels, onDock, muted, accent, Modifier.weight(1f)) { onSelect(2) }
        }
        Box(
            Modifier
                .size(70.dp)
                .offset(y = (-12).dp)
                .shadow(14.dp, CircleShape)
                .clip(CircleShape)
                .background(base.copy(alpha = (glassAlpha + 0.22f).coerceAtMost(0.78f)))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.5.dp, borderColor, CircleShape)
                .clickable { onSelect(1) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AutoAwesome, null, tint = if (selected == 1) accent else onDock, modifier = Modifier.size(26.dp))
                if (s.dockShowLabels) Text("альбомы", fontSize = 11.sp, color = if (selected == 1) accent else onDock, maxLines = 1)
            }
        }
    }
}

/** macOS Dock — стеклянная полка, иконки в скруглённых квадратах, центр крупнее */
@Composable
private fun DockMacOS(
    selected: Int, s: UiSettings, bg: Color,
    onDock: Color, muted: Color, borderColor: Color, accent: Color,
    modifier: Modifier, onSelect: (Int) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .shadow(20.dp, shape)
                .clip(shape)
                .background(bg)
                .background(Color.White.copy(alpha = 0.06f + s.frost * 0.10f))
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MacOSIcon(Icons.Filled.Landscape, "фото", selected == 0, s.dockShowLabels, onDock, muted, accent, false) { onSelect(0) }
            MacOSIcon(Icons.Filled.AutoAwesome, "альбомы", selected == 1, s.dockShowLabels, onDock, muted, accent, true) { onSelect(1) }
            MacOSIcon(Icons.Outlined.VideoLibrary, "видео", selected == 2, s.dockShowLabels, onDock, muted, accent, false) { onSelect(2) }
        }
    }
}

@Composable
private fun MacOSIcon(
    icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean,
    onDock: Color, muted: Color, accent: Color, isCenter: Boolean, onClick: () -> Unit
) {
    val size = if (isCenter && selected) 52.dp else if (isCenter) 48.dp else 42.dp
    val iconSize = if (isCenter) 26.dp else 22.dp
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(size)
                .then(if (selected) Modifier.shadow(10.dp, RoundedCornerShape(12.dp)) else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                .border(if (selected) 1.5.dp else 0.dp, if (selected) accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (selected) accent else muted, modifier = Modifier.size(iconSize))
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 10.sp, color = if (selected) accent else muted, maxLines = 1)
        }
    }
}

/** iOS Tab Bar — тонкая полупрозрачная полоса, равные вкладки, акцент + линия снизу */
@Composable
private fun DockIOS(
    selected: Int, s: UiSettings, bg: Color,
    onDock: Color, muted: Color, borderColor: Color, accent: Color,
    modifier: Modifier, onSelect: (Int) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 0.dp, vertical = 0.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(bg)
                .background(Color.White.copy(alpha = 0.04f + s.frost * 0.08f))
                .border(width = 0.5.dp, color = borderColor.copy(alpha = 0.3f), shape = shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IOSTab(Icons.Filled.Landscape, "фото", selected == 0, s.dockShowLabels, onDock, muted, accent, Modifier.weight(1f)) { onSelect(0) }
            IOSTab(Icons.Filled.AutoAwesome, "альбомы", selected == 1, s.dockShowLabels, onDock, muted, accent, Modifier.weight(1f)) { onSelect(1) }
            IOSTab(Icons.Outlined.VideoLibrary, "видео", selected == 2, s.dockShowLabels, onDock, muted, accent, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun IOSTab(
    icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean,
    onDock: Color, muted: Color, accent: Color, modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (selected) accent else muted, modifier = Modifier.size(24.dp))
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = if (selected) accent else muted, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .height(2.5.dp)
                .fillMaxWidth(0.35f)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) accent else Color.Transparent)
        )
    }
}

/** Остальные стили (пилюля, обводка, свечение, минимализм…) */
@Composable
private fun DockGeneric(
    selected: Int, s: UiSettings, style: DockStyle, bg: Color,
    onDock: Color, muted: Color, borderColor: Color, accent: Color,
    modifier: Modifier, onSelect: (Int) -> Unit
) {
    val barHeight = 56.dp
    val hPad = if (style == DockStyle.FLAT || style == DockStyle.MINIMAL) 0.dp else 16.dp
    val shape = when (style) {
        DockStyle.FLAT, DockStyle.MINIMAL -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(28.dp)
    }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = hPad, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .then(if (style != DockStyle.FLAT && style != DockStyle.MINIMAL) Modifier.shadow(18.dp, shape) else Modifier)
                .clip(shape)
                .background(bg)
                .background(Color.White.copy(alpha = 0.05f + s.frost * 0.10f))
                .border(if (style == DockStyle.FLAT || style == DockStyle.MINIMAL) 0.dp else 1.dp, borderColor, shape)
        )
        Row(
            Modifier.fillMaxWidth().height(barHeight).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DockStyledItem(Icons.Filled.Landscape, "фото", selected == 0, s.dockShowLabels, style, onDock, muted, accent, Modifier.weight(1f)) { onSelect(0) }
            DockStyledItem(Icons.Filled.AutoAwesome, "альбомы", selected == 1, s.dockShowLabels, style, onDock, muted, accent, Modifier.weight(1f)) { onSelect(1) }
            DockStyledItem(Icons.Outlined.VideoLibrary, "видео", selected == 2, s.dockShowLabels, style, onDock, muted, accent, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}


@Composable
private fun DockSimpleItem(
    icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean,
    onColor: Color, muted: Color, accent: Color, modifier: Modifier, onClick: () -> Unit
) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) accent else muted, modifier = Modifier.size(24.dp))
        if (showLabel) Text(label, fontSize = 11.sp, color = if (selected) accent else muted, maxLines = 1)
    }
}

@Composable
private fun DockStyledItem(
    icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean,
    style: DockStyle, onColor: Color, muted: Color, accent: Color,
    modifier: Modifier, onClick: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) accent else muted, modifier = Modifier.size(22.dp))
            if (showLabel) Text(label, fontSize = 11.sp, color = if (selected) accent else muted, maxLines = 1)
        }
    }

    when (style) {
        DockStyle.UNDERLINE -> {
            Column(
                modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.45f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (selected) accent else Color.Transparent)
                )
            }
        }
        DockStyle.OUTLINE -> {
            Box(
                modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .then(
                        if (selected) Modifier
                            .border(1.5.dp, accent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                        else Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) { content() }
        }
        DockStyle.GLOW -> {
            Box(
                modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .then(
                        if (selected) Modifier
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                        else Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) { content() }
        }

        DockStyle.PILL -> {
            Box(
                modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .then(
                        if (selected) Modifier
                            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                        else Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = if (selected) Color(0xFF121212) else muted, modifier = Modifier.size(22.dp))
                    if (showLabel) Text(label, fontSize = 11.sp, color = if (selected) Color(0xFF121212) else muted, maxLines = 1)
                }
            }
        }
        else -> { // MINIMAL / FLAT
            Column(modifier.clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
    }
}




