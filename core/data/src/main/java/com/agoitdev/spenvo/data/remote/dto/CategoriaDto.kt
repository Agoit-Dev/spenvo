package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

internal data class CategoriaDto(
    val id: String,
    val planId: String,
    val nombre: String,
    val icono: String,
    val iconoUrl: String?,
    val tipo: TipoCategoria,
    val editedBy: String? = null,
    val editedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null,
) {
    fun toDomain(): Categoria = Categoria(
        id = id,
        planId = planId,
        nombre = nombre,
        icono = icono,
        iconoUrl = iconoUrl,
        tipo = tipo,
        editedBy = editedBy,
        editedAt = editedAt?.toInstant(),
        deletedAt = deletedAt?.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "planId" to planId,
        "nombre" to nombre,
        "icono" to icono,
        "iconoUrl" to iconoUrl,
        "tipo" to tipo.name.lowercase(),
        "editedBy" to editedBy,
        "editedAt" to editedAt,
        "deletedAt" to deletedAt,
    )

    companion object {
        fun fromDomain(categoria: Categoria): CategoriaDto = CategoriaDto(
            id = categoria.id,
            planId = categoria.planId,
            nombre = categoria.nombre,
            icono = categoria.icono,
            iconoUrl = categoria.iconoUrl,
            tipo = categoria.tipo,
            editedBy = categoria.editedBy,
            editedAt = categoria.editedAt?.toTimestamp(),
            deletedAt = categoria.deletedAt?.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): CategoriaDto? {
            val campos = extraerCamposCategoria(data) ?: return null
            return CategoriaDto(
                id = campos.id,
                planId = campos.planId,
                nombre = campos.nombre,
                icono = campos.icono,
                iconoUrl = campos.iconoUrl,
                tipo = campos.tipo,
                editedBy = campos.editedBy,
                editedAt = campos.editedAt,
                deletedAt = campos.deletedAt,
            )
        }
    }
}

private data class CamposCategoria(
    val id: String,
    val planId: String,
    val nombre: String,
    val icono: String,
    val iconoUrl: String?,
    val tipo: TipoCategoria,
    val editedBy: String?,
    val editedAt: Timestamp?,
    val deletedAt: Timestamp?,
)

@Suppress("ReturnCount")
private fun extraerCamposCategoria(data: Map<String, Any?>): CamposCategoria? {
    val id = data["id"] as? String ?: return null
    val planId = data["planId"] as? String ?: return null
    val nombre = data["nombre"] as? String ?: return null
    val icono = data["icono"] as? String ?: "default"
    val tipo = tipoCategoriaFromStored(data["tipo"] as? String) ?: return null
    return CamposCategoria(
        id = id,
        planId = planId,
        nombre = nombre,
        icono = icono,
        iconoUrl = data["iconoUrl"] as? String,
        tipo = tipo,
        editedBy = data["editedBy"] as? String,
        editedAt = data["editedAt"] as? Timestamp,
        deletedAt = data["deletedAt"] as? Timestamp,
    )
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

private fun tipoCategoriaFromStored(value: String?): TipoCategoria? =
    TipoCategoria.entries.firstOrNull { it.name.lowercase() == value }
