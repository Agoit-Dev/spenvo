package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant

@Entity(tableName = "ediciones_pendientes")
data class EdicionPendienteEntity(
    @PrimaryKey val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)
