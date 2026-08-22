package com.agoitdev.spenvo.movimientos

import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.LocalDate
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
class MovimientosViewModelTest {

    private val movimientoRepo = FakeMovimientoRepository()
    private val categoriaRepo = FakeCategoriaRepository(
        categorias = listOf(
            Categoria(id = "cat-gasto", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            Categoria(id = "cat-ingreso", planId = "p1", nombre = "Sueldo", tipo = TipoCategoria.INGRESO),
        ),
    )
    private val sincronizador = FakeMovimientoSincronizacion()
    private val authRepository = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = MovimientosViewModel(
        observarMovimientos = ObservarMovimientosUseCase(movimientoRepo),
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        sincronizador = sincronizador,
        authRepository = authRepository,
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
    fun `guardar un gasto exitoso marca guardado`() = runTest {
        val viewModel = crearViewModel()

        viewModel.guardar(
            MovimientoFormDatos(
                planId = "p1",
                tipo = TipoCategoria.GASTO,
                categoriaId = "cat-gasto",
                monto = Monto(1500),
                fecha = LocalDate.of(2026, 8, 22),
                descripcion = "Supermercado",
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertNull(viewModel.estadoForm.value.error)
        assertEquals(1, movimientoRepo.gastosCreados.size)
        assertEquals("user-1", movimientoRepo.gastosCreados.single().creadoPor)
    }

    @Test
    fun `guardar un ingreso exitoso marca guardado`() = runTest {
        val viewModel = crearViewModel()

        viewModel.guardar(
            MovimientoFormDatos(
                planId = "p1",
                tipo = TipoCategoria.INGRESO,
                categoriaId = "cat-ingreso",
                monto = Monto(200000),
                fecha = LocalDate.of(2026, 8, 22),
                descripcion = null,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertEquals(1, movimientoRepo.ingresosCreados.size)
    }

    @Test
    fun `guardar con monto invalido expone el error del caso de uso`() = runTest {
        val viewModel = crearViewModel()

        viewModel.guardar(
            MovimientoFormDatos(
                planId = "p1",
                tipo = TipoCategoria.GASTO,
                categoriaId = "cat-gasto",
                monto = Monto(0),
                fecha = LocalDate.of(2026, 8, 22),
                descripcion = null,
            ),
        )
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertFalse(viewModel.estadoForm.value.guardado)
        assertTrue(movimientoRepo.gastosCreados.isEmpty())
    }

    @Test
    fun `consumirError limpia el error y consumirGuardado limpia el flag`() = runTest {
        val viewModel = crearViewModel()
        viewModel.guardar(
            MovimientoFormDatos(
                planId = "p1",
                tipo = TipoCategoria.GASTO,
                categoriaId = "cat-gasto",
                monto = Monto(0),
                fecha = LocalDate.of(2026, 8, 22),
                descripcion = null,
            ),
        )
        advanceUntilIdle()
        assertNotNull(viewModel.estadoForm.value.error)

        viewModel.consumirError()
        assertNull(viewModel.estadoForm.value.error)

        viewModel.guardar(
            MovimientoFormDatos(
                planId = "p1",
                tipo = TipoCategoria.GASTO,
                categoriaId = "cat-gasto",
                monto = Monto(500),
                fecha = LocalDate.of(2026, 8, 22),
                descripcion = null,
            ),
        )
        advanceUntilIdle()
        assertTrue(viewModel.estadoForm.value.guardado)

        viewModel.consumirGuardado()
        assertFalse(viewModel.estadoForm.value.guardado)
    }
}

private class FakeMovimientoRepository : MovimientoRepository {
    val gastosCreados = mutableListOf<Gasto>()
    val ingresosCreados = mutableListOf<Ingreso>()

    override suspend fun addGasto(gasto: Gasto) {
        gastosCreados.add(gasto)
    }

    override suspend fun addIngreso(ingreso: Ingreso) {
        ingresosCreados.add(ingreso)
    }

    override fun observeGastos(planId: String): Flow<List<Gasto>> =
        flowOf(gastosCreados.filter { it.planId == planId })

    override fun observeIngresos(planId: String): Flow<List<Ingreso>> =
        flowOf(ingresosCreados.filter { it.planId == planId })
}

private class FakeCategoriaRepository(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId })

    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId && it.tipo == tipo })

    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacion : MovimientoSincronizacion {
    val planesSincronizados = mutableListOf<String>()

    override fun sincronizar(planId: String): Flow<Unit> {
        planesSincronizados.add(planId)
        return flowOf(Unit)
    }
}

private class FakeAuthRepository : AuthRepository {
    override fun observeSesion(): Flow<Sesion> =
        flowOf(Sesion(uid = "user-1", esAnonima = true))

    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
}
