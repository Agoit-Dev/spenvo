package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.LocalDate

private sealed interface FormularioMovimiento {
    data object Cerrado : FormularioMovimiento
    data object Nuevo : FormularioMovimiento
}

@Composable
fun MovimientosScreen(
    planId: String,
    onVerMiembros: () -> Unit,
    onGestionarCategorias: () -> Unit,
    viewModel: MovimientosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    var tipoSeleccionado by rememberSaveable { mutableStateOf(TipoCategoria.GASTO) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var formulario by remember { mutableStateOf<FormularioMovimiento>(FormularioMovimiento.Cerrado) }

    val movimientosFlow = remember(planId) { viewModel.movimientos(planId) }
    val movimientos by movimientosFlow.collectAsStateWithLifecycle()
    val categoriasFlow = remember(planId, tipoSeleccionado) { viewModel.categorias(planId, tipoSeleccionado) }
    val categorias by categoriasFlow.collectAsStateWithLifecycle()
    val estadoForm by viewModel.estadoForm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    EfectosMovimientos(planId, estadoForm, viewModel, snackbarHostState) {
        formulario = FormularioMovimiento.Cerrado
    }

    val categoriasPorId = remember(categorias) { categorias.associateBy { it.id } }
    val movimientosFiltrados = remember(movimientos, tipoSeleccionado, busqueda, categoriasPorId) {
        filtrarMovimientos(movimientos, tipoSeleccionado, busqueda, categoriasPorId)
    }

    MovimientosScaffold(
        modifier = modifier,
        acciones = MovimientosAcciones(
            onVerMiembros = onVerMiembros,
            onGestionarCategorias = onGestionarCategorias,
            onNuevoMovimiento = { formulario = FormularioMovimiento.Nuevo },
        ),
        filtro = MovimientosFiltro(
            busqueda = busqueda,
            onBusquedaChange = { busqueda = it },
            tipoSeleccionado = tipoSeleccionado,
            onTipoChange = { tipoSeleccionado = it },
        ),
        snackbarHostState = snackbarHostState,
        lista = MovimientosListaEstado(movimientosFiltrados, categoriasPorId),
    )

    if (formulario is FormularioMovimiento.Nuevo) {
        MovimientoFormSheet(
            planId = planId,
            tipoInicial = tipoSeleccionado,
            cargando = estadoForm.guardando,
            viewModel = viewModel,
            acciones = MovimientoFormAcciones(
                onGuardar = { viewModel.guardar(it) },
                onDismiss = { formulario = FormularioMovimiento.Cerrado },
            ),
        )
    }
}

@Composable
private fun EfectosMovimientos(
    planId: String,
    estadoForm: MovimientoFormEstado,
    viewModel: MovimientosViewModel,
    snackbarHostState: SnackbarHostState,
    onFormularioGuardado: () -> Unit,
) {
    LaunchedEffect(planId) { viewModel.sincronizar(planId) }
    LaunchedEffect(estadoForm.error) {
        estadoForm.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirError()
        }
    }
    LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            onFormularioGuardado()
            viewModel.consumirGuardado()
        }
    }
}

private fun filtrarMovimientos(
    movimientos: List<Movimiento>,
    tipoSeleccionado: TipoCategoria,
    busqueda: String,
    categoriasPorId: Map<String, Categoria>,
): List<Movimiento> = movimientos.filter { movimiento ->
    val coincideTipo = when (tipoSeleccionado) {
        TipoCategoria.GASTO -> movimiento is Gasto
        TipoCategoria.INGRESO -> movimiento is Ingreso
    }
    val coincideBusqueda = busqueda.isBlank() ||
        movimiento.descripcion.orEmpty().contains(busqueda, ignoreCase = true) ||
        categoriasPorId[movimiento.categoriaId]?.nombre.orEmpty().contains(busqueda, ignoreCase = true)
    coincideTipo && coincideBusqueda
}

private data class MovimientosAcciones(
    val onVerMiembros: () -> Unit,
    val onGestionarCategorias: () -> Unit,
    val onNuevoMovimiento: () -> Unit,
)

private data class MovimientosFiltro(
    val busqueda: String,
    val onBusquedaChange: (String) -> Unit,
    val tipoSeleccionado: TipoCategoria,
    val onTipoChange: (TipoCategoria) -> Unit,
)

private data class MovimientosListaEstado(
    val movimientos: List<Movimiento>,
    val categoriasPorId: Map<String, Categoria>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovimientosScaffold(
    acciones: MovimientosAcciones,
    filtro: MovimientosFiltro,
    snackbarHostState: SnackbarHostState,
    lista: MovimientosListaEstado,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { MovimientosTopBar(acciones.onGestionarCategorias, acciones.onVerMiembros) },
        floatingActionButton = {
            FloatingActionButton(onClick = acciones.onNuevoMovimiento) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.movements_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = filtro.busqueda,
                onValueChange = filtro.onBusquedaChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.movements_search_placeholder)) },
            )
            FiltroTipoMovimiento(
                tipoSeleccionado = filtro.tipoSeleccionado,
                onTipoChange = filtro.onTipoChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListaMovimientos(
                movimientos = lista.movimientos,
                categoriasPorId = lista.categoriasPorId,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovimientosTopBar(onGestionarCategorias: () -> Unit, onVerMiembros: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.movements_title)) },
        actions = {
            IconButton(onClick = onGestionarCategorias) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.movements_manage_categories),
                )
            }
            IconButton(onClick = onVerMiembros) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = stringResource(R.string.movements_view_members),
                )
            }
        },
    )
}

@Composable
internal fun FiltroTipoMovimiento(
    tipoSeleccionado: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.GASTO,
            onClick = { onTipoChange(TipoCategoria.GASTO) },
            label = { Text(stringResource(R.string.movements_filter_expense)) },
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.INGRESO,
            onClick = { onTipoChange(TipoCategoria.INGRESO) },
            label = { Text(stringResource(R.string.movements_filter_income)) },
        )
    }
}

@Composable
private fun ListaMovimientos(
    movimientos: List<Movimiento>,
    categoriasPorId: Map<String, Categoria>,
    modifier: Modifier = Modifier,
) {
    if (movimientos.isEmpty()) {
        Text(text = stringResource(R.string.movements_empty), modifier = modifier.padding(16.dp))
        return
    }
    val hoyTexto = stringResource(R.string.movements_date_today)
    val ayerTexto = stringResource(R.string.movements_date_yesterday)
    val grupos = remember(movimientos, hoyTexto, ayerTexto) {
        movimientos.groupBy { etiquetaFecha(it.fecha, hoyTexto, ayerTexto) }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grupos.forEach { (etiqueta, movimientosDelGrupo) ->
            item(key = etiqueta) {
                Text(text = etiqueta, style = MaterialTheme.typography.labelLarge)
            }
            items(movimientosDelGrupo, key = { it.id }) { movimiento ->
                MovimientoItem(movimiento = movimiento, categoria = categoriasPorId[movimiento.categoriaId])
            }
        }
    }
}

private fun etiquetaFecha(fecha: LocalDate, hoy: String, ayer: String): String = when (fecha) {
    LocalDate.now() -> hoy
    LocalDate.now().minusDays(1) -> ayer
    else -> fecha.toString()
}
