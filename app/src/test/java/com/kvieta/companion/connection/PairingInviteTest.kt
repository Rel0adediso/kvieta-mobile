package com.kvieta.companion.connection

import org.junit.Assert.*
import org.junit.Test

class PairingInviteTest {
    private val now = 1700000000L
    private fun invite(origin: String = "https%3A%2F%2F192.168.1.50%3A24882", expires: Long = now + 120) =
        "kvieta-companion://pair?v=1&origin=$origin&pin=${"AB".repeat(32)}&token=${"CD".repeat(32)}&expires=$expires"

    @Test fun acceptsPrivateTlsInvite() {
        assertEquals("https://192.168.1.50:24882", PairingInvite.parse(invite(), now).connection.origin)
    }
    @Test fun acceptsRelayInviteWithoutLocalIp() {
        val origin = java.net.URLEncoder.encode(RelaySettings.ORIGIN, "UTF-8")
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32))
        val value = "kvieta-companion://pair?v=2&origin=$origin&room=${"ab".repeat(32)}&readToken=${"cd".repeat(32)}&key=${java.net.URLEncoder.encode(key, "UTF-8")}&decisionToken=${"ef".repeat(32)}&expires=${now + 600}"
        val invite = PairingInvite.parse(value, now)
        assertNotNull(invite.relay)
        assertEquals(RelaySettings.ORIGIN, invite.relay!!.origin)
        assertEquals("", invite.token)
    }
    @Test fun rejectsExpiredAndUnboundedLifetime() {
        listOf(now, now - 1, now + 301).forEach { expires ->
            assertThrows(IllegalArgumentException::class.java) { PairingInvite.parse(invite(expires = expires), now) }
        }
    }
    @Test fun rejectsAmbiguousOrUnexpectedFields() {
        listOf(invite() + "&token=other", invite() + "&unexpected=1", invite() + "#fragment",
            invite().replace("v=1", "v=2"), invite().replace("pin=${"AB".repeat(32)}", "pin=x"))
            .forEach { text -> assertThrows(IllegalArgumentException::class.java) { PairingInvite.parse(text, now) } }
    }
    @Test fun rejectsPublicDnsCleartextAndNonCompanionPorts() {
        listOf("http://192.168.1.50:24882", "https://127.0.0.1:24882", "https://8.8.8.8:24882",
            "https://example.com:24882", "https://192.168.1.50:443", "https://192.168.1.50:24882/path",
            "https://user@192.168.1.50:24882", "https://192.168.01.50:24882")
            .forEach { origin -> assertThrows(IllegalArgumentException::class.java) { DesktopConnection(origin, "AB".repeat(32)) } }
    }
}
