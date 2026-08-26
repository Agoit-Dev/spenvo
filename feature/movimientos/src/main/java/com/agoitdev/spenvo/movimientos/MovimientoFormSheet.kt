package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.LocalDate

private data class MovimientoFormEntrada(
    val planId: String,
    val tipo: TipoCategoria,
    val categoriaId: String,
    val montoTexto: String,
    val descripcion: String,
    val fecha: LocalDate,
)

private const val CENTIMOS_POR_UNIDAD = 100.0

private fun tipoDeMovimiento(movimiento: Movimiento): TipoCategoria =
    if (movimiento is Gasto) TipoCategoria.GASTO else TipoCategoria.INGRESO

private fun montoInicialTexto(monto: Monto): String {
    val unidades = monto.unidadesMenores / CENTIMOS_POR_UNIDAD
    return if (unidades == unidades.toLong().toDouble()) unidades.toLong().toString() else unidades.toString()
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovimientoFormSheet(
    planId: String,
    tipoInicial: TipoCategoria,
    cargando: Boolean,
    viewModel: MovimientosViewModel,
    acciones: MovimientoFormAcciones,
    movimientoExistente: Movimiento? = null,
) {
    ModalBottomSheet(
        onDismissRequest = acciones.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        MovimientoFormEstadoYContenido(
            planId = planId,
            tipoInicial = tipoInicial,
            cargando = cargando,
            viewModel = viewModel,
            acciones = acciones,
            movimientoExistente = movimientoExistente,
        )
    }
}

/**
 * The pure form state + content, without the [ModalBottomSheet] chrome (M6 Slice B, design Decision
 * 3): reused as-is by [MovimientoFormSheet] (compact, wrapped in a bottom sheet) and directly by the
 * expanded-layout detail pane (`MovimientoFormularioPanel`), so the fields/logic stay identical
 * between both layouts and only the wrapping shell differs.
 */
@Suppress("LongParameterList")
@Composable
internal fun MovimientoFormEstadoYContenido(
    planId: String,
    tipoInicial: TipoCategoria,
    cargando: Boolean,
    viewModel: MovimientosViewModel,
    acciones: MovimientoFormAcciones,
    movimientoExistente: Movimiento? = null,
) {
    var tipo by rememberSaveable { mutableStateOf(movimientoExistente?.let { tipoDeMovimiento(it) } ?: tipoInicial) }
    var categoriaId by rememberSaveable { mutableStateOf(movimientoExistente?.categoriaId.orEmpty()) }
    var montoTexto by rememberSaveable {
        mutableStateOf(movimientoExistente?.monto?.let { montoInicialTexto(it) }.orEmpty())
    }
    var descripcion by rememberSaveable { mutableStateOf(movimientoExistente?.descripcion.orEmpty()) }
    var errorLocal by remember { mutableStateOf<Int?>(null) }
    var modoEdicion by rememberSaveable { mutableStateOf(movimientoExistente == null) }

    val categoriasFlow = remember(planId, tipo) { viewModel.categorias(planId, tipo) }
    val categoriasDisponibles by categoriasFlow.collectAsStateWithLifecycle()

    LaunchedEffect(categoriasDisponibles) {
        if (categoriasDisponibles.isNotEmpty() && categoriasDisponibles.none { it.id == categoriaId }) {
            categoriaId = categoriasDisponibles.first().id
        }
    }

    MovimientoFormContenido(
        editando = movimientoExistente != null,
        modoEdicion = modoEdicion,
        tipo = tipo,
        onTipoChange = { nuevoTipo ->
            if (nuevoTipo != tipo) categoriaId = ""
            tipo = nuevoTipo
        },
        montoTexto = montoTexto,
        onMontoChange = { montoTexto = it },
        categoriasDisponibles = categoriasDisponibles,
        categoriaId = categoriaId,
        onCategoriaChange = { categoriaId = it },
        descripcion = descripcion,
        onDescripcionChange = { descripcion = it },
        errorLocal = errorLocal,
        cargando = cargando,
        onEditarClick = { modoEdicion = true },
        onCancelarClick = {
            categoriaId = movimientoExistente?.categoriaId
                ?.takeIf { id -> categoriasDisponibles.any { it.id == id } }
                ?: categoriasDisponibles.firstOrNull()?.id.orEmpty()
            montoTexto = movimientoExistente?.monto?.let { montoInicialTexto(it) }.orEmpty()
            descripcion = movimientoExistente?.descripcion.orEmpty()
            errorLocal = null
            modoEdicion = false
        },
        onGuardarClick = {
            val entrada = MovimientoFormEntrada(
                planId = planId,
                tipo = tipo,
                categoriaId = categoriaId,
                montoTexto = montoTexto,
                descripcion = descripcion,
                fecha = movimientoExistente?.fecha ?: LocalDate.now(),
            )
            errorLocal = validarYGuardar(entrada, acciones.onGuardar)
        },
        onEliminar = acciones.onEliminar,
    )
}

@Suppress("LongParameterList")
@Composable
private fun MovimientoFormContenido(
    editando: Boolean,
    modoEdicion: Boolean,
    tipo: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    montoTexto: String,
    onMontoChange: (String) -> Unit,
    categoriasDisponibles: List<Categoria>,
    categoriaId: String,
    onCategoriaChange: (String) -> Unit,
    descripcion: String,
    onDescripcionChange: (String) -> Unit,
    errorLocal: Int?,
    cargando: Boolean,
    onEditarClick: () -> Unit,
    onCancelarClick: () -> Unit,
    onGuardarClick: () -> Unit,
    onEliminar: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (editando) R.string.movements_edit else R.string.movements_add),
            style = MaterialTheme.typography.titleMedium,
        )
        FiltroTipoMovimiento(tipoSeleccionado = tipo, onTipoChange = onTipoChange, habilitado = !editando)
        OutlinedTextField(
            value = montoTexto,
            onValueChange = onMontoChange,
            label = { Text(stringResource(R.string.movements_amount)) },
            singleLine = true,
            enabled = modoEdicion,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectorCategoria(
            categorias = categoriasDisponibles,
            categoriaSeleccionada = categoriaId,
            onCategoriaChange = onCategoriaChange,
            habilitado = modoEdicion,
        )
        OutlinedTextField(
            value = descripcion,
            onValueChange = onDescripcionChange,
            label = { Text(stringResource(R.string.movements_description)) },
            singleLine = true,
            enabled = modoEdicion,
            modifier = Modifier.fillMaxWidth(),
        )
        errorLocal?.let {
            Text(text = stringResource(it), color = MaterialTheme.colorScheme.error)
        }
        MovimientoFormAccionesRow(
            editando = editando,
            modoEdicion = modoEdicion,
            cargando = cargando,
            onEditarClick = onEditarClick,
            onCancelarClick = onCancelarClick,
            onGuardarClick = onGuardarClick,
            onEliminar = onEliminar,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun MovimientoFormAccionesRow(
    editando: Boolean,
    modoEdicion: Boolean,
    cargando: Boolean,
    onEditarClick: () -> Unit,
    onCancelarClick: () -> Unit,
    onGuardarClick: () -> Unit,
    onEliminar: (() -> Unit)?,
) {
    if (editando && !modoEdicion) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEditarClick) {
                Text(stringResource(R.string.movements_edit_action))
            }
        }
        return
    }
    val eliminarClick = onEliminar.takeIf { editando }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (eliminarClick != null) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        if (eliminarClick != null) {
            TextButton(onClick = eliminarClick, enabled = !cargando) {
                Text(stringResource(R.string.movements_delete))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editando) {
                TextButton(onClick = onCancelarClick, enabled = !cargando) {
                    Text(stringResource(R.string.movements_cancel))
                }
            }
            TextButton(enabled = !cargando, onClick = onGuardarClick) {
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.movements_save))
                }
            }
        }
    }
}

private fun validarYGuardar(entrada: MovimientoFormEntrada, onGuardar: (MovimientoFormDatos) -> Unit): Int? {
    val montoDouble = entrada.montoTexto.replace(",", ".").toDoubleOrNull()
    return when {
        montoDouble == null || montoDouble <= 0.0 -> R.string.movements_invalid_amount
        entrada.categoriaId.isBlank() -> R.string.movements_select_category
        else -> {
            onGuardar(
                MovimientoFormDatos(
                    planId = entrada.planId,
                    tipo = entrada.tipo,
                    categoriaId = entrada.categoriaId,
                    monto = Monto(Math.round(montoDouble * CENTIMOS_POR_UNIDAD)),
                    fecha = entrada.fecha,
                    descripcion = entrada.descripcion.trim().ifBlank { null },
                ),
            )
            null
        }
    }
}

@Composable
private fun SelectorCategoria(
    categorias: List<Categoria>,
    categoriaSeleccionada: String,
    onCategoriaChange: (String) -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categorias, key = { it.id }) { categoria ->
            val seleccionada = categoria.id == categoriaSeleccionada
            Surface(
                onClick = { onCategoriaChange(categoria.id) },
                enabled = habilitado,
                shape = CircleShape,
                color = if (seleccionada) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = LocalContentColor.current.copy(alpha = if (habilitado) 1f else 0.38f),
                modifier = Modifier.size(48.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = iconoParaClave(categoria.icono), contentDescription = categoria.nombre)
                }
            }
        }
    }
}

