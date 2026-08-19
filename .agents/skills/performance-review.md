# Skill: performance-review

**Activate:** on screens with lists, images, or performance changes (M3, M5, M6).

## Steps
1. Lists: `LazyColumn` with stable keys; reused items; nothing heavy in `content`.
2. Recomposition: `remember` for expensive calculations; correct state hoisting; do not
   recompose the whole screen for one field.
3. Images (Coil): explicit target size, avoid `Size.ORIGINAL`, disk+memory cache.
4. DB: queries with indexes; avoid N+1 in DAOs; `Flow` with `distinctUntilChanged`.
5. Cold start: minimize work in `Application.onCreate`; lazy Hilt graph.
6. No network work on the main thread; coroutines with correct dispatchers.
7. Measure when necessary: `adb shell am start -W`, Trace, Studio profiler.

## Output
- Hot path findings + suggested changes + measurement evidence (if applicable).