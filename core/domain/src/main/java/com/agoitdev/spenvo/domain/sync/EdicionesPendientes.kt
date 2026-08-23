package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Which entity family a pending edit or a detected conflict belongs to. */
enum class TipoRegistro { GASTO, INGRESO, CATEGORIA, PLAN }

/**
 * The exact domain snapshot the user tried to persist. A typed alternative to
 * an untyped `Any`: the eventual "usar la mia" resolution (Slice 5b) reads it
 * back without an unchecked cast.
 */
sealed interface VersionPendiente {
    val tipo: TipoRegistro

    data class DeGasto(val gasto: Gasto) : VersionPendiente {
        override val tipo: TipoRegistro = TipoRegistro.GASTO
    }

    data class DeIngreso(val ingreso: Ingreso) : VersionPendiente {
        override val tipo: TipoRegistro = TipoRegistro.INGRESO
    }

    data class DeCategoria(val categoria: Categoria) : VersionPendiente {
        override val tipo: TipoRegistro = TipoRegistro.CATEGORIA
    }

    data class DePlan(val plan: PlanFinanciero) : VersionPendiente {
        override val tipo: TipoRegistro = TipoRegistro.PLAN
    }
}

/** An unconfirmed local write, tracked from the moment the repository reads `previo`. */
data class EdicionPendiente(
    val clave: String,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
    val miVersion: VersionPendiente,
)

/** What the sincronizador should do with an incoming snapshot for a given key. */
enum class DecisionSincronizacion { APLICAR, PROPIA_CONFIRMADA, CONFLICTO }

/**
 * In-memory (process-lifetime only) registry of unconfirmed local edits, keyed
 * `"$coleccion:$id"`. Feeds the sincronizador's per-document conflict decision
 * (see [evaluar]). Deliberately NOT persisted: if the process dies before the
 * Firestore echo arrives, the marker is lost and the remote version silently
 * wins on the next sync. This is accepted debt (documented in
 * `doc/architecture.md`), not a homegrown outbox.
 */
class EdicionesPendientes {
    private val pendientes = ConcurrentHashMap<String, EdicionPendiente>()

    fun registrar(edicion: EdicionPendiente) {
        pendientes[edicion.clave] = edicion
    }

    /** Same as [registrar], but a no-op when [editorId] is unknown (nothing to attribute). */
    fun registrarSiCorresponde(
        clave: String,
        editorId: String?,
        base: Instant?,
        miEditedAt: Instant?,
        miVersion: VersionPendiente,
    ) {
        if (editorId == null) return
        registrar(EdicionPendiente(clave, editorId, base, miEditedAt, miVersion))
    }

    fun obtener(clave: String): EdicionPendiente? = pendientes[clave]

    fun limpiar(clave: String) {
        pendientes.remove(clave)
    }

    /**
     * A conflict requires ALL of: a pending local edit for [clave], an
     * incoming [editedBy] that differs from the pending edit's author, and an
     * incoming [editedAt] strictly newer than the pending edit's known base.
     * A matching echo of the pending edit's own write clears it and confirms.
     * Deletion is not special-cased: a soft-delete stamps `editedBy`/`editedAt`
     * exactly like an edit, so this same rule flags delete-vs-edit conflicts.
     */
    fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion {
        val pendiente = pendientes[clave] ?: return DecisionSincronizacion.APLICAR
        val esPropiaConfirmada = editedBy == pendiente.editorId && editedAt == pendiente.miEditedAt
        if (esPropiaConfirmada) pendientes.remove(clave)
        val esDeOtroEditor = editedBy != pendiente.editorId
        val esMasReciente = editedAt != null &&
            (pendiente.base == null || editedAt.isAfter(pendiente.base))
        return when {
            esPropiaConfirmada -> DecisionSincronizacion.PROPIA_CONFIRMADA
            esDeOtroEditor && esMasReciente -> DecisionSincronizacion.CONFLICTO
            else -> DecisionSincronizacion.APLICAR
        }
    }

    companion object {
        fun clave(coleccion: String, id: String): String = "$coleccion:$id"
    }
}
