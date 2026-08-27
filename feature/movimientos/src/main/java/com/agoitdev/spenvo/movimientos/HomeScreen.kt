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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.model.TipoCategoria
import kotlin.math.abs

@Composable
fun HomeScreen(
    planId: String,
    movimientosViewModel: MovimientosViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val plan by remember(planId) { viewModel.plan(planId) }.collectAsStateWithLifecycle()
    val resumen by remember(planId) { viewModel.resumenMensual(planId) }.collectAsStateWithLifecycle()
    val balanceAcumulado by remember(planId) { viewModel.balanceAcumulado(planId) }.collectAsStateWithLifecycle()
    var tipoFormularioAbierto by rememberSaveable { mutableStateOf<TipoCategoria?>(null) }
    val estadoForm by movimientosViewModel.estadoForm.collectAsStateWithLifecycle()

    LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            tipoFormularioAbierto = null
            movimientosViewModel.consumir(guardado = true)
        }
    }

    HomeContenido(
        nombrePlan = plan?.nombre.orEmpty(),
        moneda = plan?.moneda.orEmpty(),
        resumen = resumen,
        balanceAcumulado = balanceAcumulado,
        onNuevoGasto = { tipoFormularioAbierto = TipoCategoria.GASTO },
        onNuevoIngreso = { tipoFormularioAbierto = TipoCategoria.INGRESO },
        modifier = modifier,
    )

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
                text = formatearMontoPlano(balanceAcumulado, moneda),
                style = MaterialTheme.typography.headlineMedium,
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
            modifier = Modifier.weight(1f),
        )
        ResumenMensualCard(
            etiqueta = stringResource(R.string.home_expense_label),
            monto = gastosMes,
            moneda = moneda,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResumenMensualCard(etiqueta: String, monto: Monto, moneda: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = etiqueta, style = MaterialTheme.typography.bodySmall)
            Text(text = formatearMontoPlano(monto, moneda), style = MaterialTheme.typography.titleMedium)
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
        Surface(shape = CircleShape, modifier = Modifier.size(56.dp)) {
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

private const val UNIDADES_MENORES_POR_UNIDAD = 100L

private fun formatearMontoPlano(monto: Monto, moneda: String): String {
    val negativo = monto.unidadesMenores < 0
    val valorAbsoluto = abs(monto.unidadesMenores)
    val enteros = valorAbsoluto / UNIDADES_MENORES_POR_UNIDAD
    val centimos = valorAbsoluto % UNIDADES_MENORES_POR_UNIDAD
    val signo = if (negativo) "-" else ""
    return "$signo$enteros,${centimos.toString().padStart(2, '0')} $moneda"
}
