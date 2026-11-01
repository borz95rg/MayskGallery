package com.maysk.gallery.ui

import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maysk.gallery.data.MediaItem
import kotlinx.coroutines.delay

private val speeds = listOf(1f, 1.25f, 1.5f, 2f)

private fun MediaPlayer.setSpeedSafe(x: Float, shouldPlay: Boolean) {
    try {
        playbackParams = playbackParams.setSpeed(x)
        if (!shouldPlay && isPlaying) pause()
    } catch (_: Exception) {
    }
}

/** Встроенный видеоплеер: пауза, перемотка, скорость, громкость/mute, полный экран. */
@Composable
fun VideoPlayer(item: MediaItem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    var view by remember { mutableStateOf<VideoView?>(null) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(1) }
    var speed by remember { mutableFloatStateOf(1f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(view) {
        while (true) {
            val v = view
            if (v != null && !dragging) pos = v.currentPosition
            delay(250)
        }
    }
    LaunchedEffect(controls, playing, dragging) {
        if (controls && playing && !dragging) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(mp, volume, muted) {
        val v = if (muted) 0f else volume
        mp?.setVolume(v, v)
    }
    LaunchedEffect(mp, speed) { mp?.setSpeedSafe(speed, playing) }

    DisposableEffect(full) {
        val act = ctx.findActivity()
        act?.requestedOrientation =
            if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val w = act?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            if (full) {
                c.hide(WindowInsetsCompat.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val act = ctx.findActivity()
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            act?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                VideoView(c).apply {
                    setVideoURI(item.uri)
                    setOnPreparedListener { m ->
                        mp = m
                        dur = m.duration.coerceAtLeast(1)
                        start()
                    }
                    setOnCompletionListener {
                        playing = false
                        controls = true
                    }
                    view = this
                }
            },
            onRelease = { it.stopPlayback() },
        )
        // тап по видео показывает/прячет управление
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) })

        if (controls) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent, Color.Black.copy(0.65f)))
                )
            )
            Row(
                Modifier.systemBarsPadding().fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть", tint = Color.White)
                }
                Text(item.name, color = Color.White, maxLines = 1, fontSize = 14.sp)
            }

            IconButton(
                onClick = {
                    view?.let { v ->
                        if (v.isPlaying) { v.pause(); playing = false } else {
                            if (pos >= dur - 300) v.seekTo(0)
                            v.start(); playing = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "Пауза" else "Воспроизвести",
                    tint = Color.White, modifier = Modifier.size(44.dp),
                )
            }

            Column(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(horizontal = 12.dp)) {
                Slider(
                    value = (pos.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = true; pos = (it * dur).toInt() },
                    onValueChangeFinished = { view?.seekTo(pos); dragging = false },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${formatDuration(pos.toLong())} / ${formatDuration(dur.toLong())}",
                        color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${if (speed % 1f == 0f) speed.toInt().toString() + ".0" else speed.toString()}x",
                        color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable { speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size] }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    IconButton(onClick = { muted = !muted }) {
                        Icon(if (muted || volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Звук", tint = Color.White)
                    }
                    Slider(
                        value = if (muted) 0f else volume,
                        onValueChange = { volume = it; muted = it == 0f },
                        modifier = Modifier.width(90.dp),
                    )
                    IconButton(onClick = { full = !full }) {
                        Icon(if (full) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Полный экран", tint = Color.White)
                    }
                }
            }
        }
    }
}
