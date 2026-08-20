package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Rol
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

internal data class PlanFinancieroDto(
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val moneda: String,
    val createdBy: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp? = null,
) {
    fun toDomain(): PlanFinanciero = PlanFinanciero(
        id = id,
        nombre = nombre,
        descripcion = descripcion,
        moneda = moneda,
        createdBy = createdBy,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        deletedAt = deletedAt?.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "nombre" to nombre,
        "descripcion" to descripcion,
        "moneda" to moneda,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "deletedAt" to deletedAt,
    )

    companion object {
        fun fromDomain(plan: PlanFinanciero): PlanFinancieroDto = PlanFinancieroDto(
            id = plan.id,
            nombre = plan.nombre,
            descripcion = plan.descripcion,
            moneda = plan.moneda,
            createdBy = plan.createdBy,
            createdAt = plan.createdAt.toTimestamp(),
            updatedAt = plan.updatedAt.toTimestamp(),
            deletedAt = plan.deletedAt?.let { it.toTimestamp() },
        )

        fun fromData(data: Map<String, Any?>): PlanFinancieroDto? {
            val campos = extraerCamposPlan(data) ?: return null
            return PlanFinancieroDto(
                id = campos.id,
                nombre = campos.nombre,
                descripcion = campos.descripcion,
                moneda = campos.moneda,
                createdBy = campos.createdBy,
                createdAt = campos.createdAt,
                updatedAt = campos.updatedAt,
                deletedAt = campos.deletedAt,
            )
        }
    }
}

private data class CamposPlan(
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val moneda: String,
    val createdBy: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deletedAt: Timestamp?,
)

@Suppress("ReturnCount")
private fun extraerCamposPlan(data: Map<String, Any?>): CamposPlan? {
    val id = data["id"] as? String ?: return null
    val nombre = data["nombre"] as? String ?: return null
    val moneda = data["moneda"] as? String ?: return null
    val createdBy = data["createdBy"] as? String ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    val updatedAt = data["updatedAt"] as? Timestamp ?: return null
    return CamposPlan(
        id = id,
        nombre = nombre,
        descripcion = data["descripcion"] as? String,
        moneda = moneda,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = data["deletedAt"] as? Timestamp,
    )
}

internal data class AccesoPlanDto(
    val usuarioId: String,
    val planId: String,
    val rol: Rol,
    val invitacionEstado: InvitacionEstado,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) {
    fun toDomain(): AccesoPlan = AccesoPlan(
        usuarioId = usuarioId,
        planId = planId,
        rol = rol,
        invitacionEstado = invitacionEstado,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "usuarioId" to usuarioId,
        "planId" to planId,
        "rol" to rol.name.lowercase(),
        "invitacionEstado" to invitacionEstado.name.lowercase(),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    companion object {
        fun fromDomain(acceso: AccesoPlan): AccesoPlanDto = AccesoPlanDto(
            usuarioId = acceso.usuarioId,
            planId = acceso.planId,
            rol = acceso.rol,
            invitacionEstado = acceso.invitacionEstado,
            createdAt = acceso.createdAt.toTimestamp(),
            updatedAt = acceso.updatedAt.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): AccesoPlanDto? {
            val campos = extraerCamposAcceso(data) ?: return null
            return AccesoPlanDto(
                usuarioId = campos.usuarioId,
                planId = campos.planId,
                rol = campos.rol,
                invitacionEstado = campos.invitacionEstado,
                createdAt = campos.createdAt,
                updatedAt = campos.updatedAt,
            )
        }
    }
}

private data class CamposAcceso(
    val usuarioId: String,
    val planId: String,
    val rol: Rol,
    val invitacionEstado: InvitacionEstado,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

@Suppress("ReturnCount")
private fun extraerCamposAcceso(data: Map<String, Any?>): CamposAcceso? {
    val usuarioId = data["usuarioId"] as? String ?: return null
    val planId = data["planId"] as? String ?: return null
    val rol = rolFromStored(data["rol"] as? String) ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    val updatedAt = data["updatedAt"] as? Timestamp ?: return null
    return CamposAcceso(
        usuarioId = usuarioId,
        planId = planId,
        rol = rol,
        invitacionEstado = invitacionEstadoFromStored(data["invitacionEstado"] as? String)
            ?: InvitacionEstado.PENDIENTE,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

private fun rolFromStored(value: String?): Rol? =
    Rol.entries.firstOrNull { it.name.lowercase() == value }

private fun invitacionEstadoFromStored(value: String?): InvitacionEstado? =
    InvitacionEstado.entries.firstOrNull { it.name.lowercase() == value }
