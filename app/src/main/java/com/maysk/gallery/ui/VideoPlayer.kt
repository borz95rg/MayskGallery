package com.maysk.gallery.ui

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.maysk.gallery.data.MediaItem
import kotlinx.coroutines.delay

private val playerSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val emerald = Color(0xFF16C79A)
private val gold = Color(0xFFFFC83D)

/** Полноценный локальный видеоплеер Media3 с адаптивным стеклянным интерфейсом. */
@Composable
fun VideoPlayer(item: MediaItem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAudioTracks by remember { mutableStateOf(false) }
    var showSubtitleTracks by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var selectedAudioLanguage by remember { mutableStateOf<String?>(null) }
    var trackRevision by remember { mutableIntStateOf(0) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                trackRevision++
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(player, dragging) {
        while (true) {
            if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) duration = player.duration
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(4500)
            controlsVisible = false
        }
    }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(repeat) { player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    val accent = MaterialTheme.colorScheme.primary.let { if (it == Color.White || it == Color.Black) emerald else it }
    val audioLanguages = remember(trackRevision) {
        buildList {
            player.currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_AUDIO) for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    add((f.language ?: "und") to (f.label ?: f.language ?: "Аудиодорожка ${i + 1}"))
                }
            }
        }.distinctBy { it.first }
    }
    val subtitleLanguages = remember(trackRevision) {
        buildList {
            player.currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_TEXT) for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    add((f.language ?: "und") to (f.label ?: f.language ?: "Субтитры ${i + 1}"))
                }
            }
        }.distinctBy { it.first }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF050807))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
        )

        // Незаметная зона нажатия поверх изображения: показывает/скрывает панели.
        Box(Modifier.fillMaxSize().clickable { if (!locked) controlsVisible = !controlsVisible })

        AnimatedVisibility(visible = controlsVisible || locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent, Color.Black.copy(.82f)))))

                if (!locked) {
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(item.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Maysk Gallery · Видео", color = Color.White.copy(.62f), fontSize = 11.sp)
                        }
                        IconButton(onClick = { controlsVisible = false; enterPictureInPicture(activity) }) {
                            Icon(Icons.Filled.PictureInPictureAlt, "Картинка в картинке", tint = Color.White)
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Настройки видео", tint = Color.White) }
                    }
                }

                if (!locked) {
                    Row(
                        Modifier.align(Alignment.Center).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        PlayerRoundButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }, size = 54) {
                            Icon(Icons.Filled.Replay10, "Назад на 10 секунд", tint = Color.White, modifier = Modifier.size(29.dp))
                        }
                        PlayerRoundButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            size = 76, highlighted = true,
                        ) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Пауза" else "Воспроизвести", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        PlayerRoundButton(onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration)) }, size = 54) {
                            Icon(Icons.Filled.Forward10, "Вперёд на 10 секунд", tint = Color.White, modifier = Modifier.size(29.dp))
                        }
                    }
                } else {
                    PlayerRoundButton(
                        onClick = { locked = false; controlsVisible = true },
                        modifier = Modifier.align(Alignment.Center), size = 58,
                    ) { Icon(Icons.Filled.LockOpen, "Разблокировать", tint = Color.White) }
                }

                if (!locked) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(position), color = Color.White, fontSize = 12.sp)
                            Text(" / ${formatDuration(duration)}", color = Color.White.copy(.62f), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(videoResolution(player), color = Color.White.copy(.75f), fontSize = 11.sp)
                        }
                        Slider(
                            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { dragging = true; position = (it * duration).toLong() },
                            onValueChangeFinished = { player.seekTo(position); dragging = false },
                            modifier = Modifier.fillMaxWidth().height(30.dp),
                        )
                        Surface(
                            color = Color(0xDD07110F), shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, emerald.copy(.35f), RoundedCornerShape(28.dp)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlayerTool(Icons.Filled.Lock, "Блокировка", locked) { locked = true }
                                PlayerTool(Icons.Filled.Repeat, "Повтор", repeat) { repeat = !repeat }
                                PlayerTool(Icons.Filled.Subtitles, "Субтитры", subtitlesEnabled) { showSubtitleTracks = true }
                                PlayerTool(Icons.Filled.Headphones, "Аудио", false) { showAudioTracks = true }
                                PlayerTool(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Звук", muted) { muted = !muted }
                                PlayerTool(Icons.Filled.Fullscreen, "Экран", fullScreen) {
                                    fullScreen = !fullScreen
                                    activity?.requestedOrientation = if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Скорость", color = Color.White.copy(.62f), fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            playerSpeeds.forEach { value ->
                                val selected = speed == value
                                Box(
                                    Modifier.padding(end = 5.dp).clip(CircleShape)
                                        .background(if (selected) gold.copy(.18f) else Color.White.copy(.08f))
                                        .border(if (selected) 1.dp else .5.dp, if (selected) gold else Color.White.copy(.12f), CircleShape)
                                        .clickable { speed = value }.padding(horizontal = 9.dp, vertical = 6.dp),
                                ) { Text("${value}x", color = if (selected) gold else Color.White.copy(.8f), fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF101715),
                title = { Text("Настройки видео", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Скорость воспроизведения", color = Color.White.copy(.72f))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            playerSpeeds.forEach { value ->
                                TextButton(onClick = { speed = value }) {
                                    Text("${value}x", color = if (speed == value) gold else Color.White)
                                }
                            }
                        }
                        Text("Повтор", color = Color.White.copy(.72f))
                        TextButton(onClick = { repeat = !repeat }) { Text(if (repeat) "Включён" else "Выключен", color = emerald) }
                        Text("Субтитры", color = Color.White.copy(.72f))
                        TextButton(onClick = { showSettings = false; showSubtitleTracks = true }) { Text("Выбрать дорожку", color = emerald) }
                        Text("Аудиодорожка", color = Color.White.copy(.72f))
                        TextButton(onClick = { showSettings = false; showAudioTracks = true }) { Text("Выбрать дорожку", color = emerald) }
                        Text("Качество исходного файла менять нельзя: плеер воспроизводит оригинал без перекодирования.", color = Color.White.copy(.48f), fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Готово", color = gold) } },
            )
        }

        if (showSubtitleTracks) {
            AlertDialog(
                onDismissRequest = { showSubtitleTracks = false }, containerColor = Color(0xFF101715),
                title = { Text("Субтитры", color = Color.White) },
                text = {
                    Column {
                        TextButton(onClick = {
                            subtitlesEnabled = false
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            showSubtitleTracks = false
                        }) { Text("Выключить", color = if (!subtitlesEnabled) gold else Color.White) }
                        subtitleLanguages.forEach { (language, label) ->
                            TextButton(onClick = {
                                subtitlesEnabled = true
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(language).build()
                                showSubtitleTracks = false
                            }) { Text(label, color = Color.White) }
                        }
                        if (subtitleLanguages.isEmpty()) Text("В файле нет встроенных дорожек субтитров", color = Color.White.copy(.6f), fontSize = 13.sp)
                    }
                }, confirmButton = { TextButton(onClick = { showSubtitleTracks = false }) { Text("Закрыть", color = gold) } },
            )
        }

        if (showAudioTracks) {
            AlertDialog(
                onDismissRequest = { showAudioTracks = false }, containerColor = Color(0xFF101715),
                title = { Text("Аудиодорожка", color = Color.White) },
                text = {
                    Column {
                        audioLanguages.forEach { (language, label) ->
                            TextButton(onClick = {
                                selectedAudioLanguage = language
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(language).build()
                                showAudioTracks = false
                            }) { Text(label, color = if (selectedAudioLanguage == language) gold else Color.White) }
                        }
                        if (audioLanguages.isEmpty()) Text("Дополнительных аудиодорожек нет", color = Color.White.copy(.6f), fontSize = 13.sp)
                    }
                }, confirmButton = { TextButton(onClick = { showAudioTracks = false }) { Text("Закрыть", color = gold) } },
            )
        }
    }
}

@Composable
private fun PlayerRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size.dp).clip(CircleShape)
            .background(if (highlighted) Brush.linearGradient(listOf(Color(0xCC34250A), Color(0xDD080B0A))) else Brush.linearGradient(listOf(Color(0xAA15201D), Color(0xAA050807))))
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) gold.copy(.9f) else emerald.copy(.45f), CircleShape)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PlayerTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) gold else Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White.copy(.78f), fontSize = 9.sp)
    }
}

private fun enterPictureInPicture(activity: android.app.Activity?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        runCatching {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
            )
        }
    }
}

private fun videoResolution(player: ExoPlayer): String {
    val format = player.videoFormat ?: return "Видео"
    return if (format.width > 0 && format.height > 0) "${format.width}×${format.height}" else "Видео"
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
