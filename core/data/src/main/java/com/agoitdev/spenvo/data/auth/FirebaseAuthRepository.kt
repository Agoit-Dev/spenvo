package com.agoitdev.spenvo.data.auth

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override fun observeSesion(): Flow<Sesion> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser.toSesion())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser.toSesion())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun iniciarSesionAnonima() {
        if (auth.currentUser != null) return
        suspendCancellableCoroutine { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}

private fun FirebaseUser?.toSesion(): Sesion = this?.let {
    Sesion(uid = it.uid, esAnonima = it.isAnonymous)
} ?: Sesion.Anonima

