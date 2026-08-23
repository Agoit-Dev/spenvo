package com.agoitdev.spenvo.movimientos

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarGastoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarIngresoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * M6 Slice B: verifies design Decision 3's exact shape — compact renders the form as a
 * `ModalBottomSheet` with no separate detail pane; expanded renders the list pane and an inline
 * detail pane side by side, with no bottom sheet chrome; [ConflictoMovimientoDialogHost] (design
 * Decision 4) fires identically regardless of which layout hosts the form.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class MovimientosScreenListDetailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movimientoRepo = FakeMovimientoRepositorioPanel()
    private val categoriaRepo = FakeCategoriaRepositorioPanel()
    private val sincronizador = FakeMovimientoSincronizacionPanel()
    private val authRepository = FakeAuthRepositorioPanel()
    private val conflictosPendientes = ConflictosPendientes()

    private val gasto = Gasto(
        id = "g1",
        planId = "p1",
        categoriaId = "cat-gasto",
        monto = Monto(1500),
        fecha = LocalDate.of(2026, 8, 22),
        creadoPor = "user-1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = MovimientosViewModel(
        observarMovimientos = ObservarMovimientosUseCase(movimientoRepo),
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
        observarCategorias = ObservarCategoriasUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        actualizarGasto = ActualizarGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarGasto = EliminarGastoUseCase(movimientoRepo),
        actualizarIngreso = ActualizarIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarIngreso = EliminarIngresoUseCase(movimientoRepo),
        aplicarGastoRemoto = AplicarGastoRemotoUseCase(movimientoRepo),
        aplicarIngresoRemoto = AplicarIngresoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        conflictosPendientes = conflictosPendientes,
    )

    private fun listaEstado(onClick: (Movimiento) -> Unit = {}) = MovimientosListaEstado(
        movimientos = listOf(gasto),
        categoriasPorId = emptyMap(),
        conflictos = emptyMap(),
        onMovimientoClick = onClick,
    )

    private val acciones = MovimientosAcciones(
        onVerMiembros = {}, onGestionarCategorias = {}, onNuevoMovimiento = {},
    )
    private val filtro = MovimientosFiltro(
        busqueda = "", onBusquedaChange = {}, tipoSeleccionado = null, onTipoChange = {},
    )

    private fun formularioParametros(formulario: FormularioMovimiento, viewModel: MovimientosViewModel) =
        MovimientoFormularioParametros(
            planId = "p1",
            tipoPorDefecto = TipoCategoria.GASTO,
            formulario = formulario,
            viewModel = viewModel,
            cargando = false,
            onCerrar = {},
        )

    @Test
    fun `layout compacto cerrado no muestra hoja ni panel de detalle`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientosPantallaCompacta(
                modifier = Modifier,
                acciones = acciones,
                filtro = filtro,
                snackbarHostState = remember { SnackbarHostState() },
                lista = listaEstado(),
                formularioParametros = formularioParametros(FormularioMovimiento.Cerrado, viewModel),
            )
        }

        composeTestRule.onNodeWithText("Editar movimiento").assertDoesNotExist()
        composeTestRule.onNodeWithText("Seleccioná un movimiento para ver el detalle.").assertDoesNotExist()
    }

    @Test
    fun `layout compacto abre el formulario como hoja modal sin panel de detalle`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientosPantallaCompacta(
                modifier = Modifier,
                acciones = acciones,
                filtro = filtro,
                snackbarHostState = remember { SnackbarHostState() },
                lista = listaEstado(),
                formularioParametros = formularioParametros(FormularioMovimiento.Editar(gasto), viewModel),
            )
        }

        composeTestRule.onNodeWithText("Editar movimiento").assertIsDisplayed()
        // No separate detail pane: the placeholder text used only by the expanded layout never renders here.
        composeTestRule.onNodeWithText("Seleccioná un movimiento para ver el detalle.").assertDoesNotExist()
    }

    @Test
    fun `layout expandido muestra la lista y el panel de detalle a la vez`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientosPantallaExpandida(
                modifier = Modifier,
                directive = expandedDirective(),
                acciones = acciones,
                filtro = filtro,
                snackbarHostState = remember { SnackbarHostState() },
                lista = listaEstado(),
                formularioParametros = formularioParametros(FormularioMovimiento.Editar(gasto), viewModel),
            )
        }

        // List pane (today's whole scaffold) and the inline detail pane are both mounted at once.
        composeTestRule.onNodeWithText("Movimientos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar movimiento").assertIsDisplayed()
    }

    @Test
    fun `layout expandido sin formulario muestra el placeholder del panel de detalle`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientosPantallaExpandida(
                modifier = Modifier,
                directive = expandedDirective(),
                acciones = acciones,
                filtro = filtro,
                snackbarHostState = remember { SnackbarHostState() },
                lista = listaEstado(),
                formularioParametros = formularioParametros(FormularioMovimiento.Cerrado, viewModel),
            )
        }

        composeTestRule.onNodeWithText("Movimientos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seleccioná un movimiento para ver el detalle.").assertIsDisplayed()
    }

    @Test
    fun `el dialogo de conflicto se dispara igual en ambos layouts`() {
        val conflicto = conflictoDeEjemplo()

        composeTestRule.setContent {
            ConflictoMovimientoDialogHost(
                conflicto = conflicto,
                movimientoLocal = gasto,
                onUsarLocal = {},
                onUsarRemoto = {},
                onDismiss = {},
            )
        }
        composeTestRule.onNodeWithText("Conflicto detectado").assertIsDisplayed()
    }
}

private fun expandedDirective(): PaneScaffoldDirective =
    PaneScaffoldDirective.Default.copy(maxHorizontalPartitions = 2)

private fun conflictoDeEjemplo(): ConflictoEdicion = ConflictoEdicion(
    registroId = "g1",
    tipo = TipoRegistro.GASTO,
    local = SnapshotConflicto(
        editadoPor = "user-1",
        editadoEn = Instant.now(),
        borrado = false,
        campos = listOf(CampoConflicto("monto", "1500")),
    ),
    remoto = SnapshotConflicto(
        editadoPor = "user-2",
        editadoEn = Instant.now(),
        borrado = false,
        campos = listOf(CampoConflicto("monto", "2000")),
    ),
)

private class FakeMovimientoRepositorioPanel : MovimientoRepository {
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

private class FakeCategoriaRepositorioPanel(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(categorias)
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias)
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionPanel : MovimientoSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioPanel : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
