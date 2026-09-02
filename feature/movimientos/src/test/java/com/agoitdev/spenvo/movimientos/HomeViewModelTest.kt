package com.agoitdev.spenvo.movimientos

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val plan = PlanFinanciero(
        id = "p1",
        nombre = "Casa",
        moneda = "EUR",
        createdBy = "user-1",
    )

    private val hoy = LocalDate.now()
    private val mesAnterior = hoy.minusYears(1)

    // One shared repository so the resumen and the balance use cases observe the SAME data.
    // The out-of-month gasto is what makes balance (500) differ from netoDelMes (1500): with only
    // in-month movimientos both figures would collapse to the same number and a wiring swap
    // between the two use cases would go unnoticed.
    private val movimientoRepo = FakeMovimientoRepositorioHome(
        gastos = listOf(
            gasto(id = "g-mes", unidadesMenores = 2_500, fecha = hoy),
            gasto(id = "g-viejo", unidadesMenores = 1_000, fecha = mesAnterior),
        ),
        ingresos = listOf(
            ingreso(id = "i-mes", unidadesMenores = 4_000, fecha = hoy),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `expone el plan, el resumen del mes y el balance acumulado`() = runTest {
        val viewModel = HomeViewModel(
            observarPlan = ObservarPlanUseCase(FakePlanFinancieroRepositorioHome(plan)),
            observarResumenMensual = ObservarResumenMensualPlanUseCase(movimientoRepo),
            observarBalanceAcumulado = ObservarBalanceAcumuladoPlanUseCase(movimientoRepo),
        )

        val planFlow = viewModel.plan("p1")
        val resumenFlow = viewModel.resumenMensual("p1")
        val balanceFlow = viewModel.balanceAcumulado("p1")

        val planJob = launch { planFlow.collect {} }
        val resumenJob = launch { resumenFlow.collect {} }
        val balanceJob = launch { balanceFlow.collect {} }
        advanceUntilIdle()

        assertEquals(plan, planFlow.value)
        assertEquals(4_000L, resumenFlow.value?.ingresosMes?.unidadesMenores)
        assertEquals(2_500L, resumenFlow.value?.gastosMes?.unidadesMenores)
        assertEquals(1_500L, resumenFlow.value?.netoDelMes?.unidadesMenores)
        // Accumulated balance spans every month: 4000 - (2500 + 1000).
        assertEquals(500L, balanceFlow.value.unidadesMenores)

        planJob.cancel()
        resumenJob.cancel()
        balanceJob.cancel()
    }
}

private fun gasto(id: String, unidadesMenores: Long, fecha: LocalDate) = Gasto(
    id = id,
    planId = "p1",
    categoriaId = "cat-1",
    monto = Monto(unidadesMenores),
    fecha = fecha,
    creadoPor = "user-1",
)

private fun ingreso(id: String, unidadesMenores: Long, fecha: LocalDate) = Ingreso(
    id = id,
    planId = "p1",
    categoriaId = "cat-2",
    monto = Monto(unidadesMenores),
    fecha = fecha,
    creadoPor = "user-1",
)

private class FakePlanFinancieroRepositorioHome(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHome(
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
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastos.filter { it.planId == planId })
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresos.filter { it.planId == planId })
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}
