package com.kvieta.companion.security

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinVerifierTest {

    @Test
    fun testValidFormat() {
        assertTrue(PinVerifier.isValidFormat("1234"))
        assertTrue(PinVerifier.isValidFormat("123456"))
        assertTrue(PinVerifier.isValidFormat("12345678"))
        assertFalse(PinVerifier.isValidFormat("123")) // too short
        assertFalse(PinVerifier.isValidFormat("123456789")) // too long
        assertFalse(PinVerifier.isValidFormat("12a4")) // non-digit
        assertFalse(PinVerifier.isValidFormat(""))
    }

    @Test
    fun testPinVerificationSuccess() {
        val pin = "5824"
        val salt = ByteArray(16) { it.toByte() }
        val iterations = 100_000
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().encodeToString(hash)

        assertTrue(PinVerifier.verify("5824", saltBase64, hashBase64, iterations))
        assertFalse(PinVerifier.verify("1234", saltBase64, hashBase64, iterations))
        assertFalse(PinVerifier.verify("5825", saltBase64, hashBase64, iterations))
    }

    @Test
    fun testPinVerificationFailClosed() {
        assertFalse(PinVerifier.verify("1234", null, "hash", 210_000))
        assertFalse(PinVerifier.verify("1234", "salt", null, 210_000))
        assertFalse(PinVerifier.verify("1234", "invalid-base64", "hash", 210_000))
        assertFalse(PinVerifier.verify("1234", "salt", "invalid-base64", 210_000))
        assertFalse(PinVerifier.verify("1234", "salt", "hash", 50_000)) // too few iterations
    }
}
