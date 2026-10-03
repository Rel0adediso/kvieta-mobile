package com.kvieta.companion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kvieta.companion.connection.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-rTR-w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var rootView: android.view.View
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private val request = TimeRequest("id", 30, "Ödevimi bitirmek istiyorum.", now.toString(),
        now.plusSeconds(1800).toString(), "Pending", null, null)
    private fun snapshot(family: Boolean) = DesktopSnapshot("Deniz’in bilgisayarı", if (family) "Family" else "Personal",
        "2026-09-22", now.toString(), false, 10080, 4320,
        listOf(DesktopUsage("Visual Studio Code", 5400), DesktopUsage("Firefox", 3000), DesktopUsage("Spotify", 1680)),
        sessionState = "Active", timeRequest = if (family) request else null, sessionUsedSeconds = 10080,
        weeklyUsage = (16..22).map { DesktopDay("2026-09-$it", (it - 15) * 1500L) })
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.runOnIdle {
            Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888).also {
                rootView.draw(android.graphics.Canvas(it))
            }
        }
        val file = File("build/reports/ui/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun familySelectThenApproveExactlyOnce() {
        var calls = 0
        var minutes: Int? = null
        compose.setContent { KvietaTheme(dark = false) {
            rootView = LocalView.current
            Surface(color = MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                DashboardContent(snapshot(true), true, false, now, true, DecisionStatus.IDLE, {}, {}) { _, approve, value ->
                    assertTrue(approve); calls++; minutes = value
                }
            } }
        } }
        capture("family-light")
        compose.onNodeWithText("60 dk").performScrollTo().performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("60 dk onayla").performScrollTo().performClick()
        assertEquals(1, calls)
        assertEquals(60, minutes)
    }
    @Test fun personalDetailsAndDarkTheme() {
        compose.setContent { KvietaTheme(dark = true) {
            rootView = LocalView.current
            Surface(color = MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                DashboardContent(snapshot(false), true, false, now, false, DecisionStatus.IDLE, {}, {}) { _, _, _ -> }
            } }
        } }
        capture("personal-dark")
        compose.onNodeWithText("Firefox").assertExists()
        compose.onNodeWithText("Spotify").assertExists()
        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.onNodeWithText("Kapat").assertExists()
        compose.onNodeWithText("Son 7 gün").assertExists()
    }
    @Test fun expiredRequestCannotBeApproved() {
        compose.setContent { KvietaTheme {
            Column { RequestCard(request, true, DecisionStatus.IDLE, now.plusSeconds(1801)) { _, _, _ -> fail("Expired approval") } }
        } }
        compose.onNodeWithText("30 dk onayla").assertDoesNotExist()
    }

    @Test fun welcomeHasDirectQrActionAndNoLocalWifiRequirement() {
        var scans = 0
        compose.setContent { KvietaTheme(dark = true) {
            rootView = LocalView.current
            CompanionApp(onScanQr = { scans++ })
        } }
        capture("welcome-dark")
        compose.onNodeWithText("Bilgisayar QR kodunu okut").performScrollTo().performClick()
        assertEquals(1, scans)
        compose.onNodeWithText("Aynı Wi-Fi · bilgisayarda onay gerekir").assertDoesNotExist()
    }
    @Test
    @Config(sdk = [35], qualifiers = "tr-rTR-w320dp-h800dp")
    fun narrowScreenWithLargeTextKeepsActionsReachable() {
        var approved = false
        compose.setContent { KvietaTheme(dark = false) {
            rootView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        RequestCard(request, true, DecisionStatus.IDLE, now) { _, _, _ -> approved = true }
                    }
                }
            }
        } }
        compose.onNodeWithText("30 dk onayla").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(approved)
        capture("family-narrow-large-text")
    }
}
