# Material Theme Builder Export

This directory preserves the Material Theme Builder export used as design provenance for Spenvo's
Material 3 design-system foundation. The JSON file is not a runtime input and must not be parsed or
packaged by the app.

## Source

- Tool: [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)
- Export date: 2026-09-02
- Brand seed: `#2BA94A`
- Income seed: `#7DD442`
- Expense seed: `#B85C00`

Spenvo implements the standard-contrast light and dark schemes. Medium- and high-contrast schemes
remain preserved in the export as design-tool output only.

Generated `Theme.kt` output must never replace the project theme wholesale. Treat the export as a
reference for approved color roles and integrate those roles deliberately into Spenvo's existing
theme architecture, configuration API, extended financial colors, typography, and shapes.
