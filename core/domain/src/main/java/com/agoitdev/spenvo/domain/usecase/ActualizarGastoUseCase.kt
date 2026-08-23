package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class ActualizarGastoUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(gasto: Gasto, editorId: String) {
        require(validarMonto(gasto.monto)) { "El monto debe ser positivo" }
        repository.actualizarGasto(
            gasto.copy(editedBy = editorId, editedAt = Instant.now()),
        )
    }
}
