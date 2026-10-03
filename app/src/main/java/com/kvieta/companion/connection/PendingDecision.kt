package com.kvieta.companion.connection

import org.json.JSONObject

data class PendingDecision(val id: String, val requestId: String, val approve: Boolean, val minutes: Int?,
    val sequence: Long, val box: String, val room: String, val sent: Boolean = false,
    val action: String = if (approve) "approve" else "reject", val payloadJson: String? = null) {
    fun json(): JSONObject = JSONObject().put("id", id).put("requestId", requestId).put("approve", approve)
        .put("minutes", minutes ?: JSONObject.NULL).put("sequence", sequence).put("box", box).put("room", room).put("sent", sent)
        .put("action", action).put("payloadJson", payloadJson ?: JSONObject.NULL)
    companion object {
        fun parse(data: JSONObject) = PendingDecision(data.getString("id"), data.getString("requestId"), data.getBoolean("approve"),
            if (data.isNull("minutes")) null else data.getInt("minutes"), data.getLong("sequence"), data.getString("box"),
            data.getString("room"), data.getBoolean("sent"), data.optString("action", if (data.getBoolean("approve")) "approve" else "reject"),
            if (data.isNull("payloadJson")) null else data.optString("payloadJson"))
    }
}
