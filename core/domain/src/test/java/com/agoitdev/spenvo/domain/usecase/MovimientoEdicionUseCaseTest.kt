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
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualizarGastoUseCaseTest {

    private val repo = FakeMovimientoEdicionRepository()

    @Test
    fun `actualiza el monto y estampa editedBy y editedAt`() = runTest {
        val useCase = ActualizarGastoUseCase(repo, ValidarMontoUseCase())
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(1000),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(gasto.copy(monto = Monto(2500)), editorId = "user-2")

        val actualizado = repo.gastosActualizados.single()
        assertEquals(2500, actualizado.monto.unidadesMenores)
        assertEquals("user-2", actualizado.editedBy)
        assertNotNull(actualizado.editedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza monto invalido sin persistir`() = runTest {
        val useCase = ActualizarGastoUseCase(repo, ValidarMontoUseCase())
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(0),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(gasto, editorId = "user-2")
    }
}

class EliminarGastoUseCaseTest {

    private val repo = FakeMovimientoEdicionRepository()

    @Test
    fun `marca el gasto como eliminado y estampa atribucion`() = runTest {
        val useCase = EliminarGastoUseCase(repo)
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(1000),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(gasto, editorId = "user-2")

        val eliminado = repo.gastosEliminados.single()
        assertEquals(null, gasto.deletedAt)
        assertTrue(eliminado.deletedAt != null)
        assertEquals("user-2", eliminado.editedBy)
        assertNotNull(eliminado.editedAt)
    }
}

class ActualizarIngresoUseCaseTest {

    private val repo = FakeMovimientoEdicionRepository()

    @Test
    fun `actualiza el monto y estampa editedBy y editedAt`() = runTest {
        val useCase = ActualizarIngresoUseCase(repo, ValidarMontoUseCase())
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(1000),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(ingreso.copy(monto = Monto(3000)), editorId = "user-2")

        val actualizado = repo.ingresosActualizados.single()
        assertEquals(3000, actualizado.monto.unidadesMenores)
        assertEquals("user-2", actualizado.editedBy)
        assertNotNull(actualizado.editedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza monto invalido sin persistir`() = runTest {
        val useCase = ActualizarIngresoUseCase(repo, ValidarMontoUseCase())
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(0),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(ingreso, editorId = "user-2")
    }
}

class EliminarIngresoUseCaseTest {

    private val repo = FakeMovimientoEdicionRepository()

    @Test
    fun `marca el ingreso como eliminado y estampa atribucion`() = runTest {
        val useCase = EliminarIngresoUseCase(repo)
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(1000),
            fecha = LocalDate.of(2026, 8, 20),
            creadoPor = "user-1",
        )

        useCase(ingreso, editorId = "user-2")

        val eliminado = repo.ingresosEliminados.single()
        assertEquals(null, ingreso.deletedAt)
        assertTrue(eliminado.deletedAt != null)
        assertEquals("user-2", eliminado.editedBy)
        assertNotNull(eliminado.editedAt)
    }
}

private class FakeMovimientoEdicionRepository : MovimientoRepository {
    val gastosActualizados = mutableListOf<Gasto>()
    val gastosEliminados = mutableListOf<Gasto>()
    val ingresosActualizados = mutableListOf<Ingreso>()
    val ingresosEliminados = mutableListOf<Ingreso>()

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit

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

    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}
