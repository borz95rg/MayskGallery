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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
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

/** Плавающая стеклянная капсула: Фото · Альбомы (центр) · Видео. selected: 0, 1 или 2. */
@Composable
fun OriginalDock(selected: Int, s: UiSettings, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    if (s.dockStyle == DockStyle.HIDDEN) return
    val ring = MaterialTheme.colorScheme.primary
    val shape = when (s.dockStyle) {
        DockStyle.FLAT -> RoundedCornerShape(12.dp)
        DockStyle.FLOATING -> RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 34.dp, bottomEnd = 34.dp)
        DockStyle.VOLUMETRIC -> RoundedCornerShape(30.dp)
        DockStyle.HIDDEN -> RoundedCornerShape(0.dp)
    }
    val bg = Color(s.dockColor)
    Box(modifier.fillMaxWidth().padding(horizontal = if (s.dockStyle == DockStyle.FLAT) 0.dp else 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(s.dockHeight.coerceIn(60,140).dp)
                .then(if (s.dockStyle == DockStyle.VOLUMETRIC) Modifier.shadow(16.dp, shape) else Modifier)
                .clip(shape)
                .then(if (s.dockStyle == DockStyle.VOLUMETRIC) Modifier.background(Brush.verticalGradient(listOf(bg.copy(alpha=.82f), bg, bg.copy(alpha=.92f)))) else Modifier.background(bg))
                .border(if (s.dockStyle == DockStyle.VOLUMETRIC || s.dockStyle == DockStyle.FLOATING) 1.dp else 0.dp, Color.White.copy(alpha=.16f), shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DockItem(Icons.Filled.Landscape, "Фото", selected == 0, Modifier.weight(1f), s.dockShowLabels, ring) { onSelect(0) }
            DockItem(Icons.Filled.AutoAwesome, "Альбомы", selected == 1, Modifier.weight(1f), s.dockShowLabels, ring) { onSelect(1) }
            DockItem(Icons.Outlined.VideoLibrary, "Видео", selected == 2, Modifier.weight(1f), s.dockShowLabels, ring) { onSelect(2) }
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector, title: String, on: Boolean, modifier: Modifier,
    showLabel: Boolean, accent: Color, onClick: () -> Unit
) {
    Column(modifier.clickable(onClick = onClick).padding(vertical=4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (on) accent else Color.White.copy(alpha = 0.68f), modifier = Modifier.size(26.dp))
        if (showLabel) Text(title, fontSize = 12.sp, color = if (on) accent else Color.White.copy(alpha = 0.68f), maxLines = 1)
    }
}
