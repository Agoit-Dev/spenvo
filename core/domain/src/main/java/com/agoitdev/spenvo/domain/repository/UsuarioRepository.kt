package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Usuario

interface UsuarioRepository {
    suspend fun obtener(usuarioId: String): Usuario?

    suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario>

    /** Transactional: true si el nombreUsuario estaba libre y quedó reservado para [usuarioId]. */
    suspend fun intentarReservarNombreUsuario(nombreUsuarioNormalizado: String, usuarioId: String): Boolean

    suspend fun crear(usuario: Usuario)

    /** Actualiza nombre/email/avatarUrl; nunca toca nombreUsuario (usar [renombrar]). */
    suspend fun actualizar(usuario: Usuario)

    /** Transactional: libera [anterior], reserva [nuevo]. False si [nuevo] ya estaba tomado. */
    suspend fun renombrar(usuarioId: String, nombreUsuarioAnterior: String, nombreUsuarioNuevo: String): Boolean

    suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String)

    suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String?

    suspend fun resolverPorEmail(emailNormalizado: String): String?
}
