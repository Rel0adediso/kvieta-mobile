package com.kvieta.companion.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.kvieta.companion.MainActivity
import com.kvieta.companion.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

object RequestPolling {
    private const val NAME = "kvieta-time-requests"
    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<RequestPollingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)

    suspend fun fetchAndNotify(context: Context, isStopped: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val identity = DeviceIdentity(context)
        val remote = runCatching { identity.remote() }.getOrNull() ?: return@withContext true
        val snapshot = runCatching { RelayClient.snapshot(remote, identity.remoteSequence(), identity::acceptRemoteSequence) }
            .getOrElse {
                if (it is CancellationException) throw it
                if (it is DesktopRejected) {
                    if (isStopped || identity.remote()?.room != remote.room) return@withContext true
                    identity.saveRemote(null); identity.cache(null); identity.saveDecision(null)
                    context.getSystemService(NotificationManager::class.java).cancelAll()
                    return@withContext true
                }
                return@withContext false
            }
        if (isStopped || identity.remote()?.room != remote.room) return@withContext true
        val request = snapshot.timeRequest
        if (snapshot.mode != "Family" || request == null || !request.isPending()) {
            context.getSystemService(NotificationManager::class.java).cancelAll()
            return@withContext true
        }
        if (identity.pendingDecision()?.requestId == request.id || !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return@withContext true
        val preferences = context.getSharedPreferences("request-notifications", Context.MODE_PRIVATE)
        if (preferences.getString("last", null) == request.id) return@withContext true
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "time-requests"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel,
            context.getString(R.string.request_notification_channel), NotificationManager.IMPORTANCE_HIGH))
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun createActionPendingIntent(approve: Boolean, minutes: Int?): PendingIntent {
            val actionIntent = Intent(context, DecisionActionReceiver::class.java).apply {
                action = DecisionActionReceiver.ACTION_DECISION
                putExtra(DecisionActionReceiver.EXTRA_REQUEST_ID, request.id)
                putExtra(DecisionActionReceiver.EXTRA_APPROVE, approve)
                if (minutes != null) putExtra(DecisionActionReceiver.EXTRA_MINUTES, minutes)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val reqCode = if (approve) (request.id.hashCode() * 31 + (minutes ?: 0)) else (request.id.hashCode() * 31 + 999)
            return PendingIntent.getBroadcast(context, reqCode, actionIntent, flags)
        }

        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_kvieta)
            .setContentTitle(context.getString(R.string.request_notification_title))
            .setContentText(context.getString(R.string.request_notification_body, request.requestedMinutes))
            .setContentIntent(intent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "+15 dk", createActionPendingIntent(true, 15))
            .addAction(0, "+30 dk", createActionPendingIntent(true, 30))
            .addAction(0, context.getString(R.string.request_reject), createActionPendingIntent(false, null))
            .build()
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@withContext true
        manager.notify(request.id.hashCode(), notification)
        preferences.edit().putString("last", request.id).apply()
        true
    }
}

class RequestPollingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val success = RequestPolling.fetchAndNotify(applicationContext, isStopped)
        return if (success) Result.success() else Result.retry()
    }
}
