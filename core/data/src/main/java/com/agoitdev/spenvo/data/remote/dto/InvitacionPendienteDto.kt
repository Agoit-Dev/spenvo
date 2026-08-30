package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

internal data class InvitacionPendienteDto(
    val email: String,
    val planId: String,
    val rol: Rol,
    val invitadoPor: String,
    val createdAt: Timestamp,
) {
    fun toDomain(): InvitacionPendiente = InvitacionPendiente(
        email = email,
        planId = planId,
        rol = rol,
        invitadoPor = invitadoPor,
        createdAt = createdAt.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "email" to email,
        "planId" to planId,
        "rol" to rol.name.lowercase(),
        "invitadoPor" to invitadoPor,
        "createdAt" to createdAt,
    )

    companion object {
        fun fromDomain(invitacion: InvitacionPendiente): InvitacionPendienteDto = InvitacionPendienteDto(
            email = invitacion.email,
            planId = invitacion.planId,
            rol = invitacion.rol,
            invitadoPor = invitacion.invitadoPor,
            createdAt = invitacion.createdAt.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): InvitacionPendienteDto? {
            val campos = extraerCamposInvitacionPendiente(data) ?: return null
            return InvitacionPendienteDto(
                email = campos.email,
                planId = campos.planId,
                rol = campos.rol,
                invitadoPor = campos.invitadoPor,
                createdAt = campos.createdAt,
            )
        }
    }
}

private data class CamposInvitacionPendiente(
    val email: String,
    val planId: String,
    val rol: Rol,
    val invitadoPor: String,
    val createdAt: Timestamp,
)

@Suppress("ReturnCount")
private fun extraerCamposInvitacionPendiente(data: Map<String, Any?>): CamposInvitacionPendiente? {
    val email = data["email"] as? String ?: return null
    val planId = data["planId"] as? String ?: return null
    val rol = rolFromStored(data["rol"] as? String) ?: return null
    val invitadoPor = data["invitadoPor"] as? String ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    return CamposInvitacionPendiente(email, planId, rol, invitadoPor, createdAt)
}

private fun rolFromStored(value: String?): Rol? = Rol.entries.firstOrNull { it.name.lowercase() == value }

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))
