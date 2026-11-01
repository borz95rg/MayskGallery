package com.maysk.gallery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.widget.RemoteViews
import com.maysk.gallery.MainActivity
import com.maysk.gallery.R

class GalleryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GalleryWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_gallery)
        val launch = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0),
                    )
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) views.setImageViewBitmap(R.id.widget_photo, bitmap)
                        else views.setTextViewText(R.id.widget_caption, "Откройте галерею")
                    }
                } else {
                    views.setTextViewText(R.id.widget_caption, "Ваши воспоминания")
                }
            }
        } catch (_: SecurityException) {
            views.setTextViewText(R.id.widget_caption, "Откройте галерею")
        } catch (_: Exception) {
            views.setTextViewText(R.id.widget_caption, "Ваши воспоминания")
        }
        manager.updateAppWidget(id, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.maysk.gallery.widget.REFRESH"
    }
}
