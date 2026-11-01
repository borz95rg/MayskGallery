package com.maysk.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maysk.gallery.ai.AiCategory
import com.maysk.gallery.ai.AiSearch
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings

@Composable
fun SearchScreen(
    items: List<MediaItem>,
    labels: Map<String, List<String>>,
    progress: Pair<Int, Int>?,
    s: UiSettings,
    onOpen: (list: List<MediaItem>, index: Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<AiCategory?>(null) }

    val results = remember(query, category, items, labels) {
        val base = category?.let { c -> items.filter { c.matches(labels[it.key].orEmpty()) } } ?: items
        if (query.isBlank()) base else AiSearch.search(query, base, labels)
    }
    val searching = query.isNotBlank() || category != null

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("AI Поиск")

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Например: машина, собака, цветы") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Очистить") }
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))

        ChipRow(
            options = listOf<AiCategory?>(null) + AiCategory.values().toList(),
            selected = category,
            label = { c -> c?.let { "${it.emoji} ${it.title}" } ?: "Все" },
            onSelect = { category = it },
        )
        Spacer(Modifier.height(8.dp))

        MediaGrid(
            items = if (searching) results else emptyList(),
            s = s,
            onOpen = { onOpen(results, it) },
            header = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(s)
                        .padding(14.dp)
                ) {
                    InfoLine(
                        if (progress != null) "Анализ фото: ${progress.first} из ${progress.second}"
                        else if (searching) "Найдено: ${results.size}"
                        else "Введите запрос или выберите категорию"
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Анализ идёт на устройстве, интернет не нужен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoLine(text: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
