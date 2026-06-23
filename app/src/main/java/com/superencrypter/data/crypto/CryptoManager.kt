package com.superencrypter.data.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptionResult(
    val cipherBytes: ByteArray,
    val ivBase64: String
)

class CryptoManager {
    private val random = SecureRandom()

    fun randomSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun randomIv(): ByteArray = ByteArray(12).also(random::nextBytes)

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

    fun fingerprintFromHash(hashHex: String): String =
        hashHex.take(8).uppercase().chunked(4).joinToString("-")

    fun deriveKey(fileKeyBytes: ByteArray, salt: ByteArray): SecretKey {
        val password = sha256(fileKeyBytes).toBase64().toCharArray()
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val keyBytes = SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainBytes: ByteArray, key: SecretKey): EncryptionResult {
        val iv = randomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return EncryptionResult(cipher.doFinal(plainBytes), iv.toBase64())
    }

    fun decrypt(cipherBytes: ByteArray, key: SecretKey, ivBase64: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ivBase64.fromBase64()))
        return cipher.doFinal(cipherBytes)
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun ByteArray.toBase64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

fun String.fromBase64(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)
