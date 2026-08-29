package com.agoitdev.spenvo.domain.model

fun normalizarNombreUsuario(valor: String): String = valor.trim().lowercase()

fun normalizarEmail(valor: String): String = valor.trim().lowercase()
