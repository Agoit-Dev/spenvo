package com.agoitdev.spenvo.categorias

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
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
class CategoriasScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tocar el avatar de la topbar invoca onAbrirCuenta`() {
        var invocado = false
        val categoriaRepo = FakeCategoriaRepositorioCategoriasScreen()
        val viewModel = CategoriasViewModel(
            observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
            crearCategoria = CrearCategoriaUseCase(categoriaRepo),
            actualizarCategoria = ActualizarCategoriaUseCase(categoriaRepo),
            eliminarCategoria = EliminarCategoriaUseCase(categoriaRepo),
            sincronizador = FakeCategoriaSincronizacionCategoriasScreen(),
            authRepository = FakeAuthRepositorioCategoriasScreen(),
        )

        composeTestRule.setContent {
            CategoriasScreen(
                planId = "p1",
                avatarUrl = null,
                onAbrirCuenta = { invocado = true },
                viewModel = viewModel,
            )
        }

        // The placeholder's testTag lives on the IconButton's content, which TopAppBar's actions
        // Row merges into a single accessibility node -- performClick() needs the unmerged tree to
        // still address it by its own tag.
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        assertEquals(true, invocado)
    }
}

private class FakeCategoriaRepositorioCategoriasScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeCategoriaSincronizacionCategoriasScreen : CategoriaSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioCategoriasScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
