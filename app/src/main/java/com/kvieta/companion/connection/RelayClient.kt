package com.kvieta.companion.connection

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

data class RelaySettings(val origin: String, val room: String, val readToken: String, val key: String, val decisionToken: String = "") {
    init {
        require(origin == ORIGIN)
        require(room.matches(Regex("[a-f0-9]{64}")) && readToken.matches(Regex("[a-f0-9]{64}")))
        require(decisionToken.isEmpty() || decisionToken.matches(Regex("[a-f0-9]{64}")))
        require(Base64.getDecoder().decode(key).size == 32)
    }
    fun json(): JSONObject = JSONObject().put("origin", origin).put("room", room).put("readToken", readToken).put("key", key).put("decisionToken", decisionToken)
    companion object {
        const val ORIGIN = "https://kvieta-companion-relay.ygz-gur39.workers.dev"
        fun parse(data: JSONObject) = RelaySettings(data.getString("origin"), data.getString("room"), data.getString("readToken"), data.getString("key"), data.optionalText("decisionToken") ?: "")
    }
}

object RelayClient {
    fun snapshot(settings: RelaySettings, minimumSequence: Long, acceptSequence: (Long) -> Unit): DesktopSnapshot {
        val connection = URL(settings.origin + "/v1/rooms/" + settings.room).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer " + settings.readToken)
            if (connection.responseCode == 410) throw DesktopRejected()
            if (connection.responseCode != 200) throw IOException("Remote summary unavailable")
            val bytes = connection.inputStream.use { it.readBytesLimited(262144) }
            return decrypt(settings, JSONObject(bytes.toString(Charsets.UTF_8)), minimumSequence, acceptSequence)
        } finally { connection.disconnect() }
    }

    fun sendDecision(settings: RelaySettings, requestId: String, approve: Boolean, grantedMinutes: Int?): String {
        val decision = prepareDecision(settings, requestId, approve, grantedMinutes)
        sendPreparedDecision(settings, decision)
        return decision.id
    }

    fun prepareDecision(settings: RelaySettings, requestId: String, approve: Boolean, grantedMinutes: Int?): PendingDecision {
        require(settings.decisionToken.matches(Regex("[a-f0-9]{64}")))
        require(requestId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(!approve || grantedMinutes != null && grantedMinutes in 1..180)
        val sequence = System.currentTimeMillis()
        val decisionId = java.util.UUID.randomUUID().toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", requestId)
            .put("action", if (approve) "approve" else "reject")
            .put("grantedMinutes", if (approve) grantedMinutes else JSONObject.NULL)
            .put("decidedAtUtc", java.time.Instant.now().toString())
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, requestId, approve, grantedMinutes, sequence, box, settings.room)
    }

    fun preparePlanDecision(settings: RelaySettings, schedule: List<PlanDay>): PendingDecision {
        require(schedule.size == 7)
        val decisionId = java.util.UUID.randomUUID().toString()
        val sequence = System.currentTimeMillis()
        val payloadJson = JSONObject().put("deviceId", "android").put("schedule", org.json.JSONArray().apply {
            schedule.forEach { day -> put(JSONObject().put("day", day.day).put("isEnabled", day.enabled)
                .put("allowedFrom", day.from).put("allowedUntil", day.until).put("dailyLimitMinutes", day.dailyLimitMinutes)) }
        }).toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", decisionId)
            .put("action", "update-plan").put("grantedMinutes", JSONObject.NULL).put("decidedAtUtc", java.time.Instant.now().toString())
            .put("payloadJson", payloadJson)
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, decisionId, false, null, sequence, box, settings.room, action = "update-plan", payloadJson = payloadJson)
    }

    fun prepareSessionActionDecision(settings: RelaySettings, command: String): PendingDecision {
        require(settings.decisionToken.matches(Regex("[a-f0-9]{64}")))
        require(command in listOf("lock", "pause", "resume"))
        val decisionId = java.util.UUID.randomUUID().toString()
        val sequence = System.currentTimeMillis()
        val payloadJson = JSONObject().put("command", command).toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", decisionId)
            .put("action", "session-action").put("grantedMinutes", JSONObject.NULL).put("decidedAtUtc", java.time.Instant.now().toString())
            .put("payloadJson", payloadJson)
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, decisionId, false, null, sequence, box, settings.room, action = "session-action", payloadJson = payloadJson)
    }

    fun prepareAppRuleDecision(settings: RelaySettings, appName: String, mode: String, dailyLimitMinutes: Int): PendingDecision {
        require(settings.decisionToken.matches(Regex("[a-f0-9]{64}")))
        require(appName.isNotBlank())
        require(mode in listOf("Blocked", "Limited", "Unlimited"))
        val decisionId = java.util.UUID.randomUUID().toString()
        val sequence = System.currentTimeMillis()
        val payloadJson = JSONObject()
            .put("name", appName.trim())
            .put("mode", mode)
            .put("dailyLimitMinutes", dailyLimitMinutes.coerceIn(0, 1440))
            .toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", decisionId)
            .put("action", "update-app-rule").put("grantedMinutes", JSONObject.NULL).put("decidedAtUtc", java.time.Instant.now().toString())
            .put("payloadJson", payloadJson)
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, decisionId, false, null, sequence, box, settings.room, action = "update-app-rule", payloadJson = payloadJson)
    }

    fun prepareDevicePairDecision(settings: RelaySettings, deviceName: String): PendingDecision {
        require(settings.decisionToken.matches(Regex("[a-f0-9]{64}")))
        val decisionId = java.util.UUID.randomUUID().toString()
        val sequence = System.currentTimeMillis()
        val payloadJson = JSONObject().put("deviceName", deviceName.take(64)).toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", decisionId)
            .put("action", "pair-device").put("grantedMinutes", JSONObject.NULL).put("decidedAtUtc", java.time.Instant.now().toString())
            .put("payloadJson", payloadJson)
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, decisionId, false, null, sequence, box, settings.room, action = "pair-device", payloadJson = payloadJson)
    }

    fun prepareWebGuardDecision(
        settings: RelaySettings,
        webGuardEnabled: Boolean? = null,
        safeSearchEnforced: Boolean? = null,
        blockedWebDomains: List<String>? = null,
        action: String? = null,
        domain: String? = null
    ): PendingDecision {
        require(settings.decisionToken.matches(Regex("[a-f0-9]{64}")))
        val decisionId = java.util.UUID.randomUUID().toString()
        val sequence = System.currentTimeMillis()
        val payloadObj = JSONObject()
        webGuardEnabled?.let { payloadObj.put("webGuardEnabled", it) }
        safeSearchEnforced?.let { payloadObj.put("safeSearchEnforced", it) }
        blockedWebDomains?.let { domains ->
            val arr = org.json.JSONArray()
            domains.forEach { arr.put(it) }
            payloadObj.put("blockedWebDomains", arr)
        }
        action?.let { payloadObj.put("action", it) }
        domain?.let { payloadObj.put("domain", it) }
        val payloadJson = payloadObj.toString()
        val payload = JSONObject().put("sequence", sequence).put("decisionId", decisionId).put("requestId", decisionId)
            .put("action", "update-web-guard").put("grantedMinutes", JSONObject.NULL).put("decidedAtUtc", java.time.Instant.now().toString())
            .put("payloadJson", payloadJson)
        val box = encryptDecision(settings, payload.toString().toByteArray(Charsets.UTF_8))
        return PendingDecision(decisionId, decisionId, false, null, sequence, box, settings.room, action = "update-web-guard", payloadJson = payloadJson)
    }

    fun sendPreparedDecision(settings: RelaySettings, decision: PendingDecision) {
        require(decision.room == settings.room)
        val connection = URL(settings.origin + "/v1/rooms/" + settings.room + "/decision").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
            connection.requestMethod = "PUT"; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + settings.decisionToken)
            connection.setRequestProperty("Content-Type", "application/json")
            val bytes = JSONObject().put("sequence", decision.sequence).put("box", decision.box).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode == 410) throw DesktopRejected()
            if (connection.responseCode != 200) throw IOException("Decision could not be delivered: HTTP ${connection.responseCode}")
        } finally { connection.disconnect() }
    }

    fun registerFcmToken(settings: RelaySettings, fcmToken: String) {
        val connection = URL(settings.origin + "/v1/rooms/" + settings.room + "/fcm").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + settings.readToken)
            connection.setRequestProperty("Content-Type", "application/json")
            val bytes = JSONObject().put("token", fcmToken).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            connection.responseCode
        } catch (e: Exception) {
        } finally { connection.disconnect() }
    }

    private fun encryptDecision(settings: RelaySettings, plain: ByteArray): String {
        val keyBytes = Base64.getDecoder().decode(settings.key)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            cipher.updateAAD(("kvieta-relay-decision-v1\n" + settings.room).toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))
        } finally { keyBytes.fill(0); plain.fill(0) }
    }

    fun decrypt(settings: RelaySettings, envelope: JSONObject, minimumSequence: Long, acceptSequence: (Long) -> Unit): DesktopSnapshot {
        val packed = Base64.getDecoder().decode(envelope.getString("box"))
        require(packed.size in 29..65536)
        val keyBytes = Base64.getDecoder().decode(settings.key)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, packed, 0, 12))
            cipher.updateAAD(("kvieta-relay-v1\n" + settings.room).toByteArray(Charsets.UTF_8))
            cipher.doFinal(packed, 12, packed.size - 12)
        } finally { keyBytes.fill(0) }
        try {
            val payload = JSONObject(plain.toString(Charsets.UTF_8))
            val sequence = payload.getLong("sequence")
            require(sequence > 0 && sequence >= minimumSequence && sequence == envelope.getLong("sequence"))
            val published = java.time.Instant.parse(payload.getString("publishedAtUtc"))
            require(!published.isAfter(java.time.Instant.now().plusSeconds(90)))
            val snapshot = DesktopClient.parseSnapshot(payload.getJSONObject("snapshot"))
            acceptSequence(sequence)
            return snapshot.copy(viaRelay = true, stale = snapshot.stale || published.isBefore(java.time.Instant.now().minusSeconds(180)))
        } finally { plain.fill(0) }
    }
}
