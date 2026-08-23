package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class EliminarIngresoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(ingreso: Ingreso, editorId: String) {
        val ahora = Instant.now()
        repository.eliminarIngreso(
            ingreso.copy(editedBy = editorId, editedAt = ahora, deletedAt = ahora),
        )
    }
}
