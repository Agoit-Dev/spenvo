package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.model.normalizarEmail
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

/**
 * Ensures a [Usuario] document exists in Firestore for a given uid, and keeps it in sync with
 * account linking. This is the first place `nombreUsuario` actually gets created/persisted.
 */
class AsegurarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val generarNombreUsuarioUnico: GenerarNombreUsuarioUnicoUseCase,
    private val accesosRepository: AccesoPlanRepository,
    private val pendientesRepository: InvitacionPendienteRepository,
) {
    /** Best-effort bootstrap for a freshly established anonymous session. */
    suspend fun paraSesionAnonima(usuarioId: String) {
        if (usuarioRepository.obtener(usuarioId) != null) return
        val nombreUsuario = generarNombreUsuarioUnico(usuarioId)
        usuarioRepository.crear(Usuario(id = usuarioId, nombreUsuario = nombreUsuario))
    }

    /**
     * Called right after linking an email/password credential to the anonymous account.
     * The `?:` fallback is defensive: normally [paraSesionAnonima] already created the doc by the
     * time registration happens, but this keeps the flow correct even if that step was skipped.
     *
     * Also resolves any [com.agoitdev.spenvo.domain.model.InvitacionPendiente] that was waiting on
     * this exact email: each one becomes a real [AccesoPlan] for this now-known [usuarioId], and
     * is then removed from the pending collection — see the design doc's "Invite by nombreUsuario
     * or email" section.
     */
    suspend fun paraVincularEmail(usuarioId: String, nombre: String, email: String) {
        val existente = usuarioRepository.obtener(usuarioId)
            ?: Usuario(id = usuarioId, nombreUsuario = generarNombreUsuarioUnico(usuarioId))
        usuarioRepository.actualizar(existente.copy(nombre = nombre, email = email))
        val emailNormalizado = normalizarEmail(email)
        usuarioRepository.registrarIndiceEmail(usuarioId, emailNormalizado)

        val fallidas = pendientesRepository.obtenerPorEmail(emailNormalizado).count { pendiente ->
            resolverInvitacion(usuarioId, emailNormalizado, pendiente).isFailure
        }
        if (fallidas > 0) error("$fallidas invitación(es) pendiente(s) no se pudieron resolver")
    }

    /**
     * ARCH-U802: each invite is granted independently — one Firestore failure must not block
     * granting the rest of the batch, unlike the previous plain `forEach` which aborted at the
     * first failure. [paraVincularEmail] still reports overall failure so the caller's retry
     * (`CuentaViewModel.reintentarSyncUsuario`, ARCH-U801) gets another chance at whichever
     * invites failed; [AccesoPlanRepository.invitarMiembro] and
     * [InvitacionPendienteRepository.eliminar] are both keyed by deterministic ids, so retrying
     * an already-granted invite is a safe no-op.
     */
    private suspend fun resolverInvitacion(
        usuarioId: String,
        emailNormalizado: String,
        pendiente: InvitacionPendiente,
    ): Result<Unit> = runCatching {
        accesosRepository.invitarMiembro(
            AccesoPlan(
                usuarioId = usuarioId,
                planId = pendiente.planId,
                rol = pendiente.rol,
                invitacionEstado = InvitacionEstado.PENDIENTE,
            ),
        )
        pendientesRepository.eliminar(emailNormalizado, pendiente.planId)
    }
}
