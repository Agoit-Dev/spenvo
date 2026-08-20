package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agoitdev.spenvo.data.local.converter.Converters
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "gastos",
    indices = [Index(value = ["planId", "fecha"])],
)
@TypeConverters(Converters::class)
data class GastoEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val categoriaId: String,
    val montoUnidadesMenores: Long,
    val fecha: LocalDate,
    val descripcion: String? = null,
    val creadoPor: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val editedBy: String? = null,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
)
