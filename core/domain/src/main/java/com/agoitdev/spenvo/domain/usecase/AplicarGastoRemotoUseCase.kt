package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.MovimientoRepository

/** Conflict resolution (Slice 5b): "usar la suya" / "mantener borrado" for a Gasto. */
class AplicarGastoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String) = repository.aplicarGastoRemoto(id)
}
