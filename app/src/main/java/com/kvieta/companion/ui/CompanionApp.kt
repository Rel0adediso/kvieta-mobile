package com.kvieta.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kvieta.companion.R
import com.kvieta.companion.connection.ConnectionModel

@Composable
fun CompanionApp(
    connection: ConnectionModel? = null,
    onScanQr: (() -> Unit)? = null,
    onEnableNotifications: (() -> Unit)? = null,
    notificationsEnabled: Boolean = false,
) {
    var connecting by rememberSaveable { mutableStateOf(false) }
    var manualRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(connection?.saved, connection?.input) {
        if (connection?.saved == true) connecting = false
        else if (!connection?.input.isNullOrBlank()) connecting = true
    }
    BackHandler(enabled = connecting && connection?.saved != true) { connecting = false }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 600.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.ic_kvieta), contentDescription = null, modifier = Modifier.size(36.dp))
                Text("Kvieta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            val update = connection?.updateInfo
            if (update?.isAvailable == true) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val targetUrl = update.apkDownloadUrl ?: update.releaseUrl
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_available_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(stringResource(R.string.update_available_body, update.latestVersionName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Text(stringResource(R.string.update_download_action), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            when {
                connection == null -> Welcome(onConnect = {}, onScanQr = onScanQr)
                !connection.ready -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                connection.saved -> ConnectionScreen(connection, onBack = {}, onScanQr = onScanQr,
                    showBack = false, onEnableNotifications = onEnableNotifications, notificationsEnabled = notificationsEnabled)
                connecting -> ConnectionScreen(connection, onBack = { connecting = false }, onScanQr = onScanQr, startWithManualInput = manualRequested)
                else -> Welcome(onConnect = { manualRequested = true; connecting = true }, onScanQr = onScanQr)
            }
        }
    }
}

@Composable
private fun Welcome(onConnect: () -> Unit, onScanQr: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFF283B31), contentColor = Color(0xFFF3F4E8)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF283B31), Color(0xFF52643B)))).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(116.dp)) {
                    drawCircle(Color(0xFFCCD6A3).copy(alpha = .12f))
                    drawCircle(Color(0xFFCCD6A3).copy(alpha = .35f), radius = size.minDimension * .49f, style = Stroke(1.dp.toPx()))
                    drawArc(Color(0xFFDCE5B7), -90f, 250f, false, style = Stroke(5.dp.toPx()))
                }
                Image(painterResource(R.drawable.ic_kvieta), contentDescription = null, modifier = Modifier.size(52.dp))
            }
            Text(stringResource(R.string.welcome_title), fontSize = 36.sp, lineHeight = 40.sp,
                letterSpacing = (-1).sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDCE5C8))
        }
    }
    Panel {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.connect_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onScanQr ?: onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.link_scan_qr)) }
        TextButton(onClick = onConnect) { Text(stringResource(R.string.link_manual)) }
        Text(stringResource(R.string.qr_connection_hint), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Preview(showBackground = true, locale = "tr", widthDp = 380)
@Composable
private fun WelcomePreview() { KvietaTheme { CompanionApp() } }
