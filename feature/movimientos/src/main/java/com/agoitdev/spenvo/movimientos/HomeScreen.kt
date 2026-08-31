package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

const val TAG_HOME_INGRESOS_MES = "home_ingresos_mes"
const val TAG_HOME_GASTOS_MES = "home_gastos_mes"
const val TAG_HOME_BALANCE_ACUMULADO = "home_balance_acumulado"

@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    planId: String,
    movimientosViewModel: MovimientosViewModel,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val plan by remember(planId) { viewModel.plan(planId) }.collectAsStateWithLifecycle()
    val resumen by remember(planId) { viewModel.resumenMensual(planId) }.collectAsStateWithLifecycle()
    val balanceAcumulado by remember(planId) { viewModel.balanceAcumulado(planId) }.collectAsStateWithLifecycle()
    var tipoFormularioAbierto by rememberSaveable { mutableStateOf<TipoCategoria?>(null) }
    val estadoForm by movimientosViewModel.estadoForm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Home shares MovimientosViewModel with the Movimientos tab (see EfectosMovimientos in
    // MovimientosScreen.kt): the sync attach here and the error/guardado consumption below must
    // mirror that screen's effects exactly, or a stale error/guardado flag leaks into the other tab.
    LaunchedEffect(planId) { movimientosViewModel.sincronizar(planId) }
    LaunchedEffect(estadoForm.error) {
        estadoForm.error?.let {
            snackbarHostState.showSnackbar(it)
            movimientosViewModel.consumir(error = true)
        }
    }
    LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            tipoFormularioAbierto = null
            movimientosViewModel.consumir(guardado = true)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeTopBar(nombrePlan = plan?.nombre.orEmpty(), avatarUrl = avatarUrl, onAbrirCuenta = onAbrirCuenta)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HomeContenido(
            nombrePlan = plan?.nombre.orEmpty(),
            moneda = plan?.moneda.orEmpty(),
            resumen = resumen,
            balanceAcumulado = balanceAcumulado,
            onNuevoGasto = { tipoFormularioAbierto = TipoCategoria.GASTO },
            onNuevoIngreso = { tipoFormularioAbierto = TipoCategoria.INGRESO },
            modifier = Modifier.padding(innerPadding),
        )
    }

    tipoFormularioAbierto?.let { tipo ->
        MovimientoFormSheet(
            planId = planId,
            tipoInicial = tipo,
            cargando = estadoForm.guardando,
            viewModel = movimientosViewModel,
            acciones = MovimientoFormAcciones(
                onGuardar = movimientosViewModel::guardar,
                onDismiss = { tipoFormularioAbierto = null },
                onEliminar = null,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(nombrePlan: String, avatarUrl: String?, onAbrirCuenta: () -> Unit) {
    TopAppBar(
        title = { Text(text = nombrePlan) },
        actions = {
            AvatarTopBarAction(
                photoUrl = avatarUrl,
                contentDescription = stringResource(R.string.account_menu_description),
                onClick = onAbrirCuenta,
            )
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun HomeContenido(
    nombrePlan: String,
    moneda: String,
    resumen: ResumenMensualPlan?,
    balanceAcumulado: Monto,
    onNuevoGasto: () -> Unit,
    onNuevoIngreso: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = nombrePlan, style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium)

        BalanceAcumuladoCard(balanceAcumulado = balanceAcumulado, moneda = moneda)

        ResumenMensualRow(
            ingresosMes = resumen?.ingresosMes ?: Monto(0),
            gastosMes = resumen?.gastosMes ?: Monto(0),
            moneda = moneda,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AccionRapida(
                icono = Icons.Filled.Remove,
                etiqueta = stringResource(R.string.home_action_new_expense),
                onClick = onNuevoGasto,
            )
            AccionRapida(
                icono = Icons.Filled.Add,
                etiqueta = stringResource(R.string.home_action_new_income),
                onClick = onNuevoIngreso,
            )
        }
    }
}

@Composable
private fun BalanceAcumuladoCard(balanceAcumulado: Monto, moneda: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.home_balance_acumulado), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatearMonto(balanceAcumulado, moneda),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag(TAG_HOME_BALANCE_ACUMULADO),
            )
        }
    }
}

@Composable
private fun ResumenMensualRow(ingresosMes: Monto, gastosMes: Monto, moneda: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ResumenMensualCard(
            etiqueta = stringResource(R.string.home_income_label),
            monto = ingresosMes,
            moneda = moneda,
            testTag = TAG_HOME_INGRESOS_MES,
            modifier = Modifier.weight(1f),
        )
        ResumenMensualCard(
            etiqueta = stringResource(R.string.home_expense_label),
            monto = gastosMes,
            moneda = moneda,
            testTag = TAG_HOME_GASTOS_MES,
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ResumenMensualCard(
    etiqueta: String,
    monto: Monto,
    moneda: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = etiqueta, style = MaterialTheme.typography.bodySmall)
            Text(
                text = formatearMonto(monto, moneda),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}

@Composable
private fun AccionRapida(icono: ImageVector, etiqueta: String, onClick: () -> Unit) {
    // The whole column (icon + label) is clickable, not just the icon circle -- clicking the
    // visible label text must open the form too, both for real users and for onNodeWithText-based
    // Compose UI tests, which locate this action by its label.
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Explicit container color: an unset Surface color renders transparent against the screen
        // background, making the whole circle invisible. Surface derives a matching contentColor
        // (and thus the icon's tint) from this automatically via contentColorFor(color).
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(imageVector = icono, contentDescription = null)
            }
        }
        Text(text = etiqueta, style = MaterialTheme.typography.labelMedium)
    }
}

private const val UNIDADES_MENORES_POR_UNIDAD = 100.0

/**
 * AGENTS.md i18n rule: money goes through NumberFormat, never hand-concatenation, so the sign,
 * decimal separator and currency symbol all follow the active locale correctly.
 */
private fun formatearMonto(monto: Monto, moneda: String): String {
    val formato = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { Currency.getInstance(moneda) }.getOrNull()?.let { formato.currency = it }
    return formato.format(monto.unidadesMenores / UNIDADES_MENORES_POR_UNIDAD)
}
