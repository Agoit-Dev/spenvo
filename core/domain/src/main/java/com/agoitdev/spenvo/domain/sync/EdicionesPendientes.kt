package com.agoitdev.spenvo.domain.sync

import java.time.Instant

/** Which entity family a pending edit or a detected conflict belongs to. */
enum class TipoRegistro { GASTO, INGRESO, CATEGORIA, PLAN }

/** `"$coleccion:$id"` — the key both pending-edit and conflict registries index by. */
fun claveRegistro(coleccion: String, id: String): String = "$coleccion:$id"

/**
 * An unconfirmed local write, tracked from the moment the repository reads `previo`. Room-backed
 * (ARCH-M501) — no longer carries the written version (`miVersion`, retired with
 * `VersionPendiente`): at conflict-detection time the local version is reconstructed from the
 * corresponding main-table row instead, which the write-path transaction guarantees is always in
 * sync with this marker (see the design doc's "Entities" section for why).
 */
data class EdicionPendiente(
    val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)

/** What the sincronizador should do with an incoming snapshot for a given key. */
enum class DecisionSincronizacion { APLICAR, PROPIA_CONFIRMADA, CONFLICTO }

/**
 * The LWW conflict decision — pure business policy, no persistence. A conflict requires ALL of: a
 * pending local edit for this key, an incoming `editedBy` that differs from the pending edit's
 * author, and an incoming `editedAt` strictly newer than the pending edit's known base. A matching
 * echo of the pending edit's own write confirms it. Deletion is not special-cased: a soft-delete
 * stamps `editedBy`/`editedAt` exactly like an edit, so this same rule flags delete-vs-edit
 * conflicts.
 */
fun decidirSincronizacion(
    pendiente: EdicionPendiente?,
    editedBy: String?,
    editedAt: Instant?,
): DecisionSincronizacion {
    if (pendiente == null) return DecisionSincronizacion.APLICAR
    val esPropiaConfirmada = editedBy == pendiente.editorId && editedAt == pendiente.miEditedAt
    val esDeOtroEditor = editedBy != pendiente.editorId
    val esMasReciente = editedAt != null &&
        (pendiente.base == null || editedAt.isAfter(pendiente.base))
    return when {
        esPropiaConfirmada -> DecisionSincronizacion.PROPIA_CONFIRMADA
        esDeOtroEditor && esMasReciente -> DecisionSincronizacion.CONFLICTO
        else -> DecisionSincronizacion.APLICAR
    }
}
