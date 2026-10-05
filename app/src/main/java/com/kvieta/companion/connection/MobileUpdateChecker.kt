package com.kvieta.companion.connection

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class MobileUpdateInfo(
    val isAvailable: Boolean,
    val latestVersionName: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?
)

object MobileUpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/Rel0adediso/kvieta-mobile/releases/latest"

    fun checkForUpdate(currentVersionName: String): MobileUpdateInfo? {
        return try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Kvieta-Mobile/$currentVersionName")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            if (tagName.isBlank()) return null
            val releaseUrl = json.optString("html_url", "https://github.com/Rel0adediso/kvieta-mobile/releases")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            val isNewer = isRemoteNewer(currentVersionName, tagName)
            MobileUpdateInfo(
                isAvailable = isNewer,
                latestVersionName = tagName,
                releaseUrl = releaseUrl,
                apkDownloadUrl = apkUrl ?: releaseUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isRemoteNewer(local: String, remote: String): Boolean {
        if (local.equals(remote, ignoreCase = true)) return false
        val cleanLocal = local.replace(Regex("^(?:kvieta-)?(?:alpha-?|v)?", RegexOption.IGNORE_CASE), "")
        val cleanRemote = remote.replace(Regex("^(?:kvieta-)?(?:alpha-?|v)?", RegexOption.IGNORE_CASE), "")
        if (cleanLocal.equals(cleanRemote, ignoreCase = true)) return false
        val localNum = extractNumbers(cleanLocal)
        val remoteNum = extractNumbers(cleanRemote)
        for (i in 0 until maxOf(localNum.size, remoteNum.size)) {
            val l = localNum.getOrElse(i) { 0 }
            val r = remoteNum.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun extractNumbers(s: String): List<Int> {
        val matches = Regex("\\d+").findAll(s)
        return matches.map { it.value.toIntOrNull() ?: 0 }.toList()
    }
}
