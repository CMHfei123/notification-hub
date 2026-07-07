package com.navigationhub.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    private const val AES_ALGORITHM = "AES"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH = 256

    fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(AES_ALGORITHM)
        generator.init(KEY_LENGTH, SecureRandom())
        return generator.generateKey()
    }

    fun keyToBase64(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun keyFromBase64(base64: String): SecretKey {
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        return SecretKeySpec(decoded, AES_ALGORITHM)
    }

    fun encrypt(plainText: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
