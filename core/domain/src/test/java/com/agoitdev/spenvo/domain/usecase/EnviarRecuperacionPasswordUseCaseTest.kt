package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EnviarRecuperacionPasswordUseCaseTest {

    private val repo = FakeAuthRepositoryRecovery()

    @Test
    fun `delega el email al repositorio`() = runTest {
        val useCase = EnviarRecuperacionPasswordUseCase(repo)

        useCase(email = "ana@example.com")

        assertEquals("ana@example.com", repo.ultimoEmail)
    }
}

private class FakeAuthRepositoryRecovery : AuthRepository {
    val sesion = MutableStateFlow(Sesion.Anonima)
    var ultimoEmail: String? = null

    override fun observeSesion(): Flow<Sesion> = sesion
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) {
        ultimoEmail = email
    }
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
