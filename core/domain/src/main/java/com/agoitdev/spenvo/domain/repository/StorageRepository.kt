package com.agoitdev.spenvo.domain.repository

interface StorageRepository {
    suspend fun subirAvatar(uid: String, bytes: ByteArray, contentType: String): String
}
