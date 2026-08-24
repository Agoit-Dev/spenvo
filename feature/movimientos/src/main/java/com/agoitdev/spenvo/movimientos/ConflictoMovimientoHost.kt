package com.agoitdev.spenvo.movimientos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.agoitdev.spenvo.designsystem.conflict.CampoConflictoUi
import com.agoitdev.spenvo.designsystem.conflict.ConflictoDialog
import com.agoitdev.spenvo.designsystem.conflict.ConflictoDialogTextos
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Hosts the Slice 5b conflict dialog: maps the entity-agnostic [ConflictoEdicion]
 * to the designsystem-local UI model, resolving every string here so
 * `:core:designsystem` stays free of `:core:domain` and `res/`. The dialog
 * opens only when [conflicto] is non-null, which the caller controls via
 * `MovimientosViewModel.mostrarConflicto`.
 */
@Composable
internal fun ConflictoMovimientoDialogHost(
    conflicto: ConflictoEdicion?,
    movimientoLocal: Movimiento?,
    onUsarLocal: (Movimiento) -> Unit,
    onUsarRemoto: (Movimiento) -> Unit,
    onDismiss: () -> Unit,
) {
    if (conflicto == null || movimientoLocal == null) return
    val esBorradoVsEdicion = conflicto.local.borrado || conflicto.remoto.borrado

    ConflictoDialog(
        textos = ConflictoDialogTextos(
            titulo = stringResource(R.string.conflict_dialog_title),
            encabezadoLocal = encabezadoConflicto(conflicto.local),
            encabezadoRemoto = encabezadoConflicto(conflicto.remoto),
            etiquetaBorrado = stringResource(R.string.conflict_deleted_label),
            accionPrimaria = stringResource(
                if (esBorradoVsEdicion) R.string.conflict_action_restore_mine else R.string.conflict_action_use_mine,
            ),
            accionSecundaria = stringResource(
                if (esBorradoVsEdicion) R.string.conflict_action_keep_deleted else R.string.conflict_action_use_theirs,
            ),
            cancelar = stringResource(R.string.conflict_action_cancel),
        ),
        campos = camposParaDialogo(conflicto),
        borradoLocal = conflicto.local.borrado,
        borradoRemoto = conflicto.remoto.borrado,
        onAccionPrimaria = {
            onUsarLocal(if (esBorradoVsEdicion) movimientoLocal.sinBorrar() else movimientoLocal)
        },
        onAccionSecundaria = { onUsarRemoto(movimientoLocal) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun camposParaDialogo(conflicto: ConflictoEdicion): List<CampoConflictoUi> =
    conflicto.local.campos.map { campoLocal ->
        val valorRemoto = conflicto.remoto.campos.firstOrNull { it.clave == campoLocal.clave }?.valor.orEmpty()
        CampoConflictoUi(
            etiqueta = etiquetaParaClave(campoLocal.clave),
            valorLocal = campoLocal.valor,
            valorRemoto = valorRemoto,
        )
    }

@Composable
private fun encabezadoConflicto(snapshot: SnapshotConflicto): String = stringResource(
    R.string.conflict_edited_by,
    snapshot.editadoPor.orEmpty(),
    snapshot.editadoEn?.let(::formatearInstante).orEmpty(),
)

private fun formatearInstante(instante: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(instante)

@Composable
private fun etiquetaParaClave(clave: String): String = when (clave) {
    "monto" -> stringResource(R.string.conflict_field_monto)
    "fecha" -> stringResource(R.string.conflict_field_fecha)
    "categoria" -> stringResource(R.string.conflict_field_categoria)
    "descripcion" -> stringResource(R.string.conflict_field_descripcion)
    else -> clave
}

private fun Movimiento.sinBorrar(): Movimiento = when (this) {
    is Gasto -> copy(deletedAt = null)
    is Ingreso -> copy(deletedAt = null)
}
