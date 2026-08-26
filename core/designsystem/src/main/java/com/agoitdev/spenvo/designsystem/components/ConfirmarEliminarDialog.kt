@file:Suppress("MatchingDeclarationName")

package com.agoitdev.spenvo.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class ConfirmarEliminarTextos(
    val titulo: String,
    val mensaje: String,
    val confirmar: String,
    val cancelar: String,
)

@Composable
fun ConfirmarEliminarDialog(
    textos: ConfirmarEliminarTextos,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        modifier = modifier,
        icon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
        title = { Text(textos.titulo) },
        text = { Text(textos.mensaje) },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(textos.confirmar)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(textos.cancelar)
            }
        },
    )
}
