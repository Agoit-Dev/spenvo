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
import org.junit.Test

class CrearIngresoUseCaseTest {

    private val repo = FakeIngresoRepository()

    @Test
    fun `crea ingreso valido y lo persiste`() = runTest {
        val useCase = CrearIngresoUseCase(repo, ValidarMontoUseCase())

        val result = useCase(
            CrearIngresoRequest(
                planId = "plan-1",
                categoriaId = "cat-1",
                monto = Monto(325000),
                fecha = LocalDate.of(2026, 8, 20),
                descripcion = "Sueldo",
                creadoPor = "user-1",
            ),
        )

        assertEquals("plan-1", result.planId)
        assertEquals(Monto(325000), result.monto)
        assertEquals(1, repo.saved.size)
    }

    @Test
    fun `rechaza monto invalido sin persistir`() = runTest {
        val useCase = CrearIngresoUseCase(repo, ValidarMontoUseCase())

        val error = try {
            useCase(
                CrearIngresoRequest(
                    planId = "plan-1",
                    categoriaId = "cat-1",
                    monto = Monto(0),
                    fecha = LocalDate.of(2026, 8, 20),
                    creadoPor = "user-1",
                ),
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertNotNull(error)
        assertEquals(0, repo.saved.size)
    }
}

private class FakeIngresoRepository : MovimientoRepository {
    val saved = mutableListOf<Ingreso>()

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) {
        saved.add(ingreso)
    }

    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}
