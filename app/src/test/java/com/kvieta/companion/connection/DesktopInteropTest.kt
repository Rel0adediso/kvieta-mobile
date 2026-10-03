package com.kvieta.companion.connection

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SSLHandshakeException

/** Opt-in live test against the isolated .NET --dashboard-interop fixture, never a user's pairing. */
class DesktopInteropTest {
    @Test fun kotlinClientReadsDotNetSnapshotOverPinnedTls() {
        val file = System.getenv("KVIETA_TEST_INVITE_FILE")
        assumeTrue("Run with the isolated desktop fixture", !file.isNullOrBlank())
        val invite = PairingInvite.parse(File(requireNotNull(file)).readText(Charsets.UTF_8))
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val identity = object : SigningIdentity {
            override fun ensure() {}
            override val deviceId = UUID.randomUUID().toString()
            override val publicKey = Base64.getEncoder().encodeToString(key.public.encoded)
            override fun sign(content: String) = Signature.getInstance("SHA256withECDSA").run {
                initSign(key.private)
                update(content.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sign())
            }
        }
        val client = DesktopClient(identity, "Android JVM · Türkçe")
        assertThrows(SSLHandshakeException::class.java) {
            client.pair(invite.copy(connection = invite.connection.copy(pin = "00".repeat(32))))
        }
        assertEquals(12, client.pair(invite).length)
        val data = requireNotNull(client.snapshot(invite.connection))
        assertEquals("Interop desktop", data.deviceName)
        assertEquals(300L, data.usedSeconds)
        assertEquals(600L, data.remainingSeconds)
        assertEquals("Code", data.apps.single().name)
        assertThrows(DesktopRejected::class.java) { client.pair(invite) }
    }
}
