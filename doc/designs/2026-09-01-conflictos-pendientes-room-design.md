# Persist EdicionesPendientes / ConflictosPendientes to Room (ARCH-M501)

> **For agentic workers:** Use `mobiai-mobile-planning` to turn this design into an implementation
> plan, then `mobiai-mobile-executing-plans-with-subagents` to execute it.

**Goal:** Close the accepted-debt gap documented in `doc/architecture.md`'s "Conflicts (honest
LWW)" section: `EdicionesPendientes` and `ConflictosPendientes` are process-lifetime-only, so a
process death between an optimistic Room write and the Firestore echo silently loses the pending
marker — the remote version then wins on the next sync with no conflict ever surfaced. Same risk
for an already-detected conflict awaiting user resolution.

**Architecture:** Room becomes the single source of truth for both registries — no in-memory
mirror, no hydration-on-boot step. `:core:domain` keeps pure interfaces and models (no Room
import, per `AGENTS.md`); `:core:data` provides the only implementation, backed by two new Room
tables. Every write that currently touches `EdicionesPendientes`/`ConflictosPendientes` moves
inside a `RoomDatabase.withTransaction { }` block alongside the Room write it used to merely
precede, closing the process-death race by construction rather than by best-effort mitigation.

**Tech Stack:** Room (existing `SpenvoDatabase`, version 3 → 4, real `MIGRATION_3_4` matching the
`MIGRATION_1_2`/`MIGRATION_2_3` convention — no destructive migration), kotlinx-serialization for
one JSON column (already a project dependency).

**Platform:** Android.

---

## Problem, precisely

`FirebaseCategoriaRepository.actualizarCategoria()` (representative of the same shape in
`MovimientoRepository`/`PlanFinancieroRepository`) does, today:

```kotlin
val previo = categoriaDao.get(categoria.id)
registrarPendiente(categoria, previo?.editedAt)   // in-memory ConcurrentHashMap.put — instant, but lost on process death
categoriaDao.upsert(categoria.toEntity())          // Room write — durable
```

These are two independent operations with no atomicity between them. If the process dies right
after the Room `upsert` but the in-memory marker was never durable to begin with, the next cold
start has no record that this was an unconfirmed local edit — the sincronizador's `evaluar()` call
on the next incoming snapshot returns `APLICAR` for a document that should have been a `CONFLICTO`.

On the receive side, `CategoriaSincronizador`/`MovimientoSincronizador`/`PlanSincronizador` each
call `evaluarDocumentoRemoto()` — a synchronous, `:core:domain`-side function that mutates the
in-memory maps directly — then separately fire an unawaited `launch { categoriaDao.upsertAll(...) }`
for whatever survived the filter. No transaction spans the decision and the Room write; two
overlapping snapshot callbacks have no ordering guarantee relative to each other.

## Domain layer (`:core:domain`) — pure contracts

`ConflictoEdicion`, `SnapshotConflicto`, `CampoConflicto`, `TipoRegistro`, `DecisionSincronizacion`
stay exactly as they are today. `EdicionesPendientes.clave(coleccion, id)` (today a companion
function on the class being retired) becomes a top-level pure function alongside the other models:

```kotlin
fun claveRegistro(coleccion: String, id: String): String = "$coleccion:$id"
```

`VersionPendiente` is retired: its only real read site
(`DocumentoParaSincronizar.kt:45`) goes away once the local snapshot is reconstructed from the main
table instead (see Entities below), and none of its 4 construction sites
(`FirebaseCategoriaRepository`/`FirebaseMovimientoRepository`/`FirebasePlanFinancieroRepository`)
need anything beyond the plain `TipoRegistro` the new `registrarSiCorresponde` signature takes —
keeping it around with no real caller would be dead code.

`EdicionPendiente` **does** change — its whole reason for carrying `miVersion` disappears with
`VersionPendiente`, so it can no longer read "exactly as it is today":

```kotlin
data class EdicionPendiente(
    val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)
```

Two new interfaces replace the current concrete `EdicionesPendientes`/`ConflictosPendientes`
classes as the injected type:

```kotlin
interface RegistroEdicionesPendientes {
    suspend fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion
    suspend fun registrarSiCorresponde(clave: String, editorId: String?, base: Instant?, miEditedAt: Instant?, tipo: TipoRegistro)
    suspend fun limpiar(clave: String)
}

interface RegistroConflictosPendientes {
    val conflictos: Flow<Map<String, ConflictoEdicion>>
    suspend fun conflictoPara(clave: String): ConflictoEdicion?
    suspend fun registrar(clave: String, conflicto: ConflictoEdicion)
    suspend fun resolver(clave: String)
}
```

`conflictoPara` is a direct suspend point-read (`ConflictoEdicionDao.get(clave): ConflictoEdicionEntity?`,
new alongside `observeAll()`) — needed for the rollback capture in transaction #4 below, where
reading through the reactive `conflictos: Flow` inside a transaction would be the wrong tool for a
single-row lookup.

`resolverPorRegistro(registroId)`/a `deleteByRegistro` DAO counterpart are deliberately **not**
part of this interface — the by-`registroId` lookup was already ambiguous (a Gasto and an Ingreso
could share an id, and it resolved only the first match). See "UI call-site changes" below for
where the by-`(tipo, registroId)` lookup moves instead, resolving to the unambiguous `clave` before
ever calling `resolver()`.

Every method becomes `suspend` (Room-backed now); `conflictos` becomes a cold `Flow` sourced
directly from the DAO's `@Query... : Flow<List<ConflictoEdicionEntity>>`, keyed by `clave` exactly
like today's `Map<String, ConflictoEdicion>`. This is **not** a no-op change for callers —
`MovimientosViewModel` currently assigns `ConflictosPendientes.conflictos` (already a `StateFlow`)
straight through; with a cold `Flow` backing it, that becomes
`conflictosPendientes.conflictos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyMap())`
— the same `.stateIn(...)` pattern the ViewModel already uses for `movimientos()`/`categorias()`.
See "UI call-site changes" below for the rest of what moves.

The pure LWW decision itself (what today's `EdicionesPendientes.evaluar()`'s `when` block computes)
stays a plain, Room-free function in `:core:domain` — it's business policy, not persistence:

```kotlin
fun decidirSincronizacion(
    pendiente: EdicionPendiente?,
    editedBy: String?,
    editedAt: Instant?,
): DecisionSincronizacion
```

`RegistroEdicionesPendientes.evaluar()`'s Room implementation reads the row, calls this pure
function, and (for `PROPIA_CONFIRMADA`) deletes the row — all inside whatever transaction the
caller is already in (see below). The pure function itself stays exactly as unit-testable as
`EdicionesPendientesTest.kt` already does today, no Room dependency.

## Data layer (`:core:data`) — Room as sole source of truth

**New entities** (`core/data/.../local/entity/`):

```kotlin
@Entity(tableName = "ediciones_pendientes")
data class EdicionPendienteEntity(
    @PrimaryKey val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)

@Entity(tableName = "conflictos_pendientes")
data class ConflictoEdicionEntity(
    @PrimaryKey val clave: String,
    val registroId: String,
    val tipo: TipoRegistro,
    val local: SnapshotConflicto,   // via a new JSON TypeConverter
    val remoto: SnapshotConflicto,  // via the same converter
)
```

`EdicionPendienteEntity` deliberately does **not** store `miVersion`: its only real read site today
is `DocumentoParaSincronizar.kt:45`, `pendiente.miVersion.aSnapshotConflicto()`, called exactly once,
at the moment a conflict is detected — and at that moment the corresponding main table
(`categorias`/`gastos`/`ingresos`/`planes_financieros`) already holds that exact version, because
`registrar()`/`upsert()` always land together in the same transaction (see below). The write-path
transaction guarantees the main-table row and the pending marker are always for the same edit, so
reconstructing the local snapshot from the main DAO at conflict-detection time is equivalent to
storing `miVersion` redundantly, without the extra column or serialization cost.

`ConflictoEdicionEntity` **does** keep both `local`/`remoto` snapshots — the remote version is held
back and never applied to the main table, so it only exists here.

**New converter**: `SnapshotConflicto.editadoEn: Instant?` rules out marking the domain type
`@Serializable` directly — kotlinx-serialization has no built-in `java.time.Instant` serializer,
and adding one at the domain-model level would leak a serialization detail into `:core:domain`
regardless. Same pattern the project already uses for every other entity (`Categoria`/`CategoriaDto`/
`CategoriaEntity` — domain models never carry persistence annotations): a small persistence-only DTO
pair, `editadoEn` as epoch millis:

```kotlin
@Serializable
private data class SnapshotConflictoDto(val editadoPor: String?, val editadoEnMillis: Long?, val borrado: Boolean, val campos: List<CampoConflictoDto>)
@Serializable
private data class CampoConflictoDto(val clave: String, val valor: String)

fun SnapshotConflicto.toDto() = SnapshotConflictoDto(editadoPor, editadoEn?.toEpochMilli(), borrado, campos.map { CampoConflictoDto(it.clave, it.valor) })
fun SnapshotConflictoDto.toDomain() = SnapshotConflicto(editadoPor, editadoEnMillis?.let(Instant::ofEpochMilli), borrado, campos.map { CampoConflicto(it.clave, it.valor) })
```

(`core/data/.../local/converter/Converters.kt`, extending the existing class):

```kotlin
@TypeConverter
fun snapshotConflictoToJson(value: SnapshotConflicto): String = Json.encodeToString(value.toDto())
@TypeConverter
fun jsonToSnapshotConflicto(value: String): SnapshotConflicto = Json.decodeFromString<SnapshotConflictoDto>(value).toDomain()
```

**New DAOs**:

```kotlin
@Dao
interface EdicionPendienteDao {
    @Query("SELECT * FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun get(clave: String): EdicionPendienteEntity?
    @Upsert
    suspend fun upsert(entity: EdicionPendienteEntity)
    @Query("DELETE FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun delete(clave: String)
}

@Dao
interface ConflictoEdicionDao {
    @Query("SELECT * FROM conflictos_pendientes")
    fun observeAll(): Flow<List<ConflictoEdicionEntity>>
    @Query("SELECT * FROM conflictos_pendientes WHERE clave = :clave")
    suspend fun get(clave: String): ConflictoEdicionEntity?
    @Upsert
    suspend fun upsert(entity: ConflictoEdicionEntity)
    @Query("DELETE FROM conflictos_pendientes WHERE clave = :clave")
    suspend fun delete(clave: String)
}
```

**`SpenvoDatabase`**: `version = 4`, both entities added to `entities = [...]`, both DAOs added,
new `MIGRATION_3_4` (raw `CREATE TABLE`, matching `MIGRATION_1_2`'s style) — no destructive
migration, consistent with every prior schema change in this project. `doc/database/schema.mdd`
gets versioned per `AGENTS.md`'s gate.

**Implementations** (`core/data/.../sync/`):

```kotlin
class RegistroEdicionesPendientesRoom @Inject constructor(
    private val dao: EdicionPendienteDao,
) : RegistroEdicionesPendientes { /* wraps decidirSincronizacion() + dao calls */ }

class RegistroConflictosPendientesRoom @Inject constructor(
    private val dao: ConflictoEdicionDao,
) : RegistroConflictosPendientes {
    override val conflictos = dao.observeAll().map { it.associateBy { c -> c.clave } }
    /* ... */
}
```

`ConflictoModule.kt` swaps its two `@Provides` functions for `@Binds` (interface → Room impl),
matching the `@Binds`-only module convention `AGENTS.md` already documents for repository/sync
interfaces.

## Transaction boundaries

Four places, each wrapping `SpenvoDatabase.withTransaction { }` around what today are two or more
unrelated calls:

**1. Write** (`FirebaseCategoriaRepository.actualizarCategoria()`/`eliminarCategoria()` and the
equivalent methods in `FirebaseMovimientoRepository`/`FirebasePlanFinancieroRepository`). `previo`
is read **inside** the transaction, not before it opens — reading it earlier leaves a window where
a concurrent write (another local edit, or the sincronizador applying an incoming snapshot) could
change the row between the read and the transaction actually starting, making `previo` stale by
the time it's used as the pending edit's `base`. The transaction returns `previo` so the caller
still has it for the rollback path:

```kotlin
val previo = database.withTransaction {
    val previo = categoriaDao.get(categoria.id)
    registroEdicionesPendientes.registrarSiCorresponde(clave, categoria.editedBy, previo?.editedAt, categoria.editedAt, TipoRegistro.CATEGORIA)
    categoriaDao.upsert(categoria.toEntity())
    previo
}
persistRemoto(categoria)  // Firestore call stays outside the transaction — it's network, not Room
```

**2. Rollback** (on a permanent Firestore failure, same methods' `catch` block):

```kotlin
database.withTransaction {
    registroEdicionesPendientes.limpiar(clave)
    restaurar(previo, categoria.id)
}
```

**3. Snapshot received** — one transaction per Firestore callback invocation (per batch, not per
document), decided together with the writes it produces:

```kotlin
private suspend fun procesarSnapshotCategorias(categorias: List<Categoria>) {
    database.withTransaction {
        val aplicables = categorias.mapNotNull { categoria ->
            val clave = claveRegistro(CATEGORIAS_COLLECTION, categoria.id)
            when (val decision = registroEdicionesPendientes.evaluar(clave, categoria.editedBy, categoria.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA ->
                    categoria.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    // Read INSIDE the transaction — never before it opens. The marker existing
                    // implies the main-table row should too (they're written together — transaction
                    // #1), but a missing row is handled explicitly rather than crashing: the marker
                    // is now stale (nothing local left to conflict with), so drop it and apply remote.
                    val local = categoriaDao.get(categoria.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        categoria.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(categoria.id, TipoRegistro.CATEGORIA, local.toDomain().aSnapshotConflicto(), categoria.aSnapshotConflicto()),
                        )
                        null
                    }
                }
            }
        }
        categoriaDao.upsertAll(aplicables)
    }
}
```

This keeps the existing single `upsertAll()` per snapshot, gives full atomicity between every
document's decision and every Room write in the batch, and means Room's `Flow`-backed queries only
ever emit a fully-consistent post-commit state — no reader ever observes a half-processed snapshot.
If the process dies mid-transaction, the whole batch rolls back and Firestore's next snapshot
re-delivers the same documents (Firestore snapshot listeners replay the full current state on
listener re-attach, not just the delta), so nothing is lost.

`evaluarDocumentoRemoto()` (the current synchronous `:core:data` function in
`DocumentoParaSincronizar.kt`) is retired; `procesarSnapshotCategorias`-shaped functions replace it
per sincronizador, calling the new suspend `RegistroEdicionesPendientes`/`RegistroConflictosPendientes`
inside the transaction instead.

**Ordering**: today's `CategoriaSincronizador`/`MovimientoSincronizador`/`PlanSincronizador` fire a
bare `launch { }` per Firestore callback invocation, with no ordering guarantee if two callbacks
land close together. Each sincronizador gets an internal `Channel<List<Categoria>>` (or equivalent
per entity type) with a single consumer coroutine that processes batches strictly in Firestore's
delivery order — chosen over a `Mutex` because a single-consumer channel expresses the FIFO intent
structurally, not just as a side effect of mutual exclusion. `MovimientoSincronizador`'s two
listeners (gastos, ingresos) get one channel each — they touch different tables with no
cross-dependency, so no shared ordering is needed between them.

`PlanSincronizador` (single-document listener, not a collection query) uses the same
`procesarSnapshotPlanes(listOf(plan))` shape with a one-element list — no special-casing needed.

**4. Conflict resolution** (`MovimientosViewModel.resolverConflicto()`, `movimientos`-only today —
see `doc/architecture.md`: conflict resolution UI exists only for Gasto/Ingreso). Today this is two
unrelated calls in both branches: "usar remota" does `aplicarGastoRemoto(id)` then separately
`resolverPorRegistro(id)`; "usar local" re-issues the edit via the plain `actualizar()` write path
(itself now transaction #1 above) and only *after* that commits, separately resolves the old
conflict in its success callback. A process death between either pair leaves the remote version
applied with the conflict row still present, or a fresh pending edit registered with the stale
conflict never cleared. Four new dedicated `MovimientoRepository` operations replace the two-call
sequences — four, not two: `resolverConflictoUsandoRemoto(id: String, clave: String)` would be an
identical erased signature for both Gasto and Ingreso (Kotlin/the JVM can't disambiguate two
`suspend fun` overloads that differ only by name-that-isn't-there), so each entity type gets its
own distinct method name, matching every other `MovimientoRepository` method today
(`actualizarGasto`/`actualizarIngreso`, `aplicarGastoRemoto`/`aplicarIngresoRemoto` — this interface
never overloads by type, always distinct names):

```kotlin
// "usar remota" — the network read can't live inside withTransaction (aplicarGastoRemoto today
// does firestore.collection(...).document(id).get().await() BEFORE the Room write — that's a
// real network call, not "Room-only"; holding a SQLite transaction open across it is exactly the
// kind of long-held-transaction mistake this design is otherwise careful to avoid). Fetch first,
// short transaction after — and if the fetch fails, the transaction never starts, so the conflict
// stays exactly as it was for the user to retry.
suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) {
    // NOT `?: return`: today's aplicarGastoRemoto no-ops silently on a missing/invalid remote
    // document, and the ViewModel closes the conflict dialog regardless of whether anything was
    // actually applied — a pre-existing silent-success bug this design shouldn't inherit. Throwing
    // means the caller's failure path runs instead: the conflict stays registered, the dialog
    // stays open, the user sees an error rather than a conflict that silently "resolved" into
    // nothing.
    val data = firestore.collection(GASTOS_COLLECTION).document(id).get().await().data
        ?: error("El movimiento remoto ya no existe")
    val remoto = GastoDto.fromData(data)?.toDomain()
        ?: error("El movimiento remoto no es válido")
    database.withTransaction {
        gastoDao.upsert(remoto.toEntity())
        registroConflictosPendientes.resolver(clave)
    }
}
// resolverConflictoIngresoUsandoRemoto(id, clave) — identical shape, ingresoDao/INGRESOS_COLLECTION.

// "usar local" — previo and the full conflictoPrevio are both captured inside the transaction
// (same "read inside, never before" rule as transaction #1) so a permanent Firestore failure can
// restore BOTH: the pre-edit entity AND the conflict that was about to be considered resolved.
// Losing just the entity restore (transaction #1's existing rollback) isn't enough here — a failed
// "usar local" must not silently make the conflict disappear. No separate `editorId` parameter:
// the use case layer (below) already stamps `gasto.editedBy` before calling this, so the repository
// reads it off the entity instead of trusting a second, potentially-contradictory source.
suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) {
    var conflictoPrevio: ConflictoEdicion? = null
    var entidadPrevia: GastoEntity? = null
    database.withTransaction {
        entidadPrevia = gastoDao.get(gasto.id)
        conflictoPrevio = registroConflictosPendientes.conflictoPara(clave)
        registroEdicionesPendientes.registrarSiCorresponde(clave, gasto.editedBy, entidadPrevia?.editedAt, gasto.editedAt, TipoRegistro.GASTO)
        gastoDao.upsert(gasto.toEntity())
        registroConflictosPendientes.resolver(clave)
    }
    try {
        persistRemoto(gasto)
    } catch (e: Exception) {
        database.withTransaction {
            registroEdicionesPendientes.limpiar(clave)
            entidadPrevia?.let { gastoDao.upsert(it) } ?: gastoDao.delete(gasto.id)
            conflictoPrevio?.let { registroConflictosPendientes.registrar(clave, it) }
        }
        throw e
    }
}
// resolverConflictoIngresoUsandoLocal(ingreso, clave) — identical shape, ingresoDao/TipoRegistro.INGRESO.
```

`"usar local"` folds transaction #1's steps and the conflict delete into one transaction instead of
reusing the plain `actualizar()` path — a dedicated operation, not a parameter bolted onto the
generic update method, keeping normal edits and conflict-resolution edits as distinct repository
API surface. `CategoriaRepository`/`PlanFinancieroRepository` don't need these — no resolution UI
exists for those types today.

## Use case layer (`:core:domain`)

The four new `MovimientoRepository` operations aren't called directly from
`MovimientosViewModel` — every other repository operation in this codebase goes through a
dedicated use case (`ActualizarGastoUseCase` validates the amount and stamps
`editedBy`/`editedAt = Instant.now()` before calling `repository.actualizarGasto()`;
`AplicarGastoRemotoUseCase` is a plain passthrough), and `MovimientosViewModel`'s constructor
already only takes use cases, never `MovimientoRepository` itself. Four new use cases follow the
same shape, one pair per entity type:

```kotlin
class ResolverConflictoGastoUsandoLocalUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(gasto: Gasto, editorId: String, clave: String) {
        require(validarMonto(gasto.monto)) { "El monto debe ser positivo" }
        repository.resolverConflictoGastoUsandoLocal(
            gasto.copy(editedBy = editorId, editedAt = Instant.now()),
            clave,
        )
    }
}

class ResolverConflictoGastoUsandoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String, clave: String) = repository.resolverConflictoGastoUsandoRemoto(id, clave)
}
```

Plus the Ingreso equivalents, identically shaped. This is the fix for a real gap: the `Movimiento`
`resolverConflicto()` passes into "usar local" is the *stale* version the UI/Room currently holds
(`movimientos.firstOrNull { it.id == conflictoVisible?.registroId }` in
`MovimientosScreen.kt`) — its `editedAt` is whenever it was last actually saved, not "now". Without
the use case's stamping step, `resolverConflictoUsandoLocal` would persist a pending marker and a
main-table row both carrying a stale `editedAt`, corrupting the LWW invariant that every new edit
gets a new timestamp. `MovimientosViewModel` gains these 4 as new constructor parameters,
used only inside `resolverConflicto()`; `actualizarGasto`/`actualizarIngreso` stay exactly as they
are for every normal (non-conflict) edit.

## UI call-site changes (`MovimientosViewModel`)

Not a no-op for the UI layer, despite the domain models keeping their shape:

- `val conflictos: StateFlow<Map<String, ConflictoEdicion>> = conflictosPendientes.conflictos` (today,
  a direct assignment since `ConflictosPendientes.conflictos` is already a `StateFlow`) becomes
  `conflictosPendientes.conflictos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyMap())`
  — the cold `Flow` needs the same `.stateIn(...)` conversion `movimientos()`/`categorias()` already use.
- `mostrarConflicto()`/`resolverConflicto()` currently call `conflictosPendientes.conflictoPorRegistro(id)`/
  `resolverPorRegistro(id)` — both looked up by `registroId` alone, which is ambiguous if a Gasto and
  an Ingreso ever shared an id (their id spaces aren't distinguished from each other). Since
  `conflictos` is keyed by the unambiguous `clave`, a small local helper replaces both:
  ```kotlin
  private fun claveVisible(movimiento: Movimiento): String? {
      val tipo = if (movimiento is Gasto) TipoRegistro.GASTO else TipoRegistro.INGRESO
      return conflictos.value.entries.firstOrNull { it.value.registroId == movimiento.id && it.value.tipo == tipo }?.key
  }
  ```
  `mostrarConflicto` uses it to look up the `ConflictoEdicion` to show; `resolverConflicto` uses it
  to get the exact `clave` to pass into the 4 new use cases above — no ambiguous
  by-`registroId`-only path survives anywhere in the design.
- `resolverConflicto()`'s body changes to call the 4 new use cases instead of `actualizar()`/
  `aplicarGastoRemoto`/`aplicarIngresoRemoto` directly, and only clears `_conflictoVisible` on
  success — reusing `_estadoForm`'s existing `error: String?` channel (the same one
  `guardar`/`actualizar`/`eliminar` already report failures through) rather than introducing a new
  one, so a failed resolution surfaces the same way any other failed action already does, and the
  dialog stays open for the user to retry:
  ```kotlin
  fun resolverConflicto(movimiento: Movimiento, usarLocal: Boolean) {
      val clave = claveVisible(movimiento) ?: return
      viewModelScope.launch {
          val editorId = authRepository.observeSesion().first().uid.orEmpty()
          runCatching {
              if (usarLocal) {
                  when (movimiento) {
                      is Gasto -> resolverConflictoGastoUsandoLocal(movimiento, editorId, clave)
                      is Ingreso -> resolverConflictoIngresoUsandoLocal(movimiento, editorId, clave)
                  }
              } else {
                  when (movimiento) {
                      is Gasto -> resolverConflictoGastoUsandoRemoto(movimiento.id, clave)
                      is Ingreso -> resolverConflictoIngresoUsandoRemoto(movimiento.id, clave)
                  }
              }
          }
              .onSuccess { _conflictoVisible.value = null }
              .onFailure { e -> _estadoForm.update { it.copy(error = e.message) } }
      }
  }
  ```

## Testing

Matches the existing convention (`AGENTS.md`: "DAOs are tested against an in-memory Room
database"): `EdicionPendienteDao`/`ConflictoEdicionDao` get in-memory-Room DAO tests;
`RegistroEdicionesPendientesRoom`/`RegistroConflictosPendientesRoom` get tests against the same
in-memory DB (no fakes needed — Room's in-memory builder is already the project's DAO-test
pattern); the pure `decidirSincronizacion()` function keeps plain-JUnit table tests, unchanged in
spirit from today's `EdicionesPendientesTest.kt`. The three sincronizadores' new
`procesarSnapshot*()` functions, and `FirebaseMovimientoRepository`'s new
`resolverConflicto{Gasto,Ingreso}Usando{Local,Remoto}` (4 methods), get tests against an in-memory
`SpenvoDatabase` exercising the transaction directly (crash-mid-transaction is out of scope for an
automated test — Room's transaction guarantee itself is not something this project re-verifies;
SQLite's own ACID guarantee is trusted, same as every other `withTransaction` user in the Android
ecosystem). `MovimientosViewModel`'s new `claveVisible()` helper gets plain-JUnit table tests
(same id, different `tipo` → different `clave`; matching `tipo`+`registroId` → the right one). The
4 new use cases get plain-JUnit tests with a hand-written fake `MovimientoRepository` (matching
`ActualizarGastoUseCase`'s existing test pattern) — including a negative-monto rejection case for
the two "usando local" use cases, mirroring `ActualizarGastoUseCase`'s own `require()` test. The
revised `resolverConflicto()` gets a `MovimientosViewModelTest` case for the "remote document gone"
failure path: `estadoForm.value.error` is set and `conflictoVisible.value` stays non-null (the
dialog does not close on failure) — the exact regression this design is closing.

## Out of scope (explicitly)

- `AccesoPlanEntity` sync is untouched — accesses aren't one of the four `TipoRegistro` conflict-tracked
  entity types and never went through `evaluarDocumentoRemoto()`.
- No change to Firestore rules, to the `SyncStateEntity`/`SyncStateDao` singleton-row pattern, or to
  the optimistic-write-with-rollback contract itself (`AGENTS.md`'s "Writes are optimistic
  Room-first" rule) — this only makes the *bookkeeping* around that contract durable.
- No outbox, no WorkManager, no retry-with-backoff for a stuck conflict — matches
  `.agents/rules/devils-advocate.md`'s standing rejection of that pattern. A conflict that's
  persisted now durably waits for the user to resolve it via the existing `ConflictoDialog`; it
  does not get auto-retried.
- `ConflictoDialog`/`ConflictoMovimientoHost` (the UI itself) is unchanged — only
  `MovimientosViewModel`'s `resolverConflicto()` internals, the 4 new use cases, and the 4 new
  repository operations underneath them (transaction #4) change.
