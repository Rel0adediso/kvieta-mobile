package com.kvieta.companion.connection

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64
import java.time.Instant
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class RelayClientTest {
    private val settings = RelaySettings(RelaySettings.ORIGIN, "a".repeat(64), "b".repeat(64), Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))

    private fun envelope(sequence: Long): JSONObject {
        val payload = JSONObject().put("sequence", sequence).put("publishedAtUtc", Instant.now().toString())
            .put("snapshot", JSONObject().put("deviceName", "test").put("mode", "Personal").put("localDay", "2026-09-21")
                .put("observedAtUtc", Instant.now().toString()).put("stale", false).put("usedSeconds", 123)
                .put("remainingSeconds", 456).put("applications", org.json.JSONArray()))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(settings.key), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(("kvieta-relay-v1\n" + settings.room).toByteArray())
        return JSONObject().put("sequence", sequence).put("box", Base64.getEncoder().encodeToString(iv + cipher.doFinal(payload.toString().toByteArray())))
    }

    @Test fun authenticatedSnapshotAndSameSequenceRefresh() {
        var accepted = 0L
        val snapshot = RelayClient.decrypt(settings, envelope(4), 4) { accepted = it }
        assertEquals(123L, snapshot.usedSeconds)
        assertEquals(4L, accepted)
        assertTrue(snapshot.viaRelay)
    }
    @Test fun rejectsTamperingWrongRoomAndRollback() {
        val valid = envelope(4)
        assertTrue(runCatching { RelayClient.decrypt(settings, valid, 5) {} }.isFailure)
        assertTrue(runCatching { RelayClient.decrypt(settings.copy(room = "c".repeat(64)), valid, 0) {} }.isFailure)
        val packed = Base64.getDecoder().decode(valid.getString("box"))
        packed[packed.lastIndex] = (packed.last().toInt() xor 1).toByte()
        valid.put("box", Base64.getEncoder().encodeToString(packed))
        assertTrue(runCatching { RelayClient.decrypt(settings, valid, 0) {} }.isFailure)
    }
    @Test fun rejectsUntrustedRelayOrigin() {
        assertTrue(runCatching { settings.copy(origin = "https://example.com") }.isFailure)
        assertTrue(runCatching { settings.copy(key = "invalid") }.isFailure)
    }
    @Test fun preparesSessionActionAndAppRuleEnvelopes() {
        val relay = settings.copy(decisionToken = "d".repeat(64))
        val lockDecision = RelayClient.prepareSessionActionDecision(relay, "lock")
        assertEquals("session-action", lockDecision.action)
        assertTrue(lockDecision.payloadJson!!.contains("\"command\":\"lock\""))
        assertTrue(lockDecision.box.isNotBlank())

        val ruleDecision = RelayClient.prepareAppRuleDecision(relay, "Discord", "Limited", 45)
        assertEquals("update-app-rule", ruleDecision.action)
        assertTrue(ruleDecision.payloadJson!!.contains("\"name\":\"Discord\""))
        assertTrue(ruleDecision.payloadJson!!.contains("\"mode\":\"Limited\""))
        assertTrue(ruleDecision.payloadJson!!.contains("\"dailyLimitMinutes\":45"))
    }

    @Test fun bindsThePinToTheCommandForThePairedGuardian() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val relay = settings.copy(decisionToken = "d".repeat(64),
            guardianKey = Base64.getEncoder().encodeToString(keys.public.encoded))
        val decision = RelayClient.prepareAppRuleDecision(relay, "Örnek", "Limited", 45, "482619")
        val box = Base64.getDecoder().decode(decision.box)
        val transport = Cipher.getInstance("AES/GCM/NoPadding")
        transport.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(relay.key), "AES"),
            GCMParameterSpec(128, box, 0, 12))
        transport.updateAAD(("kvieta-relay-decision-v1\n" + relay.room).toByteArray())
        val payload = JSONObject(transport.doFinal(box, 12, box.size - 12).toString(Charsets.UTF_8))
        val guardian = Cipher.getInstance("RSA/ECB/OAEPPadding")
        guardian.init(Cipher.DECRYPT_MODE, keys.private,
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        val plain = guardian.doFinal(Base64.getDecoder().decode(payload.getString("parentProof")))
        val content = listOf("kvieta-parent-policy-v2", relay.room, decision.sequence.toString(), decision.id,
            decision.id, "update-app-rule", "", Instant.parse(payload.getString("decidedAtUtc")).toEpochMilli().toString(),
            Base64.getEncoder().encodeToString(decision.payloadJson!!.toByteArray())).joinToString("\n")
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(content.toByteArray()), plain.copyOfRange(0, 32))
        assertEquals("482619", plain.copyOfRange(32, plain.size).toString(Charsets.US_ASCII))
    }
    @Test fun decryptsRealDotNetFixtureAndFetchesCloudflare() {
        val path = System.getenv("KVIETA_RELAY_FIXTURE")
        assumeTrue(!path.isNullOrBlank())
        val fixture = JSONObject(java.io.File(requireNotNull(path)).readText())
        val peer = RelaySettings.parse(fixture.getJSONObject("credentials").getJSONObject("client"))
        val local = RelayClient.decrypt(peer, fixture.getJSONObject("envelope"), 0) {}
        assertEquals("Relay integration test", local.deviceName)
        val remote = RelayClient.snapshot(peer, 0) {}
        assertEquals(123L, remote.usedSeconds)
        assertEquals(456L, remote.remainingSeconds)
        assertTrue(remote.viaRelay)
        assertEquals(30, remote.timeRequest?.requestedMinutes)
        val prepared = RelayClient.prepareDecision(peer, "11111111-1111-1111-1111-111111111111", true, 30)
        RelayClient.sendPreparedDecision(peer, prepared)
        RelayClient.sendPreparedDecision(peer, prepared)
    }
}
