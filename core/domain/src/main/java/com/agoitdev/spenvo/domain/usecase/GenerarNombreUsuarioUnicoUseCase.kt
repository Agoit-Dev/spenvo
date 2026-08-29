package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlin.random.Random

class GenerarNombreUsuarioUnicoUseCase(
    private val usuarioRepository: UsuarioRepository,
) {
    suspend operator fun invoke(usuarioId: String): String {
        repeat(MAX_INTENTOS) { intento ->
            val candidato = candidato(intento)
            if (usuarioRepository.intentarReservarNombreUsuario(normalizarNombreUsuario(candidato), usuarioId)) {
                return candidato
            }
        }
        error("No se pudo generar un nombreUsuario único tras $MAX_INTENTOS intentos")
    }

    private fun candidato(intento: Int): String {
        val adjetivo = ADJETIVOS.random()
        val sustantivo = SUSTANTIVOS.random()
        val rango = if (intento < INTENTOS_RANGO_CORTO) RANGO_CORTO else RANGO_LARGO
        val numero = Random.nextInt(rango)
        return "$adjetivo$sustantivo$numero"
    }

    private companion object {
        const val MAX_INTENTOS = 8
        const val INTENTOS_RANGO_CORTO = 5
        const val RANGO_CORTO = 100
        const val RANGO_LARGO = 100_000
        val ADJETIVOS = listOf(
            "Rapido", "Alegre", "Sabio", "Curioso", "Amable", "Valiente", "Sereno", "Astuto",
            "Brillante", "Gentil", "Audaz", "Tranquilo", "Vivaz", "Noble", "Agil",
        )
        val SUSTANTIVOS = listOf(
            "Gato", "Sol", "Rio", "Nube", "Zorro", "Bosque", "Cometa", "Delfin", "Aguila",
            "Estrella", "Roble", "Faro", "Puma", "Coral", "Lince",
        )
    }
}
