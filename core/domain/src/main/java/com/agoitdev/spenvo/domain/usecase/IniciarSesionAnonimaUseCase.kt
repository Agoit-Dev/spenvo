package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class IniciarSesionAnonimaUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() {
        authRepository.iniciarSesionAnonima()
    }
}

