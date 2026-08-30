package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Sesion
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSesion(): Flow<Sesion>

    suspend fun iniciarSesionAnonima()

    suspend fun iniciarSesionConEmail(email: String, password: String)

    suspend fun enviarRecuperacionPassword(email: String)

    suspend fun vincularEmail(email: String, password: String, nombre: String)

    suspend fun actualizarPerfil(nombre: String? = null, photoUrl: String? = null)

    suspend fun cerrarSesion()
}
