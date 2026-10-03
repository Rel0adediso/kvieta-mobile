package com.kvieta.companion.connection

import org.json.JSONArray
import org.json.JSONObject

// Only display data is cached. Transport credentials remain in their own Keystore-wrapped record.
internal fun DesktopSnapshot.cacheJson(): JSONObject = JSONObject()
    .put("deviceName", deviceName).put("mode", mode).put("localDay", localDay).put("observedAtUtc", observedAt)
    .put("stale", true).put("usedSeconds", usedSeconds).put("remainingSeconds", remainingSeconds ?: JSONObject.NULL)
    .put("sessionState", sessionState).put("sessionUsedSeconds", sessionUsedSeconds ?: JSONObject.NULL)
    .put("focusRemainingSeconds", focusRemainingSeconds ?: JSONObject.NULL)
    .put("isRemotelyLocked", isRemotelyLocked)
    .put("canLockRemotely", canLockRemotely)
    .put("canPauseRemotely", canPauseRemotely)
    .put("applications", JSONArray(apps.map {
        JSONObject().put("name", it.name).put("seconds", it.seconds)
            .put("limitMinutes", it.limitMinutes ?: JSONObject.NULL)
            .put("mode", it.mode)
    }))
    .put("appRules", JSONArray(appRules.map {
        JSONObject().put("name", it.name).put("mode", it.mode).put("dailyLimitMinutes", it.dailyLimitMinutes)
    }))
    .put("hourlyUsage", JSONArray(hourlyUsage.map {
        JSONObject().put("hour", it.hour).put("seconds", it.seconds)
    }))
    .put("schedule", JSONArray(schedule.map {
        JSONObject().put("day", it.day).put("isEnabled", it.enabled)
            .put("allowedFrom", it.from).put("allowedUntil", it.until)
            .put("dailyLimitMinutes", it.dailyLimitMinutes)
    }))
    .put("weeklyUsage", JSONArray(weeklyUsage.map { JSONObject().put("day", it.day).put("seconds", it.seconds) }))
    .put("timeRequest", timeRequest?.let {
        JSONObject().put("id", it.id).put("requestedMinutes", it.requestedMinutes).put("note", it.note)
            .put("createdAtUtc", it.createdAt).put("expiresAtUtc", it.expiresAt).put("status", it.status)
            .put("grantedMinutes", it.grantedMinutes ?: JSONObject.NULL).put("appliedAtUtc", it.appliedAt ?: JSONObject.NULL)
    } ?: JSONObject.NULL)
