package com.agoitdev.spenvo.categorias

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriasViewModelTest {

    private val repo = FakeCategoriaRepository()
    private val sincronizador = FakeCategoriaSincronizacion()
    private val authRepository = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(
        repositorio: FakeCategoriaRepository = repo,
        auth: AuthRepository = authRepository,
    ) = CategoriasViewModel(
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(repositorio),
        crearCategoria = CrearCategoriaUseCase(repositorio),
        actualizarCategoria = ActualizarCategoriaUseCase(repositorio),
        eliminarCategoria = EliminarCategoriaUseCase(repositorio),
        sincronizador = sincronizador,
        authRepository = auth,
    )

    @Test
    fun `sincronizar dispara la sincronizacion del plan`() = runTest {
        val viewModel = crearViewModel()

        viewModel.sincronizar("p1")
        advanceUntilIdle()

        assertEquals(listOf("p1"), sincronizador.planesSincronizados)
    }

    @Test
    fun `cambiar de plan resincroniza solo el nuevo plan`() = runTest {
        val viewModel = crearViewModel()

        viewModel.sincronizar("p1")
        advanceUntilIdle()
        viewModel.sincronizar("p2")
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), sincronizador.planesSincronizados)
    }

    @Test
    fun `crear categoria exitosa marca guardado`() = runTest {
        val viewModel = crearViewModel()

        viewModel.crear(planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO, icono = "comida")
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertNull(viewModel.estadoForm.value.error)
        assertEquals(1, repo.creadas.size)
    }

    @Test
    fun `crear categoria con nombre vacio expone el error del caso de uso`() = runTest {
        val viewModel = crearViewModel()

        viewModel.crear(planId = "p1", nombre = "   ", tipo = TipoCategoria.GASTO, icono = "comida")
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertFalse(viewModel.estadoForm.value.guardado)
        assertTrue(repo.creadas.isEmpty())
    }

    @Test
    fun `crear categoria duplicada expone el error del caso de uso`() = runTest {
        val repoConCategoria = FakeCategoriaRepository(
            categorias = listOf(Categoria(id = "c1", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO)),
        )
        val viewModel = crearViewModel(repoConCategoria)

        viewModel.crear(planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO, icono = "comida")
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertTrue(repoConCategoria.creadas.isEmpty())
    }

    @Test
    fun `actualizar categoria exitosa marca guardado`() = runTest {
        val viewModel = crearViewModel()
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO)

        viewModel.actualizar(categoria)
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertEquals(listOf("c1"), repo.actualizadas.map { it.id })
    }

    @Test
    fun `actualizar categoria estampa el uid de la sesion actual como editor`() = runTest {
        val viewModel = crearViewModel(auth = FakeAuthRepository(uid = "user-42"))
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO)

        viewModel.actualizar(categoria)
        advanceUntilIdle()

        assertEquals("user-42", repo.actualizadas.single().editedBy)
    }

    @Test
    fun `actualizar categoria con nombre vacio expone el error del caso de uso`() = runTest {
        val viewModel = crearViewModel()
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "   ", tipo = TipoCategoria.GASTO)

        viewModel.actualizar(categoria)
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertTrue(repo.actualizadas.isEmpty())
    }

    @Test
    fun `eliminar categoria delega en el caso de uso y marca guardado`() = runTest {
        val viewModel = crearViewModel()
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO)

        viewModel.eliminar(categoria)
        advanceUntilIdle()

        assertEquals(listOf("c1"), repo.eliminadas.map { it.id })
        assertTrue(viewModel.estadoForm.value.guardado)
    }

    @Test
    fun `eliminar categoria estampa el uid de la sesion actual como editor`() = runTest {
        val viewModel = crearViewModel(auth = FakeAuthRepository(uid = "user-42"))
        val categoria = Categoria(id = "c1", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO)

        viewModel.eliminar(categoria)
        advanceUntilIdle()

        assertEquals("user-42", repo.eliminadas.single().editedBy)
    }

    @Test
    fun `consumirError limpia el error y consumirGuardado limpia el flag`() = runTest {
        val viewModel = crearViewModel()
        viewModel.crear(planId = "p1", nombre = "", tipo = TipoCategoria.GASTO, icono = "comida")
        advanceUntilIdle()
        assertNotNull(viewModel.estadoForm.value.error)

        viewModel.consumirError()
        assertNull(viewModel.estadoForm.value.error)

        viewModel.crear(planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO, icono = "comida")
        advanceUntilIdle()
        assertTrue(viewModel.estadoForm.value.guardado)

        viewModel.consumirGuardado()
        assertFalse(viewModel.estadoForm.value.guardado)
    }
}

private class FakeCategoriaRepository(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    val creadas = mutableListOf<Categoria>()
    val actualizadas = mutableListOf<Categoria>()
    val eliminadas = mutableListOf<Categoria>()

    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf((categorias + creadas).filter { it.planId == planId })

    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf((categorias + creadas).filter { it.planId == planId && it.tipo == tipo })

    override suspend fun crearCategoria(categoria: Categoria) {
        creadas.add(categoria)
    }

    override suspend fun crearCategorias(categorias: List<Categoria>) {
        creadas.addAll(categorias)
    }

    override suspend fun actualizarCategoria(categoria: Categoria) {
        actualizadas.add(categoria)
    }

    override suspend fun eliminarCategoria(categoria: Categoria) {
        eliminadas.add(categoria)
    }
}

private class FakeCategoriaSincronizacion : CategoriaSincronizacion {
    val planesSincronizados = mutableListOf<String>()

    override fun sincronizar(planId: String): Flow<Unit> {
        planesSincronizados.add(planId)
        return flowOf(Unit)
    }
}

private class FakeAuthRepository(private val uid: String = "user-1") : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = uid, esAnonima = false))

    override suspend fun iniciarSesionAnonima() = Unit

    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
}
