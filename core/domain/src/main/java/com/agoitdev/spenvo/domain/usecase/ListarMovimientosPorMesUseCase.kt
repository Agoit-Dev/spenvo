package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.first

class ListarMovimientosPorMesUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(planId: String, anio: Int, mes: Int): List<Movimiento> {
        val mesTarget = YearMonth.of(anio, mes)
        val gastos = repository.observeGastos(planId).first()
        val ingresos = repository.observeIngresos(planId).first()

        return (gastos.filter { it.deletedAt == null && it.fecha in mesTarget }
            + ingresos.filter { it.deletedAt == null && it.fecha in mesTarget })
            .sortedBy { it.fecha }
    }
}

private operator fun YearMonth.contains(fecha: java.time.LocalDate): Boolean =
    fecha.year == this.year && fecha.monthValue == this.monthValue
