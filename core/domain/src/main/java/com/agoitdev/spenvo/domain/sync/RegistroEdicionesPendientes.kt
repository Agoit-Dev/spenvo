package com.agoitdev.spenvo.domain.sync

import java.time.Instant

/**
 * Room-backed registry of unconfirmed local edits (ARCH-M501) — see
 * `doc/designs/2026-09-01-conflictos-pendientes-room-design.md`. Every method is `suspend` because
 * every implementation reads/writes Room; callers are expected to invoke these from inside a
 * `SpenvoDatabase.withTransaction { }` block alongside the Room write the marker is tracking.
 */
interface RegistroEdicionesPendientes {
    suspend fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion
    suspend fun registrarSiCorresponde(
        clave: String,
        editorId: String?,
        base: Instant?,
        miEditedAt: Instant?,
        tipo: TipoRegistro,
    )
    suspend fun limpiar(clave: String)
}
