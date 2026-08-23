package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.StorageRepository

/** Uploads a user's avatar image (Slice A1: storage foundation, no UI wiring yet). */
class SubirAvatarUseCase(
    private val storageRepository: StorageRepository,
) {
    suspend operator fun invoke(uid: String, bytes: ByteArray, contentType: String): String =
        storageRepository.subirAvatar(uid, bytes, contentType)
}
