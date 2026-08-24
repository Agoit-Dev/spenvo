# Rule: UI Strings & i18n

## Purpose
All visible strings live in resources; Spanish is the default; code speaks English.

## Rules
1. Every UI-visible string goes in `res/values/strings.xml` with an English key.
2. **No hardcoded text** in Kotlin/XML. `HardcodedText` lint = error (blocking).
3. The default is **es** (`values/`). Translations are additive: `values-en/`, `values-pt/`,
   etc. `MissingTranslation` = error: if a key is missing in the Spanish default, it does not compile.
4. English keys (snake_case), Spanish values:
   `movements_title` → "Movimientos".
5. Use `plurals` for plurals. Dates/amounts with local formats (`DateUtils`, `NumberFormat`,
   `Money` are not hand-concatenated).
6. `contentDescription` on informative images also in strings.

## Examples
```xml
<string name="movements_title">Movimientos</string>
<string name="sync_conflict_title">Conflicto de edición</string>
```

## Common mistakes
- `Text("Guardar")` → must be `stringResource(R.string.save)`.
- Interpolating raw amounts (bad localization): use `NumberFormat`.
- Manually concatenating "%s" instead of placeholders.