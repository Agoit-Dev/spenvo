package com.agoitdev.spenvo.data.auth

import android.net.Uri
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
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
    private val sesionPreferences: SesionPreferences,
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

    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        sesionPreferences.limpiarLogout()
    }

    override suspend fun enviarRecuperacionPassword(email: String) {
        suspendCancellableCoroutine { cont ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun vincularEmail(email: String, password: String, nombre: String) {
        val currentUser = auth.currentUser
            ?: throw FirebaseAuthException("NO_CURRENT_USER", "No hay una sesión activa para vincular")
        val credencial = EmailAuthProvider.getCredential(email, password)
        suspendCancellableCoroutine { cont ->
            currentUser.linkWithCredential(credencial)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        if (nombre.isNotBlank()) {
            suspendCancellableCoroutine { cont ->
                val perfil = UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre)
                    .build()
                currentUser.updateProfile(perfil)
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }
    }

    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) {
        val currentUser = auth.currentUser
            ?: throw FirebaseAuthException("NO_CURRENT_USER", "No hay una sesión activa para actualizar")
        if (nombre == null && photoUrl == null) return
        suspendCancellableCoroutine { cont ->
            val builder = UserProfileChangeRequest.Builder()
            if (nombre != null) builder.setDisplayName(nombre)
            if (photoUrl != null) builder.setPhotoUri(Uri.parse(photoUrl))
            currentUser.updateProfile(builder.build())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun cerrarSesion() {
        sesionPreferences.marcarLogout()
        auth.signOut()
    }
}

private fun FirebaseUser?.toSesion(): Sesion = this?.let {
    Sesion(
        uid = it.uid,
        esAnonima = it.isAnonymous,
        email = it.email,
        nombre = it.displayName,
        photoUrl = it.photoUrl?.toString(),
    )
} ?: Sesion.Anonima
