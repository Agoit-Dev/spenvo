package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant
import java.util.UUID

class CrearIngresoUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(request: CrearIngresoRequest): Ingreso {
        require(validarMonto(request.monto)) { "El monto debe ser positivo" }
        val now = Instant.now()
        val ingreso = Ingreso(
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
        repository.addIngreso(ingreso)
        return ingreso
    }
}
