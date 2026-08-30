package com.agoitdev.spenvo.cuenta

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/**
 * Anti-enumeration: an unknown email and a wrong password map to the exact same message, so a
 * caller can't probe whether an email is registered by reading the error text.
 */
@StringRes
fun mapearErrorAuth(error: Throwable): Int = when (error) {
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException,
    -> R.string.account_error_credenciales_invalidas
    is FirebaseNetworkException -> R.string.account_error_sin_red
    else -> R.string.account_error_generico
}
