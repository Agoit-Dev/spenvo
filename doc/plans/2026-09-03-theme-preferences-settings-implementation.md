# Theme Preferences and Settings Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Let the user pick and persist `ThemeMode`/`ColorMode` locally, apply it immediately, and
survive process death — via a new `:feature:ajustes` Settings screen reachable from every plan
screen.

**Architecture:** `:feature:ajustes` → domain use cases (`:core:domain`) →
`AppearancePreferencesRepository` → `ThemePreferences` DataStore (`:core:data`) →
`AppearanceViewModel` (`:app`) → `SpenvoTheme(themeMode, colorMode)`. A shared `AvatarMenu`
(`:core:designsystem`) replaces the duplicated avatar `IconButton` on 5 top bars.

**Tech Stack:** Kotlin 2.4.10, DataStore Preferences 1.2.1, Hilt 2.60.1, Jetpack Compose BOM
2026.08.00, Navigation 3, JUnit4, Robolectric

**Platform:** Android

**Depends on:** UI-THEME-001 (merged, `main@5b24836`). Design:
[`doc/designs/2026-09-03-theme-preferences-settings-design.md`](../designs/2026-09-03-theme-preferences-settings-design.md).

---

## Scope and dependency order

```text
Task 1 (domain contract)
  -> Task 2 (ThemePreferences DataStore + DI)
  -> Task 3 (AvatarMenu, independent of 1-2)
  -> Task 4 (AppearanceViewModel + MainActivity gate coordination)
  -> Task 5 (:feature:ajustes module scaffold)
  -> Task 6 (AjustesViewModel)
  -> Task 7 (AjustesScreen + AjustesRoute)
  -> Task 8 (wire AvatarMenu + Ajustes across 5 top bars)
  -> Task 9 (final gates, lockfiles, docs closure)
```

Task 3 has no dependency on Tasks 1-2 and may run in parallel with them if using subagents. Task 8
depends on both Task 3 (component) and Task 7 (route to navigate to).

### Task 1: Domain contract for appearance preferences

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/model/AppearancePreferences.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AppearancePreferencesRepository.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarAppearanceUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ActualizarTemaUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ActualizarColorUseCase.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AppearanceUseCaseTest.kt`

- [ ] **Step 1: Write the failing use case tests**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceUseCaseTest {

    @Test
    fun `AppearancePreferences por defecto es SYSTEM mas BRAND`() {
        assertEquals(
            AppearancePreferences(ThemePreference.SYSTEM, ColorPreference.BRAND),
            AppearancePreferences(),
        )
    }

    @Test
    fun `ObservarAppearanceUseCase delega en el repositorio`() = runBlocking {
        val esperado = AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)
        val repo = FakeAppearanceRepository(flowOf(esperado))

        val resultado = ObservarAppearanceUseCase(repo).invoke()

        assertEquals(esperado, resultado.let { var v: AppearancePreferences? = null; it.collect { p -> v = p }; v })
    }

    @Test
    fun `ActualizarTemaUseCase delega en el repositorio`() = runBlocking {
        val repo = FakeAppearanceRepository(flowOf(AppearancePreferences()))

        ActualizarTemaUseCase(repo).invoke(ThemePreference.DARK)

        assertEquals(listOf(ThemePreference.DARK), repo.temasActualizados)
    }

    @Test
    fun `ActualizarColorUseCase delega en el repositorio`() = runBlocking {
        val repo = FakeAppearanceRepository(flowOf(AppearancePreferences()))

        ActualizarColorUseCase(repo).invoke(ColorPreference.DYNAMIC)

        assertEquals(listOf(ColorPreference.DYNAMIC), repo.coloresActualizados)
    }
}

private class FakeAppearanceRepository(
    override val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    val temasActualizados = mutableListOf<ThemePreference>()
    val coloresActualizados = mutableListOf<ColorPreference>()

    override suspend fun actualizarTema(theme: ThemePreference) {
        temasActualizados += theme
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        coloresActualizados += color
    }
}
```

- [ ] **Step 2: Run the test and confirm RED**

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.AppearanceUseCaseTest"
```

Expected: compilation failure — `AppearancePreferences`, `AppearancePreferencesRepository`, and the
three use cases don't exist yet.

- [ ] **Step 3: Add the domain model**

```kotlin
package com.agoitdev.spenvo.domain.model

enum class ThemePreference { SYSTEM, LIGHT, DARK }

enum class ColorPreference { BRAND, DYNAMIC }

data class AppearancePreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val color: ColorPreference = ColorPreference.BRAND,
)
```

- [ ] **Step 4: Add the repository contract**

```kotlin
package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface AppearancePreferencesRepository {
    val preferencias: Flow<AppearancePreferences>

    suspend fun actualizarTema(theme: ThemePreference)

    suspend fun actualizarColor(color: ColorPreference)
}
```

- [ ] **Step 5: Add the three use cases**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.flow.Flow

class ObservarAppearanceUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    fun invoke(): Flow<AppearancePreferences> = appearanceRepository.preferencias
}
```

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository

class ActualizarTemaUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    suspend fun invoke(theme: ThemePreference) = appearanceRepository.actualizarTema(theme)
}
```

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository

class ActualizarColorUseCase(
    private val appearanceRepository: AppearancePreferencesRepository,
) {
    suspend fun invoke(color: ColorPreference) = appearanceRepository.actualizarColor(color)
}
```

- [ ] **Step 6: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.AppearanceUseCaseTest"
```

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 7: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/AppearancePreferences.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AppearancePreferencesRepository.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarAppearanceUseCase.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ActualizarTemaUseCase.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ActualizarColorUseCase.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AppearanceUseCaseTest.kt
git commit -m "feat(domain): add appearance preferences contract and use cases"
```

### Task 2: `ThemePreferences` DataStore and DI

**Files:**
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/appearance/ThemePreferences.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/di/AppearanceModule.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/appearance/ThemePreferencesTest.kt`

- [ ] **Step 1: Write the failing instrumented tests**

```kotlin
package com.agoitdev.spenvo.data.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesTest {

    private fun crearPreferences(nombre: String = "appearance_test_${System.nanoTime()}"): ThemePreferences {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        return ThemePreferences(dataStore)
    }

    @Test
    fun `sin claves devuelve SYSTEM mas BRAND`() = runBlocking {
        val prefs = crearPreferences()

        val actual = prefs.preferencias.first()

        assertEquals(ThemePreference.SYSTEM, actual.theme)
        assertEquals(ColorPreference.BRAND, actual.color)
    }

    @Test
    fun `actualizarTema persiste sin afectar el color`() = runBlocking {
        val prefs = crearPreferences()
        prefs.actualizarColor(ColorPreference.DYNAMIC)

        prefs.actualizarTema(ThemePreference.DARK)

        val actual = prefs.preferencias.first()
        assertEquals(ThemePreference.DARK, actual.theme)
        assertEquals(ColorPreference.DYNAMIC, actual.color)
    }

    @Test
    fun `actualizarColor persiste sin afectar el tema`() = runBlocking {
        val prefs = crearPreferences()
        prefs.actualizarTema(ThemePreference.LIGHT)

        prefs.actualizarColor(ColorPreference.DYNAMIC)

        val actual = prefs.preferencias.first()
        assertEquals(ThemePreference.LIGHT, actual.theme)
        assertEquals(ColorPreference.DYNAMIC, actual.color)
    }

    @Test
    fun `valor desconocido decodifica al default`() = runBlocking {
        val nombre = "appearance_unknown_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rawStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        rawStore.edit { it[stringPreferencesKey("theme")] = "NOT_A_REAL_VALUE" }

        val prefs = ThemePreferences(rawStore)
        val actual = prefs.preferencias.first()

        assertEquals(ThemePreference.SYSTEM, actual.theme)
    }

    @Test
    fun `DYNAMIC persistido por debajo de API 31 se normaliza a BRAND en el store`() = runBlocking {
        val nombre = "appearance_dynamic_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rawStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile(nombre) }
        rawStore.edit { it[stringPreferencesKey("color")] = ColorPreference.DYNAMIC.name }

        val primeraLectura = ThemePreferences(rawStore).preferencias.first()
        assertEquals(ColorPreference.BRAND, primeraLectura.color)

        // The correction must have been written back, not just presented in memory.
        val segundaInstancia = ThemePreferences(rawStore).preferencias.first()
        assertEquals(ColorPreference.BRAND, segundaInstancia.color)
    }

    @Test
    fun `persiste entre instancias`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val nombreArchivo = "appearance_persistencia_test"
        fun nuevaInstancia(scope: CoroutineScope) = ThemePreferences(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.preferencesDataStoreFile(nombreArchivo)
            },
        )

        val primerScope = CoroutineScope(SupervisorJob())
        nuevaInstancia(primerScope).actualizarTema(ThemePreference.DARK)
        primerScope.cancel()

        val segundoScope = CoroutineScope(SupervisorJob())
        val persistido = nuevaInstancia(segundoScope).preferencias.first()
        segundoScope.cancel()
        context.preferencesDataStoreFile(nombreArchivo).delete()

        assertEquals(ThemePreference.DARK, persistido.theme)
    }
}
```

Note: this test file is instrumented (`androidTest`, real DataStore + real `Build.VERSION.SDK_INT`
of the test device/emulator). The "DYNAMIC below API 31" test only exercises the normalization path
when run on an emulator/device at API < 31; on a higher-API runner it degenerates to "DYNAMIC stays
DYNAMIC," which is still correct behavior for that device. Task 9 records the actual API level the
instrumented suite ran on.

- [ ] **Step 2: Run the test and confirm RED**

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest --tests "*.ThemePreferencesTest"
```

Expected: compilation failure — `ThemePreferences` doesn't exist yet.

- [ ] **Step 3: Implement `ThemePreferences`**

```kotlin
package com.agoitdev.spenvo.data.appearance

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "appearance")

@Singleton
class ThemePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : AppearancePreferencesRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appearanceDataStore)

    override val preferencias: Flow<AppearancePreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> normalizar(prefs) }

    override suspend fun actualizarTema(theme: ThemePreference) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        dataStore.edit { it[KEY_COLOR] = color.name }
    }

    /**
     * Reads decode defensively (unknown/corrupted value -> default). A persisted `DYNAMIC` on an
     * API below 31 (restored backup, OS downgrade) is corrected in the store itself, not just in
     * the value handed back — the write only fires when the anomaly is actually present, so it is
     * not a per-emission write.
     */
    private suspend fun normalizar(prefs: Preferences): AppearancePreferences {
        val theme = prefs[KEY_THEME]
            ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.SYSTEM
        val colorCrudo = prefs[KEY_COLOR]
            ?.let { runCatching { ColorPreference.valueOf(it) }.getOrNull() }
            ?: ColorPreference.BRAND
        val dynamicSoportado = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (colorCrudo == ColorPreference.DYNAMIC && !dynamicSoportado) {
            dataStore.edit { it[KEY_COLOR] = ColorPreference.BRAND.name }
            return AppearancePreferences(theme, ColorPreference.BRAND)
        }
        return AppearancePreferences(theme, colorCrudo)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_COLOR = stringPreferencesKey("color")
    }
}
```

- [ ] **Step 4: Add the Hilt module**

```kotlin
package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.appearance.ThemePreferences
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceModule {

    @Binds
    @Singleton
    abstract fun bindAppearancePreferencesRepository(
        impl: ThemePreferences,
    ): AppearancePreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppearanceUseCaseModule {

    @Provides
    fun provideObservarAppearance(
        appearanceRepository: AppearancePreferencesRepository,
    ): ObservarAppearanceUseCase = ObservarAppearanceUseCase(appearanceRepository)

    @Provides
    fun provideActualizarTema(
        appearanceRepository: AppearancePreferencesRepository,
    ): ActualizarTemaUseCase = ActualizarTemaUseCase(appearanceRepository)

    @Provides
    fun provideActualizarColor(
        appearanceRepository: AppearancePreferencesRepository,
    ): ActualizarColorUseCase = ActualizarColorUseCase(appearanceRepository)
}
```

- [ ] **Step 5: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest --tests "*.ThemePreferencesTest"
```

Expected: BUILD SUCCESSFUL, 6 tests passed, on a running emulator/device.

- [ ] **Step 6: Commit**

```bash
git add core/data/src/main/java/com/agoitdev/spenvo/data/appearance/ThemePreferences.kt \
        core/data/src/main/java/com/agoitdev/spenvo/data/di/AppearanceModule.kt \
        core/data/src/androidTest/java/com/agoitdev/spenvo/data/appearance/ThemePreferencesTest.kt
git commit -m "feat(data): add ThemePreferences DataStore and appearance DI module"
```

### Task 3: Shared `AvatarMenu`

**Files:**
- Create: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/AvatarMenu.kt`
- Test: `core/designsystem/src/test/java/com/agoitdev/spenvo/designsystem/components/AvatarMenuTest.kt`

- [ ] **Step 1: Write the failing Compose tests**

```kotlin
package com.agoitdev.spenvo.designsystem.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el menu esta cerrado hasta tocar el avatar`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }

        composeTestRule.onNodeWithText("Cuenta").assertDoesNotExist()
    }

    @Test
    fun `estadoLabel nulo no muestra fila de estado`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("test@spenvo.com").assertDoesNotExist()
    }

    @Test
    fun `estadoLabel no nulo muestra una fila deshabilitada`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = "test@spenvo.com",
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("test@spenvo.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("test@spenvo.com").assertIsNotEnabled()
    }

    @Test
    fun `tocar el avatar abre cuenta y ajustes`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    @Test
    fun `Cuenta invoca solamente onOpenAccount`() {
        var cuentaClics = 0
        var ajustesClics = 0
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = { cuentaClics++ },
                onOpenSettings = { ajustesClics++ },
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Cuenta").performClick()

        assertEquals(1, cuentaClics)
        assertEquals(0, ajustesClics)
    }

    @Test
    fun `Ajustes invoca solamente onOpenSettings`() {
        var cuentaClics = 0
        var ajustesClics = 0
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = { cuentaClics++ },
                onOpenSettings = { ajustesClics++ },
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Ajustes").performClick()

        assertEquals(0, cuentaClics)
        assertEquals(1, ajustesClics)
    }

    @Test
    fun `elegir una opcion cierra el menu`() {
        composeTestRule.setContent {
            AvatarMenu(
                photoUrl = null,
                contentDescription = "Cuenta",
                estadoLabel = null,
                accountLabel = "Cuenta",
                settingsLabel = "Ajustes",
                onOpenAccount = {},
                onOpenSettings = {},
            )
        }
        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER, useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithText("Ajustes").performClick()

        composeTestRule.onNodeWithText("Cuenta").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the test and confirm RED**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*.AvatarMenuTest"
```

Expected: compilation failure — `AvatarMenu` doesn't exist yet.

- [ ] **Step 3: Implement `AvatarMenu`**

Wraps the existing `AvatarTopBarAction` (`core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/Avatar.kt:104`) directly — no generic avatar slot. Carries no navigation
knowledge; callers own routing.

```kotlin
package com.agoitdev.spenvo.designsystem.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AvatarMenu(
    photoUrl: String?,
    contentDescription: String,
    estadoLabel: String?,
    accountLabel: String,
    settingsLabel: String,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var abierto by remember { mutableStateOf(false) }

    AvatarTopBarAction(
        photoUrl = photoUrl,
        contentDescription = contentDescription,
        onClick = { abierto = true },
        modifier = modifier,
    )
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        if (estadoLabel != null) {
            DropdownMenuItem(
                text = { Text(text = estadoLabel, style = MaterialTheme.typography.labelLarge) },
                onClick = {},
                enabled = false,
            )
        }
        DropdownMenuItem(
            text = { Text(accountLabel) },
            onClick = {
                abierto = false
                onOpenAccount()
            },
        )
        DropdownMenuItem(
            text = { Text(settingsLabel) },
            onClick = {
                abierto = false
                onOpenSettings()
            },
        )
    }
}
```

- [ ] **Step 4: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*.AvatarMenuTest"
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/AvatarMenu.kt \
        core/designsystem/src/test/java/com/agoitdev/spenvo/designsystem/components/AvatarMenuTest.kt
git commit -m "feat(designsystem): add shared AvatarMenu component"
```

### Task 4: `AppearanceViewModel` and splash-gated startup

**Files:**
- Create: `app/src/main/java/com/agoitdev/spenvo/AppearanceViewModel.kt`
- Test: `app/src/test/java/com/agoitdev/spenvo/AppearanceViewModelTest.kt`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Modify: `app/src/test/java/com/agoitdev/spenvo/SpenvoAppTest.kt`

- [ ] **Step 1: Write the failing `AppearanceViewModel` test**

```kotlin
package com.agoitdev.spenvo

import com.agoitdev.spenvo.designsystem.theme.ColorMode
import com.agoitdev.spenvo.designsystem.theme.ThemeMode
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `estado inicial es Loading`() {
        val repo = FakeAppearanceRepository(MutableStateFlow(AppearancePreferences()))
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))

        assertEquals(AppearanceUiState.Loading, viewModel.estado.value)
    }

    @Test
    fun `primera emision produce Ready con el mapeo correcto`() = runTest {
        val repo = FakeAppearanceRepository(
            MutableStateFlow(AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)),
        )
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))

        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado is AppearanceUiState.Ready)
        estado as AppearanceUiState.Ready
        assertEquals(ThemeMode.DARK, estado.themeMode)
        assertEquals(ColorMode.DYNAMIC, estado.colorMode)
    }

    @Test
    fun `mapea los seis valores de dominio a design system`() = runTest {
        val flow = MutableStateFlow(AppearancePreferences(ThemePreference.SYSTEM, ColorPreference.BRAND))
        val repo = FakeAppearanceRepository(flow)
        val viewModel = AppearanceViewModel(ObservarAppearanceUseCase(repo))
        advanceUntilIdle()

        val casos = listOf(
            ThemePreference.SYSTEM to ThemeMode.SYSTEM,
            ThemePreference.LIGHT to ThemeMode.LIGHT,
            ThemePreference.DARK to ThemeMode.DARK,
        )
        for ((dominio, esperado) in casos) {
            flow.value = AppearancePreferences(dominio, ColorPreference.BRAND)
            advanceUntilIdle()
            assertEquals(esperado, (viewModel.estado.value as AppearanceUiState.Ready).themeMode)
        }

        val coloresCasos = listOf(
            ColorPreference.BRAND to ColorMode.BRAND,
            ColorPreference.DYNAMIC to ColorMode.DYNAMIC,
        )
        for ((dominio, esperado) in coloresCasos) {
            flow.value = AppearancePreferences(ThemePreference.SYSTEM, dominio)
            advanceUntilIdle()
            assertEquals(esperado, (viewModel.estado.value as AppearanceUiState.Ready).colorMode)
        }
    }
}

private class FakeAppearanceRepository(
    override val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    override suspend fun actualizarTema(theme: ThemePreference) = Unit
    override suspend fun actualizarColor(color: ColorPreference) = Unit
}
```

- [ ] **Step 2: Run the test and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.AppearanceViewModelTest"
```

Expected: compilation failure — `AppearanceViewModel`/`AppearanceUiState` don't exist yet.

- [ ] **Step 3: Implement `AppearanceViewModel`**

```kotlin
package com.agoitdev.spenvo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.designsystem.theme.ColorMode
import com.agoitdev.spenvo.designsystem.theme.ThemeMode
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    data class Ready(val themeMode: ThemeMode, val colorMode: ColorMode) : AppearanceUiState
}

private fun AppearancePreferences.aDesignSystem(): AppearanceUiState.Ready {
    val themeMode = when (theme) {
        ThemePreference.SYSTEM -> ThemeMode.SYSTEM
        ThemePreference.LIGHT -> ThemeMode.LIGHT
        ThemePreference.DARK -> ThemeMode.DARK
    }
    val colorMode = when (color) {
        ColorPreference.BRAND -> ColorMode.BRAND
        ColorPreference.DYNAMIC -> ColorMode.DYNAMIC
    }
    return AppearanceUiState.Ready(themeMode, colorMode)
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    observarAppearance: ObservarAppearanceUseCase,
) : ViewModel() {

    val estado: StateFlow<AppearanceUiState> = observarAppearance.invoke()
        .map { it.aDesignSystem() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState.Loading)
}
```

- [ ] **Step 4: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.AppearanceViewModelTest"
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5: Combine the appearance gate with the session gate in `MainActivity`**

Modify `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`. Replace the `onCreate` body (lines
57-66) so the splash is retained until both `SesionGateViewModel` and `AppearanceViewModel` resolve,
and `SpenvoTheme` is composed with the persisted values instead of hardcoded defaults:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var appearance: AppearanceUiState = AppearanceUiState.Loading
        setContent {
            val appearanceViewModel: AppearanceViewModel = hiltViewModel()
            val estadoApariencia by appearanceViewModel.estado.collectAsStateWithLifecycle()
            appearance = estadoApariencia
            splashScreen.setKeepOnScreenCondition { appearance == AppearanceUiState.Loading }
            when (val estado = estadoApariencia) {
                AppearanceUiState.Loading -> Unit
                is AppearanceUiState.Ready -> {
                    SpenvoTheme(themeMode = estado.themeMode, colorMode = estado.colorMode) {
                        SpenvoApp()
                    }
                }
            }
        }
    }
}
```

Add the two new imports (`androidx.compose.runtime.mutableStateOf`/`setValue` are not needed — a
plain `var` captured by the lambda is enough since `setKeepOnScreenCondition`'s predicate is
re-evaluated by the platform on every frame, not recomposition):

```kotlin
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

(`getValue` and `collectAsStateWithLifecycle` are already imported at lines 20 and 27 respectively —
no new import needed beyond `hiltViewModel`, already imported at line 26.)

- [ ] **Step 6: Register `AjustesRoute` as a `NavKey` placeholder**

Task 7 wires the real screen; declare the route now next to the other routes (`MainActivity.kt`
lines 46-53) so Task 4's gate-combination commit compiles on its own:

```kotlin
@Serializable
data object AjustesRoute : NavKey
```

- [ ] **Step 7: Update `SpenvoAppTest` for the combined gate**

Read `app/src/test/java/com/agoitdev/spenvo/SpenvoAppTest.kt` and `SesionGateViewModelTest.kt`
first to match their existing Hilt/Robolectric test harness conventions before adding assertions.
Add a case proving the splash stays up while `AppearanceViewModel` is `Loading` even after
`SesionGateViewModel` resolves, and releases once both are ready. Use the existing fake
repositories in that file as the base and add a `FakeAppearanceRepository` emitting from a
`MutableStateFlow` you control per-test, mirroring Step 1's fake in `AppearanceViewModelTest`.

- [ ] **Step 8: Run the full `:app` unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/AppearanceViewModel.kt \
        app/src/test/java/com/agoitdev/spenvo/AppearanceViewModelTest.kt \
        app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        app/src/test/java/com/agoitdev/spenvo/SpenvoAppTest.kt
git commit -m "feat(app): gate splash on persisted appearance and compose SpenvoTheme with it"
```

### Task 5: `:feature:ajustes` module scaffold

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/ajustes/build.gradle.kts`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, after line 34 (`include(":feature:categorias")`):

```kotlin
include(":feature:ajustes")
```

- [ ] **Step 2: Add the module build file**

Mirrors `feature/categorias/build.gradle.kts` — same dependency shape, new namespace:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.agoitdev.spenvo.ajustes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
```

No `androidx.navigation3.*` dependency: `AjustesRoute`/`AjustesScreen` are wired from `:app`
(`MainActivity.kt`), the module itself has no navigation code — same reasoning as why
`:feature:categorias` needs `navigation3.runtime`/`ui` (it renders its own nested UI state) but
`AjustesScreen` won't (single flat screen).

- [ ] **Step 3: Verify the empty module builds**

```powershell
.\gradlew.bat :feature:ajustes:assembleDebug
```

Expected: BUILD SUCCESSFUL (empty module, no sources yet beyond the manifestless namespace).

- [ ] **Step 4: Regenerate dependency locks**

Adding a module changes the resolved graph; regenerate every module's lockfile per `AGENTS.md`:

```powershell
.\gradlew.bat :app:dependencies :core:domain:dependencies :core:data:dependencies :core:security:dependencies :core:designsystem:dependencies :feature:cuenta:dependencies :feature:planes:dependencies :feature:movimientos:dependencies :feature:categorias:dependencies :feature:ajustes:dependencies --write-locks
```

Expected: BUILD SUCCESSFUL; `feature/ajustes/gradle.lockfile` created, other lockfiles updated only
if resolution actually changed.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts feature/ajustes/build.gradle.kts feature/ajustes/gradle.lockfile \
        app/gradle.lockfile core/domain/gradle.lockfile core/data/gradle.lockfile \
        core/security/gradle.lockfile core/designsystem/gradle.lockfile \
        feature/cuenta/gradle.lockfile feature/planes/gradle.lockfile \
        feature/movimientos/gradle.lockfile feature/categorias/gradle.lockfile
git commit -m "build: register :feature:ajustes module"
```

### Task 6: `AjustesViewModel`

**Files:**
- Create: `feature/ajustes/src/main/java/com/agoitdev/spenvo/ajustes/AjustesViewModel.kt`
- Test: `feature/ajustes/src/test/java/com/agoitdev/spenvo/ajustes/AjustesViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.agoitdev.spenvo.ajustes

import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.repository.AppearancePreferencesRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    private val flow = MutableStateFlow(AppearancePreferences())
    private lateinit var repo: FakeAppearanceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repo = FakeAppearanceRepository(flow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = AjustesViewModel(
        observarAppearance = ObservarAppearanceUseCase(repo),
        actualizarTema = ActualizarTemaUseCase(repo),
        actualizarColor = ActualizarColorUseCase(repo),
    )

    @Test
    fun `el estado refleja la preferencia actual`() = runTest {
        flow.value = AppearancePreferences(ThemePreference.DARK, ColorPreference.DYNAMIC)
        val viewModel = crearViewModel()

        advanceUntilIdle()

        assertEquals(ThemePreference.DARK, viewModel.estado.value.theme)
        assertEquals(ColorPreference.DYNAMIC, viewModel.estado.value.color)
    }

    @Test
    fun `seleccionarTema invoca solo el caso de uso de tema`() = runTest {
        val viewModel = crearViewModel()

        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        assertEquals(listOf(ThemePreference.DARK), repo.temasActualizados)
        assertTrue(repo.coloresActualizados.isEmpty())
    }

    @Test
    fun `seleccionarColor invoca solo el caso de uso de color`() = runTest {
        val viewModel = crearViewModel()

        viewModel.seleccionarColor(ColorPreference.DYNAMIC)
        advanceUntilIdle()

        assertEquals(listOf(ColorPreference.DYNAMIC), repo.coloresActualizados)
        assertTrue(repo.temasActualizados.isEmpty())
    }

    @Test
    fun `escritura fallida marca errorGuardado sin tocar la preferencia`() = runTest {
        repo.fallarProximaEscritura = true
        val viewModel = crearViewModel()

        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.errorGuardado)
        assertEquals(ThemePreference.SYSTEM, viewModel.estado.value.theme)
    }

    @Test
    fun `consumirErrorGuardado limpia el error`() = runTest {
        repo.fallarProximaEscritura = true
        val viewModel = crearViewModel()
        viewModel.seleccionarTema(ThemePreference.DARK)
        advanceUntilIdle()

        viewModel.consumirErrorGuardado()

        assertFalse(viewModel.estado.value.errorGuardado)
    }
}

private class FakeAppearanceRepository(
    override val preferencias: Flow<AppearancePreferences>,
) : AppearancePreferencesRepository {
    val temasActualizados = mutableListOf<ThemePreference>()
    val coloresActualizados = mutableListOf<ColorPreference>()
    var fallarProximaEscritura = false

    override suspend fun actualizarTema(theme: ThemePreference) {
        if (fallarProximaEscritura) throw java.io.IOException("boom")
        temasActualizados += theme
    }

    override suspend fun actualizarColor(color: ColorPreference) {
        if (fallarProximaEscritura) throw java.io.IOException("boom")
        coloresActualizados += color
    }
}
```

- [ ] **Step 2: Run the test and confirm RED**

```powershell
.\gradlew.bat :feature:ajustes:testDebugUnitTest --tests "*.AjustesViewModelTest"
```

Expected: compilation failure — `AjustesViewModel`/`AjustesUiState` don't exist yet.

- [ ] **Step 3: Implement `AjustesViewModel`**

The Flow-driven state IS the rollback: a failed write never reaches DataStore, so the next
`preferencias` emission still reflects the last confirmed value — no separate optimistic/pending
state, matching D7/D8.

```kotlin
package com.agoitdev.spenvo.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.AppearancePreferences
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import com.agoitdev.spenvo.domain.usecase.ActualizarColorUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarTemaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarAppearanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AjustesUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val color: ColorPreference = ColorPreference.BRAND,
    val errorGuardado: Boolean = false,
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    observarAppearance: ObservarAppearanceUseCase,
    private val actualizarTema: ActualizarTemaUseCase,
    private val actualizarColor: ActualizarColorUseCase,
) : ViewModel() {

    private val errorGuardado = MutableStateFlow(false)

    val estado: StateFlow<AjustesUiState> = combine(
        observarAppearance.invoke(),
        errorGuardado,
    ) { preferencias, error ->
        AjustesUiState(theme = preferencias.theme, color = preferencias.color, errorGuardado = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())

    fun seleccionarTema(theme: ThemePreference) {
        viewModelScope.launch {
            runCatching { actualizarTema.invoke(theme) }.onFailure { errorGuardado.value = true }
        }
    }

    fun seleccionarColor(color: ColorPreference) {
        viewModelScope.launch {
            runCatching { actualizarColor.invoke(color) }.onFailure { errorGuardado.value = true }
        }
    }

    fun consumirErrorGuardado() {
        errorGuardado.value = false
    }
}
```

- [ ] **Step 4: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :feature:ajustes:testDebugUnitTest --tests "*.AjustesViewModelTest"
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add feature/ajustes/src/main/java/com/agoitdev/spenvo/ajustes/AjustesViewModel.kt \
        feature/ajustes/src/test/java/com/agoitdev/spenvo/ajustes/AjustesViewModelTest.kt
git commit -m "feat(ajustes): add AjustesViewModel"
```

### Task 7: `AjustesScreen` and route registration

**Files:**
- Create: `feature/ajustes/src/main/res/values/strings.xml`
- Create: `feature/ajustes/src/main/res/values-en/strings.xml`
- Create: `feature/ajustes/src/main/java/com/agoitdev/spenvo/ajustes/AjustesScreen.kt`
- Test: `feature/ajustes/src/test/java/com/agoitdev/spenvo/ajustes/AjustesScreenTest.kt`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`

- [ ] **Step 1: Add localized strings**

`feature/ajustes/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_title">Ajustes</string>
    <string name="settings_appearance_section">Apariencia</string>
    <string name="settings_theme_label">Tema</string>
    <string name="settings_theme_system">Sistema</string>
    <string name="settings_theme_light">Claro</string>
    <string name="settings_theme_dark">Oscuro</string>
    <string name="settings_color_label">Colores</string>
    <string name="settings_color_brand">Spenvo</string>
    <string name="settings_color_dynamic">Colores dinámicos</string>
    <string name="settings_color_dynamic_unsupported">Requiere Android 12</string>
    <string name="settings_save_error">No se pudo guardar la preferencia</string>
    <string name="settings_back">Volver</string>
</resources>
```

`feature/ajustes/src/main/res/values-en/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_title">Settings</string>
    <string name="settings_appearance_section">Appearance</string>
    <string name="settings_theme_label">Theme</string>
    <string name="settings_theme_system">System default</string>
    <string name="settings_theme_light">Light</string>
    <string name="settings_theme_dark">Dark</string>
    <string name="settings_color_label">Colors</string>
    <string name="settings_color_brand">Spenvo</string>
    <string name="settings_color_dynamic">Dynamic colors</string>
    <string name="settings_color_dynamic_unsupported">Requires Android 12</string>
    <string name="settings_save_error">Preference could not be saved</string>
    <string name="settings_back">Back</string>
</resources>
```

- [ ] **Step 2: Write the failing screen test**

```kotlin
package com.agoitdev.spenvo.ajustes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tocar Oscuro invoca onSeleccionarTema con DARK`() {
        var temaSeleccionado: ThemePreference? = null
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = true,
                onSeleccionarTema = { temaSeleccionado = it },
                onSeleccionarColor = {},
            )
        }

        composeTestRule.onNodeWithText("Oscuro").performClick()

        assertEquals(ThemePreference.DARK, temaSeleccionado)
    }

    @Test
    fun `tocar Colores dinamicos invoca onSeleccionarColor con DYNAMIC cuando esta disponible`() {
        var colorSeleccionado: ColorPreference? = null
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = true,
                onSeleccionarTema = {},
                onSeleccionarColor = { colorSeleccionado = it },
            )
        }

        composeTestRule.onNodeWithText("Colores dinámicos").performClick()

        assertEquals(ColorPreference.DYNAMIC, colorSeleccionado)
    }

    @Test
    fun `Colores dinamicos deshabilitado no invoca onSeleccionarColor por debajo de API 31`() {
        var invocado = false
        composeTestRule.setContent {
            AjustesContenido(
                estado = AjustesUiState(),
                dynamicDisponible = false,
                onSeleccionarTema = {},
                onSeleccionarColor = { invocado = true },
            )
        }

        composeTestRule.onNodeWithText("Colores dinámicos").performClick()

        assertEquals(false, invocado)
        composeTestRule.onNodeWithText("Requiere Android 12").assertExists()
    }
}
```

`assertExists()` needs `import androidx.compose.ui.test.assertExists`.

- [ ] **Step 3: Run the test and confirm RED**

```powershell
.\gradlew.bat :feature:ajustes:testDebugUnitTest --tests "*.AjustesScreenTest"
```

Expected: compilation failure — `AjustesContenido` doesn't exist yet.

- [ ] **Step 4: Implement `AjustesScreen`**

`AjustesContenido` is the pure, stateless part (testable without Hilt); `AjustesScreen` wires it to
the ViewModel, matching the `CategoriasScreen`/`Scaffold`/`SnackbarHost` convention.

```kotlin
package com.agoitdev.spenvo.ajustes

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoitdev.spenvo.ajustes.R
import com.agoitdev.spenvo.domain.model.ColorPreference
import com.agoitdev.spenvo.domain.model.ThemePreference

@Composable
fun AjustesScreen(modifier: Modifier = Modifier, viewModel: AjustesViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mensajeError = stringResource(R.string.settings_save_error)

    LaunchedEffect(estado.errorGuardado) {
        if (estado.errorGuardado) {
            snackbarHostState.showSnackbar(mensajeError)
            viewModel.consumirErrorGuardado()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AjustesContenido(
            estado = estado,
            dynamicDisponible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onSeleccionarTema = viewModel::seleccionarTema,
            onSeleccionarColor = viewModel::seleccionarColor,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun AjustesContenido(
    estado: AjustesUiState,
    dynamicDisponible: Boolean,
    onSeleccionarTema: (ThemePreference) -> Unit,
    onSeleccionarColor: (ColorPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_appearance_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.selectableGroup()) {
            OpcionTema(
                seleccionado = estado.theme == ThemePreference.SYSTEM,
                etiqueta = stringResource(R.string.settings_theme_system),
                onClick = { onSeleccionarTema(ThemePreference.SYSTEM) },
            )
            OpcionTema(
                seleccionado = estado.theme == ThemePreference.LIGHT,
                etiqueta = stringResource(R.string.settings_theme_light),
                onClick = { onSeleccionarTema(ThemePreference.LIGHT) },
            )
            OpcionTema(
                seleccionado = estado.theme == ThemePreference.DARK,
                etiqueta = stringResource(R.string.settings_theme_dark),
                onClick = { onSeleccionarTema(ThemePreference.DARK) },
            )
        }
        Text(
            text = stringResource(R.string.settings_color_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.selectableGroup()) {
            OpcionColor(
                seleccionado = estado.color == ColorPreference.BRAND,
                habilitado = true,
                etiqueta = stringResource(R.string.settings_color_brand),
                textoSoporte = null,
                onClick = { onSeleccionarColor(ColorPreference.BRAND) },
            )
            OpcionColor(
                seleccionado = estado.color == ColorPreference.DYNAMIC,
                habilitado = dynamicDisponible,
                etiqueta = stringResource(R.string.settings_color_dynamic),
                textoSoporte = if (dynamicDisponible) {
                    null
                } else {
                    stringResource(R.string.settings_color_dynamic_unsupported)
                },
                onClick = { onSeleccionarColor(ColorPreference.DYNAMIC) },
            )
        }
    }
}

@Composable
private fun OpcionTema(seleccionado: Boolean, etiqueta: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(etiqueta) },
        leadingContent = { RadioButton(selected = seleccionado, onClick = null) },
        modifier = Modifier.selectable(
            selected = seleccionado,
            onClick = onClick,
            role = Role.RadioButton,
        ),
    )
}

@Composable
private fun OpcionColor(
    seleccionado: Boolean,
    habilitado: Boolean,
    etiqueta: String,
    textoSoporte: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(etiqueta) },
        supportingContent = textoSoporte?.let { { Text(it) } },
        leadingContent = {
            RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
        },
        modifier = Modifier.selectable(
            selected = seleccionado,
            enabled = habilitado,
            onClick = onClick,
            role = Role.RadioButton,
        ),
    )
}
```

- [ ] **Step 5: Run the test and confirm GREEN**

```powershell
.\gradlew.bat :feature:ajustes:testDebugUnitTest --tests "*.AjustesScreenTest"
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 6: Register `AjustesRoute`'s real content in `MainActivity`**

In `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`'s `entryProvider` block (after the
`entry<CuentaRoute> { ... }` block, MainActivity.kt:217-223), add:

```kotlin
entry<AjustesRoute> {
    AjustesScreen()
}
```

Add the import:

```kotlin
import com.agoitdev.spenvo.ajustes.AjustesScreen
```

Add `:feature:ajustes` as an `:app` dependency in `app/build.gradle.kts` (find the existing
`implementation(project(":feature:categorias"))` line and add a sibling line right after it):

```kotlin
implementation(project(":feature:ajustes"))
```

- [ ] **Step 7: Build the app module**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Regenerate dependency locks for `:app`**

```powershell
.\gradlew.bat :app:dependencies --write-locks
```

- [ ] **Step 9: Commit**

```bash
git add feature/ajustes/src/main/res/values/strings.xml \
        feature/ajustes/src/main/res/values-en/strings.xml \
        feature/ajustes/src/main/java/com/agoitdev/spenvo/ajustes/AjustesScreen.kt \
        feature/ajustes/src/test/java/com/agoitdev/spenvo/ajustes/AjustesScreenTest.kt \
        app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        app/build.gradle.kts app/gradle.lockfile
git commit -m "feat(ajustes): add AjustesScreen and register AjustesRoute"
```

### Task 8: Wire `AvatarMenu` and Ajustes across the 5 top bars

**Files:**
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt`
- Modify: `feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt`
- Modify: `feature/planes/src/main/res/values/strings.xml` (+ `values-en`)
- Modify: `feature/movimientos/src/main/res/values/strings.xml` (+ `values-en`)
- Modify: `feature/categorias/src/main/res/values/strings.xml` (+ `values-en`)

This task is mechanical repetition across 5 call sites — each screen already threads
`avatarUrl: String?` and `onAbrirCuenta: () -> Unit`. Add one sibling param `onAbrirAjustes: () -> Unit`
and swap the raw `IconButton`/`CuentaMenu` for `AvatarMenu`.

- [ ] **Step 1: Thread `onAbrirAjustes` through `MainActivity`**

In `MainActivity.kt`, `SpenvoApp` (lines 204-216): add the callback to both the `PlanesScreen` and
`ContenidoPlanRoute` calls:

```kotlin
entry<PlanesRoute> {
    PlanesScreen(
        onCrearCuenta = { backStack.pushUnlessTop(CuentaRoute) },
        onAbrirAjustes = { backStack.pushUnlessTop(AjustesRoute) },
        onAbrirPlan = { planId -> backStack.pushUnlessTop(PlanRoute(planId)) },
    )
}
entry<PlanRoute> { route ->
    ContenidoPlanRoute(
        route = route,
        avatarUrl = avatarUrl,
        onAbrirCuenta = { backStack.pushUnlessTop(CuentaRoute) },
        onAbrirAjustes = { backStack.pushUnlessTop(AjustesRoute) },
    )
}
```

Update `ContenidoPlanRoute`'s signature (lines 235-239) to accept and thread the new callback to
all four `contenido*` lambdas, alongside the existing `onAbrirCuenta`:

```kotlin
@Composable
private fun ContenidoPlanRoute(
    route: PlanRoute,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
    PlanScaffold(
        contenidoHome = {
            HomeScreen(
                planId = route.planId,
                movimientosViewModel = movimientosViewModel,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                onAbrirAjustes = onAbrirAjustes,
            )
        },
        contenidoMovimientos = {
            MovimientosScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                onAbrirAjustes = onAbrirAjustes,
                viewModel = movimientosViewModel,
            )
        },
        contenidoCategorias = {
            CategoriasScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                onAbrirAjustes = onAbrirAjustes,
            )
        },
        contenidoMiembros = {
            MiembrosScreen(
                planId = route.planId,
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
                onAbrirAjustes = onAbrirAjustes,
            )
        },
    )
}
```

- [ ] **Step 2: Add the `settings_menu_item` string to the 3 owning modules**

`feature/planes/src/main/res/values/strings.xml` (next to the existing `account_menu_description`
at line 16): add `<string name="settings_menu_item">Ajustes</string>`. `values-en` counterpart:
`<string name="settings_menu_item">Settings</string>`.

Repeat identically in `feature/movimientos/src/main/res/values/strings.xml` (next to its own
`account_menu_description` at line 11) and `feature/categorias/src/main/res/values/strings.xml`
(next to line 4), each with their `values-en` counterpart.

- [ ] **Step 3: Replace `PlanesScreen`'s `CuentaMenu` with `AvatarMenu`**

In `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt`: add
`onAbrirAjustes: () -> Unit` to `PlanesScreen`'s and `PlanesTopBar`'s parameter lists (lines 59,
128-130), thread it to the existing call site at line 141-142, delete the private `CuentaMenu`
composable (lines 313-339), and replace its call site with `AvatarMenu`:

```kotlin
AvatarMenu(
    photoUrl = avatarUrl,
    contentDescription = stringResource(R.string.account_menu_description),
    estadoLabel = estado,
    accountLabel = stringResource(R.string.account_create),
    settingsLabel = stringResource(R.string.settings_menu_item),
    onOpenAccount = onCrearCuenta,
    onOpenSettings = onAbrirAjustes,
)
```

`estado` is the existing `when` expression computed at the call site (real file lines 136-140):
`null` while not authenticated, the linked email, or the guest-state string — unchanged from what
`CuentaMenu` passed. Task 3's `AvatarMenu` now renders it as the same disabled label row `CuentaMenu`
did, so this preserves the current behavior exactly rather than dropping it.

Import `com.agoitdev.spenvo.designsystem.components.AvatarMenu`; the `AvatarTopBarAction` import
may become unused here and should be removed if so (`CuentaMenu` was its only local caller —
`DropdownMenu`/`DropdownMenuItem` imports likewise).

- [ ] **Step 4: Replace the raw avatar `IconButton` in the 4 plan-tab top bars**

For each of `HomeTopBar` (`HomeScreen.kt:121-128`), the equivalent private top bar in
`MovimientosScreen.kt`, `CategoriasScreen.kt`, and `MiembrosScreen.kt`: add `onAbrirAjustes: () -> Unit`
to the public screen function and to the private top bar composable's parameter list, then replace
the `IconButton(onClick = onAbrirCuenta) { AvatarTopBarAction(...) }`-shaped call (or direct
`AvatarTopBarAction(..., onClick = onAbrirCuenta)`) with:

```kotlin
AvatarMenu(
    photoUrl = avatarUrl,
    contentDescription = stringResource(R.string.account_menu_description),
    estadoLabel = null,
    accountLabel = stringResource(R.string.account_menu_description),
    settingsLabel = stringResource(R.string.settings_menu_item),
    onOpenAccount = onAbrirCuenta,
    onOpenSettings = onAbrirAjustes,
)
```

`estadoLabel = null` here: none of these 4 screens compute the email/guest identity `PlanesScreen`
does today (they only receive `avatarUrl`), so this preserves current behavior — no label row on
these top bars, same as before this change.

Import `com.agoitdev.spenvo.designsystem.components.AvatarMenu` in each of the 4 files; drop the
now-unused `AvatarTopBarAction` import where it was the only consumer.

- [ ] **Step 5: Update each screen's existing Compose tests**

`HomeScreenTest.kt`, and the equivalent tests for `MovimientosScreen`, `CategoriasScreen`,
`MiembrosScreen`, and `PlanesScreenTest.kt`, all construct these screens directly — read each file
first, then add the new `onAbrirAjustes = {}` (or a per-test lambda where the test needs to assert
on it) to every existing call site so they keep compiling. Add one assertion per screen proving
tapping the avatar and then "Ajustes" invokes the new callback, mirroring however that file already
asserts `onAbrirCuenta` invocation.

- [ ] **Step 6: Run the full test suite for the 4 touched feature modules**

```powershell
.\gradlew.bat :feature:planes:testDebugUnitTest :feature:movimientos:testDebugUnitTest :feature:categorias:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Build the app**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt \
        feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt \
        feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt \
        feature/planes/src/main/res/values/strings.xml feature/planes/src/main/res/values-en/strings.xml \
        feature/movimientos/src/main/res/values/strings.xml feature/movimientos/src/main/res/values-en/strings.xml \
        feature/categorias/src/main/res/values/strings.xml feature/categorias/src/main/res/values-en/strings.xml \
        feature/planes/src/test feature/movimientos/src/test feature/categorias/src/test
git commit -m "feat(nav): replace duplicated avatar button with shared AvatarMenu across 5 top bars"
```

### Task 9: Final gates and documentation closure

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `backlog.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: Run the complete project gates**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat detekt
```

Expected: every command finishes with BUILD SUCCESSFUL. `lintDebug` must show zero
`HardcodedText`/`MissingTranslation` findings.

- [ ] **Step 2: Run the instrumented DataStore suite**

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest --tests "*.ThemePreferencesTest"
```

Expected: BUILD SUCCESSFUL on a running emulator/device. Record which API level ran it (needed for
the DYNAMIC-normalization test's coverage note from Task 2 Step 1).

- [ ] **Step 3: Manual verification**

On an emulator/device at API < 31 and one at API 31+: open Ajustes from `PlanesScreen` and from
each of the 4 plan tabs; confirm Dynamic is visible-disabled below API 31 and selectable at 31+;
confirm each selection applies immediately without recreating the Activity; force a DataStore write
failure (e.g., revoke storage momentarily or inspect via a temporary throw) to confirm the Snackbar
appears and the radio reverts; kill and relaunch the process after selecting Dark to confirm
restoration with no SYSTEM+BRAND flash.

- [ ] **Step 4: Update project records**

Add an English Keep a Changelog `[Unreleased]` entry under a new `### Added` section (or the
existing one if present) describing UI-THEME-002. Move the `UI-THEME-002` line in `backlog.md` from
"To Do" to "Done", check it off, and append a short delivery summary linking both the design and
this plan (matching the `UI-THEME-001` Done entry's format at `backlog.md:43-50`). Add a Phase 8
`ROADMAP.md` bullet analogous to the existing "Material 3 design-system foundation" one
(`ROADMAP.md:50`).

- [ ] **Step 5: Check the final diff**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only planned files modified or created.

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md backlog.md ROADMAP.md
git commit -m "docs: close out UI-THEME-002 delivery records"
```

## Explicitly deferred work

Firebase-synced preferences, per-user (`uid`-scoped) preferences, a custom color picker,
medium/high contrast, financial-color harmonization, per-category colors, M3 Expressive, custom
theme-transition animations, a global spacing system, and any Settings section beyond Appearance —
per the design's "Explicitly out of scope."

## Self-review

- Every D1-D19 decision from the design maps to a task: domain/data split (Task 1-2), immediate
  apply + read/write error handling (Task 2, 6), `AvatarMenu` (Task 3), splash coordination (Task
  4), module placement (Task 5), UI controls and Dynamic gating (Task 7), navigation scope across
  `PlanesScreen` + 4 tabs (Task 8), branch-from-`main` and ticket identity were already applied when
  this plan and its worktree were created.
- `AvatarMenu` (Task 3) carries an optional `estadoLabel` row, preserving `PlanesScreen`'s existing
  disabled guest/account-identity row (Task 8 Step 3) instead of dropping it; the 4 plan-tab top
  bars pass `estadoLabel = null` since they show no such row today (Task 8 Step 4).
- No placeholder steps; every step carries complete code or an exact command.
- No new external dependency; only a new local module (`:feature:ajustes`), covered by Task 5's
  lockfile regeneration.
