package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

/**
 * In-memory registry of conflicts detected by the sincronizadores, queryable
 * per-record so the UI (Slice 5b) can defer the modal until the user opens or
 * reopens the conflicted record instead of interrupting the current screen.
 */
class ConflictosPendientes {
    private val _conflictos = MutableStateFlow<Map<String, ConflictoEdicion>>(emptyMap())
    val conflictos: StateFlow<Map<String, ConflictoEdicion>> = _conflictos.asStateFlow()

    fun registrar(clave: String, conflicto: ConflictoEdicion) {
        _conflictos.update { it + (clave to conflicto) }
    }

    fun resolver(clave: String) {
        _conflictos.update { it - clave }
    }

    fun conflictoPara(clave: String): ConflictoEdicion? = conflictos.value[clave]
}

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

/** Delegates to the concrete entity's projection so callers never need to unwrap [VersionPendiente] by hand. */
fun VersionPendiente.aSnapshotConflicto(): SnapshotConflicto = when (this) {
    is VersionPendiente.DeGasto -> gasto.aSnapshotConflicto()
    is VersionPendiente.DeIngreso -> ingreso.aSnapshotConflicto()
    is VersionPendiente.DeCategoria -> categoria.aSnapshotConflicto()
    is VersionPendiente.DePlan -> plan.aSnapshotConflicto()
}
