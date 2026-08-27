package com.agoitdev.spenvo.movimientos

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
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
            observarResumenMensual = ObservarResumenMensualPlanUseCase(FakeMovimientoRepositorioHome()),
            observarBalanceAcumulado = ObservarBalanceAcumuladoPlanUseCase(FakeMovimientoRepositorioHome()),
        )

        val planFlow = viewModel.plan("p1")
        val resumenFlow = viewModel.resumenMensual("p1")
        val balanceFlow = viewModel.balanceAcumulado("p1")

        val planJob = launch { planFlow.collect {} }
        val resumenJob = launch { resumenFlow.collect {} }
        val balanceJob = launch { balanceFlow.collect {} }
        advanceUntilIdle()

        assertEquals(plan, planFlow.value)
        assertEquals(0L, resumenFlow.value?.netoDelMes?.unidadesMenores)
        assertEquals(0L, balanceFlow.value.unidadesMenores)

        planJob.cancel()
        resumenJob.cancel()
        balanceJob.cancel()
    }
}

private class FakePlanFinancieroRepositorioHome(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHome : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}
