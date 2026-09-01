package com.agoitdev.spenvo.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Room-backed registry of detected conflicts pending user resolution (ARCH-M501). No
 * `resolverPorRegistro(registroId)` — that lookup was ambiguous (a Gasto and an Ingreso could
 * share an id) and resolved only the first match; callers derive the unambiguous `clave` from
 * [conflictos] instead (see `MovimientosViewModel.claveVisible()`, Task 8).
 */
interface RegistroConflictosPendientes {
    val conflictos: Flow<Map<String, ConflictoEdicion>>
    suspend fun conflictoPara(clave: String): ConflictoEdicion?
    suspend fun registrar(clave: String, conflicto: ConflictoEdicion)
    suspend fun resolver(clave: String)
}
