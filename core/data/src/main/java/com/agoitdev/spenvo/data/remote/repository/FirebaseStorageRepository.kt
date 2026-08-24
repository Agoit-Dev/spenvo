package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.domain.repository.StorageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Uploads to the fixed `avatars/{uid}/avatar.jpg` path (overwrite on
 * re-upload, one avatar per user). Bridges the Task-based Storage
 * SDK to suspend functions manually, mirroring `FirebaseAuthRepository`'s
 * existing pattern (no `kotlinx-coroutines-play-services` dependency exists).
 */
@Singleton
class FirebaseStorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
) : StorageRepository {

    override suspend fun subirAvatar(uid: String, bytes: ByteArray, contentType: String): String {
        val avatarRef = storage.reference.child("avatars/$uid/avatar.jpg")
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        suspendCancellableCoroutine<Unit> { cont ->
            avatarRef.putBytes(bytes, metadata)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return suspendCancellableCoroutine { cont ->
            avatarRef.downloadUrl
                .addOnSuccessListener { cont.resume(it.toString()) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
