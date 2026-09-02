package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import java.time.Instant

/** One field shown side-by-side in the conflict dialog; [clave] maps to `R.string.conflict_field_*` (Slice 5b). */
data class CampoConflicto(val clave: String, val valor: String)

/** One side (local or remote) of a detected conflict. */
data class SnapshotConflicto(
    val editadoPor: String?,
    val editadoEn: Instant?,
    val borrado: Boolean,
    val campos: List<CampoConflicto>,
)

/** A genuine concurrent-edit conflict for one record, pending user resolution. */
data class ConflictoEdicion(
    val registroId: String,
    val tipo: TipoRegistro,
    val local: SnapshotConflicto,
    val remoto: SnapshotConflicto,
)

fun Movimiento.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("monto", monto.unidadesMenores.toString()),
        CampoConflicto("fecha", fecha.toString()),
        CampoConflicto("categoria", categoriaId),
        CampoConflicto("descripcion", descripcion.orEmpty()),
    ),
)

fun Categoria.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("nombre", nombre),
        CampoConflicto("tipo", tipo.toString()),
        CampoConflicto("icono", icono),
    ),
)

fun PlanFinanciero.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("nombre", nombre),
        CampoConflicto("descripcion", descripcion.orEmpty()),
        CampoConflicto("moneda", moneda),
    ),
)
