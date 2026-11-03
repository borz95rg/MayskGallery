package com.maysk.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maysk.gallery.data.AlbumInfo
import com.maysk.gallery.data.AlbumView
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.UiSettings

@Composable
fun AlbumsScreen(
    items: List<MediaItem>,
    s: UiSettings,
    openAlbum: String?,
    onOpenAlbum: (String?) -> Unit,
    onOpen: (list: List<MediaItem>, index: Int) -> Unit,
) {
    val albums = remember(items) {
        items.groupBy { it.bucket }
            .map { AlbumInfo(it.key, it.value) }
            .sortedByDescending { it.count }
    }
    val current = albums.firstOrNull { it.name == openAlbum }

    if (current != null) {
        BackHandler { onOpenAlbum(null) }
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(current.name) {
                IconButton(onClick = { onOpenAlbum(null) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Назад",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            MediaGrid(current.items, s, onOpen = { onOpen(current.items, it) })
        }
        return
    }

    val columns = when (s.albumView) {
        AlbumView.CARDS, AlbumView.GLASS, AlbumView.MINIMAL -> 2
        AlbumView.TILES, AlbumView.CIRCLES -> 3
        AlbumView.LIST -> 1
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Альбомы")
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(albums, key = { it.name }) { album ->
                AlbumTile(album, s) { onOpenAlbum(album.name) }
            }

        }
    }
}

@Composable
private fun AlbumTile(album: AlbumInfo, s: UiSettings, onClick: () -> Unit) {
    val shape = s.shape()
    val cs = MaterialTheme.colorScheme

    when (s.albumView) {
        AlbumView.CARDS, AlbumView.TILES -> {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .shadow(s.shadowDp(), shape, clip = false)
                    .clip(shape)
                    .background(cs.surfaceVariant)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(album.cover.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.count}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        AlbumView.GLASS -> {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .shadow(s.shadowDp(), shape, clip = false)
                    .clip(shape)
                    .background(cs.surfaceVariant)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(album.cover.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .fillMaxWidth()
                        .glass(s)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(album.name, color = cs.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.count}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        AlbumView.MINIMAL -> {
            Column(Modifier.clickable(onClick = onClick)) {
                AsyncImage(
                    album.cover.uri, null,
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(shape),
                    contentScale = ContentScale.Crop,
                )
                Text(album.name, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text("${album.count}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        AlbumView.CIRCLES -> {
            Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    album.cover.uri, null,
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .shadow(s.shadowDp(), CircleShape, clip = false)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    album.name, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                Text("${album.count}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        AlbumView.LIST -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(s)
                    .clickable(onClick = onClick)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    album.cover.uri, null,
                    Modifier.size(64.dp).clip(shape),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(album.name, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("${album.count} шт.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
