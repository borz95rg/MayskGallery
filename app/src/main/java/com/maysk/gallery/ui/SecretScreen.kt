package com.maysk.gallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.SecretVault
import com.maysk.gallery.data.UiSettings
import kotlinx.coroutines.delay

private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun biometricAvailable(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

private fun showBiometric(activity: FragmentActivity, onOk: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Секретный альбом")
        .setSubtitle("Отпечаток или Face Unlock")
        .setNegativeButtonText("Ввести PIN")
        .setAllowedAuthenticators(AUTH)
        .build()
    prompt.authenticate(info)
}

/** Вкладка «Сейф»: сначала вход, потом сетка скрытых файлов. */
@Composable
fun SecretTab(
    vault: SecretVault,
    items: List<MediaItem>,
    s: UiSettings,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    onOpen: (list: List<MediaItem>, index: Int) -> Unit,
) {
    if (!unlocked) {
        PinGate(vault, onUnlocked)
        return
    }
    val ctx = LocalContext.current
    val bioAvailable = remember { biometricAvailable(ctx) }
    var bio by remember { mutableStateOf(vault.biometric) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Секретный альбом")
        MediaGrid(
            items = items,
            s = s,
            onOpen = { onOpen(items, it) },
            header = {
                Column(Modifier.fillMaxWidth().glass(s).padding(14.dp)) {
                    Text(
                        if (items.isEmpty()) "Здесь пусто. Откройте фото в галерее и нажмите замок, чтобы спрятать его сюда."
                        else "Скрыто файлов: ${items.size}. Они не видны в галерее и других приложениях.",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (bioAvailable) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Вход по отпечатку / Face Unlock",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = bio, onCheckedChange = { bio = it; vault.biometric = it })
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PinGate(vault: SecretVault, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var hasPin by remember { mutableStateOf(vault.hasPin) }
    var input by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockMs by remember { mutableLongStateOf(vault.lockRemainingMs()) }
    val canBio = remember { biometricAvailable(ctx) }

    fun askBiometric() {
        ctx.findActivity()?.let { showBiometric(it, onUnlocked) }
    }

    LaunchedEffect(Unit) {
        if (hasPin && canBio && vault.biometric) askBiometric()
    }
    LaunchedEffect(lockMs > 0) {
        while (lockMs > 0) {
            delay(1000)
            lockMs = vault.lockRemainingMs()
        }
    }

    fun submit() {
        if (input.length < 4) {
            error = "Минимум 4 цифры"
            return
        }
        if (!hasPin) {
            val f = first
            if (f == null) {
                first = input
                input = ""
                error = null
            } else if (f == input) {
                vault.setPin(input)
                hasPin = true
                onUnlocked()
            } else {
                first = null
                input = ""
                error = "PIN не совпал, начните заново"
            }
        } else {
            if (vault.verify(input)) {
                onUnlocked()
            } else {
                input = ""
                lockMs = vault.lockRemainingMs()
                error = if (lockMs > 0) null else "Неверный PIN"
            }
        }
    }

    val title = when {
        hasPin -> "Введите PIN"
        first == null -> "Придумайте PIN (4–8 цифр)"
        else -> "Повторите PIN"
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = cs.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = cs.onBackground, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(16.dp)) {
            repeat(input.length) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(cs.primary))
            }
        }
        Spacer(Modifier.height(10.dp))
        val msg = if (lockMs > 0) "Слишком много попыток. Подождите ${(lockMs + 999) / 1000} с" else error
        Text(
            msg ?: " ",
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center,
            modifier = Modifier.height(22.dp),
        )
        Spacer(Modifier.height(12.dp))

        val enabled = lockMs <= 0
        fun digit(d: String) {
            if (enabled && input.length < 8) {
                input += d
                error = null
            }
        }
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                for (d in row) KeyButton({ Text(d, color = cs.onSurface, fontSize = 24.sp) }) { digit(d) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
            if (hasPin && canBio && vault.biometric) {
                KeyButton({ Icon(Icons.Filled.Fingerprint, "Отпечаток", tint = cs.primary) }) { askBiometric() }
            } else {
                KeyButton({ Icon(Icons.Filled.Backspace, "Стереть", tint = cs.onSurface) }) {
                    input = input.dropLast(1)
                }
            }
            KeyButton({ Text("0", color = cs.onSurface, fontSize = 24.sp) }) { digit("0") }
            KeyButton({ Icon(Icons.Filled.Check, "Готово", tint = cs.primary) }) { if (enabled) submit() }
        }
        if (hasPin && canBio && vault.biometric) {
            Text(
                "Стереть",
                color = cs.onSurfaceVariant,
                modifier = Modifier.clickable { input = input.dropLast(1) }.padding(12.dp),
            )
        }
    }
}

@Composable
private fun KeyButton(content: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
