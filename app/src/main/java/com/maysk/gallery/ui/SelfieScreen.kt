package com.maysk.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.maysk.gallery.data.MediaSaver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/** Экран живого предпросмотра фронтальной камеры и сохранения селфи в общую галерею. */
@Composable
fun SelfieScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var takingPhoto by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionAsked = true
        if (!granted) message = "Разрешите доступ к камере, чтобы делать селфи"
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionAsked) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, hasPermission, previewView) {
        if (!hasPermission || previewView == null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            providerFuture.addListener({
                try {
                    provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView!!.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView!!.display?.rotation ?: Surface.ROTATION_0)
                        .build()
                    provider!!.unbindAll()
                    provider!!.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                    imageCapture = capture
                    cameraReady = true
                    message = null
                } catch (_: Exception) {
                    cameraReady = false
                    message = "Не удалось открыть фронтальную камеру"
                }
            }, mainExecutor)
            onDispose {
                provider?.unbindAll()
                imageCapture = null
                cameraReady = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } },
                modifier = Modifier.fillMaxSize(),
                update = { previewView = it },
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Для съёмки селфи нужен доступ к фронтальной камере", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
            }
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text("Селфи", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (message != null) Text(message!!, color = Color.White)
            else if (!cameraReady) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Фронтальная камера", color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape)
                    .background(if (cameraReady && !takingPhoto) Color.White else Color.Gray)
                    .padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        val capture = imageCapture ?: return@IconButton
                        if (takingPhoto) return@IconButton
                        takingPhoto = true
                        message = null
                        val temp = File(context.cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(temp).build()
                        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val saved = MediaSaver.save(
                                    context,
                                    "SELFIE_$stamp.jpg",
                                    "image/jpeg",
                                    false,
                                    "MayskGallery/Selfies",
                                ) { out -> temp.inputStream().use { it.copyTo(out) } }
                                temp.delete()
                                takingPhoto = false
                                if (saved != null) {
                                    message = "Селфи сохранено в галерею"
                                    onSaved()
                                } else message = "Не удалось сохранить фотографию"
                            }
                            override fun onError(exception: ImageCaptureException) {
                                temp.delete()
                                takingPhoto = false
                                message = "Ошибка съёмки: ${exception.message ?: "попробуйте ещё раз"}"
                            }
                        })
                    },
                    enabled = cameraReady && !takingPhoto,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (takingPhoto) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(28.dp))
                    else Icon(Icons.Filled.CameraAlt, contentDescription = "Сделать селфи", tint = Color.Black, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Фото сохраняется в Галерею → MayskGallery → Selfies", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
