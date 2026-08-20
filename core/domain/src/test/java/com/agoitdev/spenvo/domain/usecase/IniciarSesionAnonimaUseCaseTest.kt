package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IniciarSesionAnonimaUseCaseTest {

    private val repo = FakeAuthRepository()

    @Test
    fun `inicia sesion anonima y emite sesion autenticada`() = runTest {
        val useCase = IniciarSesionAnonimaUseCase(repo)

        useCase()

        assertEquals(1, repo.lamadasInicio)
        assertEquals("anon-1", repo.sesion.value.uid)
        assertEquals(true, repo.sesion.value.estaAutenticada)
        assertEquals(true, repo.sesion.value.esAnonima)
    }
}

private class FakeAuthRepository : AuthRepository {
    val sesion = MutableStateFlow(Sesion.Anonima)
    var lamadasInicio = 0

    override fun observeSesion(): Flow<Sesion> = sesion

    override suspend fun iniciarSesionAnonima() {
        lamadasInicio++
        sesion.value = Sesion(uid = "anon-1", esAnonima = true)
    }
}

