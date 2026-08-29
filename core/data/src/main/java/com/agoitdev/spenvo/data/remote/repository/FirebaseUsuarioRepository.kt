package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.UsuarioDto
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class FirebaseUsuarioRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UsuarioRepository {

    override suspend fun obtener(usuarioId: String): Usuario? {
        val snapshot = firestore.collection(USUARIOS_COLLECTION).document(usuarioId).get().await()
        return snapshot.data?.let { UsuarioDto.fromData(it)?.toDomain() }
    }

    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = coroutineScope {
        usuarioIds.map { id -> async { obtener(id) } }.awaitAll().filterNotNull()
    }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean {
        val ref = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNormalizado)
        return runCatching {
            firestore.runTransaction { transaction ->
                val existente = transaction.get(ref)
                if (existente.exists()) {
                    error("nombreUsuario ya reservado")
                }
                transaction.set(ref, mapOf("usuarioId" to usuarioId))
            }.await()
            true
        }.getOrElse { false }
    }

    override suspend fun crear(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    override suspend fun actualizar(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        val refAnterior = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioAnterior)
        val refNuevo = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNuevo)
        val refUsuario = firestore.collection(USUARIOS_COLLECTION).document(usuarioId)
        return runCatching {
            firestore.runTransaction { transaction ->
                val existenteNuevo = transaction.get(refNuevo)
                if (existenteNuevo.exists()) {
                    error("nombreUsuario ya reservado")
                }
                transaction.delete(refAnterior)
                transaction.set(refNuevo, mapOf("usuarioId" to usuarioId))
                transaction.update(refUsuario, "nombreUsuario", nombreUsuarioNuevo)
            }.await()
            true
        }.getOrElse { false }
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
