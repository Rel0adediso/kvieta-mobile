package com.kvieta.companion.security

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinVerifier {

    fun isValidFormat(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    fun verify(pin: String, saltBase64: String?, hashBase64: String?, iterations: Int): Boolean {
        if (!isValidFormat(pin) || saltBase64.isNullOrBlank() || hashBase64.isNullOrBlank() || iterations < 100_000) {
            return false
        }
        return try {
            val salt = Base64.getDecoder().decode(saltBase64)
            val expected = Base64.getDecoder().decode(hashBase64)
            val keyLengthBits = expected.size * 8
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLengthBits)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val actual = factory.generateSecret(spec).encoded
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }
}
