package com.kvieta.companion.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kvieta.companion.connection.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-rTR-w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PairingRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private class Identity(var peer: DesktopConnection?) : CompanionIdentity {
        var forgotten = 0
        var relay: RelaySettings? = null
        override fun connection() = peer
        override fun save(connection: DesktopConnection) { peer = connection }
        override fun forget() { forgotten++; peer = null }
        override fun remote(): RelaySettings? = relay
        override fun saveRemote(value: RelaySettings?) { relay = value }
        override fun remoteSequence() = 0L
        override fun acceptRemoteSequence(value: Long) {}
        override fun cache(value: DesktopSnapshot?) {}
        override fun cached(): DesktopSnapshot? = null
        override fun pendingDecision(): PendingDecision? = null
        override fun saveDecision(value: PendingDecision?) {}
        override fun ensure() {}
        override val deviceId = "test"
        override val publicKey = ""
        override fun sign(content: String) = error("No network expected in recovery UI test")
    }
    private fun invite() = "kvieta-companion://pair?v=1&origin=https%3A%2F%2F192.168.1.50%3A24882" +
        "&pin=${"AB".repeat(32)}&token=${"CD".repeat(32)}&expires=${System.currentTimeMillis() / 1000 + 120}"

    @Test fun savedOfflineConnectionStillOffersQrAndCancelPreservesIt() {
        val peer = DesktopConnection("https://192.168.1.50:24882", "AB".repeat(32))
        val identity = Identity(peer)
        val model = ConnectionModel(ApplicationProvider.getApplicationContext(), identity)
        var scans = 0
        lateinit var view: android.view.View
        compose.setContent { KvietaTheme(dark = true) {
            view = LocalView.current
            CompanionApp(model, onScanQr = { scans++ })
        } }
        compose.waitUntil(5000) { model.ready }
        compose.onNodeWithText("QR ile yeniden bağlan").assertIsDisplayed().performClick()
        assertEquals(1, scans)
        assertEquals(0, identity.forgotten)
        assertEquals(peer, identity.peer)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/reports/ui/qr-recovery-dark.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            model.connectInvite(invite())
        }
        compose.waitUntil(5000) { model.replacementInvite != null }
        compose.onNodeWithText("Bu QR ile bağlanılsın mı?").assertExists()
        compose.onNodeWithText("Vazgeç").performClick()
        compose.runOnIdle {
            assertNull(model.replacementInvite)
            assertTrue(model.saved)
            assertEquals(peer, identity.peer)
            assertEquals(0, identity.forgotten)
        }
    }

    @Test fun firstScreenLaunchesQrDirectly() {
        val model = ConnectionModel(ApplicationProvider.getApplicationContext(), Identity(null))
        var scans = 0
        compose.setContent { KvietaTheme { CompanionApp(model, onScanQr = { scans++ }) } }
        compose.waitUntil(5000) { model.ready }
        compose.onNodeWithText("Bilgisayar QR kodunu okut").performScrollTo().performClick()
        assertEquals(1, scans)
        // Production ViewModelProvider still has its required single-Application constructor.
        assertNotNull(ConnectionModel::class.java.getConstructor(Application::class.java))
    }

    @Test fun invalidInviteDoesNotReplaceSavedConnection() {
        val peer = DesktopConnection("https://192.168.1.50:24882", "AB".repeat(32))
        val identity = Identity(peer)
        val model = ConnectionModel(ApplicationProvider.getApplicationContext(), identity)
        compose.setContent { KvietaTheme { CompanionApp(model, onScanQr = {}) } }
        compose.waitUntil(5000) { model.ready }
        compose.runOnIdle { model.connectInvite("https://unrelated.example/") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(model.replacementInvite)
            assertEquals(peer, identity.peer)
            assertEquals(0, identity.forgotten)
        }
    }

}
