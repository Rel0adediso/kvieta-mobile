package com.kvieta.companion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kvieta.companion.MainActivity
import com.kvieta.companion.R
import com.kvieta.companion.connection.DecisionActionReceiver
import com.kvieta.companion.connection.DeviceIdentity

class KvietaAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, KvietaAppWidgetProvider::class.java))
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(context, KvietaAppWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_kvieta)

            // Tapping the widget opens MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            val identity = try { DeviceIdentity(context) } catch (_: Exception) { null }
            val snapshot = identity?.cached()

            if (snapshot == null) {
                views.setTextViewText(R.id.widget_pc_name, "Kvieta")
                views.setTextViewText(R.id.widget_status_badge, "Bağlantı Yok")
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.badge_bg_locked)
                views.setTextViewText(R.id.widget_time_text, "Eşleştirme Yok")
                views.setTextViewText(R.id.widget_subtext, "Açmak için dokunun")
                views.setViewVisibility(R.id.widget_action_lock, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_pc_name, snapshot.deviceName)
                val isLocked = snapshot.isRemotelyLocked || (snapshot.remainingSeconds != null && snapshot.remainingSeconds == 0L)
                if (isLocked) {
                    views.setTextViewText(R.id.widget_status_badge, "Kilitli")
                    views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.badge_bg_locked)
                } else {
                    views.setTextViewText(R.id.widget_status_badge, "Aktif")
                    views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.badge_bg_active)
                }

                val timeStr = if (snapshot.remainingSeconds != null) {
                    val totalMinutes = snapshot.remainingSeconds / 60
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60
                    if (h > 0) "Kalan: ${h} sa ${m} dk" else "Kalan: ${m} dk"
                } else {
                    val h = snapshot.usedSeconds / 3600
                    val m = (snapshot.usedSeconds % 3600) / 60
                    "Bugün: ${h} sa ${m} dk"
                }
                views.setTextViewText(R.id.widget_time_text, timeStr)
                views.setTextViewText(R.id.widget_subtext, if (snapshot.stale) "Çevrimdışı (Eski veri)" else "Canlı Eşitlendi")

                // Lock button if PC is currently active and can lock
                if (snapshot.canLockRemotely && !snapshot.isRemotelyLocked) {
                    views.setViewVisibility(R.id.widget_action_lock, View.VISIBLE)
                    val lockIntent = Intent(context, DecisionActionReceiver::class.java).apply {
                        action = DecisionActionReceiver.ACTION_SESSION_ACTION
                        putExtra(DecisionActionReceiver.EXTRA_SESSION_COMMAND, "lock")
                    }
                    val lockPendingIntent = PendingIntent.getBroadcast(
                        context, 102, lockIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_action_lock, lockPendingIntent)
                } else {
                    views.setViewVisibility(R.id.widget_action_lock, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
