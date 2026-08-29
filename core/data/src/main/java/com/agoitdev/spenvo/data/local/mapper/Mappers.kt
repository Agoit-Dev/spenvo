package com.agoitdev.spenvo.data.local.mapper

import com.agoitdev.spenvo.data.local.entity.AccesoPlanEntity
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.data.local.entity.PlanFinancieroEntity
import com.agoitdev.spenvo.data.local.entity.UsuarioEntity
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Usuario

fun Usuario.toEntity(): UsuarioEntity = UsuarioEntity(
    id = id,
    nombreUsuario = nombreUsuario,
    nombre = nombre,
    email = email,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UsuarioEntity.toDomain(): Usuario = Usuario(
    id = id,
    nombreUsuario = nombreUsuario,
    nombre = nombre,
    email = email,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlanFinanciero.toEntity(): PlanFinancieroEntity = PlanFinancieroEntity(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    moneda = moneda,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun PlanFinancieroEntity.toDomain(): PlanFinanciero = PlanFinanciero(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    moneda = moneda,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun AccesoPlan.toEntity(): AccesoPlanEntity = AccesoPlanEntity(
    usuarioId = usuarioId,
    planId = planId,
    rol = rol,
    invitacionEstado = invitacionEstado,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AccesoPlanEntity.toDomain(): AccesoPlan = AccesoPlan(
    usuarioId = usuarioId,
    planId = planId,
    rol = rol,
    invitacionEstado = invitacionEstado,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Categoria.toEntity(): CategoriaEntity = CategoriaEntity(
    id = id,
    planId = planId,
    nombre = nombre,
    icono = icono,
    iconoUrl = iconoUrl,
    tipo = tipo,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)

fun CategoriaEntity.toDomain(): Categoria = Categoria(
    id = id,
    planId = planId,
    nombre = nombre,
    icono = icono,
    iconoUrl = iconoUrl,
    tipo = tipo,
    editedBy = editedBy,
    editedAt = editedAt,
    deletedAt = deletedAt,
)
