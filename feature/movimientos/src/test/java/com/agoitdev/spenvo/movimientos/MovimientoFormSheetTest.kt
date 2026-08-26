package com.agoitdev.spenvo.movimientos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
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
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class MovimientoFormSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movimientoRepo = FakeMovimientoRepositorioForm()
    private val categoriaRepo = FakeCategoriaRepositorioForm()
    private val sincronizador = FakeMovimientoSincronizacionForm()
    private val authRepository = FakeAuthRepositorioForm()

    private val gasto = Gasto(
        id = "g1",
        planId = "p1",
        categoriaId = "cat-transporte",
        monto = Monto(1500),
        fecha = LocalDate.of(2026, 8, 22),
        creadoPor = "user-1",
    )

    private val categoriasGasto = listOf(
        Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        Categoria(id = "cat-transporte", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO),
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
        conflictosPendientes = ConflictosPendientes(),
    )

    private fun montarFormulario(
        movimientoExistente: Movimiento? = null,
        onEliminar: (() -> Unit)? = null,
        onGuardar: (MovimientoFormDatos) -> Unit = {},
    ) {
        val viewModel = crearViewModel()
        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = movimientoExistente,
                acciones = MovimientoFormAcciones(
                    onGuardar = onGuardar,
                    onDismiss = {},
                    onEliminar = onEliminar,
                ),
            )
        }
    }

    @Test
    fun `categoria guardada se mantiene aunque la lista de categorias llegue vacia primero`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(
            movimientoExistente = gasto,
            onEliminar = {},
            onGuardar = { guardado = it },
        )

        // Real data arrives after the initial empty StateFlow value -- the exact race the bug depends on.
        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-transporte", guardado?.categoriaId)
    }

    @Test
    fun `cambiar de tipo al crear limpia la categoria del tipo anterior`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        composeTestRule.waitForIdle()
        // categoriaId is now auto-selected to "cat-comida" (GASTO).

        composeTestRule.onNodeWithText("Ingresos").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        // Switching tipo must clear the GASTO category -- there is no INGRESO category yet, so
        // Guardar must be blocked, not silently succeed with a GASTO category id under an Ingreso.
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals(null, guardado)
    }

    // Checks that an existing movimiento's stored category id, when it's no longer present in the
    // loaded category list, falls back to the first available category instead of staying invalid.
    @Test
    fun `categoria guardada que ya no existe en la lista cae en la primera`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(
            movimientoExistente = Gasto(
                id = "cat-borrada",
                planId = "p1",
                categoriaId = "cat-borrada",
                monto = Monto(1000),
                fecha = LocalDate.of(2026, 8, 22),
                creadoPor = "user-1",
            ),
            onEliminar = {},
            onGuardar = { guardado = it },
        )

        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    // Complementary to the previous test: here categoriaId starts blank (a genuinely new
    // movimiento), not pointing at a since-deleted category.
    @Test
    fun `movimiento nuevo selecciona la primera categoria cuando la lista llega`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    @Test
    fun `re-tocar el chip ya seleccionado no borra la categoria elegida`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Gastos").performClick() // already selected -- must be a no-op
        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    @Test
    fun `chips de tipo estan deshabilitados al editar un movimiento existente`() {
        montarFormulario(movimientoExistente = gasto, onEliminar = {})

        // Disabled AND still showing the real type as selected -- selected/enabled are independent
        // semantics, so both must be asserted to actually pin "which type is this" while locked.
        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed().assertIsNotEnabled().assertIsSelected()
        composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed().assertIsNotEnabled().assertIsNotSelected()
    }

    @Test
    fun `chips de tipo estan habilitados al crear un movimiento nuevo`() {
        montarFormulario()

        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed().assertIsEnabled()
    }
}

private class FakeMovimientoRepositorioForm : MovimientoRepository {
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

private class FakeCategoriaRepositorioForm : CategoriaRepository {
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = categorias
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        categorias.map { cats -> cats.filter { it.tipo == tipo } }
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionForm : MovimientoSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioForm : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
