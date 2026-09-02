@file:Suppress("TooManyFunctions", "UnusedPrivateMember")

package com.agoitdev.spenvo.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val PreviewDynamicLightColorScheme = lightColorScheme(
    primary = Color(0xFF405F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF284777),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF565F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF573E5C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF405F91),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color.Black,
    surfaceDim = Color(0xFFD9D9E0),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    primaryFixed = Color(0xFFD6E3FF),
    primaryFixedDim = Color(0xFFA9C7FF),
    onPrimaryFixed = Color(0xFF001B3E),
    onPrimaryFixedVariant = Color(0xFF284777),
    secondaryFixed = Color(0xFFDAE2F9),
    secondaryFixedDim = Color(0xFFBEC6DC),
    onSecondaryFixed = Color(0xFF131C2B),
    onSecondaryFixedVariant = Color(0xFF3E4759),
    tertiaryFixed = Color(0xFFFAD8FD),
    tertiaryFixedDim = Color(0xFFDDBCE0),
    onTertiaryFixed = Color(0xFF28132E),
    onTertiaryFixedVariant = Color(0xFF573E5C),
)

private val PreviewDynamicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF405F91),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFA9C7FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color.Black,
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    primaryFixed = Color(0xFFD6E3FF),
    primaryFixedDim = Color(0xFFA9C7FF),
    onPrimaryFixed = Color(0xFF001B3E),
    onPrimaryFixedVariant = Color(0xFF284777),
    secondaryFixed = Color(0xFFDAE2F9),
    secondaryFixedDim = Color(0xFFBEC6DC),
    onSecondaryFixed = Color(0xFF131C2B),
    onSecondaryFixedVariant = Color(0xFF3E4759),
    tertiaryFixed = Color(0xFFFAD8FD),
    tertiaryFixedDim = Color(0xFFDDBCE0),
    onTertiaryFixed = Color(0xFF28132E),
    onTertiaryFixedVariant = Color(0xFF573E5C),
)

@SuppressLint("HardcodedText")
@Composable
private fun ThemeCatalog() {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MaterialColorCatalog() }
            item { ExtendedColorCatalog() }
            item { TypographyCatalog() }
            item { ShapeCatalog() }
            item { ComponentCatalog() }
        }
    }
}

@Composable
private fun MaterialColorCatalog() {
    val colors = MaterialTheme.colorScheme
    CatalogSection("Material color pairs") {
        CoreMaterialColorPairs(colors)
        SurfaceColorPairs(colors)
        FixedColorPairs(colors)
    }
}

@Composable
private fun CoreMaterialColorPairs(colors: ColorScheme) {
        ColorPair("Primary / onPrimary", colors.primary, colors.onPrimary)
        ColorPair(
            "Primary container / onPrimaryContainer",
            colors.primaryContainer,
            colors.onPrimaryContainer,
        )
        ColorPair("Secondary / onSecondary", colors.secondary, colors.onSecondary)
        ColorPair(
            "Secondary container / onSecondaryContainer",
            colors.secondaryContainer,
            colors.onSecondaryContainer,
        )
        ColorPair("Tertiary / onTertiary", colors.tertiary, colors.onTertiary)
        ColorPair(
            "Tertiary container / onTertiaryContainer",
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        )
        ColorPair("Error / onError", colors.error, colors.onError)
        ColorPair(
            "Error container / onErrorContainer",
            colors.errorContainer,
            colors.onErrorContainer,
        )
        ColorPair("Background / onBackground", colors.background, colors.onBackground)
        ColorPair("Surface / onSurface", colors.surface, colors.onSurface)
        ColorPair(
            "Surface variant / onSurfaceVariant",
            colors.surfaceVariant,
            colors.onSurfaceVariant,
        )
}

@Composable
private fun SurfaceColorPairs(colors: ColorScheme) {
        ColorPair("Surface dim / onSurface", colors.surfaceDim, colors.onSurface)
        ColorPair("Surface bright / onSurface", colors.surfaceBright, colors.onSurface)
        ColorPair(
            "Surface container lowest / onSurface",
            colors.surfaceContainerLowest,
            colors.onSurface,
        )
        ColorPair(
            "Surface container low / onSurface",
            colors.surfaceContainerLow,
            colors.onSurface,
        )
        ColorPair("Surface container / onSurface", colors.surfaceContainer, colors.onSurface)
        ColorPair(
            "Surface container high / onSurface",
            colors.surfaceContainerHigh,
            colors.onSurface,
        )
        ColorPair(
            "Surface container highest / onSurface",
            colors.surfaceContainerHighest,
            colors.onSurface,
        )
        ColorPair(
            "Inverse surface / inverseOnSurface",
            colors.inverseSurface,
            colors.inverseOnSurface,
        )
}

@Composable
private fun FixedColorPairs(colors: ColorScheme) {
        ColorPair("Primary fixed / onPrimaryFixed", colors.primaryFixed, colors.onPrimaryFixed)
        ColorPair(
            "Primary fixed dim / onPrimaryFixedVariant",
            colors.primaryFixedDim,
            colors.onPrimaryFixedVariant,
        )
        ColorPair(
            "Secondary fixed / onSecondaryFixed",
            colors.secondaryFixed,
            colors.onSecondaryFixed,
        )
        ColorPair(
            "Secondary fixed dim / onSecondaryFixedVariant",
            colors.secondaryFixedDim,
            colors.onSecondaryFixedVariant,
        )
        ColorPair("Tertiary fixed / onTertiaryFixed", colors.tertiaryFixed, colors.onTertiaryFixed)
        ColorPair(
            "Tertiary fixed dim / onTertiaryFixedVariant",
            colors.tertiaryFixedDim,
            colors.onTertiaryFixedVariant,
        )
}

@Composable
private fun ExtendedColorCatalog() {
    val colors = SpenvoTheme.extendedColors
    CatalogSection("Extended financial color pairs") {
        ColorPair("Income / onIncome", colors.income, colors.onIncome)
        ColorPair(
            "Income container / onIncomeContainer",
            colors.incomeContainer,
            colors.onIncomeContainer,
        )
        ColorPair("Expense / onExpense", colors.expense, colors.onExpense)
        ColorPair(
            "Expense container / onExpenseContainer",
            colors.expenseContainer,
            colors.onExpenseContainer,
        )
    }
}

@Composable
private fun CatalogSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ColorPair(label: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Text(text = label, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TypographyCatalog() {
    CatalogSection("Typography") {
        TypographySample("Display large", MaterialTheme.typography.displayLarge)
        TypographySample("Display medium", MaterialTheme.typography.displayMedium)
        TypographySample("Display small", MaterialTheme.typography.displaySmall)
        TypographySample("Headline large", MaterialTheme.typography.headlineLarge)
        TypographySample("Headline medium", MaterialTheme.typography.headlineMedium)
        TypographySample("Headline small", MaterialTheme.typography.headlineSmall)
        TypographySample("Title large", MaterialTheme.typography.titleLarge)
        TypographySample("Title medium", MaterialTheme.typography.titleMedium)
        TypographySample("Title small", MaterialTheme.typography.titleSmall)
        TypographySample("Body large", MaterialTheme.typography.bodyLarge)
        TypographySample("Body medium", MaterialTheme.typography.bodyMedium)
        TypographySample("Body small", MaterialTheme.typography.bodySmall)
        TypographySample("Label large", MaterialTheme.typography.labelLarge)
        TypographySample("Label medium", MaterialTheme.typography.labelMedium)
        TypographySample("Label small", MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TypographySample(label: String, style: TextStyle) {
    Text(text = label, style = style)
}

@Composable
private fun ShapeCatalog() {
    val shapes = MaterialTheme.shapes
    CatalogSection("Shapes") {
        ShapeSample("Extra small", shapes.extraSmall)
        ShapeSample("Small", shapes.small)
        ShapeSample("Medium", shapes.medium)
        ShapeSample("Large", shapes.large)
        ShapeSample("Extra large", shapes.extraLarge)
    }
}

@Composable
private fun ShapeSample(label: String, shape: Shape) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ComponentCatalog() {
    CatalogSection("Components") {
        TopAppBar(title = { Text("App bar") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Filled") }
            FilledTonalButton(onClick = {}) { Text("Tonal") }
            OutlinedButton(onClick = {}) { Text("Outlined") }
        }
        Button(onClick = {}, enabled = false) { Text("Disabled") }
        OutlinedTextField(
            value = "Sample value",
            onValueChange = {},
            label = { Text("Text field") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Selected") })
            FilterChip(selected = false, onClick = {}, label = { Text("Available") })
            FilterChip(
                selected = false,
                onClick = {},
                enabled = false,
                label = { Text("Disabled") },
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text("Material card", modifier = Modifier.padding(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = true, onCheckedChange = {})
            Text("Checkbox")
            Switch(checked = true, onCheckedChange = {}, modifier = Modifier.padding(start = 16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = false, onCheckedChange = {}, enabled = false)
            Text("Disabled selection")
            Switch(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        DialogSample()
    }
}

@Composable
private fun DialogSample() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Dialog title", style = MaterialTheme.typography.headlineSmall)
            Text("Dialog supporting text", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {}) { Text("Cancel") }
                TextButton(onClick = {}) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun PreviewTheme(
    colorScheme: ColorScheme,
    extendedColors: SpenvoExtendedColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpenvoExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpenvoTypography,
            shapes = SpenvoShapes,
            content = content,
        )
    }
}

@Preview(name = "Brand Light", showBackground = true, widthDp = 420, heightDp = 3600)
@Composable
private fun BrandLightThemeCatalogPreview() {
    SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
        ThemeCatalog()
    }
}

@Preview(name = "Brand Dark", showBackground = true, widthDp = 420, heightDp = 3600)
@Composable
private fun BrandDarkThemeCatalogPreview() {
    SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.BRAND) {
        ThemeCatalog()
    }
}

@Preview(name = "Dynamic Light (deterministic)", showBackground = true, widthDp = 420, heightDp = 3600)
@Composable
private fun DynamicLightThemeCatalogPreview() {
    PreviewTheme(PreviewDynamicLightColorScheme, LightExtendedColors) {
        ThemeCatalog()
    }
}

@Preview(name = "Dynamic Dark (deterministic)", showBackground = true, widthDp = 420, heightDp = 3600)
@Composable
private fun DynamicDarkThemeCatalogPreview() {
    PreviewTheme(PreviewDynamicDarkColorScheme, DarkExtendedColors) {
        ThemeCatalog()
    }
}
