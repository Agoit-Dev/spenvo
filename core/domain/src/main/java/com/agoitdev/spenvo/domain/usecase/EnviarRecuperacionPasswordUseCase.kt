package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class EnviarRecuperacionPasswordUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String) {
        authRepository.enviarRecuperacionPassword(email)
    }
}
