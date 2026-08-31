package com.agoitdev.spenvo.cuenta

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Recovery dialog plus its one-shot success snackbar, which also dismisses the dialog. */
@Composable
internal fun RecuperacionPassword(
    recoveryEstado: RecoveryEstado,
    visible: Boolean,
    snackbarHostState: SnackbarHostState,
    onCerrar: () -> Unit,
    viewModel: CuentaViewModel,
) {
    val mensajeExito = stringResource(R.string.account_recovery_success)
    LaunchedEffect(recoveryEstado.exito) {
        if (recoveryEstado.exito) {
            onCerrar()
            snackbarHostState.showSnackbar(mensajeExito)
            viewModel.consumirRecoveryEstado()
        }
    }

    if (visible) {
        RecoveryDialog(
            cargando = recoveryEstado.cargando,
            onConfirmar = viewModel::recuperarPassword,
            onCancelar = onCerrar,
        )
    }
}

@Composable
private fun RecoveryDialog(
    cargando: Boolean,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.account_recovery_title)) },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.account_recovery_email_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            // Disabled while in flight: otherwise repeated taps fire one sendPasswordResetEmail
            // per tap, which burns the account's reset quota for no user benefit.
            TextButton(onClick = { onConfirmar(email) }, enabled = email.isNotBlank() && !cargando) {
                Text(stringResource(R.string.account_recovery_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.account_recovery_cancel)) }
        },
    )
}
