package com.agoitdev.spenvo.movimientos

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
import androidx.compose.ui.graphics.vector.ImageVector

private val ICONOS = mapOf(
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

fun iconoParaClave(clave: String): ImageVector = ICONOS[clave] ?: Icons.Filled.Category
