package com.agoitdev.spenvo.security

/**
 * Provides the SQLCipher passphrase, generating it on first use and returning the
 * same value on every subsequent call.
 */
interface PassphraseProvider {
    fun getOrCreate(): CharArray
}

