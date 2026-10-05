package com.kvieta.companion.connection

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KvietaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("KvietaFCM", "New FCM registration token received")
        val prefs = applicationContext.getSharedPreferences("kvieta-fcm", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        CoroutineScope(Dispatchers.IO).launch {
            val identity = DeviceIdentity(applicationContext)
            val remote = runCatching { identity.remote() }.getOrNull() ?: return@launch
            RelayClient.registerFcmToken(remote, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i("KvietaFCM", "Instant FCM push received: ${remoteMessage.data}")
        CoroutineScope(Dispatchers.IO).launch {
            RequestPolling.fetchAndNotify(applicationContext)
        }
    }
}
