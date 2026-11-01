package com.maysk.gallery.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** Скачивает APK из релиза и открывает системный установщик. */
object ApkInstaller {

    fun start(context: Context, info: UpdateInfo) {
        val app = context.applicationContext

        // Android просит отдельно разрешить установку из этого приложения
        if (!app.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                app,
                "Разрешите установку из Maysk Gallery и нажмите «Обновить» ещё раз",
                Toast.LENGTH_LONG,
            ).show()
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val fileName = "MayskGallery-${info.version}.apk"
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = File(dir, fileName).also { it.delete() }

        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Maysk Gallery ${info.version}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = dm.enqueue(request)

        Toast.makeText(app, "Скачиваю обновление…", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                app.unregisterReceiver(this)
                if (file.exists() && file.length() > 0) install(app, file)
                else Toast.makeText(app, "Не удалось скачать обновление", Toast.LENGTH_LONG).show()
            }
        }
        ContextCompat.registerReceiver(
            app, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
