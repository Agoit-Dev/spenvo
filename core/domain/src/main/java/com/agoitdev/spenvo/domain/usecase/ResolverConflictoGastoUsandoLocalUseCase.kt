package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class ResolverConflictoGastoUsandoLocalUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(gasto: Gasto, editorId: String, clave: String) {
        require(validarMonto(gasto.monto)) { "El monto debe ser positivo" }
        repository.resolverConflictoGastoUsandoLocal(
            gasto.copy(editedBy = editorId, editedAt = Instant.now()),
            clave,
        )
    }
}
