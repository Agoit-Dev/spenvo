package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.Date

internal data class GastoDto(
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
    fun toDomain(): Gasto = Gasto(
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
        fun fromDomain(gasto: Gasto): GastoDto = GastoDto(
            id = gasto.id,
            planId = gasto.planId,
            categoriaId = gasto.categoriaId,
            montoUnidadesMenores = gasto.monto.unidadesMenores,
            fecha = gasto.fecha.toString(),
            descripcion = gasto.descripcion,
            creadoPor = gasto.creadoPor,
            createdAt = gasto.createdAt.toTimestamp(),
            updatedAt = gasto.updatedAt.toTimestamp(),
            editedBy = gasto.editedBy,
            editedAt = gasto.editedAt?.toTimestamp(),
            deletedAt = gasto.deletedAt?.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): GastoDto? {
            val campos = extraerCamposGasto(data) ?: return null
            return GastoDto(
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

private data class CamposGasto(
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
private fun extraerCamposGasto(data: Map<String, Any?>): CamposGasto? {
    val id = data["id"] as? String ?: return null
    val planId = data["planId"] as? String ?: return null
    val categoriaId = data["categoriaId"] as? String ?: return null
    val monto = (data["montoUnidadesMenores"] as? Number)?.toLong() ?: return null
    val fecha = data["fecha"] as? String ?: return null
    val creadoPor = data["creadoPor"] as? String ?: return null
    val createdAt = data["createdAt"] as? Timestamp ?: return null
    val updatedAt = data["updatedAt"] as? Timestamp ?: return null
    return CamposGasto(
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
