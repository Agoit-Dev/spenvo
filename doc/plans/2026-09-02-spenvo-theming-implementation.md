# Spenvo Material 3 Theming Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Replace Spenvo's starter Compose theme with an explicit, tested Material 3 brand theme
and stable income/expense semantic roles.

**Architecture:** `SpenvoTheme` remains the app-wide entry point and provides Material 3 color,
typography, and shapes plus a sibling `CompositionLocal` containing eight financial color roles.
Luminosity (`SYSTEM/LIGHT/DARK`) and color source (`BRAND/DYNAMIC`) remain independent.

**Tech Stack:** Kotlin 2.4.10, Jetpack Compose BOM 2026.08.00, Material 3, Robolectric, JUnit4

**Platform:** Android

---

## Scope and dependency order

```text
Task 1 (tracking and design artifacts)
  -> Task 2 (failing theme contract tests)
  -> Task 3 (color schemes and extended colors)
  -> Task 4 (typography and shapes)
  -> Task 5 (preview catalog)
  -> Task 6 (MovimientoItem migration)
  -> Task 7 (codebase theme audit)
  -> Task 8 (full verification and documentation closure)
```

Tasks 3 and 4 both modify `Theme.kt`; execute them sequentially. Tasks 5 and 6 depend on the
final APIs from Tasks 3-4. No task adds a new external dependency.

### Task 1: Register the work and preserve Theme Builder provenance

**Files:**
- Keep: `doc/designs/2026-09-02-spenvo-theming-design.md`
- Keep: `doc/plans/2026-09-02-spenvo-theming-implementation.md`
- Create: `doc/design/theme/material-theme.json`
- Create: `doc/design/theme/README.md`
- Modify: `backlog.md`
- Modify: `ROADMAP.md`

- [x] **Step 1: Copy the approved JSON export**

Copy `C:\Users\Tiago\Downloads\material-theme.json` verbatim to
`doc/design/theme/material-theme.json`. This is design provenance, not runtime input.

- [x] **Step 2: Document the export**

Create `doc/design/theme/README.md` containing the three seeds, Theme Builder URL, export date,
standard-contrast decision, and the rule that generated `Theme.kt` must never replace the project
theme wholesale.

- [x] **Step 3: Add a backlog item**

Add `UI-THEME-001` to `backlog.md` and place it under In Progress. The item must link both approved
documents and describe Delivery 1 only.

- [x] **Step 4: Register the Phase 8 product-design work**

Add a Phase 8 roadmap bullet for the Material 3 design-system foundation without changing the
priority or scope of `FEAT-M402/M403`.

- [x] **Step 5: Verify documentation-only diff**

Run:

```powershell
git diff --check
```

Expected: exit code 0.

### Task 2: Write the failing theme contract tests

**Files:**
- Create: `core/designsystem/src/test/java/com/agoitdev/spenvo/designsystem/theme/SpenvoThemeTest.kt`

- [x] **Step 1: Add the Robolectric Compose test fixture**

Use the existing `createComposeRule()`, `RobolectricTestRunner`, and `@Config` convention from
`AvatarTest`. Capture values from inside `SpenvoTheme` into ordinary Kotlin variables, then assert
after `setContent` completes.

- [x] **Step 2: Test Brand Light and Brand Dark**

Assert at minimum:

```kotlin
assertEquals(Color(0xFF38693C), lightScheme.primary)
assertEquals(Color(0xFF9ED49D), darkScheme.primary)
assertEquals(Color(0xFFF7FBF2), lightScheme.surface)
assertEquals(Color(0xFF101510), darkScheme.surface)
```

- [x] **Step 3: Test independent theme axes**

Add tests proving:

- `ThemeMode.LIGHT` stays light under `night` qualifiers.
- `ThemeMode.DARK` stays dark under non-night qualifiers.
- `ThemeMode.SYSTEM` follows the Robolectric qualifier.
- `ColorMode.BRAND` returns the brand scheme.
- `ColorMode.DYNAMIC` on SDK 30 falls back to the matching brand scheme.

- [x] **Step 4: Test all eight extended roles**

Assert the complete approved values:

```kotlin
val expectedLight = SpenvoExtendedColors(
    income = Color(0xFF466730),
    onIncome = Color(0xFFFFFFFF),
    incomeContainer = Color(0xFFC7EEA9),
    onIncomeContainer = Color(0xFF304F1A),
    expense = Color(0xFF8A5023),
    onExpense = Color(0xFFFFFFFF),
    expenseContainer = Color(0xFFFFDCC6),
    onExpenseContainer = Color(0xFF6E390D),
)

val expectedDark = SpenvoExtendedColors(
    income = Color(0xFFACD28F),
    onIncome = Color(0xFF1A3705),
    incomeContainer = Color(0xFF304F1A),
    onIncomeContainer = Color(0xFFC7EEA9),
    expense = Color(0xFFFFB786),
    onExpense = Color(0xFF502400),
    expenseContainer = Color(0xFF6E390D),
    onExpenseContainer = Color(0xFFFFDCC6),
)
```

Add a test showing Brand and Dynamic select the same extended instance for a given luminosity.

- [x] **Step 5: Test the typography contract and verify typography and shapes are installed**

Assert `fontFamily`, `fontWeight`, `fontSize`, `lineHeight`, and `letterSpacing` for each of the
15 styles against the explicit Material 3 baseline table in Task 4. These assertions must use
literal expected values rather than values copied from `SpenvoTypography`; otherwise a malformed
scale could remain self-consistent and pass the test.

Separately capture `MaterialTheme.typography` and `MaterialTheme.shapes`; assert equality with
`SpenvoTypography` and `SpenvoShapes`. This second assertion verifies installation only, not
conformance to the Material 3 typography contract.

- [x] **Step 6: Run the test and confirm RED**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*.SpenvoThemeTest"
```

Expected: compilation failure because the approved APIs do not exist yet.

### Task 3: Implement complete Material color and financial roles

**Files:**
- Modify: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/Color.kt`
- Modify: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/Theme.kt`
- Create: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/ExtendedColors.kt`

- [x] **Step 1: Replace starter tokens with standard-contrast exported tokens**

Remove `Purple80`, `PurpleGrey80`, `Pink80`, `Purple40`, `PurpleGrey40`, `Pink40`, and
`IngresoColor`. Add the complete standard-contrast light/dark constants from the approved Theme
Builder export, including `surfaceDim`, `surfaceBright`, and all five `surfaceContainer*` roles.
Do not include medium/high-contrast variants in runtime code.

- [x] **Step 2: Define the complete extended-color contract**

```kotlin
@Immutable
data class SpenvoExtendedColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
)
```

Create `LightExtendedColors` and `DarkExtendedColors` using the exact values from Task 2. Provide a
`staticCompositionLocalOf` fallback whose eight values are `Color.Unspecified`.

- [x] **Step 3: Expose extended colors through the Spenvo theme API**

Expose a composable read-only accessor named `SpenvoTheme.extendedColors`. It returns the current
`CompositionLocal` value and does not allow feature modules to replace it.

- [x] **Step 4: Replace Boolean configuration with independent enums**

Add `ThemeMode` and `ColorMode` exactly as approved. Resolve darkness as:

```kotlin
val useDarkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
```

Use dynamic Material color only when `colorMode == ColorMode.DYNAMIC` and
`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; otherwise select Brand Light/Dark. Select extended
colors only from `useDarkTheme`, independent of `ColorMode`.

- [x] **Step 5: Provide both theme layers**

Wrap `MaterialTheme` with `CompositionLocalProvider(LocalSpenvoExtendedColors provides ...)` and
retain a single public `SpenvoTheme` composable with defaults `SYSTEM` and `BRAND`.

- [x] **Step 6: Run the focused theme test source set**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*.SpenvoThemeTest"
```

Expected intermediate result: test compilation remains red only because `SpenvoTypography` and
`SpenvoShapes` belong to Task 4. All errors for color schemes, `ThemeMode`, `ColorMode`, extended
colors, and the new `SpenvoTheme` signature must have disappeared. Gradle cannot execute the
individual color tests yet because it compiles the complete test source set first.

### Task 4: Install explicit Material typography and shapes

**Files:**
- Modify: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/Type.kt`
- Create: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/Shape.kt`
- Modify: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/Theme.kt`

- [x] **Step 1: Replace the one-style scaffold typography**

Rename the value to `SpenvoTypography`. Define all 15 Material 3 styles using
`FontFamily.Default` and the following Material 3 baseline scale adopted for Spenvo:

| Style | Weight | Size | Line height | Letter spacing |
|---|---:|---:|---:|---:|
| `displayLarge` | Normal (400) | 57sp | 64sp | -0.25sp |
| `displayMedium` | Normal (400) | 45sp | 52sp | 0sp |
| `displaySmall` | Normal (400) | 36sp | 44sp | 0sp |
| `headlineLarge` | Normal (400) | 32sp | 40sp | 0sp |
| `headlineMedium` | Normal (400) | 28sp | 36sp | 0sp |
| `headlineSmall` | Normal (400) | 24sp | 32sp | 0sp |
| `titleLarge` | Normal (400) | 22sp | 28sp | 0sp |
| `titleMedium` | Medium (500) | 16sp | 24sp | 0.15sp |
| `titleSmall` | Medium (500) | 14sp | 20sp | 0.1sp |
| `bodyLarge` | Normal (400) | 16sp | 24sp | 0.5sp |
| `bodyMedium` | Normal (400) | 14sp | 20sp | 0.25sp |
| `bodySmall` | Normal (400) | 12sp | 16sp | 0.4sp |
| `labelLarge` | Medium (500) | 14sp | 20sp | 0.1sp |
| `labelMedium` | Medium (500) | 12sp | 16sp | 0.5sp |
| `labelSmall` | Medium (500) | 11sp | 16sp | 0.5sp |

Use `FontWeight.Normal` for every display, headline, and body role plus `titleLarge`. Use
`FontWeight.Medium` only for `titleMedium`, `titleSmall`, `labelLarge`, `labelMedium`, and
`labelSmall`. Do not infer the weight from the role family name.

- [x] **Step 2: Add explicit standard Material shapes**

```kotlin
val SpenvoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [x] **Step 3: Install both subsystems**

Pass `typography = SpenvoTypography` and `shapes = SpenvoShapes` to `MaterialTheme`.

- [x] **Step 4: Run the complete design-system tests**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest
```

Expected: all tests pass.

- [x] **Step 5: Run focused quality gates**

```powershell
.\gradlew.bat :core:designsystem:lintDebug :core:designsystem:detekt
```

Expected: BUILD SUCCESSFUL with no findings.

### Task 5: Add the preview-only theme catalog

**Files:**
- Create: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/theme/ThemeCatalogPreview.kt`

- [x] **Step 1: Build reusable catalog content**

Create one private `ThemeCatalog` composable that renders standard color pairs, all eight extended
roles, the 15 typography styles, five shape samples, and representative Material 3 components.
Every colored background must use its matching `on*` foreground.

- [x] **Step 2: Add four previews**

Add Brand Light, Brand Dark, representative Dynamic Light, and representative Dynamic Dark
previews. Dynamic previews inject deterministic representative schemes and must not depend on the
Android Studio host wallpaper.

- [x] **Step 3: Ensure previews remain development-only behavior**

Do not add routes, activities, resources, or production settings. `ThemeCatalogPreview.kt` may live
in main source because only preview functions reference it and tooling dependencies already exist.

- [x] **Step 4: Compile preview code**

```powershell
.\gradlew.bat :core:designsystem:assembleDebug
```

Expected: BUILD SUCCESSFUL.

### Task 6: Migrate MovimientoItem to semantic financial colors

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoItem.kt`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoItemTest.kt`

- [x] **Step 1: Remove the standalone color import**

Delete the `IngresoColor` import and obtain the current `SpenvoTheme.extendedColors` inside
`MovimientoItem`.

- [x] **Step 2: Map both domain types**

```kotlin
val amountColor = if (movimiento is Ingreso) {
    SpenvoTheme.extendedColors.income
} else {
    SpenvoTheme.extendedColors.expense
}
```

Keep the conflict icon on `MaterialTheme.colorScheme.error`. Do not use `onIncome` or `onExpense`
because the amount is drawn over the existing Material surface, not over an income/expense fill.

- [x] **Step 3: Add a focused consumer-mapping regression test**

Use the module's existing Robolectric/Compose test harness and dependencies. Add a focused test for
the consumer mapping from `Ingreso` to `income` and from `Gasto` to `expense`, using arbitrary,
distinct extended-role values so the test proves domain selection rather than repeating theme
constants. Do not add dependencies or expose a new public API.

- [x] **Step 4: Verify the feature**

```powershell
.\gradlew.bat :feature:movimientos:testDebugUnitTest :feature:movimientos:lintDebug
```

Expected: BUILD SUCCESSFUL.

### Task 7: Audit all current theme consumers

**Files:**
- Inspect and modify only when a concrete violation exists: `app/src/main`,
  `core/designsystem/src/main`, `feature/*/src/main`
- Modify tests when an audit correction needs executable coverage:
  `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoItemTest.kt`

- [x] **Step 1: Check for hard-coded Compose colors**

```powershell
rg -n --glob '*.kt' --glob '!**/src/test/**' --glob '!**/src/androidTest/**' 'Color\(0x|Color\.(Black|White|Red|Green|Blue|Gray|Yellow|Magenta|Cyan)' app/src/main core/designsystem/src/main feature
```

Expected: production runtime hexadecimal tokens exist only in the design-system theme definitions
`Color.kt` and `ExtendedColors.kt`. `ThemeCatalogPreview.kt` intentionally contains deterministic
representative Dynamic schemes used exclusively by private `@Preview` functions; these are preview
fixtures, not runtime theme consumers.

- [x] **Step 2: Check for Material 2 components**

```powershell
rg -n --glob '*.kt' '^import androidx\.compose\.material\.' app/src/main core/designsystem/src/main feature
```

Review results and exclude `androidx.compose.material.icons.*`; no Material 2 UI component import
may remain.

- [x] **Step 3: Review typography and shape bypasses**

Inspect `TextStyle(`, `fontSize =`, `fontFamily =`, and raw `RoundedCornerShape(` occurrences.
Keep contextual `fontWeight` overrides and genuine `CircleShape` uses. Change only complete local
styles/shapes that duplicate theme roles.

- [x] **Step 4: Review color-pair correctness**

For every explicit container/background color, verify its content uses the matching `on*` role.
Keep `error` only for errors, validation, and conflicts.

- [x] **Step 5: Build after any audit corrections**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

### Task 8: Run final gates and close documentation

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `backlog.md`
- Modify: `ROADMAP.md`
- Modify if needed: `doc/architecture.md`

- [x] **Step 1: Run the complete project gates**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat detekt
```

Expected: every command finishes with BUILD SUCCESSFUL.

- [x] **Step 2: Confirm no dependency-lock work is required**

Verify `gradle/libs.versions.toml`, module build files, and all `gradle.lockfile` files are unchanged.
If any dependency declaration changed unexpectedly, stop and reconcile it rather than regenerating
locks silently.

- [x] **Step 3: Perform visual verification**

Review Brand Light/Dark, Dynamic Light/Dark on API 31+, fallback on API 30, font scaling, system
bars, phone layout, and adaptive layout. Confirm income/expense remain distinguishable and error
remains visually separate from expense.

Closure evidence: a Pixel 4 API 29 emulator verified Brand System Light/Dark after secure night-mode
changes and reboot, legible system bars, a normal phone layout at default and 1.3 font scale, and a
wide 1600x2560/density-320 adaptive override without clipping or crashes. Stable captures under
`build/visual-verification/` show the loaded real-content movimiento list
(`spenvo-adaptive-content.png`) and its opened movimiento detail
(`spenvo-adaptive-detail.png`). The list showed income green and expense copper as distinct
semantic colors; logcat contained no FATAL or ANR entries.
The emulator settings were restored before shutdown. Robolectric's native graphics mode on SDK 31
rendered representative Dynamic Light/Dark scenes under the real `SpenvoTheme`, confirming legible
Material components and typography and visually distinct income, expense, and error roles on the
normal surface. The permanent SDK 30 contract test covers Dynamic-to-Brand fallback. Screenshots
are retained only under ignored `build/visual-verification/`; no debug route or settings UI was
added.

- [x] **Step 4: Update project records**

Add an English Keep a Changelog entry, move `UI-THEME-001` to Done, check it off, and update the
Phase 8 roadmap bullet. Record that focused consumer mapping and color-pair resolver tests were
added using the existing `:feature:movimientos` test infrastructure.

- [x] **Step 5: Check the final diff**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only planned files are modified or created.

## Explicitly deferred work

- DataStore persistence for `ThemeMode` and `ColorMode`.
- User-facing settings controls and localized labels.
- Medium/high contrast theme variants.
- Runtime harmonization of financial colors.
- Per-category colors (`FEAT-M402` / `FEAT-M403`).
- M3 Expressive component, motion, typography, or shape adoption.
- Global spacing tokens or component wrappers.

## Self-review

- Every approved color role, including all eight extended roles, maps to Tasks 2-3.
- Brand/dynamic and light/dark axes map to Tasks 2-3.
- Typography, shapes, previews, consumer migration, audits, and all repository gates are covered.
- Delivery 2 is explicitly separated and no dependency addition is planned.
- No placeholder or unresolved implementation decision remains.
