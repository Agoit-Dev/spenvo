package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository

class InvitarMiembroUseCase(
    private val accesosRepository: AccesoPlanRepository,
) {
    suspend operator fun invoke(planId: String, usuarioId: String, rol: Rol) {
        accesosRepository.invitarMiembro(
            AccesoPlan(
                usuarioId = usuarioId,
                planId = planId,
                rol = rol,
                invitacionEstado = InvitacionEstado.PENDIENTE,
            ),
        )
    }
}
