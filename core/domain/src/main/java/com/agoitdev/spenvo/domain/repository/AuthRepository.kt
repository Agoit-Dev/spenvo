package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Sesion
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSesion(): Flow<Sesion>

    suspend fun iniciarSesionAnonima()
}

