package com.kvieta.companion.connection

import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class DesktopRejected : IOException()
data class DesktopUsage(val name: String, val seconds: Long, val limitMinutes: Int? = null, val mode: String = "Unlimited", val iconBase64: String? = null)
data class HourlyUsage(val hour: Int, val seconds: Long)
data class AppRuleDto(val name: String, val mode: String, val dailyLimitMinutes: Int)
data class DesktopDay(val day: String, val seconds: Long)
data class PlanDay(val day: String, val enabled: Boolean, val from: String, val until: String, val dailyLimitMinutes: Int)
data class TimeRequest(
    val id: String, val requestedMinutes: Int, val note: String, val createdAt: String,
    val expiresAt: String, val status: String, val grantedMinutes: Int?, val appliedAt: String?,
)
data class DesktopSnapshot(
    val deviceName: String, val mode: String, val localDay: String, val observedAt: String,
    val stale: Boolean, val usedSeconds: Long, val remainingSeconds: Long?, val apps: List<DesktopUsage>,
    val sessionState: String = "Unknown", val viaRelay: Boolean = false, val timeRequest: TimeRequest? = null,
    val remoteDecisionToken: String? = null, val weeklyUsage: List<DesktopDay> = emptyList(),
    val focusRemainingSeconds: Long? = null,
    val sessionUsedSeconds: Long? = null,
    val schedule: List<PlanDay> = emptyList(),
    val isRemotelyLocked: Boolean = false,
    val canLockRemotely: Boolean = false,
    val canPauseRemotely: Boolean = false,
    val hourlyUsage: List<HourlyUsage> = emptyList(),
    val appRules: List<AppRuleDto> = emptyList(),
    val webGuardEnabled: Boolean = false,
    val safeSearchEnforced: Boolean = false,
    val blockedWebDomains: List<String> = emptyList(),
)

fun TimeRequest.isPending(now: java.time.Instant = java.time.Instant.now()): Boolean =
    status == "Pending" && runCatching { java.time.Instant.parse(expiresAt).isAfter(now) }.getOrDefault(false)

fun DesktopSnapshot.isStale(now: java.time.Instant = java.time.Instant.now()): Boolean = stale ||
    runCatching { java.time.Instant.parse(observedAt).let { it.isBefore(now.minusSeconds(180)) || it.isAfter(now.plusSeconds(90)) } }.getOrDefault(true)

internal fun JSONObject.optionalText(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

class DesktopClient(private val identity: SigningIdentity, private val deviceName: String = android.os.Build.MODEL,
    private val onRemote: (RelaySettings?) -> Unit = {}) {
    fun pair(invite: PairingInvite): String {
        require(invite.expires > System.currentTimeMillis() / 1000)
        identity.ensure()
        val key = identity.publicKey
        val name = deviceName.take(80).filterNot(Char::isISOControl).ifBlank { "Android" }
        val content = "kvieta-dashboard-pair-v1\n${invite.token}\n${identity.deviceId}\n$name\n$key"
        val body = JSONObject().put("token", invite.token).put("deviceId", identity.deviceId)
            .put("name", name).put("publicKey", key).put("signature", identity.sign(content))
        val response = post(invite.connection, "/v1/pair", body)
        check(response.getString("state") == "pending")
        val expected = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(key))
            .joinToString("") { "%02X".format(it) }.take(12)
        check(response.getString("code") == expected)
        return expected
    }

    fun snapshot(connection: DesktopConnection): DesktopSnapshot? {
        val time = System.currentTimeMillis() / 1000
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02X".format(it) }
        val content = "kvieta-dashboard-read-v1\n${identity.deviceId}\n$time\n$nonce"
        val body = JSONObject().put("deviceId", identity.deviceId).put("time", time)
            .put("nonce", nonce).put("signature", identity.sign(content))
        val response = post(connection, "/v1/snapshot", body)
        if (response.getString("state") == "pending") return null
        check(response.getString("state") == "paired")
        val result = parseSnapshot(response.getJSONObject("snapshot"))
        onRemote(response.optJSONObject("remote")?.let(RelaySettings::parse))
        return result
    }

    companion object {
    fun parseSnapshot(data: JSONObject): DesktopSnapshot {
        val apps = data.getJSONArray("applications")
        val request = data.optJSONObject("timeRequest")?.let {
            TimeRequest(it.getString("id"), it.getInt("requestedMinutes"), it.optionalText("note") ?: "",
                it.getString("createdAtUtc"), it.getString("expiresAtUtc"), it.getString("status"),
                if (it.isNull("grantedMinutes")) null else it.getInt("grantedMinutes"),
                if (it.isNull("appliedAtUtc")) null else it.getString("appliedAtUtc"))
        }
        val week = data.optJSONArray("weeklyUsage")?.let { values ->
            List(values.length().coerceAtMost(7)) { index -> values.getJSONObject(index).let { DesktopDay(it.getString("day"), it.getLong("seconds")) } }
        } ?: emptyList()
        val schedule = data.optJSONArray("schedule")?.let { values ->
            List(values.length().coerceAtMost(7)) { index -> values.getJSONObject(index).let { day ->
                PlanDay(day.getString("day"), day.optBoolean("isEnabled", true), day.getString("allowedFrom"),
                    day.getString("allowedUntil"), day.getInt("dailyLimitMinutes"))
            } }
        } ?: emptyList()
        val hourly = data.optJSONArray("hourlyUsage")?.let { values ->
            List(values.length()) { index -> values.getJSONObject(index).let { HourlyUsage(it.getInt("hour"), it.getLong("seconds")) } }
        } ?: emptyList()
        val rules = data.optJSONArray("appRules")?.let { values ->
            List(values.length()) { index -> values.getJSONObject(index).let { AppRuleDto(it.getString("name"), it.getString("mode"), it.getInt("dailyLimitMinutes")) } }
        } ?: emptyList()
        return DesktopSnapshot(data.getString("deviceName"), data.getString("mode"), data.getString("localDay"),
            data.getString("observedAtUtc"), data.getBoolean("stale"), data.getLong("usedSeconds"),
            if (data.isNull("remainingSeconds")) null else data.getLong("remainingSeconds"),
            List(apps.length()) { i -> apps.getJSONObject(i).let {
                DesktopUsage(it.getString("name"), it.getLong("seconds"),
                    if (it.isNull("limitMinutes")) null else it.getInt("limitMinutes"),
                    it.optString("mode", "Unlimited"),
                    it.optionalText("iconBase64"))
            } },
            data.optString("sessionState", "Unknown"), timeRequest = request,
            remoteDecisionToken = data.optionalText("remoteDecisionToken"), weeklyUsage = week,
            focusRemainingSeconds = if (data.isNull("focusRemainingSeconds")) null else data.getLong("focusRemainingSeconds"),
            sessionUsedSeconds = if (data.isNull("sessionUsedSeconds")) null else data.getLong("sessionUsedSeconds"),
            schedule = schedule,
            isRemotelyLocked = data.optBoolean("isRemotelyLocked", false),
            canLockRemotely = data.optBoolean("canLockRemotely", false),
            canPauseRemotely = data.optBoolean("canPauseRemotely", false),
            hourlyUsage = hourly,
            appRules = rules,
            webGuardEnabled = data.optBoolean("webGuardEnabled", false),
            safeSearchEnforced = data.optBoolean("safeSearchEnforced", false),
            blockedWebDomains = data.optJSONArray("blockedWebDomains")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            } ?: emptyList())
    }
    }

    private fun post(target: DesktopConnection, path: String, body: JSONObject): JSONObject {
        val expected = target.pin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)))
                    throw CertificateException("Certificate pin mismatch")
                chain[0].checkValidity()
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val connection = URL(target.origin + path).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        try {
            connection.sslSocketFactory = ssl.socketFactory
            // Identity is the exact QR-pinned certificate, not a public CA hostname.
            connection.setHostnameVerifier { hostname, session ->
                hostname == URL(target.origin).host && runCatching {
                    MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(session.peerCertificates[0].encoded))
                }.getOrDefault(false)
            }
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode == 403) throw DesktopRejected()
            if (connection.responseCode != 200) throw IOException("Desktop response rejected")
            val response = connection.inputStream.use { it.readBytesLimited(262144) }
            return JSONObject(response.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = read(buffer)
        if (count == -1) return output.toByteArray()
        if (output.size() + count > limit) throw IOException("Oversized desktop response")
        output.write(buffer, 0, count)
    }
}
