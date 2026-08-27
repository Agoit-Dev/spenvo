package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObservarBalanceAcumuladoPlanUseCase(
    private val movimientoRepository: MovimientoRepository,
) {
    operator fun invoke(planId: String): Flow<Monto> =
        combine(
            movimientoRepository.observeGastos(planId),
            movimientoRepository.observeIngresos(planId),
        ) { gastos, ingresos ->
            val totalGastos = gastos.filter { it.deletedAt == null }.sumOf { it.monto.unidadesMenores }
            val totalIngresos = ingresos.filter { it.deletedAt == null }.sumOf { it.monto.unidadesMenores }
            Monto(totalIngresos - totalGastos)
        }
}
