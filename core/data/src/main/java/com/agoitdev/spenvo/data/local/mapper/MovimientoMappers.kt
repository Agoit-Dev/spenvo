package com.agoitdev.spenvo.data.local.mapper

import com.agoitdev.spenvo.data.local.entity.GastoEntity
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto

fun Gasto.toEntity(): GastoEntity = GastoEntity(
    id = id,
    planId = planId,
    categoriaId = categoriaId,
    montoUnidadesMenores = monto.unidadesMenores,
    fecha = fecha,
    descripcion = descripcion,
    creadoPor = creadoPor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun GastoEntity.toDomain(): Gasto = Gasto(
    id = id,
    planId = planId,
    categoriaId = categoriaId,
    monto = Monto(montoUnidadesMenores),
    fecha = fecha,
    descripcion = descripcion,
    creadoPor = creadoPor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun Ingreso.toEntity(): IngresoEntity = IngresoEntity(
    id = id,
    planId = planId,
    categoriaId = categoriaId,
    montoUnidadesMenores = monto.unidadesMenores,
    fecha = fecha,
    descripcion = descripcion,
    creadoPor = creadoPor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun IngresoEntity.toDomain(): Ingreso = Ingreso(
    id = id,
    planId = planId,
    categoriaId = categoriaId,
    monto = Monto(montoUnidadesMenores),
    fecha = fecha,
    descripcion = descripcion,
    creadoPor = creadoPor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)
