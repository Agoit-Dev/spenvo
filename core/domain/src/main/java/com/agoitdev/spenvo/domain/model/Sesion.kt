package com.agoitdev.spenvo.domain.model

data class Sesion(
    val uid: String?,
    val esAnonima: Boolean,
    val email: String? = null,
    val nombre: String? = null,
    val photoUrl: String? = null,
) {
    val estaAutenticada: Boolean get() = uid != null

    companion object {
        val Anonima = Sesion(uid = null, esAnonima = true)
    }
}

