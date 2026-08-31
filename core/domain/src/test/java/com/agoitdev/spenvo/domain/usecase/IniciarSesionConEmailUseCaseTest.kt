package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IniciarSesionConEmailUseCaseTest {

    private val repo = FakeAuthRepositoryLogin()

    @Test
    fun `delega email y password al repositorio`() = runTest {
        val useCase = IniciarSesionConEmailUseCase(repo)

        useCase(email = "ana@example.com", password = "secreta-123")

        assertEquals("ana@example.com", repo.ultimoEmail)
        assertEquals("secreta-123", repo.ultimaPassword)
        assertTrue(repo.llamado)
    }
}

private class FakeAuthRepositoryLogin : AuthRepository {
    val sesion = MutableStateFlow(Sesion.Anonima)
    var ultimoEmail: String? = null
    var ultimaPassword: String? = null
    var llamado = false

    override fun observeSesion(): Flow<Sesion> = sesion
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        llamado = true
        ultimoEmail = email
        ultimaPassword = password
        sesion.value = Sesion(uid = "user-1", esAnonima = false, email = email)
    }
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
