# Home Screen + Bottom Navigation Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Add a per-plan Home dashboard and a bottom navigation bar (Home/Movimientos/Categorías/
Miembros), per `doc/designs/2026-08-27-home-bottom-nav-design.md`, replacing the direct push to
Movimientos when a plan is opened.

**Architecture:** A new `PlanRoute(planId)` Navigation 3 route replaces the current
`MovimientosRoute(planId)` push site. Its composable hosts one `Scaffold` with a Material 3
`NavigationBar`; the 4 tabs switch via local `rememberSaveable` state (Option A from the design —
no nested backstack). One `MovimientosViewModel` is created at the `PlanRoute` level and shared
between the Home tab (quick-add actions) and the Movimientos tab. `MiembrosRoute`/`CategoriasRoute`
are deleted entirely — those screens become tab content, not pushed routes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, `NavigationBar`/`NavigationBarItem` — new to
this codebase), Navigation 3 (`androidx.navigation3` 1.1.4), Hilt, JUnit4 + kotlinx-coroutines-test
+ Robolectric + `ui-test-junit4`.

**Platform:** Android.

---

## Task 1: Domain — cumulative balance + monthly income/expense split

**Files:**
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/model/ResumenMensualPlan.kt`
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarResumenMensualPlanUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCase.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/MovimientoModule.kt`
- Modify: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarResumenMensualPlanUseCaseTest.kt`
- Modify: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt` (2 call sites need the new required fields)
- Create: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests for the extended `ResumenMensualPlan`**

Read `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarResumenMensualPlanUseCaseTest.kt` first to confirm its exact current fakes/helpers (a private, file-scoped
`FakeMovimientoResumenRepository`). Add these two assertions to its existing tests that already
build gasto/ingreso fixtures (extend the existing "combina ingresos y gastos" style test rather
than duplicating fixture setup — read the file to find the right existing test to extend):

```kotlin
        assertEquals(3000L, resumen.ingresosMes.unidadesMenores)
        assertEquals(1000L, resumen.gastosMes.unidadesMenores)
```
(Adjust the literal expected values to match whatever gasto/ingreso amounts that specific existing
test already seeds — do not invent new fixture data, reuse what's already there.)

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.ObservarResumenMensualPlanUseCaseTest"`
Expected: **compile failure** — `resumen.ingresosMes`/`resumen.gastosMes` don't exist yet.

- [ ] **Step 2: Extend `ResumenMensualPlan`**

Replace the full content of `ResumenMensualPlan.kt`:

```kotlin
package com.agoitdev.spenvo.domain.model

data class ResumenMensualPlan(
    val planId: String,
    val netoDelMes: Monto,
    val ingresosMes: Monto,
    val gastosMes: Monto,
)
```

- [ ] **Step 3: Update the use case to populate the new fields**

In `ObservarResumenMensualPlanUseCase.kt`, replace the `combine` block's final line:

```kotlin
            ResumenMensualPlan(planId, Monto(ingresosMes - gastosMes))
```
with:
```kotlin
            ResumenMensualPlan(
                planId = planId,
                netoDelMes = Monto(ingresosMes - gastosMes),
                ingresosMes = Monto(ingresosMes),
                gastosMes = Monto(gastosMes),
            )
```
(`ingresosMes`/`gastosMes` local `Long` variables already exist a few lines above this — the use
case was already computing them, just discarding them before this change.)

- [ ] **Step 4: Fix the two existing `PlanesScreenTest.kt` call sites**

`ResumenMensualPlan`'s constructor now requires 2 more fields. In
`feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt`, find the two
`ResumenMensualPlan(planId = "p1", netoDelMes = ...)` calls (in `muestra un balance positivo`/
`muestra un balance negativo`) and add the two new fields — their exact values don't affect those
tests' assertions (`PlanCard` only reads `netoDelMes`), so any valid `Monto` works:
```kotlin
                resumen = ResumenMensualPlan(
                    planId = "p1",
                    netoDelMes = Monto(2000),
                    ingresosMes = Monto(2000),
                    gastosMes = Monto(0),
                ),
```
(and the equivalent for the negative-balance test, e.g. `netoDelMes = Monto(-4000), ingresosMes = Monto(0), gastosMes = Monto(4000)`).

- [ ] **Step 5: Run tests to verify Steps 1-4 pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.ObservarResumenMensualPlanUseCaseTest" :feature:planes:testDebugUnitTest --tests "*.PlanesScreenTest"`
Expected: BUILD SUCCESSFUL, all pass.

- [ ] **Step 6: Write the failing test for the new cumulative-balance use case**

Create `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCaseTest.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservarBalanceAcumuladoPlanUseCaseTest {

    private fun gasto(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: java.time.Instant? = null) = Gasto(
        id = id,
        planId = "p1",
        categoriaId = "cat-1",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    private fun ingreso(id: String, montoMenor: Long, fecha: LocalDate, deletedAt: java.time.Instant? = null) = Ingreso(
        id = id,
        planId = "p1",
        categoriaId = "cat-2",
        monto = Monto(montoMenor),
        fecha = fecha,
        creadoPor = "user-1",
        deletedAt = deletedAt,
    )

    @Test
    fun `suma todos los movimientos sin importar el mes`() = runTest {
        val repo = FakeMovimientoRepositorioBalance(
            gastos = listOf(
                gasto("g1", 1000, LocalDate.of(2026, 1, 5)),
                gasto("g2", 500, LocalDate.of(2026, 8, 20)),
            ),
            ingresos = listOf(
                ingreso("i1", 3000, LocalDate.of(2026, 1, 1)),
                ingreso("i2", 3000, LocalDate.of(2026, 8, 1)),
            ),
        )
        val useCase = ObservarBalanceAcumuladoPlanUseCase(repo)

        val balance = useCase("p1").first()

        assertEquals(4500L, balance.unidadesMenores)
    }

    @Test
    fun `excluye movimientos borrados`() = runTest {
        val repo = FakeMovimientoRepositorioBalance(
            gastos = listOf(gasto("g1", 1000, LocalDate.of(2026, 8, 5), deletedAt = java.time.Instant.now())),
            ingresos = listOf(ingreso("i1", 3000, LocalDate.of(2026, 8, 1))),
        )
        val useCase = ObservarBalanceAcumuladoPlanUseCase(repo)

        val balance = useCase("p1").first()

        assertEquals(3000L, balance.unidadesMenores)
    }
}

private class FakeMovimientoRepositorioBalance(
    gastos: List<Gasto> = emptyList(),
    ingresos: List<Ingreso> = emptyList(),
) : MovimientoRepository {
    private val gastosFlow = MutableStateFlow(gastos)
    private val ingresosFlow = MutableStateFlow(ingresos)
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = gastosFlow
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = ingresosFlow
}
```

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.ObservarBalanceAcumuladoPlanUseCaseTest"`
Expected: **compile failure** — `ObservarBalanceAcumuladoPlanUseCase` doesn't exist yet.

- [ ] **Step 7: Implement the use case**

Create `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObservarBalanceAcumuladoPlanUseCase(
    private val movimientoRepository: MovimientoRepository,
) {
    operator fun invoke(planId: String): Flow<Monto> =
        combine(
            movimientoRepository.observeGastos(planId),
            movimientoRepository.observeIngresos(planId),
        ) { gastos, ingresos ->
            val totalGastos = gastos.filter { it.deletedAt == null }.sumOf { it.monto.unidadesMenores }
            val totalIngresos = ingresos.filter { it.deletedAt == null }.sumOf { it.monto.unidadesMenores }
            Monto(totalIngresos - totalGastos)
        }
}
```

- [ ] **Step 8: Register the use case in DI**

In `core/data/src/main/java/com/agoitdev/spenvo/data/di/MovimientoModule.kt`, add to
`MovimientoResumenUseCaseModule` (the object that already provides `ObservarResumenMensualPlanUseCase`):

```kotlin
    @Provides
    fun provideObservarBalanceAcumuladoPlan(
        movimientoRepository: MovimientoRepository,
    ): ObservarBalanceAcumuladoPlanUseCase = ObservarBalanceAcumuladoPlanUseCase(movimientoRepository)
```
Add the import `com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase`.

- [ ] **Step 9: Run tests to verify they pass**

Run: `./gradlew :core:domain:testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all pass including the 2 new tests.

- [ ] **Step 10: detekt**

Run: `./gradlew :core:domain:detekt :core:data:detekt`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 11: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/ResumenMensualPlan.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarResumenMensualPlanUseCase.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCase.kt \
        core/data/src/main/java/com/agoitdev/spenvo/data/di/MovimientoModule.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarResumenMensualPlanUseCaseTest.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ObservarBalanceAcumuladoPlanUseCaseTest.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt
git commit -m "feat(domain): expose monthly income/expense split and add cumulative plan balance"
```

---

## Task 2: `HomeScreen` + `HomeViewModel` (new)

**Files:**
- Create: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeViewModel.kt`
- Create: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt`
- Create: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeViewModelTest.kt`
- Create: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt`
- Modify: `feature/movimientos/src/main/res/values/strings.xml`
- Modify: `feature/movimientos/src/main/res/values-en/strings.xml`

`HomeScreen` lives in `:feature:movimientos` (not a new `:feature:home` module) because it shares
a `MovimientosViewModel` instance with the Movimientos tab and needs `MovimientoFormularioSheet` —
keeping it in the same module avoids a cross-feature dependency. It only needs `PlanFinanciero`,
`ObservarPlanUseCase`, `ObservarResumenMensualPlanUseCase`, `ObservarBalanceAcumuladoPlanUseCase`
from `:core:domain`, which `:feature:movimientos` already depends on.

- [ ] **Step 1: Add the new strings**

`feature/movimientos/src/main/res/values/strings.xml` — add:
```xml
    <string name="home_subtitle">Resumen financiero</string>
    <string name="home_balance_acumulado">Balance acumulado</string>
    <string name="home_income_label">Ingresos (este mes)</string>
    <string name="home_expense_label">Gastos (este mes)</string>
    <string name="home_action_new_expense">Nuevo Gasto</string>
    <string name="home_action_new_income">Nuevo Ingreso</string>
```
`feature/movimientos/src/main/res/values-en/strings.xml` — add:
```xml
    <string name="home_subtitle">Financial overview</string>
    <string name="home_balance_acumulado">Cumulative balance</string>
    <string name="home_income_label">Income (this month)</string>
    <string name="home_expense_label">Expenses (this month)</string>
    <string name="home_action_new_expense">New Expense</string>
    <string name="home_action_new_income">New Income</string>
```

Run: `./gradlew :feature:movimientos:lintDebug`
Expected: BUILD SUCCESSFUL, no `MissingTranslation`.

- [ ] **Step 2: Write the failing `HomeViewModelTest`**

Create `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeViewModelTest.kt`:

```kotlin
package com.agoitdev.spenvo.movimientos

import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val plan = PlanFinanciero(
        id = "p1",
        nombre = "Casa",
        moneda = "EUR",
        createdBy = "user-1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `expone el plan, el resumen del mes y el balance acumulado`() = runTest {
        val viewModel = HomeViewModel(
            observarPlan = ObservarPlanUseCase(FakePlanFinancieroRepositorioHome(plan)),
            observarResumenMensual = ObservarResumenMensualPlanUseCase(FakeMovimientoRepositorioHome()),
            observarBalanceAcumulado = ObservarBalanceAcumuladoPlanUseCase(FakeMovimientoRepositorioHome()),
        )

        val planJob = launch { viewModel.plan("p1").collect {} }
        val resumenJob = launch { viewModel.resumenMensual("p1").collect {} }
        val balanceJob = launch { viewModel.balanceAcumulado("p1").collect {} }
        advanceUntilIdle()

        assertEquals(plan, viewModel.plan("p1").value)
        assertEquals(0L, viewModel.resumenMensual("p1").value?.netoDelMes?.unidadesMenores)
        assertEquals(0L, viewModel.balanceAcumulado("p1").value.unidadesMenores)

        planJob.cancel()
        resumenJob.cancel()
        balanceJob.cancel()
    }
}

private class FakePlanFinancieroRepositorioHome(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHome : com.agoitdev.spenvo.domain.repository.MovimientoRepository {
    override suspend fun addGasto(gasto: com.agoitdev.spenvo.domain.model.Gasto) = Unit
    override suspend fun addIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: com.agoitdev.spenvo.domain.model.Gasto) = Unit
    override suspend fun eliminarGasto(gasto: com.agoitdev.spenvo.domain.model.Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<com.agoitdev.spenvo.domain.model.Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<com.agoitdev.spenvo.domain.model.Ingreso>> = flowOf(emptyList())
}
```

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.HomeViewModelTest"`
Expected: **compile failure** — `HomeViewModel` doesn't exist yet.

- [ ] **Step 3: Implement `HomeViewModel`**

Create `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeViewModel.kt`:

```kotlin
package com.agoitdev.spenvo.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observarPlan: ObservarPlanUseCase,
    private val observarResumenMensual: ObservarResumenMensualPlanUseCase,
    private val observarBalanceAcumulado: ObservarBalanceAcumuladoPlanUseCase,
) : ViewModel() {

    fun plan(planId: String): StateFlow<PlanFinanciero?> =
        observarPlan(planId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), null)

    fun resumenMensual(planId: String): StateFlow<ResumenMensualPlan?> =
        observarResumenMensual(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), null)

    fun balanceAcumulado(planId: String): StateFlow<Monto> =
        observarBalanceAcumulado(planId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), Monto(0))

    private companion object {
        const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L
    }
}
```

Note: `ObservarPlanUseCase`'s exact `invoke` signature was not read verbatim during planning
reconnaissance (only confirmed it exists and is DI-registered in `PlanModule.kt`) — read
`core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ObservarPlanUseCase.kt` first and
adjust the call `observarPlan(planId)` above if its actual parameter name/shape differs from this
assumption (it returns `Flow<PlanFinanciero?>` per `PlanFinancieroRepository.observarPlan`, which
this plan's Step 2 fake already matches).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.HomeViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing `HomeScreen` Compose test**

Create `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt`. This
needs a `MovimientosViewModel` too (for the quick-add flow), constructed the same way as
`MovimientoFormSheetTest.kt`'s `crearViewModel()` — read that file first (it already has
`FakeMovimientoRepositorioForm`/`FakeCategoriaRepositorioForm`/`FakeMovimientoSincronizacionForm`/
`FakeAuthRepositorioForm`, all `private` to that file) and mirror the same fakes here under a
`...Home` suffix, matching this module's established per-test-file-fakes convention:

```kotlin
package com.agoitdev.spenvo.movimientos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarGastoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarIngresoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarBalanceAcumuladoPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plan = PlanFinanciero(id = "p1", nombre = "Casa", moneda = "EUR", createdBy = "user-1")

    private val planRepo = FakePlanFinancieroRepositorioHomeScreen(plan)
    private val movimientoRepo = FakeMovimientoRepositorioHomeScreen()
    private val categoriaRepo = FakeCategoriaRepositorioHomeScreen()
    private val sincronizador = FakeMovimientoSincronizacionHomeScreen()
    private val authRepository = FakeAuthRepositorioHomeScreen()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearHomeViewModel() = HomeViewModel(
        observarPlan = ObservarPlanUseCase(planRepo),
        observarResumenMensual = ObservarResumenMensualPlanUseCase(movimientoRepo),
        observarBalanceAcumulado = ObservarBalanceAcumuladoPlanUseCase(movimientoRepo),
    )

    private fun crearMovimientosViewModel() = MovimientosViewModel(
        observarMovimientos = ObservarMovimientosUseCase(movimientoRepo),
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
        observarCategorias = ObservarCategoriasUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        actualizarGasto = ActualizarGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarGasto = EliminarGastoUseCase(movimientoRepo),
        actualizarIngreso = ActualizarIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarIngreso = EliminarIngresoUseCase(movimientoRepo),
        aplicarGastoRemoto = AplicarGastoRemotoUseCase(movimientoRepo),
        aplicarIngresoRemoto = AplicarIngresoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        conflictosPendientes = ConflictosPendientes(),
    )

    @Test
    fun `muestra el nombre del plan y el titulo de balance acumulado`() {
        composeTestRule.setContent {
            HomeScreen(planId = "p1", movimientosViewModel = crearMovimientosViewModel(), viewModel = crearHomeViewModel())
        }

        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balance acumulado").assertIsDisplayed()
    }

    @Test
    fun `tocar Nuevo Gasto abre el formulario con tipo gasto preseleccionado`() {
        composeTestRule.setContent {
            HomeScreen(planId = "p1", movimientosViewModel = crearMovimientosViewModel(), viewModel = crearHomeViewModel())
        }

        composeTestRule.onNodeWithText("Nuevo Gasto").performClick()

        composeTestRule.onNodeWithText("Nuevo movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed()
    }
}

private class FakePlanFinancieroRepositorioHomeScreen(private val plan: PlanFinanciero?) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = flowOf(listOfNotNull(plan))
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(plan)
    override suspend fun crearPlan(plan: PlanFinanciero) = Unit
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeMovimientoRepositorioHomeScreen : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}

private class FakeCategoriaRepositorioHomeScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionHomeScreen : MovimientoSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioHomeScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.HomeScreenTest"`
Expected: **compile failure** — `HomeScreen` doesn't exist yet.

- [ ] **Step 6: Implement `HomeScreen`**

Read `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt`'s
`EfectosMovimientos` (around line 206) and `MovimientosPantallaCompacta`/`MovimientoFormularioSheet`
usage first, to mirror the same save/error/snackbar handling shape. Create
`feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt`:

```kotlin
package com.agoitdev.spenvo.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon as M3Icon
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.TipoCategoria

@Composable
fun HomeScreen(
    planId: String,
    movimientosViewModel: MovimientosViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val plan by remember(planId) { viewModel.plan(planId) }.collectAsStateWithLifecycle()
    val resumen by remember(planId) { viewModel.resumenMensual(planId) }.collectAsStateWithLifecycle()
    val balanceAcumulado by remember(planId) { viewModel.balanceAcumulado(planId) }.collectAsStateWithLifecycle()
    var tipoFormularioAbierto by rememberSaveable { mutableStateOf<TipoCategoria?>(null) }
    val estadoForm by movimientosViewModel.estadoForm.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(estadoForm.guardado) {
        if (estadoForm.guardado) {
            tipoFormularioAbierto = null
            movimientosViewModel.consumir(guardado = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = plan?.nombre.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.home_balance_acumulado), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatearMontoPlano(balanceAcumulado, plan?.moneda.orEmpty()),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.home_income_label), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = formatearMontoPlano(resumen?.ingresosMes ?: Monto(0), plan?.moneda.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.home_expense_label), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = formatearMontoPlano(resumen?.gastosMes ?: Monto(0), plan?.moneda.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AccionRapida(
                icono = Icons.Filled.Remove,
                etiqueta = stringResource(R.string.home_action_new_expense),
                onClick = { tipoFormularioAbierto = TipoCategoria.GASTO },
            )
            AccionRapida(
                icono = Icons.Filled.Add,
                etiqueta = stringResource(R.string.home_action_new_income),
                onClick = { tipoFormularioAbierto = TipoCategoria.INGRESO },
            )
        }
    }

    tipoFormularioAbierto?.let { tipo ->
        MovimientoFormSheet(
            planId = planId,
            tipoInicial = tipo,
            cargando = estadoForm.guardando,
            viewModel = movimientosViewModel,
            acciones = MovimientoFormAcciones(
                onGuardar = movimientosViewModel::guardar,
                onDismiss = { tipoFormularioAbierto = null },
                onEliminar = null,
            ),
        )
    }
}

@Composable
private fun AccionRapida(icono: androidx.compose.ui.graphics.vector.ImageVector, etiqueta: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(56.dp)) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                M3Icon(imageVector = icono, contentDescription = etiqueta)
            }
        }
        Text(text = etiqueta, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatearMontoPlano(monto: Monto, moneda: String): String {
    val negativo = monto.unidadesMenores < 0
    val valorAbsoluto = kotlin.math.abs(monto.unidadesMenores)
    val enteros = valorAbsoluto / 100L
    val centimos = valorAbsoluto % 100L
    val signo = if (negativo) "-" else ""
    return "$signo$enteros,${centimos.toString().padStart(2, '0')} $moneda"
}
```

Fix the imports at the top before running (this draft mixes `androidx.compose.foundation.shape.CircleShape`
with a stray `androidx.compose.material3.CircleShape` import that doesn't exist — remove the
incorrect `import androidx.compose.material3.CircleShape` line and keep only
`androidx.compose.foundation.shape.CircleShape`; also move the inline `androidx.compose.runtime.LaunchedEffect`
and `androidx.compose.foundation.layout.size`/`androidx.compose.ui.graphics.vector.ImageVector`
references into proper top-level imports rather than fully-qualified inline references — clean
this up to match the rest of the codebase's import style before committing). This inline
qualification is called out explicitly so the implementer fixes it, not a placeholder — the
logic/structure above is complete and correct, only the import block needs tidying to compile
cleanly and pass detekt.

`MovimientoFormAcciones`, `MovimientoFormSheet` are already `internal` in
`feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt` and
`MovimientosViewModel.kt` respectively — both visible from `HomeScreen.kt` since it's the same
module/package.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.HomeScreenTest" --tests "*.HomeViewModelTest"`
Expected: all pass.

- [ ] **Step 8: detekt**

Run: `./gradlew :feature:movimientos:detekt`
Expected: BUILD SUCCESSFUL, no findings (fix any `LongMethod`/import-order findings by extracting
smaller composables if `HomeScreen` trips detekt's thresholds — same pattern used for
`MovimientoFormAccionesRow` in sub-project 1).

- [ ] **Step 9: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeViewModel.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeViewModelTest.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt \
        feature/movimientos/src/main/res/values/strings.xml \
        feature/movimientos/src/main/res/values-en/strings.xml
git commit -m "feat(movimientos): add per-plan Home dashboard"
```

---

## Task 3: Remove `MovimientosScreen`'s redundant TopBar icons

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt`
- Modify: `feature/movimientos/src/main/res/values/strings.xml`
- Modify: `feature/movimientos/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt`

This is a self-contained cleanup: once Categorías/Miembros are reachable via the bottom nav (Task
4), `MovimientosTopBar`'s two icon buttons become a redundant second path to the same screens.
Doing this as its own task first (rather than folded into Task 4) keeps Task 4 focused purely on
the new navigation shell.

- [ ] **Step 1: Update the characterization test**

Read `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt`
first (its exact current content wasn't part of this plan's reconnaissance) to find every call site
constructing `MovimientosAcciones(...)` or calling `MovimientosScreen(...)`/`MovimientosPantallaCompacta(...)`/
`MovimientosPantallaExpandida(...)` with `onVerMiembros`/`onGestionarCategorias` arguments, and
remove those two named arguments from each (keep `onNuevoMovimiento`). Run the test file first to
confirm it currently compiles/passes before touching anything else in this task, so you have a
clean baseline.

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientosScreenListDetailTest" --rerun-tasks`
Expected: BUILD SUCCESSFUL (baseline, before any production change).

- [ ] **Step 2: Remove the icon buttons from `MovimientosTopBar`**

In `MovimientosScaffoldPartes.kt`, replace `MovimientosTopBar` (currently lines 22-43):

```kotlin
/** Extracted from `MovimientosScreen.kt` to stay under detekt's `TooManyFunctions` file threshold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovimientosTopBar() {
    TopAppBar(title = { Text(stringResource(R.string.movements_title)) })
}
```

Remove the now-unused imports at the top of the file: `androidx.compose.material.icons.Icons`,
`androidx.compose.material.icons.automirrored.filled.List`, `androidx.compose.material.icons.filled.Person`,
`androidx.compose.material3.IconButton` (confirm each is genuinely unused elsewhere in this file
before removing — `FiltroTipoMovimientoLista`/`FiltroTipoMovimiento` below don't use any of them,
per this plan's reconnaissance).

- [ ] **Step 3: Remove the two params from `MovimientosScreen`, `MovimientosAcciones`, and their call site**

In `MovimientosScreen.kt`:
- `MovimientosScreen`'s signature (lines 51-57): remove `onVerMiembros: () -> Unit,` and
  `onGestionarCategorias: () -> Unit,`.
- The `MovimientosAcciones(...)` construction (lines 78-82): remove
  `onVerMiembros = onVerMiembros,` and `onGestionarCategorias = onGestionarCategorias,`, keep
  `onNuevoMovimiento = { formulario = FormularioMovimiento.Nuevo },`.
- `MovimientosAcciones` data class (lines 246-250): remove both fields, keep
  `val onNuevoMovimiento: () -> Unit`.
- `MovimientosScaffold`'s `topBar` (line 277): change
  `topBar = { MovimientosTopBar(acciones.onGestionarCategorias, acciones.onVerMiembros) }` to
  `topBar = { MovimientosTopBar() }`.

- [ ] **Step 4: Remove the two dead strings**

Confirmed by this plan's reconnaissance: `movements_manage_categories`/`movements_view_members`
have no other call site anywhere in the repo besides the two icons just removed. Delete both keys
from `feature/movimientos/src/main/res/values/strings.xml` and
`feature/movimientos/src/main/res/values-en/strings.xml`.

- [ ] **Step 5: Fix `MainActivity.kt`'s existing call site (temporary — Task 4 replaces this entirely)**

In `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`, find the current
`entry<MovimientosRoute> { route -> MovimientosScreen(planId = route.planId, onVerMiembros = { ... }, onGestionarCategorias = { ... }) }`
block and remove the `onVerMiembros`/`onGestionarCategorias` arguments, leaving just
`MovimientosScreen(planId = route.planId)`. This keeps the app compiling with the *old* flat-route
navigation structure for now; Task 4 replaces this whole route with `PlanRoute`.

- [ ] **Step 6: Run tests to verify everything still passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all pass (including the updated `MovimientosScreenListDetailTest`).

- [ ] **Step 7: detekt + lint**

Run: `./gradlew :feature:movimientos:detekt :feature:movimientos:lintDebug :app:lintDebug`
Expected: BUILD SUCCESSFUL, no findings (lint confirms the removed strings didn't leave a
`MissingTranslation`/orphaned-key issue).

- [ ] **Step 8: Build verification**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt \
        feature/movimientos/src/main/res/values/strings.xml \
        feature/movimientos/src/main/res/values-en/strings.xml \
        app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt
git commit -m "refactor(movimientos): remove TopBar shortcuts made redundant by the upcoming bottom nav"
```

---

## Task 4: `PlanScaffold` shell + `MainActivity.kt` navigation rewiring

**Files:**
- Create: `app/src/main/java/com/agoitdev/spenvo/PlanScaffold.kt`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/test/java/com/agoitdev/spenvo/PlanScaffoldTest.kt`

`:app` currently has no `values-en/strings.xml` at all (only `app_name` exists, in `values/`
only) — this task creates that file for the first time, since the new bottom-nav labels are real
user-facing text subject to the blocking `MissingTranslation` lint rule.

- [ ] **Step 1: Add the bottom nav label strings**

`app/src/main/res/values/strings.xml` — replace full content:
```xml
<resources>
    <string name="app_name">Spenvo</string>
    <string name="nav_home">Inicio</string>
    <string name="nav_movements">Movimientos</string>
    <string name="nav_categories">Categorías</string>
    <string name="nav_members">Miembros</string>
</resources>
```

Create `app/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="nav_home">Home</string>
    <string name="nav_movements">Movements</string>
    <string name="nav_categories">Categories</string>
    <string name="nav_members">Members</string>
</resources>
```
(`app_name` stays only in `values/` — it's a proper noun/brand name, matching how the rest of the
app already leaves it untranslated; do not add it to `values-en/`.)

Run: `./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL, no `MissingTranslation` (confirms `app_name` being ES-only doesn't trip
the rule, consistent with its current state before this change).

- [ ] **Step 2: Write the failing `PlanScaffoldTest`**

Create `app/src/test/java/com/agoitdev/spenvo/PlanScaffoldTest.kt`. Tests the shell's own
tab-switching behavior using simple stub content (not real screens), matching how this plan
isolates the shell's generic responsibility from each tab's own already-tested internals:

```kotlin
package com.agoitdev.spenvo

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class PlanScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `arranca en la pestana Home y cambia de contenido al tocar cada pestana`() {
        composeTestRule.setContent {
            PlanScaffold(
                contenidoHome = { Text("CONTENIDO_HOME") },
                contenidoMovimientos = { Text("CONTENIDO_MOVIMIENTOS") },
                contenidoCategorias = { Text("CONTENIDO_CATEGORIAS") },
                contenidoMiembros = { Text("CONTENIDO_MIEMBROS") },
            )
        }

        composeTestRule.onNodeWithText("CONTENIDO_HOME").assertIsDisplayed()

        composeTestRule.onNodeWithText("Movimientos").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_MOVIMIENTOS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Categorías").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_CATEGORIAS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Miembros").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_MIEMBROS").assertIsDisplayed()

        composeTestRule.onNodeWithText("Inicio").performClick()
        composeTestRule.onNodeWithText("CONTENIDO_HOME").assertIsDisplayed()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "*.PlanScaffoldTest"`
Expected: **compile failure** — `PlanScaffold` doesn't exist yet.

- [ ] **Step 3: Implement `PlanScaffold`**

Create `app/src/main/java/com/agoitdev/spenvo/PlanScaffold.kt`:

```kotlin
package com.agoitdev.spenvo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

private enum class PlanTab { HOME, MOVIMIENTOS, CATEGORIAS, MIEMBROS }

@Suppress("LongParameterList")
@Composable
internal fun PlanScaffold(
    contenidoHome: @Composable () -> Unit,
    contenidoMovimientos: @Composable () -> Unit,
    contenidoCategorias: @Composable () -> Unit,
    contenidoMiembros: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabSeleccionada by rememberSaveable { mutableStateOf(PlanTab.HOME) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.HOME,
                    onClick = { tabSeleccionada = PlanTab.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.MOVIMIENTOS,
                    onClick = { tabSeleccionada = PlanTab.MOVIMIENTOS },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_movements)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.CATEGORIAS,
                    onClick = { tabSeleccionada = PlanTab.CATEGORIAS },
                    icon = { Icon(Icons.Filled.Category, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_categories)) },
                )
                NavigationBarItem(
                    selected = tabSeleccionada == PlanTab.MIEMBROS,
                    onClick = { tabSeleccionada = PlanTab.MIEMBROS },
                    icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_members)) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tabSeleccionada) {
                PlanTab.HOME -> contenidoHome()
                PlanTab.MOVIMIENTOS -> contenidoMovimientos()
                PlanTab.CATEGORIAS -> contenidoCategorias()
                PlanTab.MIEMBROS -> contenidoMiembros()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.PlanScaffoldTest"`
Expected: PASS.

- [ ] **Step 5: Wire `PlanRoute` into `MainActivity.kt`**

Replace the `MovimientosRoute`/`MiembrosRoute`/`CategoriasRoute` data class declarations (currently
lines 32-39) with a single new route:
```kotlin
@Serializable
data class PlanRoute(val planId: String) : NavKey
```

In `SpenvoApp`'s `entryProvider`, replace the three entries:
```kotlin
                entry<MovimientosRoute> { route ->
                    MovimientosScreen(planId = route.planId)
                }
                entry<MiembrosRoute> { route ->
                    MiembrosScreen(planId = route.planId)
                }
                entry<CategoriasRoute> { route ->
                    CategoriasScreen(planId = route.planId)
                }
```
with:
```kotlin
                entry<PlanRoute> { route ->
                    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
                    PlanScaffold(
                        contenidoHome = {
                            HomeScreen(planId = route.planId, movimientosViewModel = movimientosViewModel)
                        },
                        contenidoMovimientos = {
                            MovimientosScreen(planId = route.planId, viewModel = movimientosViewModel)
                        },
                        contenidoCategorias = { CategoriasScreen(planId = route.planId) },
                        contenidoMiembros = { MiembrosScreen(planId = route.planId) },
                    )
                }
```
Add the imports: `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` (if not already present
— it likely isn't, since `MainActivity.kt` doesn't currently call `hiltViewModel()` itself, each
screen does internally) and `com.agoitdev.spenvo.movimientos.MovimientosViewModel`.

Change `PlanesScreen`'s `onAbrirPlan` callback (currently `onAbrirPlan = { planId -> backStack.add(MovimientosRoute(planId)) }`)
to `onAbrirPlan = { planId -> backStack.add(PlanRoute(planId)) }`.

`CategoriasScreen`'s `entry<CategoriasRoute>` block previously took a `route: CategoriasRoute`
whose `planId` field it read — confirm `CategoriasScreen(planId: String, ...)`'s call above still
matches (it does, `route.planId` from `PlanRoute` is the same `String` type). Same for
`MiembrosScreen`.

- [ ] **Step 6: Run the full test suite to verify no regressions**

Run: `./gradlew testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all tests pass project-wide.

- [ ] **Step 7: detekt + lint**

Run: `./gradlew detekt lintDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 8: Build verification**

Run: `./gradlew :app:assembleDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL. (If this hits the known transient Windows `FileSystemException` on
`core:domain:bundleLibCompileToJarDebug` from a stale Gradle daemon, run `./gradlew --stop` then
retry once before treating it as a real failure — seen repeatedly in this project's prior
sub-projects, not a code issue.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/PlanScaffold.kt \
        app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/java/com/agoitdev/spenvo/PlanScaffoldTest.kt
git commit -m "feat(app): bottom-nav shell hosting Home/Movimientos/Categorias/Miembros per plan"
```

---

## Task 5: Full verification and CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: All unit tests**

Run: `./gradlew testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all tests green project-wide.

- [ ] **Step 3: Lint**

Run: `./gradlew lintDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL, no `HardcodedText`/`MissingTranslation` errors.

- [ ] **Step 4: detekt**

Run: `./gradlew detekt --rerun-tasks`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 5: Update `doc/database/schema.mdd` if applicable**

No schema change in this plan (no new Room entities/columns) — skip, but confirm by grepping this
plan's own file list for any `.../local/entity/` or `SpenvoDatabase.kt` touch (there is none).

- [ ] **Step 6: Update CHANGELOG.md**

Add under `## [Unreleased]` → `### Added`:

```markdown
- Home screen: opening a plan now lands on a per-plan dashboard (cumulative balance, this month's
  income/expense split, quick "Nuevo Gasto"/"Nuevo Ingreso" actions) instead of going straight to
  the Movimientos list. A bottom navigation bar (Home/Movimientos/Categorías/Miembros) replaces the
  TopBar icon shortcuts `MovimientosScreen` used to reach Categorías/Miembros. `ResumenMensualPlan`
  now also exposes the month's income/expense totals separately (previously only the net).
```

- [ ] **Step 7: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: changelog entry for Home screen and bottom navigation"
```

---

## Self-review notes (for whoever executes this plan)

- **Task 1** is fully precise and self-contained — no gaps.
- **Task 2, Step 3 (`HomeScreen.kt`)** has one deliberately-flagged rough edge: the draft mixes an
  incorrect `androidx.compose.material3.CircleShape` import (doesn't exist) with fully-qualified
  inline references (`androidx.compose.foundation.shape.CircleShape`,
  `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.runtime.LaunchedEffect`)
  instead of clean top-level imports. This is called out explicitly in Step 6 as something to fix
  before committing — the logic is complete and correct, only the import block needs normalizing.
  `ObservarPlanUseCase`'s exact signature also wasn't verbatim-confirmed during reconnaissance;
  Step 3 flags reading it first.
- **Task 2, Step 5 (`HomeScreenTest.kt`)** asserts `onNodeWithText("Nuevo movimiento")` and
  `onNodeWithText("Gastos")` for the opened form sheet — these match `MovimientoFormSheet`'s known
  strings (`movements_add` = "Nuevo movimiento", the Gasto chip label) confirmed during
  sub-project 1's work, not reconnaissance for this plan specifically; verify against the current
  `feature/movimientos/src/main/res/values/strings.xml` if this fails.
- **Task 3, Step 1** depends on reading `MovimientosScreenListDetailTest.kt`'s current content
  fresh, since this plan's reconnaissance did not capture it verbatim — flagged explicitly rather
  than guessing its call sites.
- **Task 4** is fully precise for `PlanScaffold.kt` and the `MainActivity.kt` diff. Icon choices
  (`Home`, `ReceiptLong`, `Category`, `Group`) are all standard `material-icons-extended` icons,
  already a dependency of `:app` per the project's stack table.
- Every task after Task 1 depends on the previous task's commit landing first — do not reorder or
  parallelize; Task 3 and Task 4 in particular must land in that order, since Task 3 leaves
  `MainActivity.kt` in a valid-but-temporary state (flat routes, no icons) that only Task 4
  resolves into the final architecture.
