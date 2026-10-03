package com.kvieta.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.kvieta.companion.connection.PairingInvite
import com.kvieta.companion.ui.KvietaTheme

/** Camera exists only while this activity is visible; no photos are stored. */
class QrScannerActivity : KvietaActivity() {
    private var camera: BarcodeView? = null
    private var allowed by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var cameraFailed by mutableStateOf(false)
    private var invalid by mutableStateOf(false)
    private var torch by mutableStateOf(false)
    private var delivered = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        denied = !it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allowed = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        setContent {
            KvietaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.link_back)) }
                        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.scan_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp)).background(Color(0xFF171A13)), contentAlignment = Alignment.Center) {
                            if (allowed && !cameraFailed) {
                                AndroidView(factory = { context ->
                                    BarcodeView(context).also { view ->
                                        camera = view
                                        view.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                                        view.addStateListener(object : CameraPreview.StateListener {
                                            override fun previewSized() {}
                                            override fun previewStarted() {}
                                            override fun previewStopped() {}
                                            override fun cameraClosed() {}
                                            override fun cameraError(error: Exception) {
                                                view.pause()
                                                cameraFailed = true
                                                torch = false
                                            }
                                        })
                                        view.decodeContinuous(object : BarcodeCallback {
                                            override fun barcodeResult(result: BarcodeResult) {
                                                if (delivered || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                                                val value = result.text ?: return
                                                if (runCatching { PairingInvite.parse(value) }.isFailure) {
                                                    invalid = true
                                                    return
                                                }
                                                delivered = true
                                                view.pause()
                                                setResult(RESULT_OK, Intent().putExtra(INVITATION, value))
                                                finish()
                                            }
                                        })
                                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
                                    }
                                }, modifier = Modifier.fillMaxSize())
                                ScanCorners()
                            } else {
                                Text(stringResource(if (cameraFailed) R.string.scan_camera_error else R.string.scan_permission),
                                    modifier = Modifier.padding(28.dp), color = Color.White)
                            }
                        }
                        if (invalid) Text(stringResource(R.string.scan_invalid), color = MaterialTheme.colorScheme.error)
                        if (!allowed) {
                            Button(onClick = {
                                if (denied && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                                } else permission.launch(Manifest.permission.CAMERA)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (denied) R.string.scan_permission_settings else R.string.scan_allow))
                            }
                        } else if (cameraFailed) {
                            OutlinedButton(onClick = { cameraFailed = false }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.scan_retry))
                            }
                        } else if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                            OutlinedButton(onClick = { torch = !torch; camera?.setTorch(torch) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (torch) R.string.scan_flash_off else R.string.scan_flash_on))
                            }
                        }
                        Text(stringResource(R.string.scan_approval), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.scan_manual))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        allowed = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (allowed && !cameraFailed && !delivered) camera?.resume()
    }

    override fun onPause() {
        camera?.setTorch(false)
        camera?.pause()
        torch = false
        super.onPause()
    }

    override fun onDestroy() {
        camera?.pause()
        camera = null
        super.onDestroy()
    }

    companion object { const val INVITATION = "kvieta.invitation" }
}

@Composable
private fun ScanCorners() {
    Canvas(Modifier.fillMaxSize().padding(36.dp)) {
        val length = 28.dp.toPx()
        val stroke = 4.dp.toPx()
        val accent = Color(0xFFDFE8C5)
        for ((x, y) in listOf(0f to 0f, size.width to 0f, 0f to size.height, size.width to size.height)) {
            drawLine(accent, Offset(x, y), Offset(x + if (x == 0f) length else -length, y), stroke, StrokeCap.Round)
            drawLine(accent, Offset(x, y), Offset(x, y + if (y == 0f) length else -length), stroke, StrokeCap.Round)
        }
    }
}
