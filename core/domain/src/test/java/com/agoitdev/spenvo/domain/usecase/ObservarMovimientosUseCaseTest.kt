package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservarMovimientosUseCaseTest {

    @Test
    fun `combina gastos e ingresos sin borrados ordenados por fecha descendente`() = runTest {
        val repo = FakeObservarMovimientosRepository(
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
                    fecha = LocalDate.of(2026, 8, 12),
                    creadoPor = "user-1",
                    createdAt = Instant.parse("2026-08-12T10:00:00Z"),
                    updatedAt = Instant.parse("2026-08-12T10:00:00Z"),
                ),
            ),
        )
        val useCase = ObservarMovimientosUseCase(repo)

        val movimientos = useCase("plan-1").first()

        assertEquals(2, movimientos.size)
        assertTrue(movimientos.none { it.id == "g2" })
        assertEquals("i1", movimientos.first().id)
    }
}

private class FakeObservarMovimientosRepository(
    private val gastos: List<Gasto> = emptyList(),
    private val ingresos: List<Ingreso> = emptyList(),
) : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastos)
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresos)
}
