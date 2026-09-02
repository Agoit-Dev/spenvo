package com.agoitdev.spenvo.planes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agoitdev.spenvo.data.remote.sync.PlanSincronizacion
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_IMAGEN
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
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
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarCategoriasPorDefectoUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarPlanEjemploUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class PlanesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plan = PlanFinanciero(
        id = "p1",
        nombre = "Casa",
        moneda = "EUR",
        createdBy = "user-1",
    )

    private val sesionFlow = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true))
    private val planesFlow = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    private val planFinancieroRepo = FakePlanFinancieroRepositorioScreen(planesFlow)
    private val accesoPlanRepo = FakeAccesoPlanRepositorioScreen()
    private val categoriaRepo = FakeCategoriaRepositorioScreen()
    private val movimientoRepo = FakeMovimientoRepositorioScreen()
    private val sincronizador = FakePlanSincronizacionScreen()
    private val authRepository = FakeAuthRepositorioScreen(sesionFlow)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        sembrarPlanEjemplo = SembrarPlanEjemploUseCase(
            observarPlanes = ObservarPlanesDelUsuarioUseCase(
                FakePlanFinancieroRepositorioScreen(MutableStateFlow(listOf(plan))),
            ),
            crearPlan = CrearPlanUseCase(
                FakePlanFinancieroRepositorioScreen(MutableStateFlow(emptyList())),
                FakeAccesoPlanRepositorioScreen(),
                SembrarCategoriasPorDefectoUseCase(FakeCategoriaRepositorioScreen()),
            ),
            crearGasto = CrearGastoUseCase(FakeMovimientoRepositorioScreen(), ValidarMontoUseCase()),
            crearIngreso = CrearIngresoUseCase(FakeMovimientoRepositorioScreen(), ValidarMontoUseCase()),
        ),
        asegurarUsuario = AsegurarUsuarioUseCase(
            FakeUsuarioRepositorioScreen(),
            GenerarNombreUsuarioUnicoUseCase(FakeUsuarioRepositorioScreen()),
            accesoPlanRepo,
            FakePendientesRepositorioScreen(),
        ),
        authRepository = authRepository,
    )

    @Test
    fun `muestra un balance positivo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(
                    planId = "p1",
                    ingresosMes = Monto(2000),
                    gastosMes = Monto(0),
                ),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("+20,00 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `muestra un balance negativo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(
                    planId = "p1",
                    ingresosMes = Monto(0),
                    gastosMes = Monto(4000),
                ),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("-40,00 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `muestra el spinner en el area de lista mientras carga, sin ocultar la topbar ni el FAB`() {
        sesionFlow.value = Sesion.Anonima // never resolves -> cargandoLista stays true
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_PLANES_CARGANDO).assertIsDisplayed()
        composeTestRule.onNodeWithText("Planes").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Nuevo plan").assertIsDisplayed()
    }

    @Test
    fun `el spinner tiene descripcion accesible`() {
        sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithContentDescription("Cargando planes").assertIsDisplayed()
    }

    @Test
    fun `muestra la lista real una vez que la carga termina`() {
        planesFlow.value = listOf(plan)
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_PLANES_CARGANDO).assertDoesNotExist()
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
    }

    @Test
    fun `UX-H903 el menu de cuenta muestra el avatar real cuando la sesion tiene foto`() {
        sesionFlow.value = Sesion(
            uid = "user-1",
            esAnonima = false,
            email = "familia@example.com",
            photoUrl = "https://example.com/avatar.jpg",
        )
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_IMAGEN, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `UX-H903 el menu de cuenta muestra el placeholder cuando la sesion no tiene foto`() {
        sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = "familia@example.com")
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_TOPBAR_IMAGEN, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `muestra el estado vacio real cuando la carga termino sin planes`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag(TAG_PLANES_CARGANDO).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "Todavía no hay planes. Toca + para crear tu primer plan.",
        ).assertIsDisplayed()
    }
}

private class FakePlanFinancieroRepositorioScreen(
    private val planesFlow: MutableStateFlow<List<PlanFinanciero>>,
) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = planesFlow
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(planesFlow.value.find { it.id == planId })
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planesFlow.value = planesFlow.value + plan
    }
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeAccesoPlanRepositorioScreen(
    private val accesosFlow: MutableStateFlow<List<AccesoPlan>> = MutableStateFlow(emptyList()),
) : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = accesosFlow
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeCategoriaRepositorioScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoRepositorioScreen : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}

private class FakePlanSincronizacionScreen : PlanSincronizacion {
    override fun sincronizar(usuarioId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioScreen(
    private val sesionFlow: MutableStateFlow<Sesion> = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true)),
) : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}

private class FakeUsuarioRepositorioScreen : UsuarioRepository {
    private val usuarios = mutableMapOf<String, Usuario>()

    override suspend fun obtener(usuarioId: String): Usuario? = usuarios[usuarioId]
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        usuarioIds.mapNotNull { usuarios[it] }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) {
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

private class FakePendientesRepositorioScreen : InvitacionPendienteRepository {
    override suspend fun crear(invitacion: InvitacionPendiente) = Unit
    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}
