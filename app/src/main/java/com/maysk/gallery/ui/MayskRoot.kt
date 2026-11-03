package com.maysk.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.maysk.gallery.MainViewModel
import com.maysk.gallery.data.DockStyle
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.update.ApkInstaller


private class ViewerState(val items: List<MediaItem>, val index: Int, val secret: Boolean = false)

@Composable
fun MayskRoot(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    MayskTheme(s) {
        RootContent(vm)
    }
}

@Composable
private fun RootContent(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val faces by vm.faces.collectAsStateWithLifecycle()
    val selfieYes by vm.selfieYes.collectAsStateWithLifecycle()
    val selfieNo by vm.selfieNo.collectAsStateWithLifecycle()
    val faceProgress by vm.faceProgress.collectAsStateWithLifecycle()
    val selfieCount = remember(items, meta, faces, selfieYes, selfieNo) {
        items.count { com.maysk.gallery.data.SelfieIndex.isSelfie(it, meta[it.key], faces[it.key], selfieYes, selfieNo) }
    }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val secretItems by vm.secret.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Главный экран и нижняя капсула — единая навигация для всех цветовых тем.
    // 0 Галерея, 1 Альбомы, 2 AI, 3 Сейф, 4 Настройки, 5 Главная, 6 Видео, 7 Подборка, 8 Корзина
    var tab by rememberSaveable { mutableIntStateOf(5) }
    var filter by remember { mutableStateOf<Pair<String, List<MediaItem>>?>(null) }
    val pickCover = rememberCoverPicker(vm)
    BackHandler(enabled = tab != 5) { tab = 5 }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<ViewerState?>(null) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    var vaultUnlocked by remember { mutableStateOf(false) }

    // сейф закрывается при выходе из вкладки и при сворачивании приложения
    LaunchedEffect(tab) { if (tab != 3) vaultUnlocked = false }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, s.lockOnBackground) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && s.lockOnBackground && viewer?.secret != true) vaultUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // в сейфе запрещены скриншоты и превью в списке приложений
    val secure = tab == 3 || viewer?.secret == true
    DisposableEffect(secure) {
        val w = ctx.findActivity()?.window
        if (secure) w?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose { w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // ---- разрешения ----
    val perms = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    // Android 14+: пользователь может дать «только выбранные фото» (READ_MEDIA_VISUAL_USER_SELECTED) —
    // это тоже рабочий доступ, MediaStore просто вернёт выбранные файлы.
    fun hasMediaAccess(): Boolean {
        fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                (ok(Manifest.permission.READ_MEDIA_IMAGES) && ok(Manifest.permission.READ_MEDIA_VIDEO)) ||
                    ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> ok(Manifest.permission.READ_MEDIA_IMAGES) || ok(Manifest.permission.READ_MEDIA_VIDEO)
            else -> ok(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    fun partialAccess(): Boolean = Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasMediaAccess()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        granted = hasMediaAccess()
        if (granted) {
            vm.reload()
            if (partialAccess()) Toast.makeText(
                ctx, "Открыт доступ только к выбранным фото. Чтобы видеть всю галерею, разрешите «Все фото» в настройках Android.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    // Перечитываем MediaStore после возврата из камеры или другого приложения:
    // новые фотографии появляются без ручного перезапуска галереи.
    DisposableEffect(lifecycleOwner, granted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasMediaAccess()
                if (granted) vm.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (granted) vm.reload() else permLauncher.launch(perms)
    }

    // ACCESS_MEDIA_LOCATION (Android 10+): без него система вырезает GPS из EXIF фото.
    var locGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        locGranted = ok
        vm.setLocationAccess(ok)
    }
    LaunchedEffect(granted, locGranted) {
        if (granted) {
            vm.setLocationAccess(locGranted)
            if (!locGranted) locLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    // ---- удаление в корзину (Android 11+) ----
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewer = null
        vm.reload()
    }
    fun performDelete(itemsToDelete: List<MediaItem>) {
        if (itemsToDelete.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createTrashRequest(ctx.contentResolver, itemsToDelete.map { it.uri }, true)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            // Не удаляем безвозвратно на Android 10 и старше: там нет API
            // системной корзины MediaStore, поэтому безопаснее отменить действие.
            Toast.makeText(
                ctx,
                "Корзина доступна начиная с Android 11. Файлы не удалены.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }


    fun delete(item: MediaItem) {
        if (s.confirmDelete) pendingDelete = item else performDelete(listOf(item))
    }

    /** После копирования в сейф просим систему удалить оригинал (появится диалог подтверждения). */
    fun deleteOriginal(item: MediaItem) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(item.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            try {
                ctx.contentResolver.delete(item.uri, null, null)
                viewer = null
                vm.reload()
            } catch (e: SecurityException) {
                val ra = if (Build.VERSION.SDK_INT >= 29) e as? android.app.RecoverableSecurityException else null
                if (ra != null) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(ra.userAction.actionIntent.intentSender).build())
                } else {
                    Toast.makeText(ctx, "Не удалось удалить оригинал", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.messageShown()
        }
    }

    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.getBottom(density)
    val navBarDp = with(density) { navBarBottom.toDp() }

    Box(Modifier.fillMaxSize().background(appBackground(s))) {
        Scaffold(
            containerColor = Color.Transparent,
            // Темы меняют только палитру и эффекты; навигационная структура не меняется.
            topBar = {},
            bottomBar = {},
        ) { pad ->
            // Контент уходит под плавающую полупрозрачную панель.
            // Только системная навигация + небольшой зазор — без глухой чёрной шторки.
            val bottomPad = if (s.dockStyle == DockStyle.HIDDEN) navBarDp
            else 28.dp + navBarDp
            Box(Modifier.padding(pad).padding(bottom = bottomPad).fillMaxSize()) {



                when {
                    !granted -> PermissionScreen { permLauncher.launch(perms) }
                    !loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Загрузка…", color = MaterialTheme.colorScheme.onBackground)
                    }
                    else -> when (tab) {
                        0 -> GalleryScreen(items.filterNot { it.isVideo }, s,
                            onOpen = { list, i -> viewer = ViewerState(list, i) },
                            onDeleteSelected = { selected -> pendingBulkDelete = selected })
                        1 -> AlbumsScreen(items, s, openAlbum, { openAlbum = it }) { list, i -> viewer = ViewerState(list, i) }
                        2 -> SearchScreen(items, labels, progress, s) { list, i -> viewer = ViewerState(list, i) }
                        3 -> SecretTab(
                            vault = vm.vault,
                            items = secretItems,
                            s = s,
                            unlocked = vaultUnlocked,
                            onUnlocked = { vaultUnlocked = true },
                        ) { list, i -> viewer = ViewerState(list, i, secret = true) }
                        5 -> HomeScreen(
                            items = items,
                            s = s,
                            onOpen = { list, i -> viewer = ViewerState(list, i) },
                            onFilter = { t, list -> filter = t to list; tab = 7 },
                            onTab = { tab = it },
                            onPick = pickCover,
                            onSelfie = { tab = 14 },
                            meta = meta,
                            selfieCountOverride = selfieCount,
                        )
                        6 -> {
                            val videos = items.filter { it.isVideo }
                            GalleryScreen(videos, s,
                                onOpen = { list, i -> viewer = ViewerState(list, i) },
                                onDeleteSelected = { selected -> pendingBulkDelete = selected })
                        }
                        7 -> {
                            val f = filter
                            Column(Modifier.fillMaxSize()) {
                                ScreenTitle(f?.first ?: "") {
                                    androidx.compose.material3.IconButton(onClick = { tab = 5 }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack, "Назад",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }
                                MediaGrid(f?.second ?: emptyList(), s, onOpen = { viewer = ViewerState(f?.second ?: emptyList(), it) })
                            }
                        }
                        8 -> TrashScreen(s) { list, i -> viewer = ViewerState(list, i) }
                        14 -> SelfieScreen(
                            items = items,
                            meta = meta,
                            faces = faces,
                            yes = selfieYes,
                            no = selfieNo,
                            progress = faceProgress,
                            settings = s,
                            onBack = { tab = 5 },
                            onMark = { item, selfie -> vm.markSelfie(item, selfie) },
                            onOpen = { list, index -> viewer = ViewerState(list, index) },
                        )
                        13 -> PlacesScreen(
                            items = items,
                            meta = meta,
                            s = s,
                            locationGranted = locGranted,
                            onRequestLocation = { locLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                            onBack = { tab = 5 },
                            onOpen = { list, i -> viewer = ViewerState(list, i) },
                        )
                        in 9..12 -> ExtraFeaturesScreen(
                            feature = when (tab) { 9 -> "memories"; 10 -> "duplicates"; 11 -> "collage"; 12 -> "slideshow"; else -> "map" },
                            items = items,
                            s = s,
                            onOpen = { list, i -> viewer = ViewerState(list, i) },
                            onBack = { tab = 5 },
                        )
                        else -> SettingsScreen(vm, s)
                    }
                }
            }
        }

        if (viewer == null && editing == null && tab != 14) {
            OriginalDock(
                selected = when (tab) { 0 -> 0; 6 -> 2; else -> 1 },
                s = s,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { tab = when (it) { 0 -> 0; 2 -> 6; else -> 5 } }
        }

        pendingBulkDelete?.let { selected ->
            AlertDialog(
                onDismissRequest = { pendingBulkDelete = null },
                title = { Text("Переместить в корзину?") },
                text = { Text("Будет перемещено ${selected.size} файлов — фото и видео. Их можно будет восстановить из корзины Android.") },
                confirmButton = {
                    TextButton(onClick = { pendingBulkDelete = null; performDelete(selected) }) { Text("Удалить (${selected.size})") }
                },
                dismissButton = { TextButton(onClick = { pendingBulkDelete = null }) { Text("Отмена") } },
            )
        }

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Переместить в корзину?") },
                text = { Text("Файл «${item.name}» будет перемещён в системную корзину Android.") },
                confirmButton = {
                    TextButton(onClick = { pendingDelete = null; performDelete(listOf(item)) }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
            )
        }

        viewer?.let { v ->
            Viewer(
                items = v.items,
                startIndex = v.index,
                s = s,
                labels = labels,
                onClose = { viewer = null },
                onFavorite = { vm.toggleFavorite(it) },
                onDelete = {
                    if (v.secret) {
                        vm.deleteFromVault(it)
                        viewer = null
                    } else delete(it)
                },
                secretMode = v.secret,
                onEdit = { editing = it },
                onHide = { item ->
                    scope.launch {
                        if (vm.hideToVault(item)) deleteOriginal(item)
                        else Toast.makeText(ctx, "Не удалось спрятать файл", Toast.LENGTH_SHORT).show()
                    }
                },
                onRestore = { item ->
                    vm.restoreFromVault(item) { ok ->
                        Toast.makeText(
                            ctx,
                            if (ok) "Возвращено в Pictures/Restored" else "Не удалось вернуть файл",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (ok) viewer = null
                    }
                },
            )
        }

        editing?.let { item ->
            EditorScreen(
                item = item,
                s = s,
                onClose = { editing = null },
                onSaved = {
                    editing = null
                    viewer = null
                    vm.reload()
                },
            )
        }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Доступна версия ${info.version}") },
            text = {
                Text(
                    info.notes.ifBlank { "Вышло обновление Maysk Gallery AI." },
                    maxLines = 12,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.dismissUpdate()
                    ApkInstaller.start(ctx, info)
                }) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissUpdate() }) { Text("Позже") }
            },
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            "Maysk Gallery AI",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Не просто храните фотографии. Управляйте воспоминаниями.\n\nДайте доступ к фото и видео, чтобы показать вашу галерею.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Разрешить доступ") }
    }
}
