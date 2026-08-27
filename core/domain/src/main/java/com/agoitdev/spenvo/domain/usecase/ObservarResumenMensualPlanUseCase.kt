package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObservarResumenMensualPlanUseCase(
    private val movimientoRepository: MovimientoRepository,
) {
    operator fun invoke(planId: String, mes: YearMonth = YearMonth.now()): Flow<ResumenMensualPlan> =
        combine(
            movimientoRepository.observeGastos(planId),
            movimientoRepository.observeIngresos(planId),
        ) { g, i ->
            val gastosMes = g
                .filter { it.deletedAt == null && YearMonth.from(it.fecha) == mes }
                .sumOf { it.monto.unidadesMenores }
            val ingresosMes = i
                .filter { it.deletedAt == null && YearMonth.from(it.fecha) == mes }
                .sumOf { it.monto.unidadesMenores }
            ResumenMensualPlan(
                planId = planId,
                netoDelMes = Monto(ingresosMes - gastosMes),
                ingresosMes = Monto(ingresosMes),
                gastosMes = Monto(gastosMes),
            )
        }
}
