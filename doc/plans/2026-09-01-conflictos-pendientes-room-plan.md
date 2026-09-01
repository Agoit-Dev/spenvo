# Persist EdicionesPendientes / ConflictosPendientes to Room (ARCH-M501) Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Implement `doc/designs/2026-09-01-conflictos-pendientes-room-design.md` — make Room the
single source of truth for `EdicionesPendientes`/`ConflictosPendientes`, closing the process-death
data-loss gap documented in `doc/architecture.md`.

**Architecture:** `:core:domain` gets pure interfaces/models (no Room import); `:core:data`
implements them with two new Room tables, wrapping every write in a transaction alongside the Room
write it used to merely precede. See the design doc for full rationale — every decision here traces
back to it.

**Tech Stack:** Room (`SpenvoDatabase` v3→v4, real `MIGRATION_3_4`), kotlinx-serialization
(persistence-only DTO for the one JSON column), Hilt (`@Binds` swap).

**Platform:** Android.

---

## A note on sequencing

This slice retires a domain type (`VersionPendiente`) and reshapes another (`EdicionPendiente`)
that four repositories and three sincronizadores construct directly — there is no way to change
`EdicionPendiente`'s shape without every one of those call sites changing together. That coupling
is real, but it governs **task size, not commit hygiene**: `.agents/rules/commit-safety.md` is
explicit — *"Quality gates (build + tests + lint + detekt + changelog) apply BEFORE any commit,
local or remote"* — with no exception for "the next commit fixes it." Every commit in this plan
must leave the affected modules compiling with their tests green, full stop.

Concretely: **Task 5** below is one coordinated task touching many files (the domain retirement +
every repository/sincronizador/DI call site that depends on it), but it produces exactly **one**
commit, made only after `:core:domain` and `:core:data` both compile and both modules' test suites
pass. Everything before it is additive (old classes stay in place, nothing depends on the new ones
yet, each task's own commit is independently green); everything after it only ever touches already
cut-over code.

**Working in an isolated worktree** on branch `worktree-codex+arch-m501-room-conflicts`
(`.claude/worktrees/codex+arch-m501-room-conflicts`) — not `main` directly, given the size of this
slice (domain, Room, migration, DI, three repositories, three sincronizadores, UI).

**Execution:** sequential subagents, one per task, with review between tasks — never in parallel
on the same tree. Task 5 (the atomic cutover) is one indivisible unit for whichever agent executes
it: internal edits/fixes/iteration are expected, but nothing commits until the whole task is green.

---

## Phase 1 — Additive (Tasks 1-4)

Old classes (`EdicionesPendientes`, `ConflictosPendientes`, `VersionPendiente`) stay untouched and
in place through this whole phase. Nothing here is wired into the app yet, so nothing can break
what already works — each task is independently green and independently committed.

### Task 1: `claveRegistro()` pure function

**Files:**
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientes.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientesTest.kt`

- [ ] **Step 1: Write the failing test** — add to the top of `EdicionesPendientesTest.kt`:

```kotlin
@Test
fun `claveRegistro combina coleccion e id`() {
    assertEquals("gastos:g1", claveRegistro("gastos", "g1"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.EdicionesPendientesTest"`
Expected: FAIL — `Unresolved reference: claveRegistro`

- [ ] **Step 3: Write minimal implementation** — add as a top-level function in
  `EdicionesPendientes.kt`, above the `class EdicionesPendientes` declaration:

```kotlin
/** `"$coleccion:$id"` — the key both pending-edit and conflict registries index by. */
fun claveRegistro(coleccion: String, id: String): String = "$coleccion:$id"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.EdicionesPendientesTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientes.kt core/domain/src/test/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientesTest.kt
git commit -m "feat(domain): add claveRegistro() pure function (ARCH-M501)"
```

### Task 2: `RegistroEdicionesPendientes` / `RegistroConflictosPendientes` interfaces

Pure interface declarations — nothing implements or consumes them yet. No test (an interface with
no logic has nothing to assert against); exercised once something implements it (Task 4).

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/RegistroEdicionesPendientes.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/RegistroConflictosPendientes.kt`

- [ ] **Step 1: Write `RegistroEdicionesPendientes.kt`**

```kotlin
package com.agoitdev.spenvo.domain.sync

import java.time.Instant

/**
 * Room-backed registry of unconfirmed local edits (ARCH-M501) — see
 * `doc/designs/2026-09-01-conflictos-pendientes-room-design.md`. Every method is `suspend` because
 * every implementation reads/writes Room; callers are expected to invoke these from inside a
 * `SpenvoDatabase.withTransaction { }` block alongside the Room write the marker is tracking.
 */
interface RegistroEdicionesPendientes {
    suspend fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion
    suspend fun registrarSiCorresponde(
        clave: String,
        editorId: String?,
        base: Instant?,
        miEditedAt: Instant?,
        tipo: TipoRegistro,
    )
    suspend fun limpiar(clave: String)
}
```

- [ ] **Step 2: Write `RegistroConflictosPendientes.kt`**

```kotlin
package com.agoitdev.spenvo.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Room-backed registry of detected conflicts pending user resolution (ARCH-M501). No
 * `resolverPorRegistro(registroId)` — that lookup was ambiguous (a Gasto and an Ingreso could
 * share an id) and resolved only the first match; callers derive the unambiguous `clave` from
 * [conflictos] instead (see `MovimientosViewModel.claveVisible()`, Task 8).
 */
interface RegistroConflictosPendientes {
    val conflictos: Flow<Map<String, ConflictoEdicion>>
    suspend fun conflictoPara(clave: String): ConflictoEdicion?
    suspend fun registrar(clave: String, conflicto: ConflictoEdicion)
    suspend fun resolver(clave: String)
}
```

- [ ] **Step 3: Verify `:core:domain` still compiles**

Run: `./gradlew :core:domain:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/RegistroEdicionesPendientes.kt core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/RegistroConflictosPendientes.kt
git commit -m "feat(domain): add RegistroEdicionesPendientes/RegistroConflictosPendientes interfaces (ARCH-M501)"
```

### Task 3: `EdicionPendienteEntity`/`ConflictoEdicionEntity`, DAOs, JSON converter, `SpenvoDatabase` v4 migration

One task, one commit — write the entities/DAOs/converter and their tests, then the migration that
makes those tests pass, all before committing. Do not commit red tests.

**Files:**
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/EdicionPendienteEntity.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/ConflictoEdicionEntity.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/local/dao/EdicionPendienteDao.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/local/dao/ConflictoEdicionDao.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/local/converter/Converters.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/local/SpenvoDatabase.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/DatabaseModule.kt`
- Modify: `doc/database/schema.mdd`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/dao/EdicionPendienteDaoTest.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/dao/ConflictoEdicionDaoTest.kt`
- Test (existing, extend): `core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/SpenvoDatabaseMigrationTest.kt`

- [ ] **Step 1: Write the entities**

`EdicionPendienteEntity.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant

@Entity(tableName = "ediciones_pendientes")
data class EdicionPendienteEntity(
    @PrimaryKey val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)
```

`ConflictoEdicionEntity.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro

@Entity(tableName = "conflictos_pendientes")
data class ConflictoEdicionEntity(
    @PrimaryKey val clave: String,
    val registroId: String,
    val tipo: TipoRegistro,
    val local: SnapshotConflicto,
    val remoto: SnapshotConflicto,
)
```

- [ ] **Step 2: Rewrite `Converters.kt`** — full replacement, adding the `TipoRegistro` and
  `SnapshotConflicto` converters:

```kotlin
package com.agoitdev.spenvo.data.local.converter

import androidx.room.TypeConverter
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun tipoRegistroToString(value: TipoRegistro): String = value.name

    @TypeConverter
    fun stringToTipoRegistro(value: String): TipoRegistro = TipoRegistro.valueOf(value)

    // SnapshotConflicto carries java.time.Instant, which kotlinx-serialization has no built-in
    // serializer for — a persistence-only DTO (epoch millis) keeps that detail out of
    // :core:domain, same pattern as every other entity in this project (domain models never carry
    // persistence annotations; Categoria/CategoriaDto/CategoriaEntity is the existing precedent).
    @TypeConverter
    fun snapshotConflictoToJson(value: SnapshotConflicto): String = Json.encodeToString(value.toDto())

    @TypeConverter
    fun jsonToSnapshotConflicto(value: String): SnapshotConflicto =
        Json.decodeFromString<SnapshotConflictoDto>(value).toDomain()
}

@Serializable
private data class SnapshotConflictoDto(
    val editadoPor: String?,
    val editadoEnMillis: Long?,
    val borrado: Boolean,
    val campos: List<CampoConflictoDto>,
)

@Serializable
private data class CampoConflictoDto(val clave: String, val valor: String)

private fun SnapshotConflicto.toDto() = SnapshotConflictoDto(
    editadoPor = editadoPor,
    editadoEnMillis = editadoEn?.toEpochMilli(),
    borrado = borrado,
    campos = campos.map { CampoConflictoDto(it.clave, it.valor) },
)

private fun SnapshotConflictoDto.toDomain() = SnapshotConflicto(
    editadoPor = editadoPor,
    editadoEn = editadoEnMillis?.let(Instant::ofEpochMilli),
    borrado = borrado,
    campos = campos.map { CampoConflicto(it.clave, it.valor) },
)
```

- [ ] **Step 3: Write the DAOs**

`EdicionPendienteDao.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity

@Dao
interface EdicionPendienteDao {
    @Query("SELECT * FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun get(clave: String): EdicionPendienteEntity?

    @Upsert
    suspend fun upsert(entity: EdicionPendienteEntity)

    @Query("DELETE FROM ediciones_pendientes WHERE clave = :clave")
    suspend fun delete(clave: String)
}
```

`ConflictoEdicionDao.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import kotlinx.coroutines.flow.Flow

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

- [ ] **Step 4: Write the DAO tests (RED — `SpenvoDatabase` doesn't know about these tables yet)**

`EdicionPendienteDaoTest.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EdicionPendienteDaoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var dao: EdicionPendienteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.edicionPendienteDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `get sin registrar devuelve null`() = runTest {
        assertNull(dao.get("gastos:g1"))
    }

    @Test
    fun `upsert luego get devuelve la edicion pendiente`() = runTest {
        val ahora = Instant.now()
        val entity = EdicionPendienteEntity("gastos:g1", TipoRegistro.GASTO, "user-1", ahora.minusSeconds(60), ahora)

        dao.upsert(entity)

        assertEquals(entity, dao.get("gastos:g1"))
    }

    @Test
    fun `delete elimina la edicion pendiente`() = runTest {
        val ahora = Instant.now()
        dao.upsert(EdicionPendienteEntity("gastos:g1", TipoRegistro.GASTO, "user-1", ahora, ahora))

        dao.delete("gastos:g1")

        assertNull(dao.get("gastos:g1"))
    }
}
```

`ConflictoEdicionDaoTest.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConflictoEdicionDaoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var dao: ConflictoEdicionDao

    private fun snapshot(borrado: Boolean = false) = SnapshotConflicto(
        editadoPor = "user-1",
        editadoEn = Instant.now(),
        borrado = borrado,
        campos = listOf(CampoConflicto("monto", "1000")),
    )

    private fun entidad(clave: String = "gastos:g1", registroId: String = "g1") = ConflictoEdicionEntity(
        clave = clave,
        registroId = registroId,
        tipo = TipoRegistro.GASTO,
        local = snapshot(),
        remoto = snapshot(),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.conflictoEdicionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `get sin registrar devuelve null`() = runTest {
        assertNull(dao.get("gastos:g1"))
    }

    @Test
    fun `upsert luego get y observeAll devuelven el conflicto`() = runTest {
        val entity = entidad()

        dao.upsert(entity)

        assertEquals(entity, dao.get("gastos:g1"))
        assertEquals(listOf(entity), dao.observeAll().first())
    }

    @Test
    fun `delete elimina el conflicto`() = runTest {
        dao.upsert(entidad())

        dao.delete("gastos:g1")

        assertNull(dao.get("gastos:g1"))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `SnapshotConflicto sobrevive el roundtrip por el converter JSON`() = runTest {
        val original = entidad(clave = "gastos:g2", registroId = "g2")

        dao.upsert(original)
        val leido = dao.get("gastos:g2")

        assertEquals(original.local, leido?.local)
        assertEquals(original.remoto, leido?.remoto)
    }
}
```

Run them now to confirm they fail to compile (`SpenvoDatabase` doesn't have `edicionPendienteDao()`/
`conflictoEdicionDao()` yet — that's the RED this task's remaining steps turn GREEN):

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.local.dao.EdicionPendienteDaoTest`
Expected: FAIL to compile

- [ ] **Step 5: Extend the existing `SpenvoDatabaseMigrationTest.kt`** — read it first to match its
  style, then add a `MIGRATION_3_4` case (open a v3 schema, run the migration, assert the two new
  tables exist and are empty, assert existing tables/data are untouched).

- [ ] **Step 6: Modify `SpenvoDatabase.kt`** — add the two entities to `entities = [...]`, bump
  `version = 4`, add the two DAO accessors, add `MIGRATION_3_4`, add it to `addMigrations(...)`:

```kotlin
@Database(
    entities = [
        SyncStateEntity::class,
        UsuarioEntity::class,
        PlanFinancieroEntity::class,
        AccesoPlanEntity::class,
        CategoriaEntity::class,
        GastoEntity::class,
        IngresoEntity::class,
        EdicionPendienteEntity::class,
        ConflictoEdicionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SpenvoDatabase : RoomDatabase() {

    abstract fun syncStateDao(): SyncStateDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun planFinancieroDao(): PlanFinancieroDao
    abstract fun accesoPlanDao(): AccesoPlanDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun gastoDao(): GastoDao
    abstract fun ingresoDao(): IngresoDao
    abstract fun edicionPendienteDao(): EdicionPendienteDao
    abstract fun conflictoEdicionDao(): ConflictoEdicionDao

    companion object {
        const val DATABASE_NAME = "spenvo.db"

        // MIGRATION_1_2 and MIGRATION_2_3 unchanged — see existing file content.

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ediciones_pendientes` (" +
                        "`clave` TEXT NOT NULL, `tipo` TEXT NOT NULL, `editorId` TEXT NOT NULL, " +
                        "`base` INTEGER, `miEditedAt` INTEGER, PRIMARY KEY(`clave`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conflictos_pendientes` (" +
                        "`clave` TEXT NOT NULL, `registroId` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                        "`local` TEXT NOT NULL, `remoto` TEXT NOT NULL, PRIMARY KEY(`clave`))",
                )
            }
        }

        fun build(context: Context, passphraseProvider: PassphraseProvider): SpenvoDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = passphraseProvider.getOrCreate()
            return Room.databaseBuilder(context, SpenvoDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(String(passphrase).toByteArray(Charsets.UTF_8)))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
```

Add the two new imports at the top of the file:
`import com.agoitdev.spenvo.data.local.dao.EdicionPendienteDao`,
`import com.agoitdev.spenvo.data.local.dao.ConflictoEdicionDao`,
`import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity`,
`import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity`.

- [ ] **Step 7: Modify `DatabaseModule.kt`** — add two `@Provides` functions:

```kotlin
@Provides
fun provideEdicionPendienteDao(database: SpenvoDatabase): EdicionPendienteDao =
    database.edicionPendienteDao()

@Provides
fun provideConflictoEdicionDao(database: SpenvoDatabase): ConflictoEdicionDao =
    database.conflictoEdicionDao()
```

Plus the matching imports.

- [ ] **Step 8: Run everything to verify GREEN before committing anything**

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.local.SpenvoDatabaseMigrationTest`
Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.local.dao.EdicionPendienteDaoTest`
Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.local.dao.ConflictoEdicionDaoTest`
Expected: PASS, all three

- [ ] **Step 9: Bump `doc/database/schema.mdd`** per `AGENTS.md`'s gate — add the two new tables
  following that file's existing format/conventions for the other tables.

- [ ] **Step 10: Commit** (everything from this task lands together — nothing here compiled or
  passed independently, so nothing here commits independently):

```bash
git add core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/EdicionPendienteEntity.kt core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/ConflictoEdicionEntity.kt core/data/src/main/java/com/agoitdev/spenvo/data/local/dao/EdicionPendienteDao.kt core/data/src/main/java/com/agoitdev/spenvo/data/local/dao/ConflictoEdicionDao.kt core/data/src/main/java/com/agoitdev/spenvo/data/local/converter/Converters.kt core/data/src/main/java/com/agoitdev/spenvo/data/local/SpenvoDatabase.kt core/data/src/main/java/com/agoitdev/spenvo/data/di/DatabaseModule.kt doc/database/schema.mdd core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/dao/EdicionPendienteDaoTest.kt core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/dao/ConflictoEdicionDaoTest.kt core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/SpenvoDatabaseMigrationTest.kt
git commit -m "feat(data): EdicionPendienteEntity/ConflictoEdicionEntity, DAOs, JSON converter, SpenvoDatabase v3->v4 migration (ARCH-M501)"
```

### Task 4: `RegistroEdicionesPendientesRoom` / `RegistroConflictosPendientesRoom`

**Files:**
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/RegistroEdicionesPendientesRoom.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/RegistroConflictosPendientesRoom.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/remote/sync/RegistroEdicionesPendientesRoomTest.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/remote/sync/RegistroConflictosPendientesRoomTest.kt`

- [ ] **Step 1: Write the failing tests**

`RegistroEdicionesPendientesRoomTest.kt`:

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RegistroEdicionesPendientesRoomTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registro: RegistroEdicionesPendientesRoom
    private val ahora: Instant = Instant.now()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registro = RegistroEdicionesPendientesRoom(db.edicionPendienteDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `evaluar sin edicion pendiente aplica el remoto`() = runTest {
        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `registrarSiCorresponde no registra si editorId es null`() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = null, base = ahora, miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        assertEquals(DecisionSincronizacion.APLICAR, registro.evaluar("gastos:g1", "user-2", ahora))
    }

    @Test
    fun `registrar luego evaluar con eco propio confirma y limpia`() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora.minusSeconds(60), miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        val decision = registro.evaluar("gastos:g1", editedBy = "user-1", editedAt = ahora)

        assertEquals(DecisionSincronizacion.PROPIA_CONFIRMADA, decision)
        assertNull(db.edicionPendienteDao().get("gastos:g1"))
    }

    @Test
    fun `registrar luego evaluar con conflicto genuino deja el marcador intacto`() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora.minusSeconds(60), miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        val decision = registro.evaluar("gastos:g1", editedBy = "user-2", editedAt = ahora.plusSeconds(30))

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
        assertEquals("user-1", db.edicionPendienteDao().get("gastos:g1")?.editorId)
    }

    @Test
    fun `limpiar elimina el marcador`() = runTest {
        registro.registrarSiCorresponde("gastos:g1", editorId = "user-1", base = ahora, miEditedAt = ahora, tipo = TipoRegistro.GASTO)

        registro.limpiar("gastos:g1")

        assertNull(db.edicionPendienteDao().get("gastos:g1"))
    }
}
```

`RegistroConflictosPendientesRoomTest.kt`:

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistroConflictosPendientesRoomTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registro: RegistroConflictosPendientesRoom

    private fun snapshot() = SnapshotConflicto("user-1", Instant.now(), false, listOf(CampoConflicto("monto", "1000")))
    private fun conflicto(id: String = "g1") = ConflictoEdicion(id, TipoRegistro.GASTO, snapshot(), snapshot())

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registro = RegistroConflictosPendientesRoom(db.conflictoEdicionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `conflictoPara sin conflictos registrados devuelve null`() = runTest {
        assertNull(registro.conflictoPara("gastos:g1"))
    }

    @Test
    fun `registrar agrega el conflicto y lo emite en el flow`() = runTest {
        val conflicto = conflicto()

        registro.registrar("gastos:g1", conflicto)

        assertEquals(conflicto, registro.conflictoPara("gastos:g1"))
        assertEquals(conflicto, registro.conflictos.first()["gastos:g1"])
    }

    @Test
    fun `resolver quita el conflicto`() = runTest {
        registro.registrar("gastos:g1", conflicto())

        registro.resolver("gastos:g1")

        assertNull(registro.conflictoPara("gastos:g1"))
        assertTrue(registro.conflictos.first().isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.remote.sync.RegistroEdicionesPendientesRoomTest`
Expected: FAIL — `RegistroEdicionesPendientesRoom` doesn't exist

- [ ] **Step 3: Write `RegistroEdicionesPendientesRoom.kt`**

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.EdicionPendienteDao
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.EdicionPendiente
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.decidirSincronizacion
import java.time.Instant
import javax.inject.Inject

class RegistroEdicionesPendientesRoom @Inject constructor(
    private val dao: EdicionPendienteDao,
) : RegistroEdicionesPendientes {

    override suspend fun evaluar(clave: String, editedBy: String?, editedAt: Instant?): DecisionSincronizacion {
        val entity = dao.get(clave)
        val decision = decidirSincronizacion(entity?.toDomain(), editedBy, editedAt)
        if (decision == DecisionSincronizacion.PROPIA_CONFIRMADA) dao.delete(clave)
        return decision
    }

    override suspend fun registrarSiCorresponde(
        clave: String,
        editorId: String?,
        base: Instant?,
        miEditedAt: Instant?,
        tipo: TipoRegistro,
    ) {
        if (editorId == null) return
        dao.upsert(EdicionPendienteEntity(clave, tipo, editorId, base, miEditedAt))
    }

    override suspend fun limpiar(clave: String) = dao.delete(clave)
}

private fun EdicionPendienteEntity.toDomain() = EdicionPendiente(clave, tipo, editorId, base, miEditedAt)
```

**Note:** this file references `EdicionPendiente` with the shape it will have *after* Task 5 (no
`miVersion`, with `tipo`). Since `EdicionPendiente` hasn't been rewritten yet at this point in the
plan, this file **will not compile in isolation** until Task 5 lands — same for the `evaluar()`
implementation, which calls `decidirSincronizacion()` (also introduced in Task 5, since it
requires the new `EdicionPendiente` shape). Write it now anyway (it's tightly coupled content that
belongs conceptually with the Room implementation, not with the domain cutover), but **do not
attempt to build or commit this task's files until Task 5's domain changes are in place** — Step 6
below folds this task's tests into Task 5's verification instead of running them here.

- [ ] **Step 4: Write `RegistroConflictosPendientesRoom.kt`**

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import com.agoitdev.spenvo.data.local.dao.ConflictoEdicionDao
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegistroConflictosPendientesRoom @Inject constructor(
    private val dao: ConflictoEdicionDao,
) : RegistroConflictosPendientes {

    override val conflictos: Flow<Map<String, ConflictoEdicion>> =
        dao.observeAll().map { entities -> entities.associate { it.clave to it.toDomain() } }

    override suspend fun conflictoPara(clave: String): ConflictoEdicion? = dao.get(clave)?.toDomain()

    override suspend fun registrar(clave: String, conflicto: ConflictoEdicion) =
        dao.upsert(ConflictoEdicionEntity(clave, conflicto.registroId, conflicto.tipo, conflicto.local, conflicto.remoto))

    override suspend fun resolver(clave: String) = dao.delete(clave)
}

private fun ConflictoEdicionEntity.toDomain() = ConflictoEdicion(registroId, tipo, local, remoto)
```

This file, unlike `RegistroEdicionesPendientesRoom.kt`, does **not** depend on anything Task 5
changes (`ConflictoEdicion`/`ConflictoEdicionEntity`/`RegistroConflictosPendientes` are all
already in their final shape) — it compiles and its test passes right now.

- [ ] **Step 5: Run `RegistroConflictosPendientesRoomTest` — the one part of this task that's
  independently green today**

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.remote.sync.RegistroConflictosPendientesRoomTest`
Expected: PASS

- [ ] **Step 6: Commit only `RegistroConflictosPendientesRoom.kt` and its test now** — leave
  `RegistroEdicionesPendientesRoom.kt` and its test as uncommitted working-tree changes; Task 5
  picks them up and commits them together with the domain cutover they depend on (do not run
  `git add -A` here):

```bash
git add core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/RegistroConflictosPendientesRoom.kt core/data/src/androidTest/java/com/agoitdev/spenvo/data/remote/sync/RegistroConflictosPendientesRoomTest.kt
git commit -m "feat(data): RegistroConflictosPendientesRoom (ARCH-M501)"
```

Leave `RegistroEdicionesPendientesRoom.kt` and `RegistroEdicionesPendientesRoomTest.kt` on disk,
uncommitted, for Task 5.

---

## Phase 2 — Atomic cutover (Task 5)

**One task. One commit.** Everything below touches types retired/reshaped in this same task —
`:core:domain` and `:core:data` are expected to be temporarily inconsistent *while this task is in
progress*, but that inconsistency never reaches git: nothing commits until both modules compile
and both modules' full test suites (`testDebugUnitTest` + `connectedDebugAndroidTest`) pass
together. If the agent executing this needs to pause mid-task, it leaves the work uncommitted on
disk (`git status` shows it, `git stash` if truly needed) rather than committing a broken
intermediate state.

### Task 5: Retire `VersionPendiente`/`EdicionesPendientes`/`ConflictosPendientes`; rewrite the 3 repositories, 3 sincronizadores, `ConflictoModule`, and their tests

**Files:**
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientes.kt` → rewritten in place
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/sync/ConflictosPendientes.kt` → rewritten in place
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/MovimientoRepository.kt`
- Delete: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/DocumentoParaSincronizar.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/ConflictoModule.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseCategoriaRepository.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseMovimientoRepository.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebasePlanFinancieroRepository.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/CategoriaSincronizador.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/MovimientoSincronizador.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/PlanSincronizador.kt`
- Rewrite test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/sync/EdicionesPendientesTest.kt`
- Rewrite test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/sync/ConflictosPendientesTest.kt`
- Delete test: `core/data/src/test/java/com/agoitdev/spenvo/data/remote/sync/ConflictoParticionTest.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/remote/sync/CategoriaSincronizadorProcesamientoTest.kt`
- Test (uncommitted from Task 4): `core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/RegistroEdicionesPendientesRoom.kt`, `core/data/src/androidTest/java/com/agoitdev/spenvo/data/remote/sync/RegistroEdicionesPendientesRoomTest.kt`

**Sub-steps** (internal to this one task — no intermediate commits between them):

- [ ] **5.1 — Rewrite `EdicionesPendientes.kt`** — full replacement:

```kotlin
package com.agoitdev.spenvo.domain.sync

import java.time.Instant

/** Which entity family a pending edit or a detected conflict belongs to. */
enum class TipoRegistro { GASTO, INGRESO, CATEGORIA, PLAN }

/** `"$coleccion:$id"` — the key both pending-edit and conflict registries index by. */
fun claveRegistro(coleccion: String, id: String): String = "$coleccion:$id"

/**
 * An unconfirmed local write, tracked from the moment the repository reads `previo`. Room-backed
 * (ARCH-M501) — no longer carries the written version (`miVersion`, retired with
 * `VersionPendiente`): at conflict-detection time the local version is reconstructed from the
 * corresponding main-table row instead, which the write-path transaction guarantees is always in
 * sync with this marker (see the design doc's "Entities" section for why).
 */
data class EdicionPendiente(
    val clave: String,
    val tipo: TipoRegistro,
    val editorId: String,
    val base: Instant?,
    val miEditedAt: Instant?,
)

/** What the sincronizador should do with an incoming snapshot for a given key. */
enum class DecisionSincronizacion { APLICAR, PROPIA_CONFIRMADA, CONFLICTO }

/**
 * The LWW conflict decision — pure business policy, no persistence. A conflict requires ALL of: a
 * pending local edit for this key, an incoming `editedBy` that differs from the pending edit's
 * author, and an incoming `editedAt` strictly newer than the pending edit's known base. A matching
 * echo of the pending edit's own write confirms it. Deletion is not special-cased: a soft-delete
 * stamps `editedBy`/`editedAt` exactly like an edit, so this same rule flags delete-vs-edit
 * conflicts.
 */
fun decidirSincronizacion(
    pendiente: EdicionPendiente?,
    editedBy: String?,
    editedAt: Instant?,
): DecisionSincronizacion {
    if (pendiente == null) return DecisionSincronizacion.APLICAR
    val esPropiaConfirmada = editedBy == pendiente.editorId && editedAt == pendiente.miEditedAt
    val esDeOtroEditor = editedBy != pendiente.editorId
    val esMasReciente = editedAt != null &&
        (pendiente.base == null || editedAt.isAfter(pendiente.base))
    return when {
        esPropiaConfirmada -> DecisionSincronizacion.PROPIA_CONFIRMADA
        esDeOtroEditor && esMasReciente -> DecisionSincronizacion.CONFLICTO
        else -> DecisionSincronizacion.APLICAR
    }
}
```

- [ ] **5.2 — Rewrite `ConflictosPendientes.kt`** — full replacement (drops the concrete class and
  the `VersionPendiente.aSnapshotConflicto()` delegation, keeps everything else):

```kotlin
package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import java.time.Instant

/** One field shown side-by-side in the conflict dialog; [clave] maps to `R.string.conflict_field_*` (Slice 5b). */
data class CampoConflicto(val clave: String, val valor: String)

/** One side (local or remote) of a detected conflict. */
data class SnapshotConflicto(
    val editadoPor: String?,
    val editadoEn: Instant?,
    val borrado: Boolean,
    val campos: List<CampoConflicto>,
)

/** A genuine concurrent-edit conflict for one record, pending user resolution. */
data class ConflictoEdicion(
    val registroId: String,
    val tipo: TipoRegistro,
    val local: SnapshotConflicto,
    val remoto: SnapshotConflicto,
)

fun Movimiento.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("monto", monto.unidadesMenores.toString()),
        CampoConflicto("fecha", fecha.toString()),
        CampoConflicto("categoria", categoriaId),
        CampoConflicto("descripcion", descripcion.orEmpty()),
    ),
)

fun Categoria.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("nombre", nombre),
        CampoConflicto("tipo", tipo.toString()),
        CampoConflicto("icono", icono),
    ),
)

fun PlanFinanciero.aSnapshotConflicto(): SnapshotConflicto = SnapshotConflicto(
    editadoPor = editedBy,
    editadoEn = editedAt,
    borrado = deletedAt != null,
    campos = listOf(
        CampoConflicto("nombre", nombre),
        CampoConflicto("descripcion", descripcion.orEmpty()),
        CampoConflicto("moneda", moneda),
    ),
)
```

- [ ] **5.3 — Extend `MovimientoRepository.kt`** — add the 4 abstract methods:

```kotlin
package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import kotlinx.coroutines.flow.Flow

interface MovimientoRepository {
    suspend fun addGasto(gasto: Gasto)
    suspend fun addIngreso(ingreso: Ingreso)
    suspend fun actualizarGasto(gasto: Gasto)
    suspend fun eliminarGasto(gasto: Gasto)
    suspend fun actualizarIngreso(ingreso: Ingreso)
    suspend fun eliminarIngreso(ingreso: Ingreso)
    fun observeGastos(planId: String): Flow<List<Gasto>>
    fun observeIngresos(planId: String): Flow<List<Ingreso>>

    /**
     * Persists the remote (Firestore) version of a gasto/ingreso directly into
     * Room, bypassing the edit-attribution use case: used to resolve a
     * conflict in favor of the other user's write, which is already correctly
     * attributed and was only held back from the last sync (Slice 4). A no-op
     * if the document no longer exists remotely.
     */
    suspend fun aplicarGastoRemoto(id: String)
    suspend fun aplicarIngresoRemoto(id: String)

    /**
     * Conflict resolution (ARCH-M501) — distinct names per entity type: a generic
     * `resolverConflictoUsandoRemoto(id, clave)` would collide on erased signature for Gasto vs
     * Ingreso, matching every other method on this interface (never overloaded by type).
     */
    suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String)
    suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String)
    suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String)
    suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String)
}
```

- [ ] **5.4 — Rewrite `EdicionesPendientesTest.kt`** — full replacement:

```kotlin
package com.agoitdev.spenvo.domain.sync

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class EdicionesPendientesTest {

    private val ahora: Instant = Instant.now()

    private fun edicion(
        editorId: String = "user-1",
        base: Instant? = ahora.minusSeconds(60),
        miEditedAt: Instant? = ahora,
    ) = EdicionPendiente(
        clave = "gastos:g1",
        tipo = TipoRegistro.GASTO,
        editorId = editorId,
        base = base,
        miEditedAt = miEditedAt,
    )

    @Test
    fun `claveRegistro combina coleccion e id`() {
        assertEquals("gastos:g1", claveRegistro("gastos", "g1"))
    }

    @Test
    fun `sin edicion pendiente aplica el remoto - plain remote update is not a conflict`() {
        val decision = decidirSincronizacion(pendiente = null, editedBy = "user-2", editedAt = ahora)

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `eco de mi propia escritura confirma - own write echoed back`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", miEditedAt = ahora),
            editedBy = "user-1",
            editedAt = ahora,
        )

        assertEquals(DecisionSincronizacion.PROPIA_CONFIRMADA, decision)
    }

    @Test
    fun `edicion concurrente de otro usuario mas reciente es conflicto - genuine conflict detected`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(60)),
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(30),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `edicion de otro usuario pero no mas reciente que la base no es conflicto`() {
        val base = ahora
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = base),
            editedBy = "user-2",
            editedAt = base.minus(1, ChronoUnit.SECONDS),
        )

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `sin editedAt remoto nunca es conflicto`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(60)),
            editedBy = "user-2",
            editedAt = null,
        )

        assertEquals(DecisionSincronizacion.APLICAR, decision)
    }

    @Test
    fun `sin base conocida cualquier edicion concurrente es conflicto`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = null),
            editedBy = "user-2",
            editedAt = ahora,
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }

    @Test
    fun `borrado remoto concurrente con edicion local pendiente se marca como conflicto - delete vs edit`() {
        val decision = decidirSincronizacion(
            pendiente = edicion(editorId = "user-1", base = ahora.minusSeconds(120)),
            editedBy = "user-2",
            editedAt = ahora.plusSeconds(10),
        )

        assertEquals(DecisionSincronizacion.CONFLICTO, decision)
    }
}
```

- [ ] **5.5 — Rewrite `ConflictosPendientesTest.kt`** — full replacement (keeps the 4
  `aSnapshotConflicto()` projection tests unchanged, drops the retired class's mutation tests and
  the `VersionPendiente`-delegation test):

```kotlin
package com.agoitdev.spenvo.domain.sync

import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.TipoCategoria
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictosPendientesTest {

    private val ahora: Instant = Instant.now()

    @Test
    fun `gasto aSnapshotConflicto incluye monto fecha categoria y descripcion`() {
        val gasto = Gasto(
            id = "g1",
            planId = "p1",
            categoriaId = "c1",
            monto = Monto(2500),
            fecha = LocalDate.of(2026, 8, 20),
            descripcion = "Cena",
            creadoPor = "user-1",
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = gasto.aSnapshotConflicto()

        assertEquals("user-1", snapshot.editadoPor)
        assertEquals(ahora, snapshot.editadoEn)
        assertEquals(false, snapshot.borrado)
        assertEquals(
            listOf(
                CampoConflicto("monto", "2500"),
                CampoConflicto("fecha", "2026-08-20"),
                CampoConflicto("categoria", "c1"),
                CampoConflicto("descripcion", "Cena"),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `ingreso aSnapshotConflicto usa la misma proyeccion que gasto via Movimiento`() {
        val ingreso = Ingreso(
            id = "i1",
            planId = "p1",
            categoriaId = "c2",
            monto = Monto(500),
            fecha = LocalDate.of(2026, 1, 1),
            creadoPor = "user-1",
            editedBy = "user-2",
            editedAt = ahora,
            deletedAt = ahora,
        )

        val snapshot = ingreso.aSnapshotConflicto()

        assertEquals(true, snapshot.borrado)
        assertEquals(
            listOf(
                CampoConflicto("monto", "500"),
                CampoConflicto("fecha", "2026-01-01"),
                CampoConflicto("categoria", "c2"),
                CampoConflicto("descripcion", ""),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `categoria aSnapshotConflicto incluye nombre tipo e icono`() {
        val categoria = Categoria(
            id = "p1:gasto_comida",
            planId = "p1",
            nombre = "Comida",
            icono = "comida",
            tipo = TipoCategoria.GASTO,
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = categoria.aSnapshotConflicto()

        assertEquals(
            listOf(
                CampoConflicto("nombre", "Comida"),
                CampoConflicto("tipo", "GASTO"),
                CampoConflicto("icono", "comida"),
            ),
            snapshot.campos,
        )
    }

    @Test
    fun `plan aSnapshotConflicto incluye nombre descripcion y moneda`() {
        val plan = PlanFinanciero(
            id = "p1",
            nombre = "Casa",
            descripcion = "Gastos compartidos",
            moneda = "EUR",
            createdBy = "user-1",
            editedBy = "user-1",
            editedAt = ahora,
        )

        val snapshot = plan.aSnapshotConflicto()

        assertEquals(
            listOf(
                CampoConflicto("nombre", "Casa"),
                CampoConflicto("descripcion", "Gastos compartidos"),
                CampoConflicto("moneda", "EUR"),
            ),
            snapshot.campos,
        )
    }
}
```

- [ ] **5.6 — Run `:core:domain` tests** (this sub-module is now internally consistent — no
  dependency on `:core:data`, which is still broken at this point):

Run: `./gradlew :core:domain:testDebugUnitTest`
Expected: PASS — every `:core:domain` test green

- [ ] **5.7 — Delete `DocumentoParaSincronizar.kt` and `ConflictoParticionTest.kt`**

```bash
rm core/data/src/main/java/com/agoitdev/spenvo/data/remote/sync/DocumentoParaSincronizar.kt
rm core/data/src/test/java/com/agoitdev/spenvo/data/remote/sync/ConflictoParticionTest.kt
```

- [ ] **5.8 — Rewrite `ConflictoModule.kt`**

```kotlin
package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.sync.RegistroConflictosPendientesRoom
import com.agoitdev.spenvo.data.remote.sync.RegistroEdicionesPendientesRoom
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Cross-cutting ARCH-M501 conflict-detection registries, shared by the Movimiento, Categoria and
 * Plan repositories/sincronizadores. Room-backed since ARCH-M501 — see
 * `doc/designs/2026-09-01-conflictos-pendientes-room-design.md`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConflictoModule {

    @Binds
    @Singleton
    abstract fun bindRegistroEdicionesPendientes(impl: RegistroEdicionesPendientesRoom): RegistroEdicionesPendientes

    @Binds
    @Singleton
    abstract fun bindRegistroConflictosPendientes(impl: RegistroConflictosPendientesRoom): RegistroConflictosPendientes
}
```

- [ ] **5.9 — Rewrite `FirebaseCategoriaRepository.kt`** — `previo` read inside the transaction
  (per design), `SpenvoDatabase` added to the constructor:

```kotlin
package com.agoitdev.spenvo.data.remote.repository

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract). Update/delete also register an
 * unconfirmed pending edit (Slice 4 conflict detection) at the point `previo`
 * is read, for free — both the marker and the entity land in one Room
 * transaction (ARCH-M501), so a process death between them can't happen.
 */
@Singleton
class FirebaseCategoriaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val categoriaDao: CategoriaDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
) : CategoriaRepository {

    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        categoriaDao.observeByPlan(planId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observarCategoriasPorTipo(
        planId: String,
        tipo: TipoCategoria,
    ): Flow<List<Categoria>> =
        categoriaDao.observeByPlanAndTipo(planId, tipo).map { entities ->
            entities.map { it.toDomain() }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearCategoria(categoria: Categoria) {
        categoriaDao.upsert(categoria.toEntity())
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            categoriaDao.delete(categoria.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearCategorias(categorias: List<Categoria>) {
        if (categorias.isEmpty()) return
        categoriaDao.upsertAll(categorias.map { it.toEntity() })
        try {
            val batch = firestore.batch()
            categorias.forEach { categoria ->
                batch.set(
                    firestore.collection(CATEGORIAS_COLLECTION).document(categoria.id),
                    CategoriaDto.fromDomain(categoria).toMap(),
                )
            }
            batch.commit().await()
        } catch (e: Exception) {
            categorias.forEach { categoriaDao.delete(it.id) }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarCategoria(categoria: Categoria) {
        val previo = escribir(categoria)
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            rollback(categoria.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarCategoria(categoria: Categoria) {
        val previo = escribir(categoria)
        try {
            persistRemoto(categoria)
        } catch (e: Exception) {
            rollback(categoria.id, previo)
            throw e
        }
    }

    private suspend fun escribir(categoria: Categoria): CategoriaEntity? = database.withTransaction {
        val previo = categoriaDao.get(categoria.id)
        registroEdicionesPendientes.registrarSiCorresponde(
            clave = claveRegistro(CATEGORIAS_COLLECTION, categoria.id),
            editorId = categoria.editedBy,
            base = previo?.editedAt,
            miEditedAt = categoria.editedAt,
            tipo = TipoRegistro.CATEGORIA,
        )
        categoriaDao.upsert(categoria.toEntity())
        previo
    }

    private suspend fun rollback(id: String, previo: CategoriaEntity?) = database.withTransaction {
        registroEdicionesPendientes.limpiar(claveRegistro(CATEGORIAS_COLLECTION, id))
        if (previo != null) categoriaDao.upsert(previo) else categoriaDao.delete(id)
    }

    private suspend fun persistRemoto(categoria: Categoria) {
        firestore.collection(CATEGORIAS_COLLECTION)
            .document(categoria.id)
            .set(CategoriaDto.fromDomain(categoria).toMap())
            .await()
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}
```

- [ ] **5.10 — Rewrite `FirebasePlanFinancieroRepository.kt`** — same pattern:

```kotlin
package com.agoitdev.spenvo.data.remote.repository

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract). Update also registers an unconfirmed
 * pending edit (Slice 4 conflict detection) at the point `previo` is read,
 * for free — both land in one Room transaction (ARCH-M501).
 */
@Singleton
class FirebasePlanFinancieroRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val planDao: PlanFinancieroDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
) : PlanFinancieroRepository {

    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> =
        planDao.observeByUsuario(usuarioId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observarPlan(planId: String): Flow<PlanFinanciero?> =
        planDao.observe(planId).map { it?.toDomain() }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planDao.upsert(plan.toEntity())
        try {
            persistRemoto(plan)
        } catch (e: Exception) {
            planDao.delete(plan.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarPlan(plan: PlanFinanciero) {
        val previo = database.withTransaction {
            val previo = planDao.get(plan.id)
            registroEdicionesPendientes.registrarSiCorresponde(
                clave = claveRegistro(PLANES_COLLECTION, plan.id),
                editorId = plan.editedBy,
                base = previo?.editedAt,
                miEditedAt = plan.editedAt,
                tipo = TipoRegistro.PLAN,
            )
            planDao.upsert(plan.toEntity())
            previo
        }
        try {
            persistRemoto(plan)
        } catch (e: Exception) {
            database.withTransaction {
                registroEdicionesPendientes.limpiar(claveRegistro(PLANES_COLLECTION, plan.id))
                if (previo != null) planDao.upsert(previo) else planDao.delete(plan.id)
            }
            throw e
        }
    }

    private suspend fun persistRemoto(plan: PlanFinanciero) {
        val dto = PlanFinancieroDto.fromDomain(plan)
        firestore.collection(PLANES_COLLECTION)
            .document(plan.id)
            .set(dto.toMap())
            .await()
    }

    private companion object {
        const val PLANES_COLLECTION = "planes_financieros"
    }
}
```

- [ ] **5.11 — Rewrite `FirebaseMovimientoRepository.kt`** — write/rollback for
  `actualizarGasto`/`eliminarGasto`/`actualizarIngreso`/`eliminarIngreso`, plus the 4 new
  conflict-resolution methods:

```kotlin
package com.agoitdev.spenvo.data.remote.repository

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.entity.GastoEntity
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Optimistic Room-first writes: Room updates immediately, then Firestore. A
 * permanent Firestore error rolls Room back to the previous snapshot (see
 * `data-consistency.md` write contract). Update/delete also register an
 * unconfirmed pending edit (Slice 4 conflict detection) at the point `previo`
 * is read, for free — both land in one Room transaction (ARCH-M501).
 */
@Singleton
class FirebaseMovimientoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : MovimientoRepository {

    override fun observeGastos(planId: String): Flow<List<Gasto>> =
        gastoDao.observeByPlan(planId).map { entities -> entities.map { it.toDomain() } }

    override fun observeIngresos(planId: String): Flow<List<Ingreso>> =
        ingresoDao.observeByPlan(planId).map { entities -> entities.map { it.toDomain() } }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun addGasto(gasto: Gasto) {
        gastoDao.upsert(gasto.toEntity())
        try {
            firestore.collection(GASTOS_COLLECTION)
                .document(gasto.id)
                .set(GastoDto.fromDomain(gasto).toMap())
                .await()
        } catch (e: Exception) {
            gastoDao.delete(gasto.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun addIngreso(ingreso: Ingreso) {
        ingresoDao.upsert(ingreso.toEntity())
        try {
            firestore.collection(INGRESOS_COLLECTION)
                .document(ingreso.id)
                .set(IngresoDto.fromDomain(ingreso).toMap())
                .await()
        } catch (e: Exception) {
            ingresoDao.delete(ingreso.id)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarGasto(gasto: Gasto) {
        val previo = escribirGasto(gasto)
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            rollbackGasto(gasto.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarGasto(gasto: Gasto) {
        val previo = escribirGasto(gasto)
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            rollbackGasto(gasto.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun actualizarIngreso(ingreso: Ingreso) {
        val previo = escribirIngreso(ingreso)
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            rollbackIngreso(ingreso.id, previo)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun eliminarIngreso(ingreso: Ingreso) {
        val previo = escribirIngreso(ingreso)
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            rollbackIngreso(ingreso.id, previo)
            throw e
        }
    }

    override suspend fun aplicarGastoRemoto(id: String) {
        val data = firestore.collection(GASTOS_COLLECTION).document(id).get().await().data ?: return
        gastoDao.upsert(GastoDto.fromData(data)?.toDomain()?.toEntity() ?: return)
    }

    override suspend fun aplicarIngresoRemoto(id: String) {
        val data = firestore.collection(INGRESOS_COLLECTION).document(id).get().await().data ?: return
        ingresoDao.upsert(IngresoDto.fromData(data)?.toDomain()?.toEntity() ?: return)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) {
        val data = firestore.collection(GASTOS_COLLECTION).document(id).get().await().data
            ?: error("El movimiento remoto ya no existe")
        val remoto = GastoDto.fromData(data)?.toDomain()
            ?: error("El movimiento remoto no es válido")
        database.withTransaction {
            gastoDao.upsert(remoto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) {
        val data = firestore.collection(INGRESOS_COLLECTION).document(id).get().await().data
            ?: error("El movimiento remoto ya no existe")
        val remoto = IngresoDto.fromData(data)?.toDomain()
            ?: error("El movimiento remoto no es válido")
        database.withTransaction {
            ingresoDao.upsert(remoto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) {
        var conflictoPrevio: ConflictoEdicion? = null
        var entidadPrevia: GastoEntity? = null
        database.withTransaction {
            entidadPrevia = gastoDao.get(gasto.id)
            conflictoPrevio = registroConflictosPendientes.conflictoPara(clave)
            registroEdicionesPendientes.registrarSiCorresponde(
                clave, gasto.editedBy, entidadPrevia?.editedAt, gasto.editedAt, TipoRegistro.GASTO,
            )
            gastoDao.upsert(gasto.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
        try {
            persistRemotoGasto(gasto)
        } catch (e: Exception) {
            database.withTransaction {
                registroEdicionesPendientes.limpiar(clave)
                entidadPrevia?.let { gastoDao.upsert(it) } ?: gastoDao.delete(gasto.id)
                conflictoPrevio?.let { registroConflictosPendientes.registrar(clave, it) }
            }
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) {
        var conflictoPrevio: ConflictoEdicion? = null
        var entidadPrevia: IngresoEntity? = null
        database.withTransaction {
            entidadPrevia = ingresoDao.get(ingreso.id)
            conflictoPrevio = registroConflictosPendientes.conflictoPara(clave)
            registroEdicionesPendientes.registrarSiCorresponde(
                clave, ingreso.editedBy, entidadPrevia?.editedAt, ingreso.editedAt, TipoRegistro.INGRESO,
            )
            ingresoDao.upsert(ingreso.toEntity())
            registroConflictosPendientes.resolver(clave)
        }
        try {
            persistRemotoIngreso(ingreso)
        } catch (e: Exception) {
            database.withTransaction {
                registroEdicionesPendientes.limpiar(clave)
                entidadPrevia?.let { ingresoDao.upsert(it) } ?: ingresoDao.delete(ingreso.id)
                conflictoPrevio?.let { registroConflictosPendientes.registrar(clave, it) }
            }
            throw e
        }
    }

    private suspend fun escribirGasto(gasto: Gasto): GastoEntity? = database.withTransaction {
        val previo = gastoDao.get(gasto.id)
        registroEdicionesPendientes.registrarSiCorresponde(
            claveRegistro(GASTOS_COLLECTION, gasto.id), gasto.editedBy, previo?.editedAt, gasto.editedAt, TipoRegistro.GASTO,
        )
        gastoDao.upsert(gasto.toEntity())
        previo
    }

    private suspend fun rollbackGasto(id: String, previo: GastoEntity?) = database.withTransaction {
        registroEdicionesPendientes.limpiar(claveRegistro(GASTOS_COLLECTION, id))
        if (previo != null) gastoDao.upsert(previo) else gastoDao.delete(id)
    }

    private suspend fun escribirIngreso(ingreso: Ingreso): IngresoEntity? = database.withTransaction {
        val previo = ingresoDao.get(ingreso.id)
        registroEdicionesPendientes.registrarSiCorresponde(
            claveRegistro(INGRESOS_COLLECTION, ingreso.id), ingreso.editedBy, previo?.editedAt, ingreso.editedAt, TipoRegistro.INGRESO,
        )
        ingresoDao.upsert(ingreso.toEntity())
        previo
    }

    private suspend fun rollbackIngreso(id: String, previo: IngresoEntity?) = database.withTransaction {
        registroEdicionesPendientes.limpiar(claveRegistro(INGRESOS_COLLECTION, id))
        if (previo != null) ingresoDao.upsert(previo) else ingresoDao.delete(id)
    }

    private suspend fun persistRemotoGasto(gasto: Gasto) {
        firestore.collection(GASTOS_COLLECTION)
            .document(gasto.id)
            .set(GastoDto.fromDomain(gasto).toMap())
            .await()
    }

    private suspend fun persistRemotoIngreso(ingreso: Ingreso) {
        firestore.collection(INGRESOS_COLLECTION)
            .document(ingreso.id)
            .set(IngresoDto.fromDomain(ingreso).toMap())
            .await()
    }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}
```

- [ ] **5.12 — Rewrite `CategoriaSincronizador.kt`**

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface CategoriaSincronizacion {
    fun sincronizar(planId: String): Flow<Unit>
}

/**
 * Syncs a plan's categories from Firestore into Room while it is collected.
 * Snapshot listener only lives during collection (active scope), per AGENTS.md
 * rule 3. Soft-deleted categories arrive as upserts with `deletedAt` set; Room
 * queries filter them. Each Firestore callback is processed inside one Room
 * transaction (ARCH-M501) — decision and write happen together, and a single
 * `Channel` consumer keeps overlapping callbacks in Firestore's own delivery
 * order rather than racing as independent coroutines.
 */
@Singleton
class CategoriaSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val categoriaDao: CategoriaDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : CategoriaSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val lotes = Channel<List<Categoria>>(Channel.UNLIMITED)
        val consumidor = launch {
            for (lote in lotes) {
                procesarSnapshotCategorias(database, categoriaDao, registroEdicionesPendientes, registroConflictosPendientes, lote)
            }
        }
        val listener = firestore.collection(CATEGORIAS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val categorias = snapshot?.documents.orEmpty()
                    .mapNotNull { CategoriaDto.fromData(it.data ?: return@mapNotNull null) }
                    .map { it.toDomain() }
                lotes.trySend(categorias)
            }
        awaitClose {
            listener.remove()
            lotes.close()
            consumidor.cancel()
        }
    }

    private companion object {
        const val CATEGORIAS_COLLECTION = "categorias"
    }
}

/**
 * One transaction per Firestore batch: every document's LWW decision and every Room write happen
 * together, so `Flow`-backed queries only ever emit a fully-consistent post-commit state, and a
 * process death mid-batch rolls the whole thing back — Firestore re-delivers the full current
 * state on listener re-attach, so nothing is lost.
 */
internal suspend fun procesarSnapshotCategorias(
    database: SpenvoDatabase,
    categoriaDao: CategoriaDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    categorias: List<Categoria>,
) {
    database.withTransaction {
        val aplicables = categorias.mapNotNull { categoria ->
            val clave = claveRegistro(CATEGORIAS_COLLECTION_INTERNAL, categoria.id)
            when (registroEdicionesPendientes.evaluar(clave, categoria.editedBy, categoria.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA ->
                    categoria.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
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

private const val CATEGORIAS_COLLECTION_INTERNAL = "categorias"
```

- [ ] **5.13 — Rewrite `MovimientoSincronizador.kt`** — same pattern, two independent channels
  (gastos, ingresos — no shared ordering needed, different tables):

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.GastoDto
import com.agoitdev.spenvo.data.remote.dto.IngresoDto
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface MovimientoSincronizacion {
    fun sincronizar(planId: String): Flow<Unit>
}

/**
 * Syncs a plan's gastos/ingresos from Firestore into Room while collected.
 * Same active-scope-only pattern as CategoriaSincronizador (AGENTS.md rule 3),
 * same one-transaction-per-batch + single-consumer-channel-per-listener shape
 * (ARCH-M501) as its documents-received counterparts.
 */
@Singleton
class MovimientoSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val gastoDao: GastoDao,
    private val ingresoDao: IngresoDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : MovimientoSincronizacion {

    override fun sincronizar(planId: String): Flow<Unit> = callbackFlow {
        val gastoLotes = Channel<List<Gasto>>(Channel.UNLIMITED)
        val ingresoLotes = Channel<List<Ingreso>>(Channel.UNLIMITED)
        val consumidorGastos = launch {
            for (lote in gastoLotes) procesarSnapshotGastos(database, gastoDao, registroEdicionesPendientes, registroConflictosPendientes, lote)
        }
        val consumidorIngresos = launch {
            for (lote in ingresoLotes) procesarSnapshotIngresos(database, ingresoDao, registroEdicionesPendientes, registroConflictosPendientes, lote)
        }
        val gastosListener = firestore.collection(GASTOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                gastoLotes.trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { GastoDto.fromData(it.data ?: return@mapNotNull null) }
                        .map { it.toDomain() },
                )
            }
        val ingresosListener = firestore.collection(INGRESOS_COLLECTION)
            .whereEqualTo("planId", planId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                ingresoLotes.trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { IngresoDto.fromData(it.data ?: return@mapNotNull null) }
                        .map { it.toDomain() },
                )
            }
        awaitClose {
            gastosListener.remove()
            ingresosListener.remove()
            gastoLotes.close()
            ingresoLotes.close()
            consumidorGastos.cancel()
            consumidorIngresos.cancel()
        }
    }

    private companion object {
        const val GASTOS_COLLECTION = "gastos"
        const val INGRESOS_COLLECTION = "ingresos"
    }
}

internal suspend fun procesarSnapshotGastos(
    database: SpenvoDatabase,
    gastoDao: GastoDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    gastos: List<Gasto>,
) {
    database.withTransaction {
        val aplicables = gastos.mapNotNull { gasto ->
            val clave = claveRegistro("gastos", gasto.id)
            when (registroEdicionesPendientes.evaluar(clave, gasto.editedBy, gasto.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> gasto.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = gastoDao.get(gasto.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        gasto.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(gasto.id, TipoRegistro.GASTO, local.toDomain().aSnapshotConflicto(), gasto.aSnapshotConflicto()),
                        )
                        null
                    }
                }
            }
        }
        gastoDao.upsertAll(aplicables)
    }
}

internal suspend fun procesarSnapshotIngresos(
    database: SpenvoDatabase,
    ingresoDao: IngresoDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    ingresos: List<Ingreso>,
) {
    database.withTransaction {
        val aplicables = ingresos.mapNotNull { ingreso ->
            val clave = claveRegistro("ingresos", ingreso.id)
            when (registroEdicionesPendientes.evaluar(clave, ingreso.editedBy, ingreso.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> ingreso.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = ingresoDao.get(ingreso.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        ingreso.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(ingreso.id, TipoRegistro.INGRESO, local.toDomain().aSnapshotConflicto(), ingreso.aSnapshotConflicto()),
                        )
                        null
                    }
                }
            }
        }
        ingresoDao.upsertAll(aplicables)
    }
}
```

- [ ] **5.14 — Rewrite `PlanSincronizador.kt`** — same algorithm, `procesarSnapshotPlanes` called
  with a one-element list per plan document (single-document listener, not a collection query):

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.withTransaction
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.mapper.toDomain
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.data.remote.dto.AccesoPlanDto
import com.agoitdev.spenvo.data.remote.dto.PlanFinancieroDto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.DecisionSincronizacion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.sync.RegistroEdicionesPendientes
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.aSnapshotConflicto
import com.agoitdev.spenvo.domain.sync.claveRegistro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

interface PlanSincronizacion {
    fun sincronizar(usuarioId: String): Flow<Unit>
}

/**
 * Syncs the user's plans and accesses from Firestore into Room while it is
 * collected. Listens on the user's accesses and attaches a snapshot listener
 * per active plan document, so remote plan edits (not only access changes) are
 * reflected in Room. Listeners only live during collection (active scope), per
 * AGENTS.md rule 3. Same one-transaction-per-batch shape as the other two
 * sincronizadores (ARCH-M501) — a single plan document processed as a
 * one-element list.
 */
@Singleton
class PlanSincronizador @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: SpenvoDatabase,
    private val planDao: PlanFinancieroDao,
    private val accesoDao: AccesoPlanDao,
    private val registroEdicionesPendientes: RegistroEdicionesPendientes,
    private val registroConflictosPendientes: RegistroConflictosPendientes,
) : PlanSincronizacion {

    override fun sincronizar(usuarioId: String): Flow<Unit> = callbackFlow {
        val planListeners = mutableMapOf<String, ListenerRegistration>()
        val planLotes = Channel<List<PlanFinanciero>>(Channel.UNLIMITED)
        val consumidor = launch {
            for (lote in planLotes) procesarSnapshotPlanes(database, planDao, registroEdicionesPendientes, registroConflictosPendientes, lote)
        }
        val accesoListener = firestore.collection(ACCESO_COLLECTION)
            .whereEqualTo("usuarioId", usuarioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Unit)
                val accesos = snapshot?.documents.orEmpty()
                    .mapNotNull { AccesoPlanDto.fromData(it.data ?: return@mapNotNull null) }
                val planIds = accesos.map { it.planId }.toSet()
                accesos.forEach { acceso ->
                    launch { accesoDao.upsert(acceso.toDomain().toEntity()) }
                }
                planIds.forEach { planId ->
                    if (!planListeners.containsKey(planId)) {
                        planListeners[planId] = firestore.collection(PLANES_COLLECTION)
                            .document(planId)
                            .addSnapshotListener { doc, planError ->
                                if (planError == null && doc != null && doc.data != null) {
                                    val planDto = PlanFinancieroDto.fromData(doc.data ?: emptyMap())
                                    if (planDto != null) planLotes.trySend(listOf(planDto.toDomain()))
                                }
                            }
                    }
                }
                planListeners.keys.filter { it !in planIds }.forEach { planId ->
                    planListeners.remove(planId)?.remove()
                }
            }
        awaitClose {
            planListeners.values.forEach { it.remove() }
            accesoListener.remove()
            planLotes.close()
            consumidor.cancel()
        }
    }

    private companion object {
        const val ACCESO_COLLECTION = "acceso_plan_financiero"
        const val PLANES_COLLECTION = "planes_financieros"
    }
}

internal suspend fun procesarSnapshotPlanes(
    database: SpenvoDatabase,
    planDao: PlanFinancieroDao,
    registroEdicionesPendientes: RegistroEdicionesPendientes,
    registroConflictosPendientes: RegistroConflictosPendientes,
    planes: List<PlanFinanciero>,
) {
    database.withTransaction {
        val aplicables = planes.mapNotNull { plan ->
            val clave = claveRegistro("planes_financieros", plan.id)
            when (registroEdicionesPendientes.evaluar(clave, plan.editedBy, plan.editedAt)) {
                DecisionSincronizacion.APLICAR, DecisionSincronizacion.PROPIA_CONFIRMADA -> plan.toEntity()
                DecisionSincronizacion.CONFLICTO -> {
                    val local = planDao.get(plan.id)
                    if (local == null) {
                        registroEdicionesPendientes.limpiar(clave)
                        plan.toEntity()
                    } else {
                        registroConflictosPendientes.registrar(
                            clave,
                            ConflictoEdicion(plan.id, TipoRegistro.PLAN, local.toDomain().aSnapshotConflicto(), plan.aSnapshotConflicto()),
                        )
                        null
                    }
                }
            }
        }
        planDao.upsertAll(aplicables)
    }
}
```

- [ ] **5.15 — Write `CategoriaSincronizadorProcesamientoTest.kt`** — proves the
  `procesarSnapshot*()` transaction pattern once against a real in-memory `SpenvoDatabase`; the
  transaction logic itself is identical per entity type, so this test stands as the pattern's
  proof (add the Gasto/Ingreso/Plan equivalents only if the executing agent judges this single
  proof isn't sufficient coverage):

```kotlin
package com.agoitdev.spenvo.data.remote.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.data.local.mapper.toEntity
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import com.agoitdev.spenvo.domain.sync.claveRegistro
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoriaSincronizadorProcesamientoTest {
    private lateinit var db: SpenvoDatabase
    private lateinit var registroEdiciones: RegistroEdicionesPendientesRoom
    private lateinit var registroConflictos: RegistroConflictosPendientesRoom

    private fun categoria(id: String = "p1:comida", editedBy: String? = "user-2", editedAt: Instant? = Instant.now()) = Categoria(
        id = id,
        planId = "p1",
        nombre = "Comida",
        icono = "comida",
        tipo = TipoCategoria.GASTO,
        editedBy = editedBy,
        editedAt = editedAt,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpenvoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registroEdiciones = RegistroEdicionesPendientesRoom(db.edicionPendienteDao())
        registroConflictos = RegistroConflictosPendientesRoom(db.conflictoEdicionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `documento sin edicion pendiente se aplica`() = runTest {
        procesarSnapshotCategorias(db, db.categoriaDao(), registroEdiciones, registroConflictos, listOf(categoria()))

        assertEquals("Comida", db.categoriaDao().get("p1:comida")?.nombre)
    }

    @Test
    fun `conflicto genuino no se aplica y se registra con la version local reconstruida de Room`() = runTest {
        val local = categoria(editedBy = "user-1", editedAt = Instant.now().minusSeconds(60))
        db.withTransaction {
            registroEdiciones.registrarSiCorresponde(
                claveRegistro(CATEGORIAS_COLLECTION_TEST, local.id), "user-1", local.editedAt?.minusSeconds(60), local.editedAt, TipoRegistro.CATEGORIA,
            )
            db.categoriaDao().upsert(local.toEntity())
        }

        val remoto = categoria(editedBy = "user-2", editedAt = Instant.now().plusSeconds(30))
        procesarSnapshotCategorias(db, db.categoriaDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals("Comida", db.categoriaDao().get("p1:comida")?.nombre) // local unchanged, not overwritten
        val conflicto = registroConflictos.conflictoPara(claveRegistro(CATEGORIAS_COLLECTION_TEST, local.id))
        assertEquals("user-1", conflicto?.local?.editadoPor)
        assertEquals("user-2", conflicto?.remoto?.editadoPor)
    }

    @Test
    fun `marcador huerfano sin fila local se limpia y se aplica el remoto sin crash`() = runTest {
        db.edicionPendienteDao().upsert(
            EdicionPendienteEntity(
                claveRegistro(CATEGORIAS_COLLECTION_TEST, "p1:comida"), TipoRegistro.CATEGORIA, "user-1", null, Instant.now().minusSeconds(60),
            ),
        )

        val remoto = categoria(editedBy = "user-2", editedAt = Instant.now())
        procesarSnapshotCategorias(db, db.categoriaDao(), registroEdiciones, registroConflictos, listOf(remoto))

        assertEquals("Comida", db.categoriaDao().get("p1:comida")?.nombre)
        assertNull(db.edicionPendienteDao().get(claveRegistro(CATEGORIAS_COLLECTION_TEST, "p1:comida")))
        assertTrue(registroConflictos.conflictos.first().isEmpty())
    }

    private companion object {
        const val CATEGORIAS_COLLECTION_TEST = "categorias"
    }
}
```

- [ ] **5.16 — Run everything: `:core:domain` and `:core:data` in full, unit and instrumented**

Run: `./gradlew :core:domain:testDebugUnitTest :core:data:compileDebugKotlin :core:data:testDebugUnitTest`
Expected: PASS

Run: `./gradlew :core:data:connectedDebugAndroidTest`
Expected: PASS — this exercises Task 3's, Task 4's, and this task's own `androidTest` suites
together, including `RegistroEdicionesPendientesRoomTest` (left uncommitted from Task 4, now
finally compilable and green)

Run: `./gradlew :core:data:lintDebug :core:data:detekt :core:domain:lintDebug :core:domain:detekt`
Expected: PASS — fix any findings before committing (the constructor param counts on the 3
repositories/3 sincronizadores likely need `@Suppress("LongParameterList")`, matching this
codebase's established convention rather than restructuring working code)

- [ ] **5.17 — Only once everything above is green, commit the whole task as one commit:**

```bash
git add -A -- core/domain core/data
git status --short  # confirm nothing unexpected is staged before committing
git commit -m "refactor: retire VersionPendiente/EdicionesPendientes/ConflictosPendientes for Room-backed registries (ARCH-M501)

Room is now the single source of truth for pending-edit and conflict tracking.
Every write that touches these registries lands in the same Room transaction as
the write it used to merely precede — closes the process-death data-loss gap
documented in doc/architecture.md's 'Conflicts (honest LWW)' section.

:core:domain and :core:data both compile and pass their full test suites as of
this commit — no intermediate broken state, per .agents/rules/commit-safety.md."
```

---

## Phase 3 — Verification checkpoint (Task 6)

### Task 6: `:core:domain` + `:core:data` full module gates, `:app:assembleDebug`

**Files:** none (verification only)

- [ ] **Step 1: Run the full gate for both cut-over modules plus the app**

Run: `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:domain:lintDebug :core:data:lintDebug :core:domain:detekt :core:data:detekt`
Run: `./gradlew :app:assembleDebug`
Expected: all PASS — confirms the atomic cutover didn't leave anything the module-level gates in
Task 5 missed (e.g. a downstream `:app`/`:feature:*` compile error from a changed constructor
signature not yet caught, since Task 5's own gates only ran the two modules directly touched)

- [ ] **Step 2: If `:app:assembleDebug` or any `:feature:*` module fails** — this means Task 5's
  domain/data changes broke a downstream consumer not yet updated (expected: `:feature:movimientos`
  still references `ConflictosPendientes` directly in `MovimientosViewModel.kt` until Task 8). Note
  the failure and proceed to Phase 4 — Task 8 fixes it; do not attempt to patch `:feature:movimientos`
  here out of sequence.

No commit for this task — it's a checkpoint, not a code change.

---

## Phase 4 — Domain use cases + ViewModel (Tasks 7-8)

### Task 7: `ResolverConflicto{Gasto,Ingreso}Usando{Local,Remoto}UseCase`

Fully independent of `:core:data`'s internals — only needs `MovimientoRepository`'s interface
(already extended in Task 5) and the existing `ValidarMontoUseCase`. Each of the 4 use cases is
its own small, always-green unit.

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoGastoUsandoLocalUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoIngresoUsandoLocalUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoGastoUsandoRemotoUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoIngresoUsandoRemotoUseCase.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoGastoUsandoLocalUseCaseTest.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ResolverConflictoGastoUsandoRemotoUseCaseTest.kt`

(Ingreso equivalents get identically-shaped tests — write them too, matching the Gasto pair below.)

- [ ] **Step 1: Write the failing tests** — `ResolverConflictoGastoUsandoLocalUseCaseTest.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverConflictoGastoUsandoLocalUseCaseTest {

    private fun gasto(editedBy: String? = null, editedAt: java.time.Instant? = null) = Gasto(
        id = "g1", planId = "p1", categoriaId = "c1", monto = Monto(1000),
        fecha = LocalDate.of(2026, 8, 20), creadoPor = "user-1", editedBy = editedBy, editedAt = editedAt,
    )

    @Test
    fun `estampa editedBy y editedAt frescos antes de llamar al repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflicto()
        val useCase = ResolverConflictoGastoUsandoLocalUseCase(repo, ValidarMontoUseCase())

        useCase(gasto(), editorId = "user-2", clave = "gastos:g1")

        val guardado = repo.gastoResueltoLocal
        assertEquals("user-2", guardado?.editedBy)
        assertTrue(guardado?.editedAt != null)
        assertEquals("gastos:g1", repo.claveResueltaLocal)
    }

    @Test
    fun `rechaza un monto invalido antes de tocar el repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflicto()
        val useCase = ResolverConflictoGastoUsandoLocalUseCase(repo, ValidarMontoUseCase())

        try {
            useCase(gasto().copy(monto = Monto(-100)), editorId = "user-2", clave = "gastos:g1")
            org.junit.Assert.fail("esperaba IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        org.junit.Assert.assertNull(repo.gastoResueltoLocal)
    }
}

private class FakeMovimientoRepositorioResolverConflicto : MovimientoRepository {
    var gastoResueltoLocal: Gasto? = null
    var claveResueltaLocal: String? = null

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: com.agoitdev.spenvo.domain.model.Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<com.agoitdev.spenvo.domain.model.Ingreso>> = flowOf(emptyList())
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) {
        gastoResueltoLocal = gasto
        claveResueltaLocal = clave
    }
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: com.agoitdev.spenvo.domain.model.Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}
```

`ResolverConflictoGastoUsandoRemotoUseCaseTest.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolverConflictoGastoUsandoRemotoUseCaseTest {
    @Test
    fun `delega id y clave al repositorio`() = runTest {
        val repo = FakeMovimientoRepositorioResolverConflictoRemoto()
        val useCase = ResolverConflictoGastoUsandoRemotoUseCase(repo)

        useCase(id = "g1", clave = "gastos:g1")

        assertEquals("g1" to "gastos:g1", repo.idClaveResuelto)
    }
}

private class FakeMovimientoRepositorioResolverConflictoRemoto : MovimientoRepository {
    var idClaveResuelto: Pair<String, String>? = null

    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) {
        idClaveResuelto = id to clave
    }
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}
```

Write `ResolverConflictoIngresoUsandoLocalUseCaseTest.kt`/`ResolverConflictoIngresoUsandoRemotoUseCaseTest.kt`
identically, swapping `Gasto`→`Ingreso` and the corresponding repository methods.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.ResolverConflicto*"`
Expected: FAIL to compile — the 4 use case classes don't exist

- [ ] **Step 3: Write the 4 use cases**

`ResolverConflictoGastoUsandoLocalUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

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
```

`ResolverConflictoIngresoUsandoLocalUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import java.time.Instant

class ResolverConflictoIngresoUsandoLocalUseCase(
    private val repository: MovimientoRepository,
    private val validarMonto: ValidarMontoUseCase,
) {
    suspend operator fun invoke(ingreso: Ingreso, editorId: String, clave: String) {
        require(validarMonto(ingreso.monto)) { "El monto debe ser positivo" }
        repository.resolverConflictoIngresoUsandoLocal(
            ingreso.copy(editedBy = editorId, editedAt = Instant.now()),
            clave,
        )
    }
}
```

`ResolverConflictoGastoUsandoRemotoUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.MovimientoRepository

class ResolverConflictoGastoUsandoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String, clave: String) = repository.resolverConflictoGastoUsandoRemoto(id, clave)
}
```

`ResolverConflictoIngresoUsandoRemotoUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.MovimientoRepository

class ResolverConflictoIngresoUsandoRemotoUseCase(
    private val repository: MovimientoRepository,
) {
    suspend operator fun invoke(id: String, clave: String) = repository.resolverConflictoIngresoUsandoRemoto(id, clave)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.ResolverConflicto*"`
Expected: PASS, all

- [ ] **Step 5: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverConflicto*.kt core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/ResolverConflicto*.kt
git commit -m "feat(domain): 4 conflict-resolution use cases, one pair per entity type (ARCH-M501)"
```

### Task 8: `MovimientosViewModel` — `.stateIn()` conversion, `claveVisible()`, rewritten `resolverConflicto()`

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosViewModel.kt`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosViewModelTest.kt`

- [ ] **Step 1: Write the failing test** — add to `MovimientosViewModelTest.kt`, matching whatever
  existing fakes/helper functions the file already has for `crearViewModel()` (extend that helper
  to pass fakes for the 4 new use cases; the existing tests named "resolverConflicto usarLocal..."
  /"resolverConflicto usarRemoto..." already in this file need their fakes updated to route
  through the 4 new use cases too — read the file first to match its exact existing style before
  editing):

```kotlin
@Test
fun `resolverConflicto usarRemoto cuando el remoto ya no existe mantiene el dialogo y muestra error`() = runTest {
    // Arrange: a fake ResolverConflictoGastoUsandoRemotoUseCase (or its backing fake repository)
    // that throws `error("El movimiento remoto ya no existe")`, and a conflictoVisible already set
    // for a Gasto — matching however this file's existing "resolverConflicto usarRemoto..." test
    // sets that up.

    viewModel.resolverConflicto(gasto, usarLocal = false)
    advanceUntilIdle()

    assertNotNull(viewModel.estadoForm.value.error)
    assertNotNull(viewModel.conflictoVisible.value) // dialog stays open — did NOT close on failure
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientosViewModelTest"`
Expected: FAIL — either a compile error (constructor signature mismatch once Step 3 lands) or the
old `resolverConflicto()` closing the dialog unconditionally

- [ ] **Step 3: Modify `MovimientosViewModel.kt`** — add the 4 new constructor params, convert
  `conflictos` to `.stateIn(...)`, add `claveVisible()`, rewrite `mostrarConflicto()`/
  `resolverConflicto()`:

Constructor gains (alongside the existing params, matching the file's `@Suppress("LongParameterList")`
convention already in place):

```kotlin
private val resolverConflictoGastoUsandoLocal: ResolverConflictoGastoUsandoLocalUseCase,
private val resolverConflictoIngresoUsandoLocal: ResolverConflictoIngresoUsandoLocalUseCase,
private val resolverConflictoGastoUsandoRemoto: ResolverConflictoGastoUsandoRemotoUseCase,
private val resolverConflictoIngresoUsandoRemoto: ResolverConflictoIngresoUsandoRemotoUseCase,
```

Replace `private val conflictosPendientes: ConflictosPendientes` with
`private val registroConflictosPendientes: RegistroConflictosPendientes` (the interface, per
Phase 1/2 — Hilt resolves it to the Room-backed impl via `ConflictoModule`'s `@Binds`).

Replace:

```kotlin
val conflictos: StateFlow<Map<String, ConflictoEdicion>> = conflictosPendientes.conflictos
```

with:

```kotlin
val conflictos: StateFlow<Map<String, ConflictoEdicion>> = registroConflictosPendientes.conflictos
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyMap())
```

Replace `mostrarConflicto()` and `resolverConflicto()`:

```kotlin
/** Opens the conflict dialog for [movimiento] if one is pending, or closes it when null. Never an interrupt. */
fun mostrarConflicto(movimiento: Movimiento?) {
    _conflictoVisible.value = movimiento?.let { m ->
        claveVisible(m)?.let { conflictos.value[it] }
    }
}

/**
 * Resolves the conflict for [movimiento]. `usarLocal` re-issues it as a fresh edit ("usar la mía" /
 * "restaurar mi edición"); otherwise the remote version wins verbatim ("usar la suya" / "mantener
 * borrado"). Only clears the dialog on success — a failed resolution (e.g. the remote document was
 * deleted in the meantime) surfaces through the same `_estadoForm.error` channel every other
 * action already uses, and the dialog stays open so the user can retry.
 */
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

/**
 * By-`(tipo, registroId)` lookup, unambiguous unlike the retired `conflictoPorRegistro`/
 * `resolverPorRegistro` (which resolved by `registroId` alone — ambiguous if a Gasto and an
 * Ingreso ever shared an id).
 */
private fun claveVisible(movimiento: Movimiento): String? {
    val tipo = if (movimiento is Gasto) TipoRegistro.GASTO else TipoRegistro.INGRESO
    return conflictos.value.entries.firstOrNull { it.value.registroId == movimiento.id && it.value.tipo == tipo }?.key
}
```

Update imports: drop `com.agoitdev.spenvo.domain.sync.ConflictosPendientes`, add
`com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes`,
`com.agoitdev.spenvo.domain.sync.TipoRegistro`,
`com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoLocalUseCase`,
`com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoLocalUseCase`,
`com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoRemotoUseCase`,
`com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoRemotoUseCase`.

- [ ] **Step 4: Run the full test file to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientosViewModelTest"`
Expected: PASS — including the pre-existing "resolverConflicto usarLocal.../usarRemoto..." tests,
now updated to route through the 4 use cases

- [ ] **Step 5: Full downstream check** — this is the point where `:feature:movimientos` and
  `:app` should both compile again (Task 6's checkpoint flagged this as the expected pending
  breakage; confirm it's now resolved):

Run: `./gradlew :feature:movimientos:testDebugUnitTest :feature:movimientos:lintDebug :feature:movimientos:detekt`
Run: `./gradlew :app:assembleDebug`
Expected: PASS, all

- [ ] **Step 6: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosViewModel.kt feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosViewModelTest.kt
git commit -m "refactor(movimientos): wire MovimientosViewModel to the 4 conflict-resolution use cases (ARCH-M501)"
```

---

## Phase 5 — Full verification and living docs (Task 9)

### Task 9: Full gate run, `CHANGELOG.md`/`backlog.md`/`ROADMAP.md`/`doc/architecture.md`

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `backlog.md`
- Modify: `ROADMAP.md`
- Modify: `doc/architecture.md`

- [ ] **Step 1: Full repo gates**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug detekt`
Run: `./gradlew :app:assembleDebug`
Expected: all PASS

- [ ] **Step 2: `CHANGELOG.md`** — new `### Changed` entry describing the ARCH-M501 slice: Room is
  now the source of truth for `EdicionesPendientes`/`ConflictosPendientes`, the accepted-debt note
  in `doc/architecture.md` is resolved, `VersionPendiente` retired.

- [ ] **Step 3: `backlog.md`** — move `ARCH-M501` from "To Do" to "Done," summarizing the same
  shape of one-paragraph outcome note this file already uses for other closed items (see
  `ARCH-U801`/`ARCH-U802`'s existing entries for the exact style).

- [ ] **Step 4: `ROADMAP.md`** — Phase 8's "Conflict State Persistence" bullet becomes `[x]`.

- [ ] **Step 5: `doc/architecture.md`** — update the "Conflicts (honest LWW)" section's
  "**Accepted debt**: both registries are in-memory only..." paragraph to say this is resolved,
  matching how the front-1 design doc's own "Known gap, deliberately deferred" sections were
  updated when `ARCH-U801`/`ARCH-U802` closed them.

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md backlog.md ROADMAP.md doc/architecture.md
git commit -m "docs: close out ARCH-M501 (Room-backed conflict registries)"
```

---

## After Task 9: merge back

This plan runs entirely inside the `worktree-codex+arch-m501-room-conflicts` worktree/branch.
Once Task 9's commit lands and every gate is green, hand control back for the merge decision
(fast-forward merge to `main`, or a PR — ask rather than assume) rather than merging
unilaterally.

## Self-Review

**Spec coverage:** every design-doc section has a task — domain interfaces/models (Tasks 1-2, 5),
Room entities/DAOs/converter/migration/impl (Tasks 3-4), the 4 transaction boundaries (Task 5), the
4 use cases (Task 7), the ViewModel call-site changes (Task 8). Testing section's every named case
has a corresponding test in the task that introduces it.

**Commit hygiene:** every commit in this plan leaves its module(s) compiling with tests green — no
task commits a known-broken or known-red state. The one genuinely large, multi-file change (Task 5)
is still exactly one commit, made only after both `:core:domain` and `:core:data` are fully green
together — task size and commit atomicity are two different things, and this plan no longer
conflates them (an earlier draft did; corrected before execution).

**Placeholder scan:** no TBD/TODO; every step has real code. Task 5's sincronizador rewrite and
Task 8's ViewModel test note where the executing agent should read an existing file's exact style
before extending it (existing fakes/helpers this plan can't fully replicate without duplicating
the whole test file inline) — that's a legitimate "look at the file" instruction, not a
placeholder for undone design work.

**Type consistency:** `RegistroEdicionesPendientes`/`RegistroConflictosPendientes` method
signatures match between the interface (Task 2), the pure-function callers inside them (Task 5's
`decidirSincronizacion`), the Room implementations (Task 4, finished compiling in Task 5), and
every call site in the 3 repositories/3 sincronizadores (Task 5). The 4 `MovimientoRepository`
conflict-resolution method names match across the interface (Task 5), the implementation (Task 5),
the 4 use cases (Task 7), and the ViewModel (Task 8) — this exact mismatch was the last issue the
design review caught, checked carefully here.
