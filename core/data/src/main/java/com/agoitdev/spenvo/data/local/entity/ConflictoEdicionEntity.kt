package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro

@Entity(tableName = "conflictos_pendientes")
data class ConflictoEdicionEntity(
    @PrimaryKey val clave: String,
    val registroId: String,
    val tipo: TipoRegistro,
    val local: SnapshotConflicto,
    val remoto: SnapshotConflicto,
)
