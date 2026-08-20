package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant
import java.util.UUID

class CrearGastoUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(request: CrearGastoRequest): Gasto {
        require(validarMonto(request.monto)) { "El monto debe ser positivo" }
        val now = Instant.now()
        val gasto = Gasto(
            id = UUID.randomUUID().toString(),
            planId = request.planId,
            categoriaId = request.categoriaId,
            monto = request.monto,
            fecha = request.fecha,
            descripcion = request.descripcion,
            creadoPor = request.creadoPor,
            createdAt = now,
            updatedAt = now,
        )
        repository.addGasto(gasto)
        return gasto
    }
}
