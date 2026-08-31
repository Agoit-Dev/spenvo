package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class IniciarSesionConEmailUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        authRepository.iniciarSesionConEmail(email, password)
    }
}
