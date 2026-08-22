package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObservarMovimientosUseCase(
    private val repository: MovimientoRepository,
) {
    operator fun invoke(planId: String): Flow<List<Movimiento>> =
        combine(repository.observeGastos(planId), repository.observeIngresos(planId)) { gastos, ingresos ->
            (gastos.filter { it.deletedAt == null } + ingresos.filter { it.deletedAt == null })
                .sortedByDescending { it.fecha }
        }
}
