package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListarMovimientosPorMesUseCaseTest {

    @Test
    fun `combina gastos e ingresos del mes sin borrados`() = runTest {
        val repo = FakeMovimientoRepository(
            gastos = listOf(
                Gasto(
                    id = "g1",
                    planId = "plan-1",
                    categoriaId = "cat-1",
                    monto = Monto(1000),
                    fecha = LocalDate.of(2026, 8, 5),
                    creadoPor = "user-1",
                    createdAt = Instant.parse("2026-08-05T10:00:00Z"),
                    updatedAt = Instant.parse("2026-08-05T10:00:00Z"),
                ),
                Gasto(
                    id = "g2",
                    planId = "plan-1",
                    categoriaId = "cat-1",
                    monto = Monto(500),
                    fecha = LocalDate.of(2026, 7, 30),
                    creadoPor = "user-1",
                    createdAt = Instant.parse("2026-07-30T10:00:00Z"),
                    updatedAt = Instant.parse("2026-07-30T10:00:00Z"),
                ),
                Gasto(
                    id = "g3",
                    planId = "plan-1",
                    categoriaId = "cat-1",
                    monto = Monto(700),
                    fecha = LocalDate.of(2026, 8, 10),
                    creadoPor = "user-1",
                    createdAt = Instant.parse("2026-08-10T10:00:00Z"),
                    updatedAt = Instant.parse("2026-08-10T10:00:00Z"),
                    deletedAt = Instant.parse("2026-08-11T10:00:00Z"),
                ),
            ),
            ingresos = listOf(
                Ingreso(
                    id = "i1",
                    planId = "plan-1",
                    categoriaId = "cat-2",
                    monto = Monto(2000),
                    fecha = LocalDate.of(2026, 8, 1),
                    creadoPor = "user-1",
                    createdAt = Instant.parse("2026-08-01T10:00:00Z"),
                    updatedAt = Instant.parse("2026-08-01T10:00:00Z"),
                ),
            ),
        )
        val useCase = ListarMovimientosPorMesUseCase(repo)

        val movimientos = useCase("plan-1", 2026, 8)

        assertEquals(2, movimientos.size)
        assertTrue(movimientos.none { it.id == "g3" })
        assertTrue(movimientos.all { it.fecha.year == 2026 && it.fecha.monthValue == 8 })
    }
}

private class FakeMovimientoRepository(
    private val gastos: List<Gasto> = emptyList(),
    private val ingresos: List<Ingreso> = emptyList(),
) : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastos)
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresos)
}
