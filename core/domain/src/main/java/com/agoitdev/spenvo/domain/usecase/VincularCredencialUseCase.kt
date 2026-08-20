package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class VincularCredencialUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String, nombre: String) {
        authRepository.vincularEmail(email, password, nombre)
    }
}
