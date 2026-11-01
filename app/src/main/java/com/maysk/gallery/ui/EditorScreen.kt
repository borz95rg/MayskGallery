package com.maysk.gallery.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaSaver
import com.maysk.gallery.data.UiSettings
import com.maysk.gallery.edit.CropRatio
import com.maysk.gallery.edit.EditFilter
import com.maysk.gallery.edit.EditState
import com.maysk.gallery.edit.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Редактор: фильтры, яркость/контраст/насыщенность, поворот, отражение, обрезка. Сохраняет копию. */
@Composable
fun EditorScreen(item: MediaItem, s: UiSettings, onClose: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var st by remember { mutableStateOf(EditState()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        val bmp = withContext(Dispatchers.IO) { ImageOps.load(ctx, item.uri, 1280) }
        if (bmp == null) failed = true else source = bmp
    }
    LaunchedEffect(source, st) {
        val src = source ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { ImageOps.render(src, st) }
    }
    // миниатюры фильтров считаем один раз
    val filterThumbs = remember(source) {
        source?.let { src ->
            val small = ImageOps.thumbnail(src, 160)
            EditFilter.values().map { it to ImageOps.render(small, EditState(filter = it)) }
        }.orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
            Text("Редактор", color = Color.White, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Button(
                enabled = !saving && source != null && !st.isDefault,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveCopy(ctx, item, st) }
                        saving = false
                        Toast.makeText(
                            ctx,
                            if (ok) "Сохранено в Pictures/MayskGallery" else "Не удалось сохранить",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (ok) onSaved()
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            ) { Text(if (saving) "Сохраняю…" else "Сохранить") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val p = preview
            when {
                failed -> Text("Не удалось открыть изображение", color = Color.White)
                p == null -> Text("Загрузка…", color = Color.White)
                else -> Image(
                    bitmap = p.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(Modifier.fillMaxWidth().glass(s, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Фильтры", "Настройки", "Обрезка").forEachIndexed { i, t ->
                    Text(
                        t,
                        color = if (tab == i) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(filterThumbs) { (f, bmp) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = f.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        if (st.filter == f) 2.dp else 0.dp,
                                        cs.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { st = st.copy(filter = f) },
                            )
                            Text(f.title, color = cs.onSurface, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                1 -> Column(Modifier.padding(horizontal = 16.dp)) {
                    AdjustSlider("Яркость", st.brightness, -0.5f..0.5f) { st = st.copy(brightness = it) }
                    AdjustSlider("Контраст", st.contrast, 0.5f..1.5f) { st = st.copy(contrast = it) }
                    AdjustSlider("Насыщенность", st.saturation, 0f..2f) { st = st.copy(saturation = it) }
                }

                else -> Column {
                    ChipRow(CropRatio.values().toList(), st.crop, { it.title }) { st = st.copy(crop = it) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { st = st.copy(rotation = (st.rotation + 270) % 360) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, "Повернуть влево", tint = cs.onSurface)
                        }
                        IconButton(onClick = { st = st.copy(rotation = (st.rotation + 90) % 360) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, "Повернуть вправо", tint = cs.onSurface)
                        }
                        IconButton(onClick = { st = st.copy(flip = !st.flip) }) {
                            Icon(Icons.Filled.Flip, "Отразить", tint = cs.onSurface)
                        }
                        Text(
                            "Сбросить",
                            color = cs.primary,
                            modifier = Modifier.clickable { st = EditState() }.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Применяет правки к полноразмерной картинке и сохраняет новый файл. Оригинал не меняется. */
private fun saveCopy(ctx: android.content.Context, item: MediaItem, st: EditState): Boolean {
    val full = ImageOps.load(ctx, item.uri, 4096) ?: return false
    val out = ImageOps.render(full, st)
    val base = item.name.substringBeforeLast('.', item.name)
    val name = "${base}_maysk_${System.currentTimeMillis() / 1000}.jpg"
    val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    return MediaSaver.save(ctx, name, "image/jpeg", false, "MayskGallery") { it.write(bytes) } != null
}
