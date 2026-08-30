package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.UsuarioDto
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
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

    /**
     * The `nombres_usuario` index doc IDs are always the normalized (lowercase) form, but the
     * `usuarios/{uid}.nombreUsuario` display field must keep [nombreUsuarioNuevo]'s original
     * casing (e.g. `GatoAzul42`, never `gatoazul42`) — see the design doc's "nombreUsuario
     * generation" section. It IS trimmed, though: the `usuarios` update rule re-derives the
     * reservation's doc ID as `request.resource.data.nombreUsuario.lower()`, and the rules
     * language has no `.trim()`, so an untrimmed display value would no longer resolve to its
     * own reservation and a legitimate rename would be denied.
     *
     * Deliberately TWO sequential, separately-committed transactions rather than one. Verified
     * empirically against the Firebase Emulator (see the two `renombrar en ... transaccion(es)`
     * cases in `rules-tests/rules.test.mjs`): a `get()` inside a security rule reads the
     * pre-transaction snapshot, so a reservation written earlier in the SAME transaction is not
     * yet visible when the `usuarios` update is validated — the single-transaction shape is
     * denied outright by the anti-impersonation rule.
     *
     * Tradeoff, accepted: this is no longer all-or-nothing. If A commits and B fails, the caller
     * simply owns both the new and the old reservation and keeps their old display name — nothing
     * is lost or handed to anyone else, and re-running `renombrar` completes it (A returns false
     * for a reservation the caller already owns, so recovery today means picking the name again).
     * Left unhandled on purpose, same "known gap, deliberately deferred" stance as the design doc.
     */
    @Suppress("SwallowedException")
    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        val nombreUsuarioVisible = nombreUsuarioNuevo.trim()
        val refAnterior = firestore.collection(NOMBRES_USUARIO_COLLECTION)
            .document(normalizarNombreUsuario(nombreUsuarioAnterior))
        val refNuevo = firestore.collection(NOMBRES_USUARIO_COLLECTION)
            .document(normalizarNombreUsuario(nombreUsuarioNuevo))
        val refUsuario = firestore.collection(USUARIOS_COLLECTION).document(usuarioId)

        val reservado = try {
            firestore.runTransaction { transaction ->
                val existenteNuevo = transaction.get(refNuevo)
                if (existenteNuevo.exists()) {
                    throw NombreUsuarioYaReservadoException()
                }
                transaction.set(refNuevo, mapOf("usuarioId" to usuarioId))
            }.await()
            true
        } catch (e: NombreUsuarioYaReservadoException) {
            // e is our own control-flow signal, not a real failure to surface — see class KDoc.
            false
        }
        if (!reservado) return false

        firestore.runTransaction { transaction ->
            transaction.update(refUsuario, "nombreUsuario", nombreUsuarioVisible)
            transaction.delete(refAnterior)
        }.await()
        return true
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
