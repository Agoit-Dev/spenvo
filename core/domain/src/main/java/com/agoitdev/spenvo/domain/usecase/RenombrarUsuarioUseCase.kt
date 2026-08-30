package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.UsuarioRepository

/**
 * Passes the raw, display-form [nombreUsuarioAnterior]/[nombreUsuarioNuevo] straight through —
 * normalization (lowercasing for the `nombres_usuario` index doc IDs) happens inside the
 * repository implementation, which also needs the raw new value to keep the display field's
 * original casing (e.g. `GatoAzul42`, never `gatoazul42`).
 */
class RenombrarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
) {
    suspend operator fun invoke(usuarioId: String, nombreUsuarioAnterior: String, nombreUsuarioNuevo: String): Boolean =
        usuarioRepository.renombrar(
            usuarioId = usuarioId,
            nombreUsuarioAnterior = nombreUsuarioAnterior,
            nombreUsuarioNuevo = nombreUsuarioNuevo,
        )
}
