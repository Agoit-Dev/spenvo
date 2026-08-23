package com.agoitdev.spenvo.planes

import com.agoitdev.spenvo.data.remote.sync.PlanSincronizacion
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarCategoriasPorDefectoUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanesViewModelTest {

    private val planesFlow = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    private val planFinancieroRepo = FakePlanFinancieroRepository(planesFlow)
    private val accesoPlanRepo = FakeAccesoPlanRepository()
    private val categoriaRepo = FakeCategoriaRepository()
    private val movimientoRepo = FakeMovimientoRepository()
    private val sincronizador = FakePlanSincronizacion()
    private val authRepository = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = PlanesViewModel(
        crearPlan = CrearPlanUseCase(
            planFinancieroRepo,
            accesoPlanRepo,
            SembrarCategoriasPorDefectoUseCase(categoriaRepo),
        ),
        observarPlanes = ObservarPlanesDelUsuarioUseCase(planFinancieroRepo),
        aceptarInvitacion = AceptarInvitacionUseCase(accesoPlanRepo),
        sincronizador = sincronizador,
        accesosRepository = accesoPlanRepo,
        observarResumenMensualPlan = ObservarResumenMensualPlanUseCase(movimientoRepo),
        authRepository = authRepository,
    )

    private fun plan(id: String, nombre: String = "Plan $id") = PlanFinanciero(
        id = id,
        nombre = nombre,
        moneda = "EUR",
        createdBy = "user-1",
    )

    private fun gasto(planId: String, montoMenor: Long, fecha: LocalDate) = Gasto(
        id = "g-$planId-$montoMenor",
        planId = planId,
        categoriaId = "cat-1",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
    )

    private fun ingreso(planId: String, montoMenor: Long, fecha: LocalDate) = Ingreso(
        id = "i-$planId-$montoMenor",
        planId = planId,
        categoriaId = "cat-2",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
    )

    @Test
    fun `resumenesPorPlan esta vacio cuando no hay planes`() = runTest {
        val viewModel = crearViewModel()

        val job = launch { viewModel.resumenesPorPlan.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.resumenesPorPlan.value.isEmpty())
        job.cancel()
    }

    @Test
    fun `resumenesPorPlan combina un resumen por cada plan del usuario`() = runTest {
        movimientoRepo.gastosPorPlan["p1"] = listOf(gasto("p1", 1000, LocalDate.of(2026, 8, 5)))
        movimientoRepo.ingresosPorPlan["p1"] = listOf(ingreso("p1", 3000, LocalDate.of(2026, 8, 1)))
        movimientoRepo.gastosPorPlan["p2"] = listOf(gasto("p2", 500, LocalDate.of(2026, 8, 5)))
        movimientoRepo.ingresosPorPlan["p2"] = listOf(ingreso("p2", 200, LocalDate.of(2026, 8, 1)))
        planesFlow.value = listOf(plan("p1"), plan("p2"))
        val viewModel = crearViewModel()

        val job = launch { viewModel.resumenesPorPlan.collect {} }
        advanceUntilIdle()

        val resumenes = viewModel.resumenesPorPlan.value
        assertEquals(setOf("p1", "p2"), resumenes.keys)
        assertEquals(2000L, resumenes.getValue("p1").netoDelMes.unidadesMenores)
        assertEquals(-300L, resumenes.getValue("p2").netoDelMes.unidadesMenores)
        job.cancel()
    }

    @Test
    fun `resumenesPorPlan se recombina cuando cambia la lista de planes`() = runTest {
        movimientoRepo.gastosPorPlan["p1"] = listOf(gasto("p1", 1000, LocalDate.of(2026, 8, 5)))
        movimientoRepo.ingresosPorPlan["p1"] = listOf(ingreso("p1", 3000, LocalDate.of(2026, 8, 1)))
        planesFlow.value = listOf(plan("p1"))
        val viewModel = crearViewModel()

        val job = launch { viewModel.resumenesPorPlan.collect {} }
        advanceUntilIdle()
        assertEquals(setOf("p1"), viewModel.resumenesPorPlan.value.keys)

        movimientoRepo.gastosPorPlan["p2"] = listOf(gasto("p2", 100, LocalDate.of(2026, 8, 5)))
        movimientoRepo.ingresosPorPlan["p2"] = listOf(ingreso("p2", 900, LocalDate.of(2026, 8, 1)))
        planesFlow.value = listOf(plan("p1"), plan("p2"))
        advanceUntilIdle()

        val resumenes = viewModel.resumenesPorPlan.value
        assertEquals(setOf("p1", "p2"), resumenes.keys)
        assertEquals(800L, resumenes.getValue("p2").netoDelMes.unidadesMenores)
        job.cancel()
    }
}

private class FakePlanFinancieroRepository(
    private val planesFlow: MutableStateFlow<List<PlanFinanciero>>,
) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = planesFlow
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(planesFlow.value.find { it.id == planId })
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planesFlow.value = planesFlow.value + plan
    }
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeAccesoPlanRepository : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeCategoriaRepository : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoRepository : MovimientoRepository {
    val gastosPorPlan = mutableMapOf<String, List<Gasto>>()
    val ingresosPorPlan = mutableMapOf<String, List<Ingreso>>()

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(gastosPorPlan[planId].orEmpty())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(ingresosPorPlan[planId].orEmpty())
}

private class FakePlanSincronizacion : PlanSincronizacion {
    val usuariosSincronizados = mutableListOf<String>()
    override fun sincronizar(usuarioId: String): Flow<Unit> {
        usuariosSincronizados.add(usuarioId)
        return flowOf(Unit)
    }
}

private class FakeAuthRepository : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
