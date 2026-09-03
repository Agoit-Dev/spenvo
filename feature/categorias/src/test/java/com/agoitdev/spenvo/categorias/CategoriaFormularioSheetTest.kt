package com.agoitdev.spenvo.categorias

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.CrearCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class CategoriaFormularioSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val categoria = Categoria(
        id = "cat-comida",
        planId = "p1",
        nombre = "Comida",
        tipo = TipoCategoria.GASTO,
    )
    private val repo = FakeCategoriaRepositorioSheet(listOf(categoria))
    private val sincronizador = FakeCategoriaSincronizacionSheet()
    private val authRepository = FakeAuthRepositorioSheet()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = CategoriasViewModel(
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(repo),
        crearCategoria = CrearCategoriaUseCase(repo),
        actualizarCategoria = ActualizarCategoriaUseCase(repo),
        eliminarCategoria = EliminarCategoriaUseCase(repo),
        sincronizador = sincronizador,
        authRepository = authRepository,
    )

    @Test
    fun `eliminar categoria abre el dialogo compartido y confirma el borrado`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            CategoriasScreen(
                planId = "p1",
                avatarUrl = null,
                onAbrirCuenta = {},
                onAbrirAjustes = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Comida").performClick()
        composeTestRule.onNodeWithText("Eliminar").performClick()

        composeTestRule.onNodeWithText("Eliminar categoría").assertIsDisplayed()
        composeTestRule.onNodeWithText("Esta acción no se puede deshacer.").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Eliminar")[1].performClick()

        composeTestRule.onNodeWithText("Eliminar categoría").assertDoesNotExist()
        assertEquals(listOf(categoria.id), repo.eliminadas.map { it.id })
    }
}

private class FakeCategoriaRepositorioSheet(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    val eliminadas = mutableListOf<Categoria>()
    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId })
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId && it.tipo == tipo })
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) {
        eliminadas.add(categoria)
    }
}

private class FakeCategoriaSincronizacionSheet : CategoriaSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioSheet : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = false))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
}
