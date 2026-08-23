package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VincularCredencialUseCaseTest {

    private val repo = FakeAuthRepositoryVincular()

@Test
    fun `vincula email y password a la sesion anonima`() = runTest {
        val useCase = VincularCredencialUseCase(repo)

        useCase(email = "ana@example.com", password = "secreta-123", nombre = "Ana")

        assertEquals("ana@example.com", repo.ultimoEmail)
        assertEquals("secreta-123", repo.ultimaPassword)
        assertEquals("Ana", repo.ultimoNombre)
        assertFalse(repo.sesion.value.esAnonima)
        assertEquals(true, repo.sesion.value.estaAutenticada)
    }

    @Test
    fun `vincula credencial manteniendo el uid de la sesion anonima`() = runTest {
        val useCase = VincularCredencialUseCase(repo)

        useCase(email = "ana@example.com", password = "secreta-123", nombre = "Ana")

        assertEquals("anon-1", repo.sesion.value.uid)
    }
}

private class FakeAuthRepositoryVincular : AuthRepository {
    val sesion = MutableStateFlow(Sesion(uid = "anon-1", esAnonima = true))
    var ultimoEmail: String? = null
    var ultimaPassword: String? = null
    var ultimoNombre: String? = null

    override fun observeSesion(): Flow<Sesion> = sesion

    override suspend fun iniciarSesionAnonima() {
        sesion.value = Sesion(uid = "anon-1", esAnonima = true)
    }

    override suspend fun vincularEmail(email: String, password: String, nombre: String) {
        ultimoEmail = email
        ultimaPassword = password
        ultimoNombre = nombre
        sesion.value = Sesion(uid = sesion.value.uid, esAnonima = false)
    }

    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit

    override suspend fun cerrarSesion() = Unit
}
