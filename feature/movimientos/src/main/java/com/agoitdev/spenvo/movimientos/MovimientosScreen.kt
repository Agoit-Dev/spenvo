package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
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
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import java.time.LocalDate

@Composable
fun MovimientosScreen(
    planId: String,
    viewModel: MovimientosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    var tipoSeleccionado by rememberSaveable { mutableStateOf<TipoCategoria?>(null) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var formulario by remember { mutableStateOf<FormularioMovimiento>(FormularioMovimiento.Cerrado) }

    val movimientos by remember(planId) { viewModel.movimientos(planId) }.collectAsStateWithLifecycle()
    val categorias by remember(planId) { viewModel.categoriasTodas(planId) }.collectAsStateWithLifecycle()
    val estadoForm by viewModel.estadoForm.collectAsStateWithLifecycle()
    val conflictos by viewModel.conflictos.collectAsStateWithLifecycle()
    val conflictoVisible by viewModel.conflictoVisible.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    EfectosMovimientos(planId, estadoForm, viewModel, snackbarHostState) { formulario = FormularioMovimiento.Cerrado }

    val categoriasPorId = remember(categorias) { categorias.associateBy { it.id } }
    val movimientosFiltrados = remember(movimientos, tipoSeleccionado, busqueda, categoriasPorId) {
        filtrarMovimientos(movimientos, tipoSeleccionado, busqueda, categoriasPorId)
    }

    MovimientosPantallas(
        modifier = modifier,
        acciones = MovimientosAcciones(
            onNuevoMovimiento = { formulario = FormularioMovimiento.Nuevo },
        ),
        filtro = MovimientosFiltro(
            busqueda = busqueda,
            onBusquedaChange = { busqueda = it },
            tipoSeleccionado = tipoSeleccionado,
            onTipoChange = { tipoSeleccionado = it },
        ),
        snackbarHostState = snackbarHostState,
        lista = MovimientosListaEstado(
            movimientos = movimientosFiltrados,
            categoriasPorId = categoriasPorId,
            conflictos = conflictos,
            onMovimientoClick = { movimiento ->
                if (tieneConflicto(conflictos, movimiento)) {
                    viewModel.mostrarConflicto(movimiento)
                } else {
                    formulario = FormularioMovimiento.Editar(movimiento)
                }
            },
        ),
        formularioParametros = MovimientoFormularioParametros(
            planId = planId,
            tipoPorDefecto = tipoSeleccionado ?: TipoCategoria.GASTO,
            formulario = formulario,
            viewModel = viewModel,
            cargando = estadoForm.guardando,
            onCerrar = { formulario = FormularioMovimiento.Cerrado },
        ),
        viewModel = viewModel,
        conflictoVisible = conflictoVisible,
        movimientos = movimientos,
    )
}

/**
 * Chooses the layout (M6 Slice B) and hosts the layout-independent conflict dialog. NOT the
 * nav-graph SceneStrategy, which would replace the list with a
 * full-screen push on compact instead of preserving it behind a sheet.
 * `directive.maxHorizontalPartitions > 1` matches Material's Expanded width breakpoint (>= 840dp);
 * compact/medium stay single-pane, unchanged from before M6.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MovimientosPantallas(
    modifier: Modifier,
    acciones: MovimientosAcciones,
    filtro: MovimientosFiltro,
    snackbarHostState: SnackbarHostState,
    lista: MovimientosListaEstado,
    formularioParametros: MovimientoFormularioParametros,
    viewModel: MovimientosViewModel,
    conflictoVisible: ConflictoEdicion?,
    movimientos: List<Movimiento>,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    if (directive.maxHorizontalPartitions > 1) {
        MovimientosPantallaExpandida(
            modifier, directive, acciones, filtro, snackbarHostState, lista, formularioParametros,
        )
    } else {
        MovimientosPantallaCompacta(modifier, acciones, filtro, snackbarHostState, lista, formularioParametros)
    }

    ConflictoMovimientoDialogHost(
        conflicto = conflictoVisible,
        movimientoLocal = movimientos.firstOrNull { it.id == conflictoVisible?.registroId },
        onUsarLocal = { m -> viewModel.resolverConflicto(m, usarLocal = true) },
        onUsarRemoto = { m -> viewModel.resolverConflicto(m, usarLocal = false) },
        onDismiss = { viewModel.mostrarConflicto(null) },
    )
}

/** Expanded/wide layout (M6 Slice B): list pane keeps today's whole scaffold; the form is promoted
 * into the detail pane instead of a full-screen bottom sheet, so the list stays visible beside it. */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun MovimientosPantallaExpandida(
    modifier: Modifier,
    directive: PaneScaffoldDirective,
    acciones: MovimientosAcciones,
    filtro: MovimientosFiltro,
    snackbarHostState: SnackbarHostState,
    lista: MovimientosListaEstado,
    formularioParametros: MovimientoFormularioParametros,
) {
    ListDetailPaneScaffold(
        modifier = modifier,
        directive = directive,
        value = ThreePaneScaffoldValue(
            primary = PaneAdaptedValue.Expanded,
            secondary = PaneAdaptedValue.Expanded,
            tertiary = PaneAdaptedValue.Hidden,
        ),
        listPane = {
            MovimientosScaffold(
                acciones = acciones, filtro = filtro, snackbarHostState = snackbarHostState, lista = lista,
            )
        },
        detailPane = { MovimientoFormularioPanel(formularioParametros) },
    )
}

/** Compact/narrow layout: exactly today's behavior — no list/detail pane split. */
@Suppress("LongParameterList")
@Composable
internal fun MovimientosPantallaCompacta(
    modifier: Modifier,
    acciones: MovimientosAcciones,
    filtro: MovimientosFiltro,
    snackbarHostState: SnackbarHostState,
    lista: MovimientosListaEstado,
    formularioParametros: MovimientoFormularioParametros,
) {
    MovimientosScaffold(
        modifier = modifier, acciones = acciones, filtro = filtro, snackbarHostState = snackbarHostState, lista = lista,
    )
    MovimientoFormularioSheet(formularioParametros)
}

private fun tieneConflicto(conflictos: Map<String, ConflictoEdicion>, movimiento: Movimiento): Boolean =
    conflictos.values.any { it.registroId == movimiento.id }

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
            viewModel.consumir(error = true)
        }
    }
    LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            onFormularioGuardado()
            viewModel.consumir(guardado = true)
        }
    }
}

private fun filtrarMovimientos(
    movimientos: List<Movimiento>,
    tipoSeleccionado: TipoCategoria?,
    busqueda: String,
    categoriasPorId: Map<String, Categoria>,
): List<Movimiento> = movimientos.filter { movimiento ->
    val coincideTipo = when (tipoSeleccionado) {
        null -> true
        TipoCategoria.GASTO -> movimiento is Gasto
        TipoCategoria.INGRESO -> movimiento is Ingreso
    }
    val coincideBusqueda = busqueda.isBlank() ||
        movimiento.descripcion.orEmpty().contains(busqueda, ignoreCase = true) ||
        categoriasPorId[movimiento.categoriaId]?.nombre.orEmpty().contains(busqueda, ignoreCase = true)
    coincideTipo && coincideBusqueda
}

internal data class MovimientosAcciones(
    val onNuevoMovimiento: () -> Unit,
)

internal data class MovimientosFiltro(
    val busqueda: String,
    val onBusquedaChange: (String) -> Unit,
    val tipoSeleccionado: TipoCategoria?,
    val onTipoChange: (TipoCategoria?) -> Unit,
)

internal data class MovimientosListaEstado(
    val movimientos: List<Movimiento>,
    val categoriasPorId: Map<String, Categoria>,
    val conflictos: Map<String, ConflictoEdicion>,
    val onMovimientoClick: (Movimiento) -> Unit,
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
        topBar = { MovimientosTopBar() },
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
            FiltroTipoMovimientoLista(
                tipoSeleccionado = filtro.tipoSeleccionado,
                onTipoChange = filtro.onTipoChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListaMovimientos(
                movimientos = lista.movimientos,
                categoriasPorId = lista.categoriasPorId,
                conflictos = lista.conflictos,
                onMovimientoClick = lista.onMovimientoClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ListaMovimientos(
    movimientos: List<Movimiento>,
    categoriasPorId: Map<String, Categoria>,
    conflictos: Map<String, ConflictoEdicion>,
    onMovimientoClick: (Movimiento) -> Unit,
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
                MovimientoItem(
                    movimiento = movimiento,
                    categoria = categoriasPorId[movimiento.categoriaId],
                    onClick = { onMovimientoClick(movimiento) },
                    tieneConflicto = tieneConflicto(conflictos, movimiento),
                )
            }
        }
    }
}

private fun etiquetaFecha(fecha: LocalDate, hoy: String, ayer: String): String = when (fecha) {
    LocalDate.now() -> hoy
    LocalDate.now().minusDays(1) -> ayer
    else -> fecha.toString()
}
