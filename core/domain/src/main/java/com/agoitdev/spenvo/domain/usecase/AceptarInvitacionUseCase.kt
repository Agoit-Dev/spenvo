package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository

class AceptarInvitacionUseCase(
    private val accesosRepository: AccesoPlanRepository,
) {
    suspend operator fun invoke(usuarioId: String, planId: String) {
        accesosRepository.aceptarInvitacion(usuarioId, planId)
    }
}
