package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservarResumenMensualPlanUseCaseTest {

    @Test
    fun `emite balance cero cuando no hay movimientos en el mes`() = runTest {
        val repo = FakeMovimientoResumenRepository()
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val resumen = useCase("plan-1", YearMonth.of(2026, 8)).first()

        assertEquals("plan-1", resumen.planId)
        assertEquals(0L, resumen.netoDelMes.unidadesMenores)
    }

    @Test
    fun `calcula el balance neto positivo cuando los ingresos superan los gastos`() = runTest {
        val repo = FakeMovimientoResumenRepository(
            gastos = listOf(gasto("g1", 1000, LocalDate.of(2026, 8, 5))),
            ingresos = listOf(ingreso("i1", 3000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val resumen = useCase("plan-1", YearMonth.of(2026, 8)).first()

        assertEquals(2000L, resumen.netoDelMes.unidadesMenores)
        assertEquals(3000L, resumen.ingresosMes.unidadesMenores)
        assertEquals(1000L, resumen.gastosMes.unidadesMenores)
    }

    @Test
    fun `calcula el balance neto negativo cuando los gastos superan los ingresos`() = runTest {
        val repo = FakeMovimientoResumenRepository(
            gastos = listOf(gasto("g1", 5000, LocalDate.of(2026, 8, 5))),
            ingresos = listOf(ingreso("i1", 1000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val resumen = useCase("plan-1", YearMonth.of(2026, 8)).first()

        assertEquals(-4000L, resumen.netoDelMes.unidadesMenores)
    }

    @Test
    fun `excluye movimientos fuera del mes solicitado`() = runTest {
        val repo = FakeMovimientoResumenRepository(
            gastos = listOf(gasto("g1", 1000, LocalDate.of(2026, 7, 30))),
            ingresos = listOf(ingreso("i1", 3000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val resumen = useCase("plan-1", YearMonth.of(2026, 8)).first()

        assertEquals(3000L, resumen.netoDelMes.unidadesMenores)
    }

    @Test
    fun `excluye gastos e ingresos borrados logicamente`() = runTest {
        val repo = FakeMovimientoResumenRepository(
            gastos = listOf(
                gasto("g1", 1000, LocalDate.of(2026, 8, 5)),
                gasto("g2", 700, LocalDate.of(2026, 8, 10), deletedAt = Instant.parse("2026-08-11T10:00:00Z")),
            ),
            ingresos = listOf(
                ingreso("i1", 3000, LocalDate.of(2026, 8, 1)),
                ingreso("i2", 500, LocalDate.of(2026, 8, 2), deletedAt = Instant.parse("2026-08-03T10:00:00Z")),
            ),
        )
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val resumen = useCase("plan-1", YearMonth.of(2026, 8)).first()

        assertEquals(2000L, resumen.netoDelMes.unidadesMenores)
    }

    @Test
    fun `actualiza reactivamente cuando el repositorio emite un nuevo gasto`() = runTest {
        val gastosFlow = MutableStateFlow(listOf(gasto("g1", 1000, LocalDate.of(2026, 8, 5))))
        val repo = FakeMovimientoResumenRepository(
            gastosFlow = gastosFlow,
            ingresos = listOf(ingreso("i1", 3000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarResumenMensualPlanUseCase(repo)

        val primero = useCase("plan-1", YearMonth.of(2026, 8)).first()
        assertEquals(2000L, primero.netoDelMes.unidadesMenores)

        gastosFlow.value = gastosFlow.value + gasto("g2", 500, LocalDate.of(2026, 8, 12))

        val segundo = useCase("plan-1", YearMonth.of(2026, 8)).first()
        assertEquals(1500L, segundo.netoDelMes.unidadesMenores)
    }

    private fun gasto(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: Instant? = null) = Gasto(
        id = id,
        planId = "plan-1",
        categoriaId = "cat-1",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    private fun ingreso(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: Instant? = null) = Ingreso(
        id = id,
        planId = "plan-1",
        categoriaId = "cat-2",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )
}

private class FakeMovimientoResumenRepository(
    gastos: List<Gasto> = emptyList(),
    ingresos: List<Ingreso> = emptyList(),
    private val gastosFlow: MutableStateFlow<List<Gasto>> = MutableStateFlow(gastos),
    private val ingresosFlow: MutableStateFlow<List<Ingreso>> = MutableStateFlow(ingresos),
) : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = gastosFlow
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = ingresosFlow
}
