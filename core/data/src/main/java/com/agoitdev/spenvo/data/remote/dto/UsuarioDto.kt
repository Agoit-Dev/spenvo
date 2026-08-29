package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Usuario
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

internal data class UsuarioDto(
    val uid: String,
    val nombreUsuario: String,
    val nombre: String?,
    val email: String?,
    val avatarUrl: String?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) {
    fun toDomain(): Usuario = Usuario(
        id = uid,
        nombreUsuario = nombreUsuario,
        nombre = nombre,
        email = email,
        avatarUrl = avatarUrl,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "nombreUsuario" to nombreUsuario,
        "nombre" to nombre,
        "email" to email,
        "avatarUrl" to avatarUrl,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    companion object {
        fun fromDomain(usuario: Usuario): UsuarioDto = UsuarioDto(
            uid = usuario.id,
            nombreUsuario = usuario.nombreUsuario,
            nombre = usuario.nombre,
            email = usuario.email,
            avatarUrl = usuario.avatarUrl,
            createdAt = usuario.createdAt.toTimestamp(),
            updatedAt = usuario.updatedAt.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): UsuarioDto? {
            val campos = extraerCamposUsuario(data) ?: return null
            return UsuarioDto(
                uid = campos.uid,
                nombreUsuario = campos.nombreUsuario,
                nombre = data["nombre"] as? String,
                email = data["email"] as? String,
                avatarUrl = data["avatarUrl"] as? String,
                createdAt = campos.createdAt,
                updatedAt = campos.updatedAt,
            )
        }
    }
}

private data class CamposUsuario(
    val uid: String,
    val nombreUsuario: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

@Suppress("ReturnCount")
private fun extraerCamposUsuario(data: Map<String, Any?>): CamposUsuario? {
    val uid = data["uid"] as? String ?: return null
    val nombreUsuario = data["nombreUsuario"] as? String ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    val updatedAt = data["updatedAt"] as? Timestamp ?: return null
    return CamposUsuario(uid, nombreUsuario, createdAt, updatedAt)
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))
