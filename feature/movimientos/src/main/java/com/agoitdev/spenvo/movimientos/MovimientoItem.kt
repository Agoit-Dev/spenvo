package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agoitdev.spenvo.designsystem.theme.IngresoColor
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import java.util.Locale

@Composable
internal fun MovimientoItem(
    movimiento: Movimiento,
    categoria: Categoria?,
    onClick: () -> Unit = {},
    tieneConflicto: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val esIngreso = movimiento is Ingreso
    val colorMonto = if (esIngreso) IngresoColor else MaterialTheme.colorScheme.error
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = iconoParaMovimiento(categoria?.icono), contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movimiento.descripcion?.takeIf { it.isNotBlank() } ?: categoria?.nombre.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = categoria?.nombre.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tieneConflicto) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.conflict_badge_description),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = (if (esIngreso) "+ " else "- ") + formatearMonto(movimiento.monto),
                style = MaterialTheme.typography.titleMedium,
                color = colorMonto,
            )
        }
    }
}

private const val CENTIMOS_POR_UNIDAD = 100.0

private fun formatearMonto(monto: Monto): String =
    String.format(Locale.getDefault(), "%.2f €", monto.unidadesMenores / CENTIMOS_POR_UNIDAD)

private val ICONOS_MOVIMIENTO = mapOf(
    "comida" to Icons.Filled.Restaurant,
    "transporte" to Icons.Filled.DirectionsCar,
    "entretenimiento" to Icons.Filled.Movie,
    "salud" to Icons.Filled.LocalHospital,
    "vivienda" to Icons.Filled.Home,
    "mercado" to Icons.Filled.LocalGroceryStore,
    "ropa" to Icons.Filled.Checkroom,
    "suministros" to Icons.Filled.Inventory2,
    "sueldo" to Icons.Filled.Payments,
    "regalos" to Icons.Filled.CardGiftcard,
    "otra" to Icons.Filled.Category,
)

private fun iconoParaMovimiento(clave: String?): ImageVector = ICONOS_MOVIMIENTO[clave] ?: Icons.Filled.Category
