package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

class RenombrarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
) {
    suspend operator fun invoke(usuarioId: String, nombreUsuarioAnterior: String, nombreUsuarioNuevo: String): Boolean =
        usuarioRepository.renombrar(
            usuarioId = usuarioId,
            nombreUsuarioAnterior = normalizarNombreUsuario(nombreUsuarioAnterior),
            nombreUsuarioNuevo = normalizarNombreUsuario(nombreUsuarioNuevo),
        )
}
