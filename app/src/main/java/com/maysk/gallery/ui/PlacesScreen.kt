package com.maysk.gallery.ui

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaMeta
import com.maysk.gallery.data.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Группа фото, сделанных в одном месте (сетка ~11 км). */
private class Place(val key: String, val lat: Double, val lon: Double, val items: List<MediaItem>)

private fun clusterKey(lat: Double, lon: Double) =
    "%d_%d".format(Math.round(lat * 10), Math.round(lon * 10))

/**
 * Раздел «Места»: фото сгруппированы по геопозиции из EXIF, у каждой группы —
 * название города/района (через системный Geocoder, кэшируется).
 */
@Composable
fun PlacesScreen(
    items: List<MediaItem>,
    meta: Map<String, MediaMeta>,
    s: UiSettings,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val places = remember(items, meta) {
        items.filter { !it.isVideo }
            .mapNotNull { i -> meta[i.key]?.takeIf { it.hasGeo }?.let { i to it } }
            .groupBy { (_, m) -> clusterKey(m.lat, m.lon) }
            .map { (k, list) ->
                Place(
                    k,
                    list.map { it.second.lat }.average(),
                    list.map { it.second.lon }.average(),
                    list.map { it.first }.sortedByDescending { it.dateMillis },
                )
            }
            .sortedByDescending { it.items.size }
    }

    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(places) {
        val cacheFile = File(ctx.filesDir, "place_names.json")
        val cache = HashMap<String, String>()
        withContext(Dispatchers.IO) {
            runCatching {
                val j = JSONObject(cacheFile.readText())
                for (k in j.keys()) cache[k] = j.getString(k)
            }
        }
        names = cache.toMap()
        if (!Geocoder.isPresent()) return@LaunchedEffect
        val geocoder = Geocoder(ctx, Locale.getDefault())
        var changed = false
        for (p in places) {
            if (p.key in cache) continue
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val a = geocoder.getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
                    a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: a?.countryName
                }.getOrNull()
            }
            if (name != null) {
                cache[p.key] = name
                names = cache.toMap()
                changed = true
            }
        }
        if (changed) withContext(Dispatchers.IO) {
            runCatching {
                val j = JSONObject(); cache.forEach { (k, v) -> j.put(k, v) }
                cacheFile.writeText(j.toString())
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Места", actions = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
        if (!locationGranted) {
            Text(
                "Чтобы определить, где сделаны фото, нужен доступ к геопозиции в снимках: Android скрывает её без этого разрешения.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TextButton(onClick = onRequestLocation, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Разрешить доступ к геопозиции")
            }
        }
        val total = places.sumOf { it.items.size }
        Text(
            if (places.isEmpty()) "Фото с геопозицией не найдены. Включи «Геотеги» в настройках камеры — новые снимки появятся здесь."
            else "Мест: ${places.size} · фото с геопозицией: $total",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(places, key = { it.key }) { p ->
                Column {
                    MediaThumb(p.items.first(), s, false) { onOpen(p.items, 0) }
                    Text(
                        names[p.key] ?: "%.3f, %.3f".format(p.lat, p.lon),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp, maxLines = 1,
                    )
                    Text(
                        "${p.items.size} фото",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    TextButton(onClick = {
                        val q = Uri.encode(names[p.key] ?: "${p.lat},${p.lon}")
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}($q)")))
                        }
                    }) { Text("На карте", fontSize = 12.sp) }
                }
            }
        }
    }
}
