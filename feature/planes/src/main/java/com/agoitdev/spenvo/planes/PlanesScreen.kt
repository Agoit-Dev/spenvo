package com.agoitdev.spenvo.planes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.designsystem.components.AvatarMenu
import com.agoitdev.spenvo.designsystem.components.AvatarMenuTextos
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.model.Sesion

const val TAG_PLANES_CARGANDO = "planes_cargando"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanesScreen(
    onCrearCuenta: () -> Unit,
    onAbrirPlan: (String) -> Unit,
    onAbrirAjustes: () -> Unit,
    viewModel: PlanesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val cargandoLista by viewModel.cargandoLista.collectAsStateWithLifecycle()
    val planes by viewModel.planes.collectAsStateWithLifecycle()
    val resumenesPorPlan by viewModel.resumenesPorPlan.collectAsStateWithLifecycle()
    val invitaciones by viewModel.invitacionesPendientes.collectAsStateWithLifecycle()
    val estadoCrear by viewModel.estadoCrear.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogoCrear by rememberSaveable { mutableStateOf(false) }

    PlanesEfectos(
        estadoCrear = estadoCrear,
        snackbarHostState = snackbarHostState,
        onErrorConsumido = viewModel::consumirError,
        onCreadoConsumido = {
            mostrarDialogoCrear = false
            viewModel.consumirCreado()
        },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            PlanesTopBar(
                sesion = sesion,
                onCrearCuenta = onCrearCuenta,
                onAbrirAjustes = onAbrirAjustes,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogoCrear = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plans_create),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PlanesLista(
            cargandoLista = cargandoLista,
            planes = planes,
            resumenesPorPlan = resumenesPorPlan,
            invitaciones = invitaciones,
            onAceptarInvitacion = viewModel::aceptarInvitacion,
            onAbrirPlan = onAbrirPlan,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (mostrarDialogoCrear) {
        CrearPlanDialog(
            creandoPlan = estadoCrear.cargando,
            onConfirmar = { nombre, moneda ->
                viewModel.crearPlan(nombre, moneda)
            },
            onCancelar = { mostrarDialogoCrear = false },
        )
    }
}

/** Extracted from [PlanesScreen] to stay under detekt's `LongMethod` function threshold. */
@Composable
private fun PlanesEfectos(
    estadoCrear: CrearPlanEstado,
    snackbarHostState: SnackbarHostState,
    onErrorConsumido: () -> Unit,
    onCreadoConsumido: () -> Unit,
) {
    LaunchedEffect(estadoCrear.error) {
        estadoCrear.error?.let {
            snackbarHostState.showSnackbar(it)
            onErrorConsumido()
        }
    }
    LaunchedEffect(estadoCrear.creado) {
        if (estadoCrear.creado) {
            onCreadoConsumido()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanesTopBar(
    sesion: Sesion,
    onCrearCuenta: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.plans_title)) },
        actions = {
            AvatarMenu(
                photoUrl = sesion.photoUrl,
                contentDescription = stringResource(R.string.account_menu_description),
                textos = AvatarMenuTextos(
                    estado = when {
                        !sesion.estaAutenticada -> null
                        sesion.email != null -> sesion.email
                        else -> stringResource(R.string.account_guest_state)
                    },
                    cuenta = stringResource(R.string.account_create),
                    ajustes = stringResource(R.string.settings_menu_item),
                ),
                onOpenAccount = onCrearCuenta,
                onOpenSettings = onAbrirAjustes,
            )
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun PlanesLista(
    cargandoLista: Boolean,
    planes: List<PlanFinanciero>,
    resumenesPorPlan: Map<String, ResumenMensualPlan>,
    invitaciones: List<AccesoPlan>,
    onAceptarInvitacion: (String) -> Unit,
    onAbrirPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cargandoLista) {
        val cargandoDescripcion = stringResource(R.string.plans_loading)
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(TAG_PLANES_CARGANDO)
                .semantics { contentDescription = cargandoDescripcion },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (invitaciones.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_invitations_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(invitaciones, key = { it.planId }) { acceso ->
                InvitacionCard(
                    acceso = acceso,
                    onAceptar = { onAceptarInvitacion(acceso.planId) },
                )
            }
        }
        if (planes.isEmpty() && invitaciones.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(planes, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    resumen = resumenesPorPlan[plan.id],
                    onClick = { onAbrirPlan(plan.id) },
                )
            }
        }
    }
}

@Composable
internal fun PlanCard(plan: PlanFinanciero, resumen: ResumenMensualPlan?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text = plan.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                text = plan.moneda,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (resumen != null) {
                Text(
                    text = stringResource(
                        R.string.plans_balance_mensual,
                        formatearNetoDelMes(resumen.netoDelMes, plan.moneda),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatearNetoDelMes(neto: Monto, moneda: String): String {
    val negativo = neto.unidadesMenores < 0
    val valorAbsoluto = kotlin.math.abs(neto.unidadesMenores)
    val enteros = valorAbsoluto / UNIDADES_MENORES_POR_UNIDAD
    val centimos = valorAbsoluto % UNIDADES_MENORES_POR_UNIDAD
    val signo = if (negativo) "-" else "+"
    return "$signo$enteros,${centimos.toString().padStart(2, '0')} $moneda"
}

private const val UNIDADES_MENORES_POR_UNIDAD = 100L

@Composable
private fun InvitacionCard(acceso: AccesoPlan, onAceptar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = acceso.planId, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.plans_invitation_role) + ": " + acceso.rol.name.lowercase(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAceptar) {
                Text(stringResource(R.string.plans_invitation_accept))
            }
        }
    }
}

@Composable
private fun CrearPlanDialog(
    creandoPlan: Boolean,
    onConfirmar: (String, String) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var moneda by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.plans_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.plans_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = moneda,
                    onValueChange = { moneda = it.uppercase() },
                    label = { Text(stringResource(R.string.plans_create_currency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nombre.trim(), moneda.trim()) },
                enabled = !creandoPlan,
            ) {
                if (creandoPlan) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.plans_create_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.plans_create_cancel))
            }
        },
    )
}
