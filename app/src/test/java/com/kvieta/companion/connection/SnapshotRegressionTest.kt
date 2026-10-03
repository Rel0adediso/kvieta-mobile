package com.kvieta.companion.connection

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SnapshotRegressionTest {
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private fun json() = JSONObject().put("deviceName", "Computer").put("mode", "Family")
        .put("localDay", "2026-09-22").put("observedAtUtc", now.toString()).put("stale", false)
        .put("usedSeconds", 120).put("sessionUsedSeconds", 900).put("remainingSeconds", 1800)
        .put("applications", JSONArray()).put("remoteDecisionToken", JSONObject.NULL)
        .put("timeRequest", JSONObject().put("id", "request").put("requestedMinutes", 30)
            .put("note", JSONObject.NULL).put("createdAtUtc", now.toString())
            .put("expiresAtUtc", now.plusSeconds(1800).toString()).put("status", "Pending"))

    @Test fun nullableFieldsAndDifferentUsageCounters() {
        val value = DesktopClient.parseSnapshot(json())
        assertNull(value.remoteDecisionToken)
        assertEquals("", value.timeRequest!!.note)
        assertEquals(120L, value.usedSeconds)
        assertEquals(900L, value.sessionUsedSeconds)
        assertNull(value.timeRequest.grantedMinutes)
    }
    @Test fun encryptedDisplayCacheDoesNotContainTransportToken() {
        val value = DesktopClient.parseSnapshot(json()).copy(remoteDecisionToken = "secret-token")
        val cache = value.cacheJson()
        assertFalse(cache.toString().contains("secret-token"))
        val restored = DesktopClient.parseSnapshot(cache)
        assertTrue(restored.isStale(now))
        assertEquals(value.usedSeconds, restored.usedSeconds)
        assertEquals(value.sessionUsedSeconds, restored.sessionUsedSeconds)
        assertEquals(value.timeRequest, restored.timeRequest)
        assertEquals(value.isRemotelyLocked, restored.isRemotelyLocked)
        assertEquals(value.hourlyUsage, restored.hourlyUsage)
        assertEquals(value.appRules, restored.appRules)
    }
    @Test fun expiryAndFreshnessFailClosed() {
        val value = DesktopClient.parseSnapshot(json())
        assertTrue(value.timeRequest!!.isPending(now))
        assertFalse(value.timeRequest.isPending(now.plusSeconds(1800)))
        assertFalse(value.timeRequest.copy(expiresAt = "invalid").isPending(now))
        assertFalse(value.timeRequest.copy(status = "Applied").isPending(now))
        assertFalse(value.isStale(now))
        assertTrue(value.isStale(now.plusSeconds(181)))
        assertTrue(value.copy(observedAt = "bad").isStale(now))
        assertTrue(value.copy(observedAt = now.plusSeconds(120).toString()).isStale(now))
    }
    @Test fun retryPreservesIdenticalEncryptedEnvelope() {
        val value = PendingDecision("decision", "request", false, null, 42, "ciphertext", "room", false)
        assertEquals(value, PendingDecision.parse(value.json()))
        assertEquals(value.box, value.copy(sent = true).box)
        assertEquals(value.sequence, value.copy(sent = true).sequence)
    }
}
