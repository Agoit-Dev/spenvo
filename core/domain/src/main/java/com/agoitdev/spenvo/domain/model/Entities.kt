package com.agoitdev.spenvo.domain.model

import java.time.Instant

data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class PlanFinanciero(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val moneda: String,
    val createdBy: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val editedBy: String? = null,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
)

data class AccesoPlan(
    val usuarioId: String,
    val planId: String,
    val rol: Rol,
    val invitacionEstado: InvitacionEstado = InvitacionEstado.PENDIENTE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class Categoria(
    val id: String,
    val planId: String,
    val nombre: String,
    val icono: String = "default",
    val iconoUrl: String? = null,
    val tipo: TipoCategoria,
    val editedBy: String? = null,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
)

sealed interface Movimiento {
    val id: String
    val planId: String
    val categoriaId: String
    val monto: Monto
    val fecha: java.time.LocalDate
    val descripcion: String?
    val creadoPor: String
    val createdAt: Instant
    val updatedAt: Instant
    val editedBy: String?
    val editedAt: Instant?
    val deletedAt: Instant?
}

data class Gasto(
    override val id: String,
    override val planId: String,
    override val categoriaId: String,
    override val monto: Monto,
    override val fecha: java.time.LocalDate,
    override val descripcion: String? = null,
    override val creadoPor: String,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    override val editedBy: String? = null,
    override val editedAt: Instant? = null,
    override val deletedAt: Instant? = null,
) : Movimiento

data class Ingreso(
    override val id: String,
    override val planId: String,
    override val categoriaId: String,
    override val monto: Monto,
    override val fecha: java.time.LocalDate,
    override val descripcion: String? = null,
    override val creadoPor: String,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    override val editedBy: String? = null,
    override val editedAt: Instant? = null,
    override val deletedAt: Instant? = null,
) : Movimiento
