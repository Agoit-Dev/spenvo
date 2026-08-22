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
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.LocalDate

private data class MovimientoFormEntrada(
    val planId: String,
    val tipo: TipoCategoria,
    val categoriaId: String,
    val montoTexto: String,
    val descripcion: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovimientoFormSheet(
    planId: String,
    tipoInicial: TipoCategoria,
    cargando: Boolean,
    viewModel: MovimientosViewModel,
    acciones: MovimientoFormAcciones,
) {
    var tipo by rememberSaveable { mutableStateOf(tipoInicial) }
    var categoriaId by rememberSaveable { mutableStateOf("") }
    var montoTexto by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<Int?>(null) }

    val categoriasFlow = remember(planId, tipo) { viewModel.categorias(planId, tipo) }
    val categoriasDisponibles by categoriasFlow.collectAsStateWithLifecycle()

    LaunchedEffect(categoriasDisponibles) {
        if (categoriaId.isBlank() || categoriasDisponibles.none { it.id == categoriaId }) {
            categoriaId = categoriasDisponibles.firstOrNull()?.id.orEmpty()
        }
    }

    ModalBottomSheet(
        onDismissRequest = acciones.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        MovimientoFormContenido(
            tipo = tipo,
            onTipoChange = { tipo = it },
            montoTexto = montoTexto,
            onMontoChange = { montoTexto = it },
            categoriasDisponibles = categoriasDisponibles,
            categoriaId = categoriaId,
            onCategoriaChange = { categoriaId = it },
            descripcion = descripcion,
            onDescripcionChange = { descripcion = it },
            errorLocal = errorLocal,
            cargando = cargando,
            onGuardarClick = {
                val entrada = MovimientoFormEntrada(planId, tipo, categoriaId, montoTexto, descripcion)
                errorLocal = validarYGuardar(entrada, acciones.onGuardar)
            },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun MovimientoFormContenido(
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
    onGuardarClick: () -> Unit,
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
        Text(text = stringResource(R.string.movements_add), style = MaterialTheme.typography.titleMedium)
        FiltroTipoMovimiento(tipoSeleccionado = tipo, onTipoChange = onTipoChange)
        OutlinedTextField(
            value = montoTexto,
            onValueChange = onMontoChange,
            label = { Text(stringResource(R.string.movements_amount)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectorCategoria(
            categorias = categoriasDisponibles,
            categoriaSeleccionada = categoriaId,
            onCategoriaChange = onCategoriaChange,
        )
        OutlinedTextField(
            value = descripcion,
            onValueChange = onDescripcionChange,
            label = { Text(stringResource(R.string.movements_description)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        errorLocal?.let {
            Text(text = stringResource(it), color = MaterialTheme.colorScheme.error)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                    fecha = LocalDate.now(),
                    descripcion = entrada.descripcion.trim().ifBlank { null },
                ),
            )
            null
        }
    }
}

private const val CENTIMOS_POR_UNIDAD = 100.0

@Composable
private fun SelectorCategoria(
    categorias: List<Categoria>,
    categoriaSeleccionada: String,
    onCategoriaChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categorias, key = { it.id }) { categoria ->
            val seleccionada = categoria.id == categoriaSeleccionada
            Surface(
                onClick = { onCategoriaChange(categoria.id) },
                shape = CircleShape,
                color = if (seleccionada) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
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
