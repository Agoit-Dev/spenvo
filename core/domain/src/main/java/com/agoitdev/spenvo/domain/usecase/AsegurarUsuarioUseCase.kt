package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.model.normalizarEmail
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

/**
 * Ensures a [Usuario] document exists in Firestore for a given uid, and keeps it in sync with
 * account linking. This is the first place `nombreUsuario` actually gets created/persisted.
 */
class AsegurarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val generarNombreUsuarioUnico: GenerarNombreUsuarioUnicoUseCase,
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
     */
    suspend fun paraVincularEmail(usuarioId: String, nombre: String, email: String) {
        val existente = usuarioRepository.obtener(usuarioId)
            ?: Usuario(id = usuarioId, nombreUsuario = generarNombreUsuarioUnico(usuarioId))
        usuarioRepository.actualizar(existente.copy(nombre = nombre, email = email))
        usuarioRepository.registrarIndiceEmail(usuarioId, normalizarEmail(email))
    }
}
