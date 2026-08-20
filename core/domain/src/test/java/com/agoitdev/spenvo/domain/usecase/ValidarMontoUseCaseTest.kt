package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidarMontoUseCaseTest {

    private val useCase = ValidarMontoUseCase()

    @Test
    fun `monto positivo es valido`() {
        assertTrue(useCase(Monto(100)))
        assertTrue(useCase(Monto(1)))
    }

    @Test
    fun `monto cero es invalido`() {
        assertFalse(useCase(Monto(0)))
    }

    @Test
    fun `monto negativo es invalido`() {
        assertFalse(useCase(Monto(-1)))
        assertFalse(useCase(Monto(-500)))
    }

    @Test
    fun `monto se crea desde euros con centimos`() {
        assertEquals(Monto(1234), Monto.fromEuros(12, 34))
    }
}
