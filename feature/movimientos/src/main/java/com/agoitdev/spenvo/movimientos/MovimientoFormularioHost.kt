@file:Suppress("MatchingDeclarationName")

package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarDialog
import com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarTextos
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.TipoCategoria

/**
 * Params bundle for the compact/expanded form hosts (M6 Slice B): both [MovimientoFormularioSheet]
 * (compact, `ModalBottomSheet`) and [MovimientoFormularioPanel] (expanded, inline detail pane)
 * take the exact same inputs, only the wrapping shell differs.
 */
internal data class MovimientoFormularioParametros(
    val planId: String,
    val tipoPorDefecto: TipoCategoria,
    val formulario: FormularioMovimiento,
    val viewModel: MovimientosViewModel,
    val cargando: Boolean,
    val onCerrar: () -> Unit,
)

/** Compact layout: today's `MovimientoFormSheet` opened as a `ModalBottomSheet`, unchanged since before M6. */
@Composable
internal fun MovimientoFormularioSheet(parametros: MovimientoFormularioParametros) {
    if (parametros.formulario is FormularioMovimiento.Cerrado) return
    MovimientoFormularioEstado(parametros) { movimientoExistente, acciones ->
        MovimientoFormSheet(
            planId = parametros.planId,
            tipoInicial = parametros.tipoPorDefecto,
            cargando = parametros.cargando,
            viewModel = parametros.viewModel,
            movimientoExistente = movimientoExistente,
            acciones = acciones,
        )
    }
}

/**
 * Expanded layout (M6 Slice B): the same form reused inline as the detail pane of
 * the local `ListDetailPaneScaffold` split in `MovimientosScreen`, with no `ModalBottomSheet` chrome.
 * Shows a placeholder when no movimiento is open, since the detail pane must always render something.
 */
@Composable
internal fun MovimientoFormularioPanel(parametros: MovimientoFormularioParametros) {
    if (parametros.formulario is FormularioMovimiento.Cerrado) {
        MovimientoFormularioPanelVacio()
        return
    }
    MovimientoFormularioEstado(parametros) { movimientoExistente, acciones ->
        MovimientoFormEstadoYContenido(
            planId = parametros.planId,
            tipoInicial = parametros.tipoPorDefecto,
            cargando = parametros.cargando,
            viewModel = parametros.viewModel,
            movimientoExistente = movimientoExistente,
            acciones = acciones,
        )
    }
}

@Composable
private fun MovimientoFormularioPanelVacio() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.movements_detail_placeholder),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Shared state resolution for both form hosts: resolves whether [formulario] is
 * editing an existing movimiento, wires save/delete actions to the [viewModel], and owns the
 * delete-confirmation dialog — identical regardless of which shell (sheet or panel) renders [contenido].
 */
@Composable
private fun MovimientoFormularioEstado(
    parametros: MovimientoFormularioParametros,
    contenido: @Composable (movimientoExistente: Movimiento?, acciones: MovimientoFormAcciones) -> Unit,
) {
    val movimientoExistente = (parametros.formulario as? FormularioMovimiento.Editar)?.movimiento
    var mostrarConfirmarEliminar by remember(parametros.formulario) { mutableStateOf(false) }
    val viewModel = parametros.viewModel

    contenido(
        movimientoExistente,
        MovimientoFormAcciones(
            onGuardar = { datos ->
                if (movimientoExistente == null) {
                    viewModel.guardar(datos)
                } else {
                    viewModel.actualizar(aplicarDatos(movimientoExistente, datos))
                }
            },
            onDismiss = parametros.onCerrar,
            onEliminar = movimientoExistente?.let { { mostrarConfirmarEliminar = true } },
        ),
    )

    if (mostrarConfirmarEliminar && movimientoExistente != null) {
        ConfirmarEliminarDialog(
            textos = ConfirmarEliminarTextos(
                titulo = stringResource(R.string.movements_delete_confirm_title),
                mensaje = stringResource(R.string.movements_delete_confirm_message),
                confirmar = stringResource(R.string.movements_delete),
                cancelar = stringResource(R.string.movements_cancel),
            ),
            onConfirmar = {
                mostrarConfirmarEliminar = false
                viewModel.eliminar(movimientoExistente)
            },
            onCancelar = { mostrarConfirmarEliminar = false },
        )
    }
}

private fun aplicarDatos(movimiento: Movimiento, datos: MovimientoFormDatos): Movimiento = when (movimiento) {
    is Gasto -> movimiento.copy(
        categoriaId = datos.categoriaId,
        monto = datos.monto,
        fecha = datos.fecha,
        descripcion = datos.descripcion,
    )
    is Ingreso -> movimiento.copy(
        categoriaId = datos.categoriaId,
        monto = datos.monto,
        fecha = datos.fecha,
        descripcion = datos.descripcion,
    )
}
