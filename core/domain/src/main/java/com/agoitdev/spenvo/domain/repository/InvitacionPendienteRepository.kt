package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.InvitacionPendiente

interface InvitacionPendienteRepository {
    suspend fun crear(invitacion: InvitacionPendiente)

    suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente>

    suspend fun eliminar(emailNormalizado: String, planId: String)
}
