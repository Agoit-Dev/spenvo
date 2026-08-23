package com.agoitdev.spenvo.designsystem.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One field shown side-by-side in the conflict dialog. Entity-agnostic: the
 * calling feature module resolves both the field label and the two values to
 * plain strings before constructing this (design Decision 5, Slice 5b).
 */
data class CampoConflictoUi(
    val etiqueta: String,
    val valorLocal: String,
    val valorRemoto: String,
) {
    val difiere: Boolean get() = valorLocal != valorRemoto
}

/** Pre-resolved strings for [ConflictoDialog] — the caller supplies `stringResource(...)` output. */
data class ConflictoDialogTextos(
    val titulo: String,
    val encabezadoLocal: String,
    val encabezadoRemoto: String,
    val etiquetaBorrado: String,
    val accionPrimaria: String,
    val accionSecundaria: String,
    val cancelar: String,
)

const val TAG_COLUMNA_LOCAL = "conflicto_columna_local"
const val TAG_COLUMNA_REMOTO = "conflicto_columna_remoto"
const val TAG_BORRADO_LOCAL = "conflicto_borrado_local"
const val TAG_BORRADO_REMOTO = "conflicto_borrado_remoto"

/**
 * Generic two-column conflict resolution dialog (design Decision 4/5). Renders
 * the same ordered [campos] list for both sides, emphasising rows that
 * differ, and lets the caller decide what "accionPrimaria"/"accionSecundaria"
 * mean (edit-vs-edit: usar la mía/usar la suya; delete-vs-edit: restaurar mi
 * edición/mantener borrado — the case is entirely the caller's concern).
 */
@Suppress("LongParameterList")
@Composable
fun ConflictoDialog(
    textos: ConflictoDialogTextos,
    campos: List<CampoConflictoUi>,
    borradoLocal: Boolean,
    borradoRemoto: Boolean,
    onAccionPrimaria: () -> Unit,
    onAccionSecundaria: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(textos.titulo) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ConflictoColumna(
                    encabezado = textos.encabezadoLocal,
                    borrado = borradoLocal,
                    etiquetaBorrado = textos.etiquetaBorrado,
                    campos = campos,
                    lado = Lado.LOCAL,
                    modifier = Modifier.testTag(TAG_COLUMNA_LOCAL),
                )
                ConflictoColumna(
                    encabezado = textos.encabezadoRemoto,
                    borrado = borradoRemoto,
                    etiquetaBorrado = textos.etiquetaBorrado,
                    campos = campos,
                    lado = Lado.REMOTO,
                    modifier = Modifier.testTag(TAG_COLUMNA_REMOTO),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccionPrimaria) { Text(textos.accionPrimaria) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAccionSecundaria) { Text(textos.accionSecundaria) }
                TextButton(onClick = onDismiss) { Text(textos.cancelar) }
            }
        },
    )
}

private enum class Lado { LOCAL, REMOTO }

@Suppress("LongParameterList")
@Composable
private fun ConflictoColumna(
    encabezado: String,
    borrado: Boolean,
    etiquetaBorrado: String,
    campos: List<CampoConflictoUi>,
    lado: Lado,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = encabezado, style = MaterialTheme.typography.labelLarge)
        if (borrado) {
            AssistChip(
                onClick = {},
                label = { Text(etiquetaBorrado) },
                modifier = Modifier.testTag(tagBorrado(lado)),
            )
        }
        campos.forEach { campo ->
            val valor = if (lado == Lado.LOCAL) campo.valorLocal else campo.valorRemoto
            Text(
                text = "${campo.etiqueta}: $valor",
                fontWeight = if (campo.difiere) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.testTag(tagCampo(campo.etiqueta, lado, campo.difiere)),
            )
        }
    }
}

private fun tagBorrado(lado: Lado): String =
    if (lado == Lado.LOCAL) TAG_BORRADO_LOCAL else TAG_BORRADO_REMOTO

private fun tagCampo(etiqueta: String, lado: Lado, difiere: Boolean): String {
    val sufijoLado = if (lado == Lado.LOCAL) "local" else "remoto"
    val sufijoDiff = if (difiere) "_diferente" else ""
    return "conflicto_campo_${etiqueta}_$sufijoLado$sufijoDiff"
}
