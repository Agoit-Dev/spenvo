package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import java.time.Instant
import java.util.UUID

data class CrearPlanRequest(
    val nombre: String,
    val moneda: String,
    val descripcion: String? = null,
    val creadoPor: String,
)

class CrearPlanUseCase(
    private val planesRepository: PlanFinancieroRepository,
    private val accesosRepository: AccesoPlanRepository,
    private val sembrarCategorias: SembrarCategoriasPorDefectoUseCase,
) {
    suspend operator fun invoke(request: CrearPlanRequest): PlanFinanciero {
        val now = Instant.now()
        val plan = PlanFinanciero(
            id = UUID.randomUUID().toString(),
            nombre = request.nombre,
            descripcion = request.descripcion,
            moneda = request.moneda,
            createdBy = request.creadoPor,
            createdAt = now,
            updatedAt = now,
        )
        planesRepository.crearPlan(plan)
        accesosRepository.invitarMiembro(
            AccesoPlan(
                usuarioId = request.creadoPor,
                planId = plan.id,
                rol = Rol.OWNER,
                invitacionEstado = InvitacionEstado.ACEPTADA,
            ),
        )
        sembrarCategorias(plan.id)
        return plan
    }
}
