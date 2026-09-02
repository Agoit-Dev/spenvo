package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverConflictoIngresoUsandoLocalUseCaseTest {

    private fun ingreso(editedBy: String? = null, editedAt: java.time.Instant? = null) = Ingreso(
        id = "i1", planId = "p1", categoriaId = "c1", monto = Monto(1000),
        fecha = LocalDate.of(2026, 8, 20), creadoPor = "user-1", editedBy = editedBy, editedAt = editedAt,
    )

    @Test
    fun `estampa editedBy y editedAt frescos antes de llamar al repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflictoIngreso()
        val useCase = ResolverConflictoIngresoUsandoLocalUseCase(repo, ValidarMontoUseCase())

        useCase(ingreso(), editorId = "user-2", clave = "ingresos:i1")

        val guardado = repo.ingresoResueltoLocal
        assertEquals("user-2", guardado?.editedBy)
        assertTrue(guardado?.editedAt != null)
        assertEquals("ingresos:i1", repo.claveResueltaLocal)
    }

    @Test
    fun `rechaza un monto invalido antes de tocar el repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflictoIngreso()
        val useCase = ResolverConflictoIngresoUsandoLocalUseCase(repo, ValidarMontoUseCase())

        val excepcion = try {
            useCase(ingreso().copy(monto = Monto(-100)), editorId = "user-2", clave = "ingresos:i1")
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertNotNull(excepcion)
        assertNull(repo.ingresoResueltoLocal)
    }
}

private class FakeMovimientoRepositorioResolverConflictoIngreso : MovimientoRepository {
    var ingresoResueltoLocal: Ingreso? = null
    var claveResueltaLocal: String? = null

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) {
        ingresoResueltoLocal = ingreso
        claveResueltaLocal = clave
    }
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}
