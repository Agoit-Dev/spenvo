package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.TypeConverters
import com.agoitdev.spenvo.data.local.converter.Converters
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.Rol
import java.time.Instant

@Entity(
    tableName = "acceso_plan_financiero",
    primaryKeys = ["usuarioId", "planId"],
)
@TypeConverters(Converters::class)
data class AccesoPlanEntity(
    val usuarioId: String,
    val planId: String,
    val rol: Rol,
    val invitacionEstado: InvitacionEstado = InvitacionEstado.PENDIENTE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
