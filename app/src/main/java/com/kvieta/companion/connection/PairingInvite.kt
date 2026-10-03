package com.kvieta.companion.connection

import java.net.URI
import java.net.URLDecoder

data class DesktopConnection(val origin: String, val pin: String) {
    init {
        val uri = URI(origin)
        require(uri.scheme == "https" && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrEmpty() && uri.port in 24873..24882)
        val parts = uri.host?.split('.') ?: emptyList()
        require(parts.size == 4 && parts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() in 0..255 })
        val ip = parts.map(String::toInt)
        require(ip[0] == 10 || ip[0] == 192 && ip[1] == 168 || ip[0] == 172 && ip[1] in 16..31)
        require(pin.matches(Regex("[A-Fa-f0-9]{64}")))
    }
}

data class PairingInvite(val connection: DesktopConnection, val token: String, val expires: Long, val relay: RelaySettings? = null) {
    companion object {
        fun parse(value: String, now: Long = System.currentTimeMillis() / 1000): PairingInvite {
            require(value.length <= 2048)
            val uri = URI(value.trim())
            require(uri.scheme == "kvieta-companion" && uri.host == "pair" && uri.port == -1 &&
                uri.userInfo == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty())
            val entries = (uri.rawQuery ?: error("No query")).split('&').map {
                val fields = it.split('=', limit = 2)
                require(fields.size == 2)
                URLDecoder.decode(fields[0], "UTF-8") to URLDecoder.decode(fields[1], "UTF-8")
            }
            val fields = entries.toMap()
            val version = fields.getValue("v")
            if (version == "2") {
                require(entries.size == 7 && fields.keys == setOf("v","origin","room","readToken","key","decisionToken","expires"))
                val expires = fields.getValue("expires").toLong()
                require(expires > now && expires <= now + 900)
                val relay = RelaySettings(fields.getValue("origin"), fields.getValue("room"),
                    fields.getValue("readToken"), fields.getValue("key"), fields.getValue("decisionToken"))
                val dummy = DesktopConnection("https://192.168.0.1:24882", "00".repeat(32))
                return PairingInvite(dummy, "", expires, relay)
            }
            require(version == "1" && entries.size == 5 && fields.keys == setOf("v","origin","pin","token","expires"))
            val token = fields.getValue("token")
            require(token.matches(Regex("[A-Fa-f0-9]{64}")))
            val expires = fields.getValue("expires").toLong()
            require(expires > now && expires <= now + 300)
            return PairingInvite(DesktopConnection(fields.getValue("origin"), fields.getValue("pin")), token, expires)
        }
    }
}
