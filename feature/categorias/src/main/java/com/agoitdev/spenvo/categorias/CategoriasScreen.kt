package com.agoitdev.spenvo.categorias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
import com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarDialog
import com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarTextos
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria

private sealed interface FormularioCategoria {
    data object Cerrado : FormularioCategoria
    data object Nueva : FormularioCategoria
    data class Editar(val categoria: Categoria) : FormularioCategoria
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(
    planId: String,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: CategoriasViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    var tipoSeleccionado by rememberSaveable { mutableStateOf(TipoCategoria.GASTO) }
    val categoriasFlow = remember(planId, tipoSeleccionado) {
        viewModel.categorias(planId, tipoSeleccionado)
    }
    val categorias by categoriasFlow.collectAsStateWithLifecycle()
    val estadoForm by viewModel.estadoForm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var formulario by remember { mutableStateOf<FormularioCategoria>(FormularioCategoria.Cerrado) }

    LaunchedEffect(planId) { viewModel.sincronizar(planId) }
    LaunchedEffect(estadoForm.error) {
        estadoForm.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirError()
        }
    }
    LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            formulario = FormularioCategoria.Cerrado
            viewModel.consumirGuardado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { CategoriasTopBar(avatarUrl = avatarUrl, onAbrirCuenta = onAbrirCuenta) },
        floatingActionButton = {
            FloatingActionButton(onClick = { formulario = FormularioCategoria.Nueva }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.categories_add),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FiltroTipo(
                tipoSeleccionado = tipoSeleccionado,
                onTipoChange = { tipoSeleccionado = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            GrillaCategorias(
                categorias = categorias,
                onCategoriaClick = { formulario = FormularioCategoria.Editar(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    CategoriaFormularioSheet(
        planId = planId,
        tipoPorDefecto = tipoSeleccionado,
        formulario = formulario,
        viewModel = viewModel,
        onCerrar = { formulario = FormularioCategoria.Cerrado },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriasTopBar(avatarUrl: String?, onAbrirCuenta: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.categories_title)) },
        actions = {
            AvatarTopBarAction(
                photoUrl = avatarUrl,
                contentDescription = stringResource(R.string.account_menu_description),
                onClick = onAbrirCuenta,
            )
        },
    )
}

@Composable
private fun CategoriaFormularioSheet(
    planId: String,
    tipoPorDefecto: TipoCategoria,
    formulario: FormularioCategoria,
    viewModel: CategoriasViewModel,
    onCerrar: () -> Unit,
) {
    if (formulario is FormularioCategoria.Cerrado) return
    val categoriaExistente = (formulario as? FormularioCategoria.Editar)?.categoria
    var mostrarConfirmarEliminar by remember(formulario) { mutableStateOf(false) }
    val estadoForm by viewModel.estadoForm.collectAsStateWithLifecycle()

    CategoriaFormSheet(
        tipoInicial = categoriaExistente?.tipo ?: tipoPorDefecto,
        cargando = estadoForm.guardando,
        categoriaExistente = categoriaExistente,
        acciones = CategoriaFormAcciones(
            onGuardar = { nombre, tipo, icono ->
                if (categoriaExistente == null) {
                    viewModel.crear(planId = planId, nombre = nombre, tipo = tipo, icono = icono)
                } else {
                    viewModel.actualizar(categoriaExistente.copy(nombre = nombre, tipo = tipo, icono = icono))
                }
            },
            onDismiss = onCerrar,
            onEliminar = categoriaExistente?.let { { mostrarConfirmarEliminar = true } },
        ),
    )

    if (mostrarConfirmarEliminar && categoriaExistente != null) {
        ConfirmarEliminarDialog(
            textos = ConfirmarEliminarTextos(
                titulo = stringResource(R.string.categories_delete_confirm_title),
                mensaje = stringResource(R.string.categories_delete_confirm_message),
                confirmar = stringResource(R.string.categories_delete),
                cancelar = stringResource(R.string.categories_cancel),
            ),
            onConfirmar = {
                mostrarConfirmarEliminar = false
                viewModel.eliminar(categoriaExistente)
            },
            onCancelar = { mostrarConfirmarEliminar = false },
        )
    }
}

@Composable
private fun FiltroTipo(
    tipoSeleccionado: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.GASTO,
            onClick = { onTipoChange(TipoCategoria.GASTO) },
            label = { Text(stringResource(R.string.categories_filter_expense)) },
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.INGRESO,
            onClick = { onTipoChange(TipoCategoria.INGRESO) },
            label = { Text(stringResource(R.string.categories_filter_income)) },
        )
    }
}

@Composable
private fun GrillaCategorias(
    categorias: List<Categoria>,
    onCategoriaClick: (Categoria) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categorias.isEmpty()) {
        Text(
            text = stringResource(R.string.categories_empty),
            modifier = modifier.padding(16.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(categorias, key = { it.id }) { categoria ->
            CategoriaTile(categoria = categoria, onClick = { onCategoriaClick(categoria) })
        }
    }
}

@Composable
private fun CategoriaTile(categoria: Categoria, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = iconoParaClave(categoria.icono), contentDescription = null)
            Text(
                text = categoria.nombre,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private data class CategoriaFormAcciones(
    val onGuardar: (nombre: String, tipo: TipoCategoria, icono: String) -> Unit,
    val onDismiss: () -> Unit,
    val onEliminar: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriaFormSheet(
    tipoInicial: TipoCategoria,
    cargando: Boolean,
    acciones: CategoriaFormAcciones,
    categoriaExistente: Categoria? = null,
) {
    var nombre by rememberSaveable { mutableStateOf(categoriaExistente?.nombre.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(tipoInicial) }
    var icono by rememberSaveable { mutableStateOf(categoriaExistente?.icono ?: CLAVES_ICONOS.first()) }

    ModalBottomSheet(onDismissRequest = acciones.onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (categoriaExistente == null) R.string.categories_add else R.string.categories_edit,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(stringResource(R.string.categories_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FiltroTipo(tipoSeleccionado = tipo, onTipoChange = { tipo = it })
            SelectorIcono(iconoSeleccionado = icono, onIconoChange = { icono = it })
            FormSheetBotones(
                cargando = cargando,
                onEliminar = acciones.onEliminar,
                onGuardar = { acciones.onGuardar(nombre.trim(), tipo, icono) },
            )
        }
    }
}

@Composable
private fun FormSheetBotones(
    cargando: Boolean,
    onEliminar: (() -> Unit)?,
    onGuardar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (onEliminar != null) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        if (onEliminar != null) {
            TextButton(onClick = onEliminar, enabled = !cargando) {
                Text(stringResource(R.string.categories_delete))
            }
        }
        TextButton(onClick = onGuardar, enabled = !cargando) {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.categories_save))
            }
        }
    }
}

@Composable
private fun SelectorIcono(
    iconoSeleccionado: String,
    onIconoChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CLAVES_ICONOS) { clave ->
            val seleccionado = clave == iconoSeleccionado
            Surface(
                onClick = { onIconoChange(clave) },
                shape = CircleShape,
                color = if (seleccionado) {
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
                    Icon(imageVector = iconoParaClave(clave), contentDescription = clave)
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 3
