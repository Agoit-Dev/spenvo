package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.UsuarioDto
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** Signals "the transaction aborted because the target was already taken", never a real I/O failure. */
private class NombreUsuarioYaReservadoException : Exception()

@Singleton
class FirebaseUsuarioRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UsuarioRepository {

    override suspend fun obtener(usuarioId: String): Usuario? {
        val snapshot = firestore.collection(USUARIOS_COLLECTION).document(usuarioId).get().await()
        return snapshot.data?.let { UsuarioDto.fromData(it)?.toDomain() }
    }

    /**
     * One member's lookup failing (network blip, permission edge case) must not take down the
     * rest of the batch — [supervisorScope] isolates each child, and only cancellation still
     * propagates, per structured concurrency. A failed lookup resolves to null, same as a
     * genuinely-missing doc, letting the caller show a per-member loading/unknown state.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = supervisorScope {
        usuarioIds.map { id ->
            async {
                try {
                    obtener(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Un miembro que no resuelve no debe tumbar el resto del batch (ver KDoc arriba).
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    @Suppress("SwallowedException")
    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean {
        val ref = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNormalizado)
        return try {
            firestore.runTransaction { transaction ->
                val existente = transaction.get(ref)
                if (existente.exists()) {
                    throw NombreUsuarioYaReservadoException()
                }
                transaction.set(ref, mapOf("usuarioId" to usuarioId))
            }.await()
            true
        } catch (e: NombreUsuarioYaReservadoException) {
            // e is our own control-flow signal, not a real failure to surface — see class KDoc.
            false
        }
    }

    override suspend fun crear(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    override suspend fun actualizar(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    @Suppress("SwallowedException")
    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        val refAnterior = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioAnterior)
        val refNuevo = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNuevo)
        val refUsuario = firestore.collection(USUARIOS_COLLECTION).document(usuarioId)
        return try {
            firestore.runTransaction { transaction ->
                val existenteNuevo = transaction.get(refNuevo)
                if (existenteNuevo.exists()) {
                    throw NombreUsuarioYaReservadoException()
                }
                transaction.delete(refAnterior)
                transaction.set(refNuevo, mapOf("usuarioId" to usuarioId))
                transaction.update(refUsuario, "nombreUsuario", nombreUsuarioNuevo)
            }.await()
            true
        } catch (e: NombreUsuarioYaReservadoException) {
            // e is our own control-flow signal, not a real failure to surface — see class KDoc.
            false
        }
    }

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) {
        firestore.collection(EMAILS_USUARIO_COLLECTION).document(emailNormalizado)
            .set(mapOf("usuarioId" to usuarioId)).await()
    }

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? {
        val snapshot = firestore.collection(NOMBRES_USUARIO_COLLECTION)
            .document(nombreUsuarioNormalizado).get().await()
        return snapshot.getString("usuarioId")
    }

    override suspend fun resolverPorEmail(emailNormalizado: String): String? {
        val snapshot = firestore.collection(EMAILS_USUARIO_COLLECTION)
            .document(emailNormalizado).get().await()
        return snapshot.getString("usuarioId")
    }

    private companion object {
        const val USUARIOS_COLLECTION = "usuarios"
        const val NOMBRES_USUARIO_COLLECTION = "nombres_usuario"
        const val EMAILS_USUARIO_COLLECTION = "emails_usuario"
    }
}
