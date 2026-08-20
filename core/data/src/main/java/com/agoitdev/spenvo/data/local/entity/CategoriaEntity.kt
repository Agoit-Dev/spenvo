package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agoitdev.spenvo.data.local.converter.Converters
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.Instant

@Entity(
    tableName = "categorias",
    indices = [Index(value = ["planId", "tipo"])],
)
@TypeConverters(Converters::class)
data class CategoriaEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val nombre: String,
    val icono: String = "default",
    val iconoUrl: String? = null,
    val tipo: TipoCategoria,
    val editedBy: String? = null,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
)
