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
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.AsegurarUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarCategoriasPorDefectoUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarPlanEjemploUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanesViewModelTest {

    private val sesionFlow = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true))
    private val accesosFlow = MutableStateFlow<List<AccesoPlan>>(emptyList())
    private val planesFlow = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    private val planFinancieroRepo = FakePlanFinancieroRepository(planesFlow)
    private val accesoPlanRepo = FakeAccesoPlanRepository(accesosFlow)
    private val categoriaRepo = FakeCategoriaRepository()
    private val movimientoRepo = FakeMovimientoRepository()
    private val sincronizador = FakePlanSincronizacion()
    private val authRepository = FakeAuthRepository(sesionFlow)
    private val usuarioRepo = FakeUsuarioRepository()
    private val pendientesRepo = FakePendientesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(
        accesosRepo: AccesoPlanRepository = accesoPlanRepo,
        asegurarUsuario: AsegurarUsuarioUseCase = AsegurarUsuarioUseCase(
            usuarioRepo,
            GenerarNombreUsuarioUnicoUseCase(usuarioRepo),
            accesosRepo,
            pendientesRepo,
        ),
    ) = PlanesViewModel(
        crearPlan = CrearPlanUseCase(
            planFinancieroRepo,
            accesosRepo,
            SembrarCategoriasPorDefectoUseCase(categoriaRepo),
        ),
        observarPlanes = ObservarPlanesDelUsuarioUseCase(planFinancieroRepo),
        aceptarInvitacion = AceptarInvitacionUseCase(accesosRepo),
        sincronizador = sincronizador,
        accesosRepository = accesosRepo,
        observarResumenMensualPlan = ObservarResumenMensualPlanUseCase(movimientoRepo),
        // Isolated fakes pre-seeded with a plan, so the guard always sees "already has a
        // plan" and never touches the shared repos the tests actually assert on.
        sembrarPlanEjemplo = SembrarPlanEjemploUseCase(
            observarPlanes = ObservarPlanesDelUsuarioUseCase(
                FakePlanFinancieroRepository(MutableStateFlow(listOf(plan("seed-guard")))),
            ),
            crearPlan = CrearPlanUseCase(
                FakePlanFinancieroRepository(MutableStateFlow(emptyList())),
                FakeAccesoPlanRepository(),
                SembrarCategoriasPorDefectoUseCase(FakeCategoriaRepository()),
            ),
            crearGasto = com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase(
                FakeMovimientoRepository(),
                com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase(),
            ),
            crearIngreso = com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase(
                FakeMovimientoRepository(),
                com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase(),
            ),
        ),
        asegurarUsuario = asegurarUsuario,
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

    @Test
    fun `cargandoLista arranca en true y pasa a false una vez que planes e invitaciones resuelven`() = runTest {
        val viewModel = crearViewModel()

        val job = launch { viewModel.cargandoLista.collect {} }
        assertTrue(viewModel.cargandoLista.value)
        advanceUntilIdle()
        assertFalse(viewModel.cargandoLista.value)
        job.cancel()
    }

    @Test
    fun `cargandoLista permanece en true mientras la sesion no tenga uid`() = runTest {
        sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()

        val job = launch { viewModel.cargandoLista.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.cargandoLista.value)
        job.cancel()
    }

    @Test
    fun `planes conserva la lista real y no queda pegado al valor inicial del StateFlow`() = runTest {
        val planP1 = plan("p1")
        planesFlow.value = listOf(planP1)
        val viewModel = crearViewModel()

        val job = launch { viewModel.planes.collect {} }
        val cargandoListaJob = launch { viewModel.cargandoLista.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(planP1), viewModel.planes.value)
        assertFalse(viewModel.cargandoLista.value)
        job.cancel()
        cargandoListaJob.cancel()
    }

    @Test
    fun `fallo en asegurarUsuario durante el bootstrap se loguea y no bloquea el resto del init`() = runTest {
        ShadowLog.reset()
        val usuarioRepoQueFalla = FakeUsuarioRepository(excepcionAlObtener = IllegalStateException("PERMISSION_DENIED"))
        val asegurarUsuarioQueFalla = AsegurarUsuarioUseCase(
            usuarioRepoQueFalla,
            GenerarNombreUsuarioUnicoUseCase(usuarioRepoQueFalla),
            accesoPlanRepo,
            pendientesRepo,
        )
        val viewModel = crearViewModel(asegurarUsuario = asegurarUsuarioQueFalla)

        val job = launch { viewModel.cargandoLista.collect {} }
        advanceUntilIdle()

        // No debe crashear ni bloquear el resto del bootstrap del init (M5/asegurarUsuario es
        // best-effort), pero el fallo tampoco puede quedar invisible como antes de este fix.
        assertFalse(viewModel.cargandoLista.value)
        val logueado = ShadowLog.getLogs().any { it.type == android.util.Log.ERROR && it.tag == "PlanesViewModel" }
        assertTrue("esperaba un Log.e con tag PlanesViewModel tras el fallo de asegurarUsuario", logueado)
        job.cancel()
    }

    @Test
    fun `cargandoLista sigue en true si invitaciones no resolvio aunque planes ya lo hizo`() = runTest {
        val accesosSinResolver = MutableSharedFlow<List<AccesoPlan>>()
        val accesoPlanRepoSinResolver = FakeAccesoPlanRepository(accesosSinResolver)
        val planP1 = plan("p1")
        planesFlow.value = listOf(planP1)
        val viewModel = crearViewModel(accesosRepo = accesoPlanRepoSinResolver)

        val job = launch { viewModel.cargandoLista.collect {} }
        val planesJob = launch { viewModel.planes.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(planP1), viewModel.planes.value)
        assertTrue(viewModel.cargandoLista.value)
        job.cancel()
        planesJob.cancel()
    }

    @Test
    fun `asegura el Usuario del uid una vez que la sesion anonima resuelve`() = runTest {
        crearViewModel()

        advanceUntilIdle()

        assertEquals(listOf("user-1"), usuarioRepo.creados.map { it.id })
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

private class FakeAccesoPlanRepository(
    private val accesosFlow: Flow<List<AccesoPlan>> = MutableStateFlow(emptyList()),
) : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = accesosFlow
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

private class FakeAuthRepository(
    private val sesionFlow: MutableStateFlow<Sesion> = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true)),
) : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}

private class FakeUsuarioRepository(
    private val excepcionAlObtener: Throwable? = null,
) : UsuarioRepository {
    private val usuarios = mutableMapOf<String, Usuario>()
    val creados = mutableListOf<Usuario>()

    override suspend fun obtener(usuarioId: String): Usuario? {
        excepcionAlObtener?.let { throw it }
        return usuarios[usuarioId]
    }
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        usuarioIds.mapNotNull { usuarios[it] }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) {
        creados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun actualizar(usuario: Usuario) {
        usuarios[usuario.id] = usuario
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean = true

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

private class FakePendientesRepository : InvitacionPendienteRepository {
    override suspend fun crear(invitacion: InvitacionPendiente) = Unit
    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}
