package com.kvieta.companion

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kvieta.companion.ui.CompanionApp
import com.kvieta.companion.ui.KvietaTheme
import com.kvieta.companion.connection.ConnectionModel
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

class MainActivity : KvietaActivity() {
    private lateinit var connection: ConnectionModel
    private val notificationsEnabled = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        connection = ViewModelProvider(this)[ConnectionModel::class.java]
        if (savedInstanceState == null) intent?.dataString?.let(connection::connectInvite)
        setContent { KvietaTheme { CompanionApp(connection, onScanQr = ::launchQrScanner, onEnableNotifications = ::enableNotifications,
            notificationsEnabled = notificationsEnabled.value) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(connection::connectInvite)
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.getStringExtra(QrScannerActivity.INVITATION)?.let(connection::connectInvite)
    }

    private fun launchQrScanner() {
        scanLauncher.launch(Intent(this, QrScannerActivity::class.java))
    }

    override fun onStart() { super.onStart(); if (::connection.isInitialized) connection.setForeground(true) }
    override fun onStop() { if (::connection.isInitialized) connection.setForeground(false); super.onStop() }
    override fun onResume() { super.onResume(); notificationsEnabled.value = NotificationManagerCompat.from(this).areNotificationsEnabled() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled.value = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
    private fun enableNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationsEnabled.value &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
    }
}
