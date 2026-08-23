package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class EliminarGastoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(gasto: Gasto, editorId: String) {
        val ahora = Instant.now()
        repository.eliminarGasto(
            gasto.copy(editedBy = editorId, editedAt = ahora, deletedAt = ahora),
        )
    }
}
