package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.AccesoPlan
import kotlinx.coroutines.flow.Flow

interface AccesoPlanRepository {
    fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>>

    fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>>

    suspend fun invitarMiembro(acceso: AccesoPlan)

    suspend fun aceptarInvitacion(usuarioId: String, planId: String)
}
