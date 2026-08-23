package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.MovimientoRepository

/** Conflict resolution (Slice 5b): "usar la suya" / "mantener borrado" for an Ingreso. */
class AplicarIngresoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String) = repository.aplicarIngresoRemoto(id)
}
