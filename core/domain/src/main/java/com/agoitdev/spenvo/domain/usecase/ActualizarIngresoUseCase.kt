package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class ActualizarIngresoUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(ingreso: Ingreso, editorId: String) {
        require(validarMonto(ingreso.monto)) { "El monto debe ser positivo" }
        repository.actualizarIngreso(
            ingreso.copy(editedBy = editorId, editedAt = Instant.now()),
        )
    }
}
