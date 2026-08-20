package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agoitdev.spenvo.data.local.converter.Converters
import java.time.Instant
import androidx.room.TypeConverters

@Entity(tableName = "usuarios")
@TypeConverters(Converters::class)
data class UsuarioEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val email: String,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
