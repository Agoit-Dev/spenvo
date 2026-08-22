package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.Date

internal data class IngresoDto(
    val id: String,
    val planId: String,
    val categoriaId: String,
    val montoUnidadesMenores: Long,
    val fecha: String,
    val descripcion: String?,
    val creadoPor: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val editedBy: String? = null,
    val editedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null,
) {
    fun toDomain(): Ingreso = Ingreso(
        id = id,
        planId = planId,
        categoriaId = categoriaId,
        monto = Monto(montoUnidadesMenores),
        fecha = LocalDate.parse(fecha),
        descripcion = descripcion,
        creadoPor = creadoPor,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        editedBy = editedBy,
        editedAt = editedAt?.toInstant(),
        deletedAt = deletedAt?.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "planId" to planId,
        "categoriaId" to categoriaId,
        "montoUnidadesMenores" to montoUnidadesMenores,
        "fecha" to fecha,
        "descripcion" to descripcion,
        "creadoPor" to creadoPor,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "editedBy" to editedBy,
        "editedAt" to editedAt,
        "deletedAt" to deletedAt,
    )

    companion object {
        fun fromDomain(ingreso: Ingreso): IngresoDto = IngresoDto(
            id = ingreso.id,
            planId = ingreso.planId,
            categoriaId = ingreso.categoriaId,
            montoUnidadesMenores = ingreso.monto.unidadesMenores,
            fecha = ingreso.fecha.toString(),
            descripcion = ingreso.descripcion,
            creadoPor = ingreso.creadoPor,
            createdAt = ingreso.createdAt.toTimestamp(),
            updatedAt = ingreso.updatedAt.toTimestamp(),
            editedBy = ingreso.editedBy,
            editedAt = ingreso.editedAt?.toTimestamp(),
            deletedAt = ingreso.deletedAt?.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): IngresoDto? {
            val campos = extraerCamposIngreso(data) ?: return null
            return IngresoDto(
                id = campos.id,
                planId = campos.planId,
                categoriaId = campos.categoriaId,
                montoUnidadesMenores = campos.monto,
                fecha = campos.fecha,
                descripcion = campos.descripcion,
                creadoPor = campos.creadoPor,
                createdAt = campos.createdAt,
                updatedAt = campos.updatedAt,
                editedBy = campos.editedBy,
                editedAt = campos.editedAt,
                deletedAt = campos.deletedAt,
            )
        }
    }
}

private data class CamposIngreso(
    val id: String,
    val planId: String,
    val categoriaId: String,
    val monto: Long,
    val fecha: String,
    val descripcion: String?,
    val creadoPor: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val editedBy: String?,
    val editedAt: Timestamp?,
    val deletedAt: Timestamp?,
)

@Suppress("ReturnCount")
private fun extraerCamposIngreso(data: Map<String, Any?>): CamposIngreso? {
    val id = data["id"] as? String ?: return null
    val planId = data["planId"] as? String ?: return null
    val categoriaId = data["categoriaId"] as? String ?: return null
    val monto = (data["montoUnidadesMenores"] as? Number)?.toLong() ?: return null
    val fecha = data["fecha"] as? String ?: return null
    val creadoPor = data["creadoPor"] as? String ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    val updatedAt = data["updatedAt"] as? Timestamp ?: return null
    return CamposIngreso(
        id = id,
        planId = planId,
        categoriaId = categoriaId,
        monto = monto,
        fecha = fecha,
        descripcion = data["descripcion"] as? String,
        creadoPor = creadoPor,
        createdAt = createdAt,
        updatedAt = updatedAt,
        editedBy = data["editedBy"] as? String,
        editedAt = data["editedAt"] as? Timestamp,
        deletedAt = data["deletedAt"] as? Timestamp,
    )
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))
