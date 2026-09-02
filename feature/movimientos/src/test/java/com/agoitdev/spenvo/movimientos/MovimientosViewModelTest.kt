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
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
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
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private val conflictosPendientes = FakeRegistroConflictosPendientes()
    private val movimientoRepo = FakeMovimientoRepository(conflictosPendientes)
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
        observarCategorias = ObservarCategoriasUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        actualizarGasto = ActualizarGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarGasto = EliminarGastoUseCase(movimientoRepo),
        actualizarIngreso = ActualizarIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarIngreso = EliminarIngresoUseCase(movimientoRepo),
        aplicarGastoRemoto = AplicarGastoRemotoUseCase(movimientoRepo),
        aplicarIngresoRemoto = AplicarIngresoRemotoUseCase(movimientoRepo),
        resolverConflictoGastoUsandoLocal = ResolverConflictoGastoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoIngresoUsandoLocal = ResolverConflictoIngresoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoGastoUsandoRemoto = ResolverConflictoGastoUsandoRemotoUseCase(movimientoRepo),
        resolverConflictoIngresoUsandoRemoto = ResolverConflictoIngresoUsandoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        registroConflictosPendientes = conflictosPendientes,
    )

    /** Subscribes to [MovimientosViewModel.conflictos] so its `WhileSubscribed` `.stateIn()` starts
     * relaying [conflictosPendientes] updates, then lets the scheduler catch up. Cancel the
     * returned [Job] once the test no longer needs live updates. */
    private fun TestScope.suscribirConflictos(viewModel: MovimientosViewModel): Job {
        val job = launch { viewModel.conflictos.collect {} }
        advanceUntilIdle()
        return job
    }

    @Test
    fun `categoriasTodas devuelve categorias de ambos tipos`() = runTest {
        val viewModel = crearViewModel()

        val categorias = viewModel.categoriasTodas("p1")
        val job = launch { categorias.collect {} }
        advanceUntilIdle()

        assertEquals(setOf("cat-gasto", "cat-ingreso"), categorias.value.map { it.id }.toSet())
        job.cancel()
    }

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
    fun `actualizar un gasto exitoso marca guardado y sella editedBy`() = runTest {
        val viewModel = crearViewModel()
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "cat-gasto",
            monto = Monto(1500),
            fecha = LocalDate.of(2026, 8, 22),
            descripcion = "Supermercado",
            creadoPor = "user-1",
        )

        viewModel.actualizar(gasto.copy(monto = Monto(2000)))
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertNull(viewModel.estadoForm.value.error)
        assertEquals(1, movimientoRepo.gastosActualizados.size)
        assertEquals("user-1", movimientoRepo.gastosActualizados.single().editedBy)
    }

    @Test
    fun `actualizar un ingreso exitoso marca guardado y sella editedBy`() = runTest {
        val viewModel = crearViewModel()
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "cat-ingreso",
            monto = Monto(200000),
            fecha = LocalDate.of(2026, 8, 22),
            creadoPor = "user-1",
        )

        viewModel.actualizar(ingreso.copy(monto = Monto(250000)))
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertEquals(1, movimientoRepo.ingresosActualizados.size)
        assertEquals("user-1", movimientoRepo.ingresosActualizados.single().editedBy)
    }

    @Test
    fun `actualizar un gasto con monto invalido expone el error del caso de uso`() = runTest {
        val viewModel = crearViewModel()
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "cat-gasto",
            monto = Monto(1500),
            fecha = LocalDate.of(2026, 8, 22),
            creadoPor = "user-1",
        )

        viewModel.actualizar(gasto.copy(monto = Monto(0)))
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertFalse(viewModel.estadoForm.value.guardado)
        assertTrue(movimientoRepo.gastosActualizados.isEmpty())
    }

    @Test
    fun `eliminar un gasto marca guardado y sella deletedAt y editedBy`() = runTest {
        val viewModel = crearViewModel()
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "cat-gasto",
            monto = Monto(1500),
            fecha = LocalDate.of(2026, 8, 22),
            creadoPor = "user-1",
        )

        viewModel.eliminar(gasto)
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertNull(viewModel.estadoForm.value.error)
        assertEquals(1, movimientoRepo.gastosEliminados.size)
        assertNotNull(movimientoRepo.gastosEliminados.single().deletedAt)
        assertEquals("user-1", movimientoRepo.gastosEliminados.single().editedBy)
    }

    @Test
    fun `eliminar un ingreso marca guardado y sella deletedAt`() = runTest {
        val viewModel = crearViewModel()
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "cat-ingreso",
            monto = Monto(200000),
            fecha = LocalDate.of(2026, 8, 22),
            creadoPor = "user-1",
        )

        viewModel.eliminar(ingreso)
        advanceUntilIdle()

        assertTrue(viewModel.estadoForm.value.guardado)
        assertEquals(1, movimientoRepo.ingresosEliminados.size)
        assertNotNull(movimientoRepo.ingresosEliminados.single().deletedAt)
    }

    @Test
    fun `consumir limpia el error y el flag de guardado`() = runTest {
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

        viewModel.consumir(error = true)
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

        viewModel.consumir(guardado = true)
        assertFalse(viewModel.estadoForm.value.guardado)
    }

    @Test
    fun `un conflicto registrado para otro registro no abre el dialogo`() = runTest {
        val viewModel = crearViewModel()

        conflictosPendientes.registrar("gastos:otro-id", conflictoDeEjemplo("otro-id"))

        assertNull(viewModel.conflictoVisible.value)
    }

    @Test
    fun `mostrarConflicto muestra el conflicto solo del registro reabierto`() = runTest {
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1"))
        advanceUntilIdle()

        viewModel.mostrarConflicto(gasto)

        assertEquals("g1", viewModel.conflictoVisible.value?.registroId)
        job.cancel()
    }

    @Test
    fun `resolverConflicto usarLocal reenvia mi version con estampa nueva y limpia el conflicto`() = runTest {
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1"))
        advanceUntilIdle()
        viewModel.mostrarConflicto(gasto)

        viewModel.resolverConflicto(gasto, usarLocal = true)
        advanceUntilIdle()

        val (gastoResuelto, claveResuelta) = movimientoRepo.gastosResueltosLocal.single()
        assertEquals("user-1", gastoResuelto.editedBy)
        assertEquals("gastos:g1", claveResuelta)
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
        assertNull(viewModel.conflictoVisible.value)
        job.cancel()
    }

    @Test
    fun `resolverConflicto usarRemoto aplica la version remota y limpia el conflicto`() = runTest {
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1"))
        advanceUntilIdle()
        viewModel.mostrarConflicto(gasto)

        viewModel.resolverConflicto(gasto, usarLocal = false)
        advanceUntilIdle()

        assertEquals(listOf("g1" to "gastos:g1"), movimientoRepo.gastosResueltosRemoto)
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
        assertNull(viewModel.conflictoVisible.value)
        job.cancel()
    }

    @Test
    fun `restaurar mi edicion limpia deletedAt y reenvia mi version tras un conflicto de borrado`() = runTest {
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1", borradoRemoto = true))
        advanceUntilIdle()
        viewModel.mostrarConflicto(gasto)

        viewModel.resolverConflicto(gasto.copy(deletedAt = null), usarLocal = true)
        advanceUntilIdle()

        val (actualizado, claveResuelta) = movimientoRepo.gastosResueltosLocal.single()
        assertNull(actualizado.deletedAt)
        assertEquals("user-1", actualizado.editedBy)
        assertEquals("gastos:g1", claveResuelta)
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
        assertNull(viewModel.conflictoVisible.value)
        job.cancel()
    }

    @Test
    fun `mantener borrado aplica la version remota borrada y limpia el conflicto`() = runTest {
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1", borradoRemoto = true))
        advanceUntilIdle()
        viewModel.mostrarConflicto(gasto)

        viewModel.resolverConflicto(gasto, usarLocal = false)
        advanceUntilIdle()

        assertEquals(listOf("g1" to "gastos:g1"), movimientoRepo.gastosResueltosRemoto)
        assertNull(conflictosPendientes.conflictoPara("gastos:g1"))
        assertNull(viewModel.conflictoVisible.value)
        job.cancel()
    }

    @Test
    fun `resolverConflicto usarRemoto cuando el remoto ya no existe mantiene el dialogo y muestra error`() = runTest {
        movimientoRepo.errorResolverGastoRemoto = "El movimiento remoto ya no existe"
        val viewModel = crearViewModel()
        val job = suscribirConflictos(viewModel)
        val gasto = gastoDeEjemplo()
        conflictosPendientes.registrar("gastos:g1", conflictoDeEjemplo("g1"))
        advanceUntilIdle()
        viewModel.mostrarConflicto(gasto)

        viewModel.resolverConflicto(gasto, usarLocal = false)
        advanceUntilIdle()

        assertNotNull(viewModel.estadoForm.value.error)
        assertNotNull(viewModel.conflictoVisible.value) // dialog stays open — did NOT close on failure
        job.cancel()
    }
}

private fun gastoDeEjemplo(): Gasto = Gasto(
    id = "g1",
    planId = "p1",
    categoriaId = "cat-gasto",
    monto = Monto(1500),
    fecha = LocalDate.of(2026, 8, 22),
    creadoPor = "user-1",
)

private fun conflictoDeEjemplo(id: String, borradoRemoto: Boolean = false): ConflictoEdicion = ConflictoEdicion(
    registroId = id,
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
        borrado = borradoRemoto,
        campos = listOf(CampoConflicto("monto", "2000")),
    ),
)

private class FakeMovimientoRepository(
    private val conflictosPendientes: FakeRegistroConflictosPendientes,
) : MovimientoRepository {
    val gastosCreados = mutableListOf<Gasto>()
    val ingresosCreados = mutableListOf<Ingreso>()
    val gastosActualizados = mutableListOf<Gasto>()
    val gastosEliminados = mutableListOf<Gasto>()
    val ingresosActualizados = mutableListOf<Ingreso>()
    val ingresosEliminados = mutableListOf<Ingreso>()
    val gastosRemotosAplicados = mutableListOf<String>()
    val ingresosRemotosAplicados = mutableListOf<String>()
    val gastosResueltosLocal = mutableListOf<Pair<Gasto, String>>()
    val ingresosResueltosLocal = mutableListOf<Pair<Ingreso, String>>()
    val gastosResueltosRemoto = mutableListOf<Pair<String, String>>()
    val ingresosResueltosRemoto = mutableListOf<Pair<String, String>>()

    /** Set to simulate the remote document being gone (e.g. deleted concurrently) when resolving. */
    var errorResolverGastoRemoto: String? = null

    override suspend fun addGasto(gasto: Gasto) {
        gastosCreados.add(gasto)
    }

    override suspend fun addIngreso(ingreso: Ingreso) {
        ingresosCreados.add(ingreso)
    }

    override suspend fun actualizarGasto(gasto: Gasto) {
        gastosActualizados.add(gasto)
    }

    override suspend fun eliminarGasto(gasto: Gasto) {
        gastosEliminados.add(gasto)
    }

    override suspend fun actualizarIngreso(ingreso: Ingreso) {
        ingresosActualizados.add(ingreso)
    }

    override suspend fun eliminarIngreso(ingreso: Ingreso) {
        ingresosEliminados.add(ingreso)
    }

    override suspend fun aplicarGastoRemoto(id: String) {
        gastosRemotosAplicados.add(id)
    }

    override suspend fun aplicarIngresoRemoto(id: String) {
        ingresosRemotosAplicados.add(id)
    }

    override fun observeGastos(planId: String): Flow<List<Gasto>> =
        flowOf(gastosCreados.filter { it.planId == planId })

    override fun observeIngresos(planId: String): Flow<List<Ingreso>> =
        flowOf(ingresosCreados.filter { it.planId == planId })

    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) {
        gastosResueltosLocal.add(gasto to clave)
        conflictosPendientes.resolver(clave)
    }

    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) {
        ingresosResueltosLocal.add(ingreso to clave)
        conflictosPendientes.resolver(clave)
    }

    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) {
        errorResolverGastoRemoto?.let { error(it) }
        gastosResueltosRemoto.add(id to clave)
        conflictosPendientes.resolver(clave)
    }

    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) {
        ingresosResueltosRemoto.add(id to clave)
        conflictosPendientes.resolver(clave)
    }
}

private class FakeRegistroConflictosPendientes : RegistroConflictosPendientes {
    private val _conflictos = MutableStateFlow<Map<String, ConflictoEdicion>>(emptyMap())
    override val conflictos: Flow<Map<String, ConflictoEdicion>> = _conflictos.asStateFlow()

    override suspend fun conflictoPara(clave: String): ConflictoEdicion? = _conflictos.value[clave]

    override suspend fun registrar(clave: String, conflicto: ConflictoEdicion) {
        _conflictos.update { it + (clave to conflicto) }
    }

    override suspend fun resolver(clave: String) {
        _conflictos.update { it - clave }
    }
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
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
