package com.kvieta.companion.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

interface SigningIdentity {
    fun ensure()
    val deviceId: String
    val publicKey: String
    fun sign(content: String): String
}

interface CompanionIdentity : SigningIdentity {
    fun connection(): DesktopConnection?
    fun save(connection: DesktopConnection)
    fun forget()
    fun remote(): RelaySettings?
    fun saveRemote(value: RelaySettings?)
    fun remoteSequence(): Long
    fun acceptRemoteSequence(value: Long)
    fun cache(value: DesktopSnapshot?)
    fun cached(): DesktopSnapshot?
    fun pendingDecision(): PendingDecision?
    fun saveDecision(value: PendingDecision?)
}

class DeviceIdentity(private val context: Context) : CompanionIdentity {
    companion object { private val sequenceLock = Any() }
    private val preferences = context.getSharedPreferences("desktop-connection", Context.MODE_PRIVATE)
    private val alias = "kvieta-dashboard-device-v1"
    private val relayAlias = "kvieta-dashboard-relay-wrap-v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun ensure() {
        if (!store().containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generator.generateKeyPair()
            check(preferences.edit().clear().putString("deviceId", UUID.randomUUID().toString()).commit())
        } else if (preferences.getString("deviceId", null) == null) {
            check(preferences.edit().putString("deviceId", UUID.randomUUID().toString()).commit())
        }
    }
    override val deviceId: String get() = requireNotNull(preferences.getString("deviceId", null))
    override val publicKey: String get() = Base64.getEncoder().encodeToString(store().getCertificate(alias).publicKey.encoded)
    override fun sign(content: String): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(store().getKey(alias, null) as PrivateKey)
        update(content.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }
    override fun connection(): DesktopConnection? {
        if (!store().containsAlias(alias)) return null
        val origin = preferences.getString("origin", null) ?: return null
        val pin = preferences.getString("pin", null) ?: return null
        return runCatching { DesktopConnection(origin, pin) }.getOrNull()
    }
    override fun save(connection: DesktopConnection) {
        check(preferences.edit().putString("origin", connection.origin).putString("pin", connection.pin).commit())
    }
    override fun forget() {
        RequestPolling.cancel(context)
        store().deleteEntry(alias)
        store().deleteEntry(relayAlias)
        check(preferences.edit().clear().commit())
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        context.getSharedPreferences("request-notifications", Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun remote(): RelaySettings? {
        val packed = preferences.getString("remote", null)?.let { Base64.getDecoder().decode(it) } ?: return null
        val key = store().getKey(relayAlias, null) ?: return null
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, packed, 0, 12))
        val plain = cipher.doFinal(packed, 12, packed.size - 12)
        try { return RelaySettings.parse(org.json.JSONObject(plain.toString(Charsets.UTF_8))) }
        finally { plain.fill(0) }
    }

    override fun saveRemote(value: RelaySettings?) {
        if (value == null) {
            check(preferences.edit().remove("remote").remove("remoteSequence").commit())
            RequestPolling.cancel(context)
            return
        }
        val previous = remote()
        if (previous == value) { RequestPolling.schedule(context); return }
        val changedRoom = previous?.room != value.room
        if (!store().containsAlias(relayAlias)) {
            val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(relayAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, store().getKey(relayAlias, null))
        val plain = value.json().toString().toByteArray(Charsets.UTF_8)
        try {
            val packed = cipher.iv + cipher.doFinal(plain)
            val edit = preferences.edit().putString("remote", Base64.getEncoder().encodeToString(packed))
            if (changedRoom) edit.remove("remoteSequence").remove("pendingDecision")
            check(edit.commit())
            RequestPolling.schedule(context)
        } finally { plain.fill(0) }
    }

    override fun remoteSequence(): Long = preferences.getLong("remoteSequence", 0)
    override fun acceptRemoteSequence(value: Long) = synchronized(sequenceLock) {
        require(value >= remoteSequence()) { "Older remote snapshot" }
        check(preferences.edit().putLong("remoteSequence", value).commit())
    }

    private fun writePrivate(name: String, value: org.json.JSONObject?) {
        if (value == null) { check(preferences.edit().remove(name).commit()); return }
        if (!store().containsAlias(relayAlias)) {
            val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(relayAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, store().getKey(relayAlias, null))
        val plain = value.toString().toByteArray(Charsets.UTF_8)
        try { check(preferences.edit().putString(name, Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))).commit()) }
        finally { plain.fill(0) }
    }

    private fun readPrivate(name: String): org.json.JSONObject? {
        val packed = preferences.getString(name, null)?.let { Base64.getDecoder().decode(it) } ?: return null
        val key = store().getKey(relayAlias, null) ?: return null
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, packed, 0, 12))
        val plain = cipher.doFinal(packed, 12, packed.size - 12)
        try { return org.json.JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }
    override fun cache(value: DesktopSnapshot?) = writePrivate("snapshot", value?.cacheJson())
    override fun cached(): DesktopSnapshot? = readPrivate("snapshot")?.let(DesktopClient::parseSnapshot)
    override fun pendingDecision(): PendingDecision? = readPrivate("pendingDecision")?.let(PendingDecision::parse)
    override fun saveDecision(value: PendingDecision?) = writePrivate("pendingDecision", value?.json())
}
