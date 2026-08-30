package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.normalizarEmail
import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

/**
 * Invites a member into a plan by nombreUsuario or email, per the anti-enumeration design: the
 * caller (see [com.agoitdev.spenvo.planes.MiembrosViewModel]) always shows the same generic
 * confirmation once this suspend function returns normally, regardless of whether [identificador]
 * actually resolved to a real account — this use case never signals "not found" as a distinct
 * outcome, only as silent no-ops on the two branches below. A genuine Firestore failure (network,
 * permission) still propagates as an exception; only "no matching account" is swallowed.
 */
class InvitarMiembroUseCase(
    private val accesosRepository: AccesoPlanRepository,
    private val usuarioRepository: UsuarioRepository,
    private val pendientesRepository: InvitacionPendienteRepository,
) {
    suspend operator fun invoke(planId: String, identificador: String, rol: Rol, invitadoPor: String) {
        val esEmail = identificador.contains('@')
        val usuarioId = if (esEmail) {
            usuarioRepository.resolverPorEmail(normalizarEmail(identificador))
        } else {
            usuarioRepository.resolverPorNombreUsuario(normalizarNombreUsuario(identificador))
        }

        if (usuarioId != null) {
            accesosRepository.invitarMiembro(
                AccesoPlan(
                    usuarioId = usuarioId,
                    planId = planId,
                    rol = rol,
                    invitacionEstado = InvitacionEstado.PENDIENTE,
                ),
            )
            return
        }

        if (esEmail) {
            pendientesRepository.crear(
                InvitacionPendiente(
                    email = normalizarEmail(identificador),
                    planId = planId,
                    rol = rol,
                    invitadoPor = invitadoPor,
                ),
            )
        } else {
            // nombreUsuario no resuelto: se descarta, no hay cuenta "futura" que esperar. El segundo
            // await (idéntico al primero, sin efecto) existe solo para que esta rama tarde lo mismo
            // que las otras tres — sin él, "no existe" respondería mensurablemente más rápido que
            // "existe", filtrando por timing lo que el mensaje genérico ya oculta.
            usuarioRepository.resolverPorNombreUsuario(normalizarNombreUsuario(identificador))
        }
    }
}
