package com.kvieta.companion.connection

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kvieta.companion.widget.KvietaAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DecisionActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DECISION = "com.kvieta.companion.ACTION_DECISION"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_APPROVE = "approve"
        const val EXTRA_MINUTES = "minutes"

        const val ACTION_SESSION_ACTION = "com.kvieta.companion.ACTION_SESSION_ACTION"
        const val EXTRA_SESSION_COMMAND = "command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_DECISION) {
            val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
            val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
            val minutes = if (intent.hasExtra(EXTRA_MINUTES)) intent.getIntExtra(EXTRA_MINUTES, 15) else null

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(requestId.hashCode())

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val identity = DeviceIdentity(context)
                    val remote = identity.remote() ?: return@launch
                    val decision = RelayClient.prepareDecision(remote, requestId, approve, minutes)
                    identity.saveDecision(decision)
                    RelayClient.sendPreparedDecision(remote, decision)
                    identity.saveDecision(decision.copy(sent = true))
                    KvietaAppWidgetProvider.updateAll(context)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (action == ACTION_SESSION_ACTION) {
            val command = intent.getStringExtra(EXTRA_SESSION_COMMAND) ?: return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val identity = DeviceIdentity(context)
                    val remote = identity.remote() ?: return@launch
                    val envelope = RelayClient.prepareSessionActionDecision(remote, command)
                    RelayClient.sendPreparedDecision(remote, envelope)
                    val currentSnapshot = identity.cached()
                    if (currentSnapshot != null) {
                        val isLocked = command == "lock"
                        identity.cache(currentSnapshot.copy(isRemotelyLocked = isLocked))
                    }
                    KvietaAppWidgetProvider.updateAll(context)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
