package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agoitdev.spenvo.data.local.converter.Converters
import java.time.Instant

@Entity(tableName = "planes_financieros")
@TypeConverters(Converters::class)
data class PlanFinancieroEntity(
    @PrimaryKey val id: String,
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
