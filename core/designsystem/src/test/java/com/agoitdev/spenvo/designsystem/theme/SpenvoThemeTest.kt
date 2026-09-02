package com.agoitdev.spenvo.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SpenvoThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `brand light exposes the approved light scheme`() {
        val scheme = captureColorScheme(ThemeMode.LIGHT, ColorMode.BRAND)

        assertColorSchemeEquals(expectedLightColorScheme(), scheme)
    }

    @Test
    fun `brand dark exposes the approved dark scheme`() {
        val scheme = captureColorScheme(ThemeMode.DARK, ColorMode.BRAND)

        assertColorSchemeEquals(expectedDarkColorScheme(), scheme)
    }

    @Test
    @Config(qualifiers = "night")
    fun `explicit light mode remains light under night qualifier`() {
        val scheme = captureColorScheme(ThemeMode.LIGHT, ColorMode.BRAND)

        assertEquals(Color(0xFF38693C), scheme.primary)
        assertEquals(Color(0xFFF7FBF2), scheme.surface)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `explicit dark mode remains dark under non-night qualifier`() {
        val scheme = captureColorScheme(ThemeMode.DARK, ColorMode.BRAND)

        assertEquals(Color(0xFF9ED49D), scheme.primary)
        assertEquals(Color(0xFF101510), scheme.surface)
    }

    @Test
    @Config(qualifiers = "night")
    fun `system mode selects dark scheme under night qualifier`() {
        val scheme = captureColorScheme(ThemeMode.SYSTEM, ColorMode.BRAND)

        assertEquals(Color(0xFF9ED49D), scheme.primary)
        assertEquals(Color(0xFF101510), scheme.surface)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `system mode selects light scheme under non-night qualifier`() {
        val scheme = captureColorScheme(ThemeMode.SYSTEM, ColorMode.BRAND)

        assertEquals(Color(0xFF38693C), scheme.primary)
        assertEquals(Color(0xFFF7FBF2), scheme.surface)
    }

    @Test
    fun `dynamic mode falls back to matching brand schemes below Android 12`() {
        lateinit var brandLight: ColorScheme
        lateinit var dynamicLight: ColorScheme
        lateinit var brandDark: ColorScheme
        lateinit var dynamicDark: ColorScheme
        composeTestRule.setContent {
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                brandLight = MaterialTheme.colorScheme
            }
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.DYNAMIC) {
                dynamicLight = MaterialTheme.colorScheme
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.BRAND) {
                brandDark = MaterialTheme.colorScheme
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.DYNAMIC) {
                dynamicDark = MaterialTheme.colorScheme
            }
        }

        assertSame(brandLight, dynamicLight)
        assertSame(brandDark, dynamicDark)
    }

    @Test
    @Config(sdk = [31])
    fun `dynamic mode uses system schemes and retains financial roles on Android 12`() {
        lateinit var brandLightScheme: ColorScheme
        lateinit var dynamicLightScheme: ColorScheme
        lateinit var brandDarkScheme: ColorScheme
        lateinit var dynamicDarkScheme: ColorScheme
        lateinit var expectedDynamicLightScheme: ColorScheme
        lateinit var expectedDynamicDarkScheme: ColorScheme
        lateinit var brandLightExtended: SpenvoExtendedColors
        lateinit var dynamicLightExtended: SpenvoExtendedColors
        lateinit var brandDarkExtended: SpenvoExtendedColors
        lateinit var dynamicDarkExtended: SpenvoExtendedColors

        composeTestRule.setContent {
            val context = LocalContext.current
            expectedDynamicLightScheme = dynamicLightColorScheme(context)
            expectedDynamicDarkScheme = dynamicDarkColorScheme(context)
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                brandLightScheme = MaterialTheme.colorScheme
                brandLightExtended = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.DYNAMIC) {
                dynamicLightScheme = MaterialTheme.colorScheme
                dynamicLightExtended = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.BRAND) {
                brandDarkScheme = MaterialTheme.colorScheme
                brandDarkExtended = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.DYNAMIC) {
                dynamicDarkScheme = MaterialTheme.colorScheme
                dynamicDarkExtended = SpenvoTheme.extendedColors
            }
        }

        assertNotEquals(brandLightScheme, dynamicLightScheme)
        assertNotEquals(brandDarkScheme, dynamicDarkScheme)
        assertColorSchemeEquals(expectedDynamicLightScheme, dynamicLightScheme)
        assertColorSchemeEquals(expectedDynamicDarkScheme, dynamicDarkScheme)
        assertSame(brandLightExtended, dynamicLightExtended)
        assertSame(brandDarkExtended, dynamicDarkExtended)
    }

    @Test
    fun `light theme exposes every approved extended color role`() {
        val colors = captureExtendedColors(ThemeMode.LIGHT, ColorMode.BRAND)

        assertEquals(Color(0xFF466730), colors.income)
        assertEquals(Color(0xFFFFFFFF), colors.onIncome)
        assertEquals(Color(0xFFC7EEA9), colors.incomeContainer)
        assertEquals(Color(0xFF304F1A), colors.onIncomeContainer)
        assertEquals(Color(0xFF8A5023), colors.expense)
        assertEquals(Color(0xFFFFFFFF), colors.onExpense)
        assertEquals(Color(0xFFFFDCC6), colors.expenseContainer)
        assertEquals(Color(0xFF6E390D), colors.onExpenseContainer)
    }

    @Test
    fun `dark theme exposes every approved extended color role`() {
        val colors = captureExtendedColors(ThemeMode.DARK, ColorMode.BRAND)

        assertEquals(Color(0xFFACD28F), colors.income)
        assertEquals(Color(0xFF1A3705), colors.onIncome)
        assertEquals(Color(0xFF304F1A), colors.incomeContainer)
        assertEquals(Color(0xFFC7EEA9), colors.onIncomeContainer)
        assertEquals(Color(0xFFFFB786), colors.expense)
        assertEquals(Color(0xFF502400), colors.onExpense)
        assertEquals(Color(0xFF6E390D), colors.expenseContainer)
        assertEquals(Color(0xFFFFDCC6), colors.onExpenseContainer)
    }

    @Test
    fun `brand and dynamic modes share extended color instances for each luminosity`() {
        lateinit var brandLight: SpenvoExtendedColors
        lateinit var dynamicLight: SpenvoExtendedColors
        lateinit var brandDark: SpenvoExtendedColors
        lateinit var dynamicDark: SpenvoExtendedColors

        composeTestRule.setContent {
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                brandLight = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.DYNAMIC) {
                dynamicLight = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.BRAND) {
                brandDark = SpenvoTheme.extendedColors
            }
            SpenvoTheme(themeMode = ThemeMode.DARK, colorMode = ColorMode.DYNAMIC) {
                dynamicDark = SpenvoTheme.extendedColors
            }
        }

        assertSame(brandLight, dynamicLight)
        assertSame(brandDark, dynamicDark)
    }

    @Test
    fun `typography implements the complete Spenvo baseline contract`() {
        lateinit var typography: Typography
        composeTestRule.setContent {
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                typography = MaterialTheme.typography
            }
        }

        assertTextStyle(typography.displayLarge, FontWeight.Normal, 57.sp, 64.sp, (-0.25).sp)
        assertTextStyle(typography.displayMedium, FontWeight.Normal, 45.sp, 52.sp, 0.sp)
        assertTextStyle(typography.displaySmall, FontWeight.Normal, 36.sp, 44.sp, 0.sp)
        assertTextStyle(typography.headlineLarge, FontWeight.Normal, 32.sp, 40.sp, 0.sp)
        assertTextStyle(typography.headlineMedium, FontWeight.Normal, 28.sp, 36.sp, 0.sp)
        assertTextStyle(typography.headlineSmall, FontWeight.Normal, 24.sp, 32.sp, 0.sp)
        assertTextStyle(typography.titleLarge, FontWeight.Normal, 22.sp, 28.sp, 0.sp)
        assertTextStyle(typography.titleMedium, FontWeight.Medium, 16.sp, 24.sp, 0.15.sp)
        assertTextStyle(typography.titleSmall, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp)
        assertTextStyle(typography.bodyLarge, FontWeight.Normal, 16.sp, 24.sp, 0.5.sp)
        assertTextStyle(typography.bodyMedium, FontWeight.Normal, 14.sp, 20.sp, 0.25.sp)
        assertTextStyle(typography.bodySmall, FontWeight.Normal, 12.sp, 16.sp, 0.4.sp)
        assertTextStyle(typography.labelLarge, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp)
        assertTextStyle(typography.labelMedium, FontWeight.Medium, 12.sp, 16.sp, 0.5.sp)
        assertTextStyle(typography.labelSmall, FontWeight.Medium, 11.sp, 16.sp, 0.5.sp)
    }

    @Test
    fun `Spenvo theme installs its typography and shapes`() {
        lateinit var typography: Typography
        lateinit var shapes: Shapes
        composeTestRule.setContent {
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                typography = MaterialTheme.typography
                shapes = MaterialTheme.shapes
            }
        }

        assertSame(SpenvoTypography, typography)
        assertSame(SpenvoShapes, shapes)
    }

    @Test
    fun `shapes implement the complete Spenvo baseline contract`() {
        lateinit var shapes: Shapes
        composeTestRule.setContent {
            SpenvoTheme(themeMode = ThemeMode.LIGHT, colorMode = ColorMode.BRAND) {
                shapes = MaterialTheme.shapes
            }
        }

        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), shapes.small)
        assertEquals(RoundedCornerShape(12.dp), shapes.medium)
        assertEquals(RoundedCornerShape(16.dp), shapes.large)
        assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
    }

    private fun captureColorScheme(themeMode: ThemeMode, colorMode: ColorMode): ColorScheme {
        lateinit var captured: ColorScheme
        composeTestRule.setContent {
            SpenvoTheme(themeMode = themeMode, colorMode = colorMode) {
                captured = MaterialTheme.colorScheme
            }
        }
        return captured
    }

    private fun captureExtendedColors(
        themeMode: ThemeMode,
        colorMode: ColorMode,
    ): SpenvoExtendedColors {
        lateinit var captured: SpenvoExtendedColors
        composeTestRule.setContent {
            SpenvoTheme(themeMode = themeMode, colorMode = colorMode) {
                captured = SpenvoTheme.extendedColors
            }
        }
        return captured
    }

    private fun assertTextStyle(
        actual: TextStyle,
        expectedWeight: FontWeight,
        expectedSize: TextUnit,
        expectedLineHeight: TextUnit,
        expectedLetterSpacing: TextUnit,
    ) {
        assertEquals(FontFamily.Default, actual.fontFamily)
        assertEquals(expectedWeight, actual.fontWeight)
        assertEquals(expectedSize, actual.fontSize)
        assertEquals(expectedLineHeight, actual.lineHeight)
        assertEquals(expectedLetterSpacing, actual.letterSpacing)
    }

    private fun assertColorSchemeEquals(expected: ColorScheme, actual: ColorScheme) {
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.onPrimary, actual.onPrimary)
        assertEquals(expected.primaryContainer, actual.primaryContainer)
        assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer)
        assertEquals(expected.inversePrimary, actual.inversePrimary)
        assertEquals(expected.primaryFixed, actual.primaryFixed)
        assertEquals(expected.primaryFixedDim, actual.primaryFixedDim)
        assertEquals(expected.onPrimaryFixed, actual.onPrimaryFixed)
        assertEquals(expected.onPrimaryFixedVariant, actual.onPrimaryFixedVariant)
        assertEquals(expected.secondary, actual.secondary)
        assertEquals(expected.onSecondary, actual.onSecondary)
        assertEquals(expected.secondaryContainer, actual.secondaryContainer)
        assertEquals(expected.onSecondaryContainer, actual.onSecondaryContainer)
        assertEquals(expected.secondaryFixed, actual.secondaryFixed)
        assertEquals(expected.secondaryFixedDim, actual.secondaryFixedDim)
        assertEquals(expected.onSecondaryFixed, actual.onSecondaryFixed)
        assertEquals(expected.onSecondaryFixedVariant, actual.onSecondaryFixedVariant)
        assertEquals(expected.tertiary, actual.tertiary)
        assertEquals(expected.onTertiary, actual.onTertiary)
        assertEquals(expected.tertiaryContainer, actual.tertiaryContainer)
        assertEquals(expected.onTertiaryContainer, actual.onTertiaryContainer)
        assertEquals(expected.tertiaryFixed, actual.tertiaryFixed)
        assertEquals(expected.tertiaryFixedDim, actual.tertiaryFixedDim)
        assertEquals(expected.onTertiaryFixed, actual.onTertiaryFixed)
        assertEquals(expected.onTertiaryFixedVariant, actual.onTertiaryFixedVariant)
        assertEquals(expected.error, actual.error)
        assertEquals(expected.onError, actual.onError)
        assertEquals(expected.errorContainer, actual.errorContainer)
        assertEquals(expected.onErrorContainer, actual.onErrorContainer)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.onBackground, actual.onBackground)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.surfaceTint, actual.surfaceTint)
        assertEquals(expected.surfaceVariant, actual.surfaceVariant)
        assertEquals(expected.onSurfaceVariant, actual.onSurfaceVariant)
        assertEquals(expected.outline, actual.outline)
        assertEquals(expected.outlineVariant, actual.outlineVariant)
        assertEquals(expected.scrim, actual.scrim)
        assertEquals(expected.inverseSurface, actual.inverseSurface)
        assertEquals(expected.inverseOnSurface, actual.inverseOnSurface)
        assertEquals(expected.surfaceDim, actual.surfaceDim)
        assertEquals(expected.surfaceBright, actual.surfaceBright)
        assertEquals(expected.surfaceContainerLowest, actual.surfaceContainerLowest)
        assertEquals(expected.surfaceContainerLow, actual.surfaceContainerLow)
        assertEquals(expected.surfaceContainer, actual.surfaceContainer)
        assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        assertEquals(expected.surfaceContainerHighest, actual.surfaceContainerHighest)
    }

    private fun expectedLightColorScheme(): ColorScheme = lightColorScheme(
        primary = Color(0xFF38693C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB9F0B8),
        onPrimaryContainer = Color(0xFF1F5027),
        inversePrimary = Color(0xFF9ED49D),
        secondary = Color(0xFF516350),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD4E8D0),
        onSecondaryContainer = Color(0xFF3A4B39),
        tertiary = Color(0xFF39656C),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBCEBF2),
        onTertiaryContainer = Color(0xFF1F4D53),
        background = Color(0xFFF7FBF2),
        onBackground = Color(0xFF181D18),
        surface = Color(0xFFF7FBF2),
        onSurface = Color(0xFF181D18),
        surfaceVariant = Color(0xFFDEE5D9),
        onSurfaceVariant = Color(0xFF424940),
        surfaceTint = Color(0xFF38693C),
        inverseSurface = Color(0xFF2D322C),
        inverseOnSurface = Color(0xFFEEF2E9),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF72796F),
        outlineVariant = Color(0xFFC2C9BD),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF7FBF2),
        surfaceDim = Color(0xFFD7DBD3),
        surfaceContainer = Color(0xFFEBEFE6),
        surfaceContainerHigh = Color(0xFFE6E9E1),
        surfaceContainerHighest = Color(0xFFE0E4DB),
        surfaceContainerLow = Color(0xFFF1F5EC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        primaryFixed = Color(0xFFB9F0B8),
        primaryFixedDim = Color(0xFF9ED49D),
        onPrimaryFixed = Color(0xFF002107),
        onPrimaryFixedVariant = Color(0xFF1F5027),
        secondaryFixed = Color(0xFFD4E8D0),
        secondaryFixedDim = Color(0xFFB9CCB5),
        onSecondaryFixed = Color(0xFF101F10),
        onSecondaryFixedVariant = Color(0xFF3A4B39),
        tertiaryFixed = Color(0xFFBCEBF2),
        tertiaryFixedDim = Color(0xFFA1CED6),
        onTertiaryFixed = Color(0xFF001F24),
        onTertiaryFixedVariant = Color(0xFF1F4D53),
    )

    private fun expectedDarkColorScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFF9ED49D),
        onPrimary = Color(0xFF033912),
        primaryContainer = Color(0xFF1F5027),
        onPrimaryContainer = Color(0xFFB9F0B8),
        inversePrimary = Color(0xFF38693C),
        secondary = Color(0xFFB9CCB5),
        onSecondary = Color(0xFF243424),
        secondaryContainer = Color(0xFF3A4B39),
        onSecondaryContainer = Color(0xFFD4E8D0),
        tertiary = Color(0xFFA1CED6),
        onTertiary = Color(0xFF00363C),
        tertiaryContainer = Color(0xFF1F4D53),
        onTertiaryContainer = Color(0xFFBCEBF2),
        background = Color(0xFF101510),
        onBackground = Color(0xFFE0E4DB),
        surface = Color(0xFF101510),
        onSurface = Color(0xFFE0E4DB),
        surfaceVariant = Color(0xFF424940),
        onSurfaceVariant = Color(0xFFC2C9BD),
        surfaceTint = Color(0xFF9ED49D),
        inverseSurface = Color(0xFFE0E4DB),
        inverseOnSurface = Color(0xFF2D322C),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8C9389),
        outlineVariant = Color(0xFF424940),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF363A34),
        surfaceDim = Color(0xFF101510),
        surfaceContainer = Color(0xFF1C211B),
        surfaceContainerHigh = Color(0xFF272B26),
        surfaceContainerHighest = Color(0xFF313630),
        surfaceContainerLow = Color(0xFF181D18),
        surfaceContainerLowest = Color(0xFF0B0F0B),
        primaryFixed = Color(0xFFB9F0B8),
        primaryFixedDim = Color(0xFF9ED49D),
        onPrimaryFixed = Color(0xFF002107),
        onPrimaryFixedVariant = Color(0xFF1F5027),
        secondaryFixed = Color(0xFFD4E8D0),
        secondaryFixedDim = Color(0xFFB9CCB5),
        onSecondaryFixed = Color(0xFF101F10),
        onSecondaryFixedVariant = Color(0xFF3A4B39),
        tertiaryFixed = Color(0xFFBCEBF2),
        tertiaryFixedDim = Color(0xFFA1CED6),
        onTertiaryFixed = Color(0xFF001F24),
        onTertiaryFixedVariant = Color(0xFF1F4D53),
    )
}
