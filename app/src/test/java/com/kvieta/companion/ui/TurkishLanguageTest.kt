package com.kvieta.companion.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kvieta.companion.R
import com.kvieta.companion.turkishContext
import com.kvieta.companion.KvietaActivity
import androidx.appcompat.app.AppCompatDelegate
import org.robolectric.Robolectric
import com.kvieta.companion.connection.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class TurkishLanguageTest {
    class TestActivity : KvietaActivity()
    @Test fun englishPhoneStillUsesTurkishResources() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            assertEquals("tr", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        } finally { controller.pause().stop().destroy() }
    }
    @Test
    @Config(sdk = [32], qualifiers = "en-rUS")
    fun olderAndroidUsesTurkishActivityResources() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val context = controller.get()
            assertEquals("Bilgisayar QR kodunu okut", context.getString(R.string.link_scan_qr))
            assertTrue(context.getString(R.string.pair_network_error).startsWith("QR okundu"))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun pairingErrorsDoNotHideTlsFailureAsNetworkFailure() {
        assertEquals(PairingFailure.CERTIFICATE, pairingFailure(SSLHandshakeException("pin")))
        assertEquals(PairingFailure.REJECTED, pairingFailure(DesktopRejected()))
        assertEquals(PairingFailure.NETWORK, pairingFailure(IOException("timeout")))
        assertEquals(PairingFailure.OTHER, pairingFailure(IllegalStateException()))
    }
}
