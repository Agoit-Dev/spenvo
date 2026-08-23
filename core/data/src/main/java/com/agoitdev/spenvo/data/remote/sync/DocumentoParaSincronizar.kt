package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.EdicionesPendientes
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import java.time.Instant

/** One remote document identity as seen by [evaluarDocumentoRemoto], before it is applied or held back. */
internal data class DocumentoParaSincronizar(
    val coleccion: String,
    val id: String,
    val editedBy: String?,
    val editedAt: Instant?,
    val tipo: TipoRegistro,
) {
    val clave: String get() = EdicionesPendientes.clave(coleccion, id)
}

/**
 * Applies the Slice 4 conflict decision to one incoming remote document.
 * Returns `true` when the sincronizador should upsert it to Room; registers a
 * [ConflictoEdicion] in [conflictosPendientes] and returns `false` when it
 * must be held back instead (design Decision 7 — the user's own pending edit
 * keeps showing until they resolve it).
 */
internal fun evaluarDocumentoRemoto(
    edicionesPendientes: EdicionesPendientes,
    conflictosPendientes: ConflictosPendientes,
    documento: DocumentoParaSincronizar,
    remoto: () -> SnapshotConflicto,
): Boolean {
    val clave = documento.clave
    val decision = edicionesPendientes.evaluar(clave, documento.editedBy, documento.editedAt)
    val pendiente = if (decision == DecisionSincronizacion.CONFLICTO) edicionesPendientes.obtener(clave) else null
    if (pendiente == null) return true
    conflictosPendientes.registrar(
        clave,
        ConflictoEdicion(
            registroId = documento.id,
            tipo = documento.tipo,
            local = pendiente.miVersion.aSnapshotConflicto(),
            remoto = remoto(),
        ),
    )
    return false
}
