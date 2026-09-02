# Spenvo Material 3 Theming Design

**Date:** 2026-09-02

**Status:** Approved

**Platform:** Android / Jetpack Compose Material 3

## Context

Spenvo already wraps its Compose tree in `SpenvoTheme`, but the implementation is the Android
Studio starter scaffold: Purple/Pink seed colors, a single customized typography style, implicit
Material shapes, and one `dynamicColor` Boolean. Android 12+ therefore replaces the starter
palette with wallpaper-derived colors, while the only product-specific color (`IngresoColor`) is
a standalone constant consumed directly by `MovimientoItem`.

The objective is to turn this scaffold into a deliberate Material 3 design system that centralizes
brand color, typography, shapes, light/dark behavior, optional dynamic color, and Spenvo's financial
semantics. User-facing persistence and settings are deliberately deferred to a second delivery.

## Decisions

### Brand source

- Primary seed: `#2BA94A`, the dominant medium green in the current Spenvo icon.
- Logo references: dark green `#014325`, lime `#7DD442`, cream background `#FFF8E7`.
- The references are not assigned directly to Material roles. Material Theme Builder derives
  accessible tonal palettes from the primary seed.
- `#FFF8E7` is not forced into the scheme. The generated neutral surface palette is retained.

### Theme configuration

Theme luminosity and color source are independent axes:

```kotlin
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ColorMode {
    BRAND,
    DYNAMIC,
}
```

- Default: `ThemeMode.SYSTEM` + `ColorMode.BRAND`.
- `SYSTEM` follows `isSystemInDarkTheme()`.
- `LIGHT` and `DARK` override the system setting.
- `DYNAMIC` uses Android wallpaper colors on Android 12+.
- `DYNAMIC` falls back to the matching Brand scheme below Android 12.
- Theme settings are injectable now but are not persisted or exposed in UI in this delivery.

### Material 3 architecture

Spenvo extends Material 3 instead of replacing it:

```text
SpenvoTheme
├── MaterialTheme
│   ├── SpenvoColorScheme
│   ├── SpenvoTypography
│   └── SpenvoShapes
└── SpenvoExtendedColors
    ├── income / onIncome
    ├── incomeContainer / onIncomeContainer
    ├── expense / onExpense
    └── expenseContainer / onExpenseContainer
```

Standard UI consumes `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and
`MaterialTheme.shapes`. Only financial semantics consume `SpenvoExtendedColors`. A full custom
design system, global component wrappers, and spacing tokens are out of scope.

### Brand schemes

Material Theme Builder generated the standard-contrast light and dark schemes from `#2BA94A`.
The implementation includes the complete standard Material 3 role set, including modern surface
container roles. Medium- and high-contrast exports are retained as design-tool output only and are
not implemented in this delivery.

Key generated roles:

| Role | Light | Dark |
|---|---:|---:|
| primary | `#38693C` | `#9ED49D` |
| onPrimary | `#FFFFFF` | `#033912` |
| primaryContainer | `#B9F0B8` | `#1F5027` |
| onPrimaryContainer | `#1F5027` | `#B9F0B8` |
| secondary | `#516350` | `#B9CCB5` |
| tertiary | `#39656C` | `#A1CED6` |
| error | `#BA1A1A` | `#FFB4AB` |
| background | `#F7FBF2` | `#101510` |
| surface | `#F7FBF2` | `#101510` |
| onSurface | `#181D18` | `#E0E4DB` |

### Financial semantic colors

Income seed: logo lime `#7DD442`.

Expense seed: copper orange `#B85C00`.

Expense is not an error. `MaterialTheme.colorScheme.error` remains reserved for validation,
failures, and conflicts.

All eight roles are required even though `MovimientoItem` initially consumes only `income` and
`expense` as text colors over an existing Material surface. The `on*` roles are used only when the
matching base/container color becomes a background.

| Role | Light | Dark |
|---|---:|---:|
| income | `#466730` | `#ACD28F` |
| onIncome | `#FFFFFF` | `#1A3705` |
| incomeContainer | `#C7EEA9` | `#304F1A` |
| onIncomeContainer | `#304F1A` | `#C7EEA9` |
| expense | `#8A5023` | `#FFB786` |
| onExpense | `#FFFFFF` | `#502400` |
| expenseContainer | `#FFDCC6` | `#6E390D` |
| onExpenseContainer | `#6E390D` | `#FFDCC6` |

These roles remain stable when `ColorMode.DYNAMIC` is active. Only their light/dark instance
changes; they are not harmonized with each wallpaper palette.

### Typography

- Use `FontFamily.Default` (Android system sans-serif/Roboto) in this delivery.
- Declare the complete 15-style Material 3 type scale as `SpenvoTypography`.
- Features consume styles through `MaterialTheme.typography`.
- Local weight overrides remain valid for contextual emphasis, but features do not define complete
  `TextStyle` instances.
- The scale must support Android font scaling and long Spanish strings.
- A future font-family change remains isolated to `SpenvoTypography`.

### Shapes

- Declare `SpenvoShapes` explicitly using the standard Material 3 shape scale.
- Features consume scalable shapes through `MaterialTheme.shapes`.
- `CircleShape` remains valid for avatars and genuinely circular selectors.
- Material 3 Expressive shape strategies are out of scope.

### Visual catalog

A preview-only catalog in `:core:designsystem` displays:

- Standard Material color roles and container/content pairs.
- All eight financial roles.
- Typography and shape scales.
- Buttons, text fields, cards, chips, app bars, dialogs, selected states, and disabled states.
- Brand Light/Dark and representative Dynamic Light/Dark configurations.

The catalog is not a production screen and adds no navigation route.

## Delivery boundaries

### Delivery 1: design system

Includes schemes, configuration enums, extended colors, typography, shapes, previews, tests,
`MovimientoItem` migration, UI audit, and project documentation.

### Delivery 2: user preferences

Deferred to a separate design and plan: DataStore persistence, reactive preference flow, settings
UI, localized labels, process-death restoration, and UI tests.

Category colors (`FEAT-M402` / `FEAT-M403`) remain product data and are not theme tokens.

## Testing and acceptance

- Contract tests cover Brand Light/Dark, system/manual mode resolution, dynamic fallback, all eight
  extended roles, typography, and shapes.
- The full project build, unit tests, lint, and detekt must pass.
- Manual review covers Brand/Dynamic Light/Dark, Android below/above API 31, font scaling,
  edge-to-edge/system bars, phone layout, and adaptive layout.
- No Material 2 component imports may be introduced. `androidx.compose.material.icons.*` remains
  valid with Material 3.
- No feature may import a hexadecimal theme color directly.
