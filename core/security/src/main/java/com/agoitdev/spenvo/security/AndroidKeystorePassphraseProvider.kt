package com.agoitdev.spenvo.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random 256-bit passphrase on first use and persists it encrypted with
 * an AES-256 key held in the Android Keystore. Later calls decrypt and return the
 * same passphrase.
 */
class AndroidKeystorePassphraseProvider(context: Context) : PassphraseProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getOrCreate(): CharArray {
        val stored = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        return stored?.let { decrypt(it) } ?: createAndStore()
    }

    private fun createAndStore(): CharArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(passphrase, Base64.NO_WRAP).toCharArray()
        prefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, encrypt(encoded)).apply()
        return encoded
    }

    private fun encrypt(passphrase: CharArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val cipherText = cipher.doFinal(String(passphrase).toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): CharArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_BYTES)
        val cipherText = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(cipherText)
        return String(plain, Charsets.UTF_8).toCharArray()
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "spenvo_sqlcipher_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "spenvo_security"
        const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        const val PASSPHRASE_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

