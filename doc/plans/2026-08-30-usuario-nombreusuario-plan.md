# Usuario entity + nombreUsuario Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Turn the orphaned `Usuario`/`UsuarioEntity`/`UsuarioDao` skeleton into a live, synced
entity with a unique `nombreUsuario`, replacing raw UID display in Miembros and enabling
invite-by-identifier — without the app ever confirming or denying whether a given email or
nombreUsuario belongs to a real account.

**Architecture:** Client-only Firestore (no Cloud Functions), single-document `get`/transaction
lookups only (never `list`/query on anything identity-sensitive), Room as the UI read model.
Follows the existing repository/DTO/DI conventions used throughout `core/data`.

**Tech Stack:** Kotlin, Jetpack Compose, Room + SQLCipher, Firestore, Hilt, JUnit4 +
kotlinx-coroutines-test, Firebase Emulator (`rules-tests/`), Firebase Analytics (new dependency).

**Platform:** Android.

**Design doc:** `doc/designs/2026-08-30-usuario-nombreusuario-design.md`

---

### Task 1: Domain model + Room persistence

**Files:**
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/model/Entities.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/UsuarioEntity.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/local/mapper/Mappers.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/local/SpenvoDatabase.kt`
- Test: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/SpenvoDatabaseMigrationTest.kt`
- Test: `core/data/src/test/java/com/agoitdev/spenvo/data/local/mapper/MappersTest.kt`

- [ ] **Step 1: Write the failing migration test**

Append to `SpenvoDatabaseMigrationTest.kt`:

```kotlin
    @Test
    fun migra_de_v2_a_v3_agregando_nombreUsuario() {
        helper.createDatabase(TEST_DB_V2_V3, 2).use { db ->
            db.execSQL(
                "INSERT INTO usuarios (id, nombre, email, avatarUrl, createdAt, updatedAt) " +
                    "VALUES ('u1', 'Ana', 'ana@example.com', NULL, 123456, 123456)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_V2_V3, 3, true, SpenvoDatabase.MIGRATION_2_3)

        db.query("SELECT nombre, email, nombreUsuario FROM usuarios WHERE id = 'u1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Ana", cursor.getString(0))
            assertEquals("ana@example.com", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        private const val TEST_DB_V2_V3 = "migration-test-v2-to-v3"
    }
```

(Note: the existing `TEST_DB` companion constant stays; this adds a second one scoped to this
test since `MigrationTestHelper` needs a distinct DB name per test to avoid cross-test state.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:connectedDebugAndroidTest --tests "*.SpenvoDatabaseMigrationTest"`
Expected: FAIL — `MIGRATION_2_3` doesn't exist yet, compile error.

- [ ] **Step 3: Update the domain model**

In `Entities.kt`, replace the `Usuario` data class:

```kotlin
data class Usuario(
    val id: String,
    val nombreUsuario: String,
    val nombre: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
```

- [ ] **Step 4: Update the Room entity**

Replace `UsuarioEntity.kt`:

```kotlin
package com.agoitdev.spenvo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.agoitdev.spenvo.data.local.converter.Converters
import java.time.Instant

@Entity(tableName = "usuarios")
@TypeConverters(Converters::class)
data class UsuarioEntity(
    @PrimaryKey val id: String,
    val nombreUsuario: String,
    val nombre: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
```

- [ ] **Step 5: Update the mapper**

In `Mappers.kt`, replace the two `Usuario`/`UsuarioEntity` mapper functions:

```kotlin
fun Usuario.toEntity(): UsuarioEntity = UsuarioEntity(
    id = id,
    nombreUsuario = nombreUsuario,
    nombre = nombre,
    email = email,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UsuarioEntity.toDomain(): Usuario = Usuario(
    id = id,
    nombreUsuario = nombreUsuario,
    nombre = nombre,
    email = email,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
```

Update the existing `MappersTest.kt` cases for `Usuario`/`UsuarioEntity` round-trip to include
`nombreUsuario` in the fixture and assertion (mechanical — same shape as the other round-trip
tests already in that file, just with the two new/changed fields).

- [ ] **Step 6: Add the migration and bump the DB version**

In `SpenvoDatabase.kt`: bump `version = 2` to `version = 3` on the `@Database` annotation, add
`.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` in `build()`, and add the migration:

```kotlin
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usuarios ADD COLUMN nombreUsuario TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE usuarios RENAME COLUMN nombre TO nombre_old")
                db.execSQL("ALTER TABLE usuarios ADD COLUMN nombre TEXT")
                db.execSQL("UPDATE usuarios SET nombre = nombre_old")
                db.execSQL("ALTER TABLE usuarios DROP COLUMN nombre_old")
                db.execSQL("ALTER TABLE usuarios RENAME COLUMN email TO email_old")
                db.execSQL("ALTER TABLE usuarios ADD COLUMN email TEXT")
                db.execSQL("UPDATE usuarios SET email = email_old")
                db.execSQL("ALTER TABLE usuarios DROP COLUMN email_old")
            }
        }
```

(SQLite's `ALTER TABLE ... MODIFY COLUMN` doesn't exist — making an existing `NOT NULL` column
nullable needs the rename→add→copy→drop dance above rather than a single statement. This is only
needed for `nombre`/`email`; `nombreUsuario` is a straightforward `ADD COLUMN`.)

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :core:data:connectedDebugAndroidTest --tests "*.SpenvoDatabaseMigrationTest"`
Run: `./gradlew :core:data:testDebugUnitTest --tests "*.MappersTest"`
Expected: both PASS.

- [ ] **Step 8: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/Entities.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/local/entity/UsuarioEntity.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/local/mapper/Mappers.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/local/SpenvoDatabase.kt \
  core/data/src/androidTest/java/com/agoitdev/spenvo/data/local/SpenvoDatabaseMigrationTest.kt \
  core/data/src/test/java/com/agoitdev/spenvo/data/local/mapper/MappersTest.kt
git commit -m "feat(data): live Usuario entity with nombreUsuario, Room migration 2->3"
```

---

### Task 2: nombreUsuario generator + UsuarioRepository interface

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/model/NombreUsuario.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/UsuarioRepository.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/GenerarNombreUsuarioUnicoUseCase.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/GenerarNombreUsuarioUnicoUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerarNombreUsuarioUnicoUseCaseTest {

    @Test
    fun `genera un candidato y lo reserva en el primer intento si esta libre`() = runTest {
        val repo = FakeUsuarioRepository()
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val nombreUsuario = useCase("u1")

        assertEquals(1, repo.intentosDeReserva)
        assertTrue(repo.reservados.containsKey(nombreUsuario.lowercase()))
        assertEquals("u1", repo.reservados[nombreUsuario.lowercase()])
    }

    @Test
    fun `reintenta con otro candidato si el primero esta tomado`() = runTest {
        val repo = FakeUsuarioRepository(rechazarPrimerosIntentos = 2)
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val nombreUsuario = useCase("u1")

        assertEquals(3, repo.intentosDeReserva)
        assertTrue(repo.reservados.containsKey(nombreUsuario.lowercase()))
    }

    @Test
    fun `falla si ningun intento logra reservar`() = runTest {
        val repo = FakeUsuarioRepository(rechazarPrimerosIntentos = Int.MAX_VALUE)
        val useCase = GenerarNombreUsuarioUnicoUseCase(repo)

        val resultado = runCatching { useCase("u1") }

        assertTrue(resultado.isFailure)
    }
}

private class FakeUsuarioRepository(
    private val rechazarPrimerosIntentos: Int = 0,
) : UsuarioRepository {
    var intentosDeReserva = 0
    val reservados = mutableMapOf<String, String>()

    override suspend fun obtener(usuarioId: String): Usuario? = null
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = emptyList()

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean {
        intentosDeReserva++
        if (intentosDeReserva <= rechazarPrimerosIntentos) return false
        if (reservados.containsKey(nombreUsuarioNormalizado)) return false
        reservados[nombreUsuarioNormalizado] = usuarioId
        return true
    }

    override suspend fun crear(usuario: Usuario) = Unit
    override suspend fun actualizar(usuario: Usuario) = Unit
    override suspend fun renombrar(usuarioId: String, anterior: String, nuevo: String): Boolean = true
    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.GenerarNombreUsuarioUnicoUseCaseTest"`
Expected: FAIL — `UsuarioRepository`/`GenerarNombreUsuarioUnicoUseCase` don't exist yet.

- [ ] **Step 3: Write the normalization helper**

```kotlin
package com.agoitdev.spenvo.domain.model

fun normalizarNombreUsuario(valor: String): String = valor.trim().lowercase()

fun normalizarEmail(valor: String): String = valor.trim().lowercase()
```

- [ ] **Step 4: Write the repository interface**

```kotlin
package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Usuario

interface UsuarioRepository {
    suspend fun obtener(usuarioId: String): Usuario?

    suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario>

    /** Transactional: true si el nombreUsuario estaba libre y quedó reservado para [usuarioId]. */
    suspend fun intentarReservarNombreUsuario(nombreUsuarioNormalizado: String, usuarioId: String): Boolean

    suspend fun crear(usuario: Usuario)

    /** Actualiza nombre/email/avatarUrl; nunca toca nombreUsuario (usar [renombrar]). */
    suspend fun actualizar(usuario: Usuario)

    /** Transactional: libera [anterior], reserva [nuevo]. False si [nuevo] ya estaba tomado. */
    suspend fun renombrar(usuarioId: String, nombreUsuarioAnterior: String, nombreUsuarioNuevo: String): Boolean

    suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String)

    suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String?

    suspend fun resolverPorEmail(emailNormalizado: String): String?
}
```

- [ ] **Step 5: Write the generator use case**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlin.random.Random

class GenerarNombreUsuarioUnicoUseCase(
    private val usuarioRepository: UsuarioRepository,
) {
    suspend operator fun invoke(usuarioId: String): String {
        repeat(MAX_INTENTOS) { intento ->
            val candidato = candidato(intento)
            if (usuarioRepository.intentarReservarNombreUsuario(normalizarNombreUsuario(candidato), usuarioId)) {
                return candidato
            }
        }
        error("No se pudo generar un nombreUsuario único tras $MAX_INTENTOS intentos")
    }

    private fun candidato(intento: Int): String {
        val adjetivo = ADJETIVOS.random()
        val sustantivo = SUSTANTIVOS.random()
        val rango = if (intento < INTENTOS_RANGO_CORTO) RANGO_CORTO else RANGO_LARGO
        val numero = Random.nextInt(rango)
        return "$adjetivo$sustantivo$numero"
    }

    private companion object {
        const val MAX_INTENTOS = 8
        const val INTENTOS_RANGO_CORTO = 5
        const val RANGO_CORTO = 100
        const val RANGO_LARGO = 100_000
        val ADJETIVOS = listOf(
            "Rapido", "Alegre", "Sabio", "Curioso", "Amable", "Valiente", "Sereno", "Astuto",
            "Brillante", "Gentil", "Audaz", "Tranquilo", "Vivaz", "Noble", "Agil",
        )
        val SUSTANTIVOS = listOf(
            "Gato", "Sol", "Rio", "Nube", "Zorro", "Bosque", "Cometa", "Delfin", "Aguila",
            "Estrella", "Roble", "Faro", "Puma", "Coral", "Lince",
        )
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.GenerarNombreUsuarioUnicoUseCaseTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/NombreUsuario.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/UsuarioRepository.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/GenerarNombreUsuarioUnicoUseCase.kt \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/GenerarNombreUsuarioUnicoUseCaseTest.kt
git commit -m "feat(domain): nombreUsuario generator with bounded collision retry"
```

---

### Task 3: FirebaseUsuarioRepository + DI wiring

**Files:**
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/dto/UsuarioDto.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseUsuarioRepository.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt`
- Test: `core/data/src/test/java/com/agoitdev/spenvo/data/remote/dto/UsuarioDtoTest.kt`

`UsuarioRepository`'s `get`-based methods (`obtener`, `obtenerVarios`, `resolverPorNombreUsuario`,
`resolverPorEmail`) and the reservation/rename transactions need Firestore's suspend-friendly
`.await()` (already used throughout `core/data`, see `FirebasePlanFinancieroRepository`) and
`runTransaction { }.await()` for the two-step reserve/rename operations — no new Firestore APIs.

- [ ] **Step 1: Write the failing DTO test**

```kotlin
package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Usuario
import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsuarioDtoTest {

    @Test
    fun `fromDomain y toDomain hacen un round-trip completo`() {
        val usuario = Usuario(
            id = "u1",
            nombreUsuario = "GatoAzul42",
            nombre = "Ana",
            email = "ana@example.com",
            avatarUrl = "https://example.com/a.jpg",
            createdAt = Instant.parse("2026-08-30T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-30T10:00:00Z"),
        )

        val dto = UsuarioDto.fromDomain(usuario)
        val vuelta = dto.toDomain()

        assertEquals(usuario, vuelta)
    }

    @Test
    fun `fromData con campos ausentes usa null para nombre y email`() {
        val data = mapOf(
            "uid" to "u1",
            "nombreUsuario" to "GatoAzul42",
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
        )

        val dto = UsuarioDto.fromData(data)

        assertEquals("u1", dto?.uid)
        assertNull(dto?.nombre)
        assertNull(dto?.email)
    }

    @Test
    fun `fromData sin nombreUsuario devuelve null`() {
        val data = mapOf("uid" to "u1", "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now())

        assertNull(UsuarioDto.fromData(data))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*.UsuarioDtoTest"`
Expected: FAIL — `UsuarioDto` doesn't exist yet.

- [ ] **Step 3: Write the DTO**

Field name is `uid`, not `id`, matching the existing `usuarios/{usuarioId}` Firestore rule's
`request.resource.data.uid == usuarioId` check (`firestore.rules`, pre-existing):

```kotlin
package com.agoitdev.spenvo.data.remote.dto

import com.agoitdev.spenvo.domain.model.Usuario
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

internal data class UsuarioDto(
    val uid: String,
    val nombreUsuario: String,
    val nombre: String?,
    val email: String?,
    val avatarUrl: String?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) {
    fun toDomain(): Usuario = Usuario(
        id = uid,
        nombreUsuario = nombreUsuario,
        nombre = nombre,
        email = email,
        avatarUrl = avatarUrl,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "nombreUsuario" to nombreUsuario,
        "nombre" to nombre,
        "email" to email,
        "avatarUrl" to avatarUrl,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    companion object {
        fun fromDomain(usuario: Usuario): UsuarioDto = UsuarioDto(
            uid = usuario.id,
            nombreUsuario = usuario.nombreUsuario,
            nombre = usuario.nombre,
            email = usuario.email,
            avatarUrl = usuario.avatarUrl,
            createdAt = usuario.createdAt.toTimestamp(),
            updatedAt = usuario.updatedAt.toTimestamp(),
        )

        fun fromData(data: Map<String, Any?>): UsuarioDto? {
            val uid = data["uid"] as? String ?: return null
            val nombreUsuario = data["nombreUsuario"] as? String ?: return null
            val createdAt = data["createdAt"] as? Timestamp ?: return null
            val updatedAt = data["updatedAt"] as? Timestamp ?: return null
            return UsuarioDto(
                uid = uid,
                nombreUsuario = nombreUsuario,
                nombre = data["nombre"] as? String,
                email = data["email"] as? String,
                avatarUrl = data["avatarUrl"] as? String,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}

private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*.UsuarioDtoTest"`
Expected: PASS.

- [ ] **Step 5: Write the Firebase repository**

```kotlin
package com.agoitdev.spenvo.data.remote.repository

import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.UsuarioDto
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll

@Singleton
class FirebaseUsuarioRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UsuarioRepository {

    override suspend fun obtener(usuarioId: String): Usuario? {
        val snapshot = firestore.collection(USUARIOS_COLLECTION).document(usuarioId).get().await()
        return snapshot.data?.let { UsuarioDto.fromData(it)?.toDomain() }
    }

    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = coroutineScope {
        usuarioIds.map { id -> async { obtener(id) } }.awaitAll().filterNotNull()
    }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean {
        val ref = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNormalizado)
        return runCatching {
            firestore.runTransaction { transaction ->
                val existente = transaction.get(ref)
                if (existente.exists()) {
                    error("nombreUsuario ya reservado")
                }
                transaction.set(ref, mapOf("usuarioId" to usuarioId))
            }.await()
            true
        }.getOrElse { false }
    }

    override suspend fun crear(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    override suspend fun actualizar(usuario: Usuario) {
        val dto = UsuarioDto.fromDomain(usuario)
        firestore.collection(USUARIOS_COLLECTION).document(usuario.id).set(dto.toMap()).await()
    }

    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean {
        val refAnterior = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioAnterior)
        val refNuevo = firestore.collection(NOMBRES_USUARIO_COLLECTION).document(nombreUsuarioNuevo)
        val refUsuario = firestore.collection(USUARIOS_COLLECTION).document(usuarioId)
        return runCatching {
            firestore.runTransaction { transaction ->
                val existenteNuevo = transaction.get(refNuevo)
                if (existenteNuevo.exists()) {
                    error("nombreUsuario ya reservado")
                }
                transaction.delete(refAnterior)
                transaction.set(refNuevo, mapOf("usuarioId" to usuarioId))
                transaction.update(refUsuario, "nombreUsuario", nombreUsuarioNuevo)
            }.await()
            true
        }.getOrElse { false }
    }

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) {
        firestore.collection(EMAILS_USUARIO_COLLECTION).document(emailNormalizado)
            .set(mapOf("usuarioId" to usuarioId)).await()
    }

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? {
        val snapshot = firestore.collection(NOMBRES_USUARIO_COLLECTION)
            .document(nombreUsuarioNormalizado).get().await()
        return snapshot.getString("usuarioId")
    }

    override suspend fun resolverPorEmail(emailNormalizado: String): String? {
        val snapshot = firestore.collection(EMAILS_USUARIO_COLLECTION)
            .document(emailNormalizado).get().await()
        return snapshot.getString("usuarioId")
    }

    private companion object {
        const val USUARIOS_COLLECTION = "usuarios"
        const val NOMBRES_USUARIO_COLLECTION = "nombres_usuario"
        const val EMAILS_USUARIO_COLLECTION = "emails_usuario"
    }
}
```

(`com.agoitdev.spenvo.data.remote.await` is the project's existing `Task<T>.await()` extension,
already used by every other `Firebase*Repository` — confirm its exact import path against
`FirebasePlanFinancieroRepository.kt`'s own import before wiring this in, since this plan's
reconnaissance saw it used but not its own file.)

- [ ] **Step 6: Wire DI**

```kotlin
package com.agoitdev.spenvo.data.di

import com.agoitdev.spenvo.data.remote.repository.FirebaseUsuarioRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.GenerarNombreUsuarioUnicoUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsuarioModule {

    @Binds
    @Singleton
    abstract fun bindUsuarioRepository(impl: FirebaseUsuarioRepository): UsuarioRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UsuarioUseCaseModule {

    @Provides
    fun provideGenerarNombreUsuarioUnico(
        usuarioRepository: UsuarioRepository,
    ): GenerarNombreUsuarioUnicoUseCase = GenerarNombreUsuarioUnicoUseCase(usuarioRepository)
}
```

- [ ] **Step 7: Build to verify wiring compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add core/data/src/main/java/com/agoitdev/spenvo/data/remote/dto/UsuarioDto.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseUsuarioRepository.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt \
  core/data/src/test/java/com/agoitdev/spenvo/data/remote/dto/UsuarioDtoTest.kt
git commit -m "feat(data): FirebaseUsuarioRepository, wired via UsuarioModule"
```

---

### Task 4: AsegurarUsuarioUseCase — create on first sign-in, update on vincularEmail

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCase.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt`
- Modify: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt` (add `@Provides`)
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/PlanModule.kt` (wire into `PlanesViewModel`'s constructor via its `@Provides`/Hilt injection point — see note below)
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCaseTest.kt`
- Test: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt`
- Test: `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt`

- [ ] **Step 1: Write the failing use case test**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AsegurarUsuarioUseCaseTest {

    @Test
    fun `crea un Usuario nuevo con nombreUsuario generado si no existe`() = runTest {
        val repo = FakeUsuarioRepository()
        val generar = GenerarNombreUsuarioUnicoUseCase(repo)
        val useCase = AsegurarUsuarioUseCase(repo, generar)

        useCase.paraSesionAnonima(usuarioId = "u1")

        val creado = repo.creados.single()
        assertEquals("u1", creado.id)
        assertNotNull(creado.nombreUsuario)
        assertNull(creado.nombre)
        assertNull(creado.email)
    }

    @Test
    fun `no crea de nuevo si el Usuario ya existe`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val repo = FakeUsuarioRepository(existentes = listOf(existente))
        val useCase = AsegurarUsuarioUseCase(repo, GenerarNombreUsuarioUnicoUseCase(repo))

        useCase.paraSesionAnonima(usuarioId = "u1")

        assertEquals(0, repo.creados.size)
    }

    @Test
    fun `al vincular email actualiza nombre y email conservando el nombreUsuario`() = runTest {
        val existente = Usuario(id = "u1", nombreUsuario = "GatoAzul1")
        val repo = FakeUsuarioRepository(existentes = listOf(existente))
        val useCase = AsegurarUsuarioUseCase(repo, GenerarNombreUsuarioUnicoUseCase(repo))

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        val actualizado = repo.actualizados.single()
        assertEquals("GatoAzul1", actualizado.nombreUsuario)
        assertEquals("Ana", actualizado.nombre)
        assertEquals("ana@example.com", actualizado.email)
        assertEquals(listOf("ana@example.com" to "u1"), repo.indicesEmail)
    }
}

private class FakeUsuarioRepository(
    existentes: List<Usuario> = emptyList(),
) : UsuarioRepository {
    private val usuarios = existentes.associateBy { it.id }.toMutableMap()
    val creados = mutableListOf<Usuario>()
    val actualizados = mutableListOf<Usuario>()
    val indicesEmail = mutableListOf<Pair<String, String>>()

    override suspend fun obtener(usuarioId: String): Usuario? = usuarios[usuarioId]
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> =
        usuarioIds.mapNotNull { usuarios[it] }

    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true

    override suspend fun crear(usuario: Usuario) {
        creados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun actualizar(usuario: Usuario) {
        actualizados.add(usuario)
        usuarios[usuario.id] = usuario
    }

    override suspend fun renombrar(usuarioId: String, anterior: String, nuevo: String): Boolean = true

    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) {
        indicesEmail.add(emailNormalizado to usuarioId)
    }

    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.AsegurarUsuarioUseCaseTest"`
Expected: FAIL — `AsegurarUsuarioUseCase` doesn't exist yet.

- [ ] **Step 3: Write the use case**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.model.normalizarEmail
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

class AsegurarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
    private val generarNombreUsuarioUnico: GenerarNombreUsuarioUnicoUseCase,
) {
    suspend fun paraSesionAnonima(usuarioId: String) {
        if (usuarioRepository.obtener(usuarioId) != null) return
        val nombreUsuario = generarNombreUsuarioUnico(usuarioId)
        usuarioRepository.crear(Usuario(id = usuarioId, nombreUsuario = nombreUsuario))
    }

    suspend fun paraVincularEmail(usuarioId: String, nombre: String, email: String) {
        val existente = usuarioRepository.obtener(usuarioId)
            ?: Usuario(id = usuarioId, nombreUsuario = generarNombreUsuarioUnico(usuarioId))
        usuarioRepository.actualizar(existente.copy(nombre = nombre, email = email))
        usuarioRepository.registrarIndiceEmail(usuarioId, normalizarEmail(email))
    }
}
```

(The `?:` fallback in `paraVincularEmail` covers a defensive edge case — normally the anonymous
bootstrap in `paraSesionAnonima` has already created the `Usuario` doc by the time registration
happens, but this keeps the flow correct even if that step was somehow skipped.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.AsegurarUsuarioUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Wire into `UsuarioModule`**

Add to `UsuarioUseCaseModule`:

```kotlin
    @Provides
    fun provideAsegurarUsuario(
        usuarioRepository: UsuarioRepository,
        generarNombreUsuarioUnico: GenerarNombreUsuarioUnicoUseCase,
    ): AsegurarUsuarioUseCase = AsegurarUsuarioUseCase(usuarioRepository, generarNombreUsuarioUnico)
```

- [ ] **Step 6: Write the failing PlanesViewModel test**

Add to `PlanesViewModelTest.kt` (needs `AsegurarUsuarioUseCase` added to `crearViewModel()`'s
constructor call and a `FakeAsegurarUsuario`-style spy, or reuse `FakeUsuarioRepository` directly
since the real use case is cheap to construct in tests):

```kotlin
    @Test
    fun `al arrancar asegura el Usuario del uid activo tras la sesion anonima`() = runTest {
        val usuarioRepo = FakeUsuarioRepository()
        val viewModel = crearViewModel(
            asegurarUsuario = AsegurarUsuarioUseCase(usuarioRepo, GenerarNombreUsuarioUnicoUseCase(usuarioRepo)),
        )

        advanceUntilIdle()

        assertEquals(listOf("user-1"), usuarioRepo.creados.map { it.id })
    }
```

(`FakeUsuarioRepository` here mirrors the one in Step 1 above — either extract it to a small
shared test-fixtures file under `core/domain/src/test/...` reused by both test classes, or
duplicate it locally in `PlanesViewModelTest.kt` following this codebase's existing pattern of
per-test-file private fakes, whichever keeps the diff smaller once the actual file is open.)

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesViewModelTest"`
Expected: FAIL — `PlanesViewModel` doesn't take an `AsegurarUsuarioUseCase` yet.

- [ ] **Step 8: Wire `AsegurarUsuarioUseCase` into `PlanesViewModel`**

Add `private val asegurarUsuario: AsegurarUsuarioUseCase` to the constructor, and change the
`init` block's retry loop (`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt:98-105`)
to ensure the `Usuario` doc right after the anonymous sign-in succeeds:

```kotlin
    init {
        viewModelScope.launch {
            while (true) {
                val uid = runCatching { authRepository.iniciarSesionAnonima() }
                    .mapCatching { authRepository.observeSesion().first().uid ?: error("sin uid") }
                    .getOrNull()
                if (uid != null) {
                    runCatching { asegurarUsuario.paraSesionAnonima(uid) }
                    break
                }
                delay(RETRY_DELAY_MS)
            }
        }
    }
```

Update `PlanModule.kt`'s (or wherever `PlanesViewModel` is `@Provides`d/constructed — confirm via
`@HiltViewModel @Inject constructor` on the class itself, since Hilt ViewModels are typically
constructor-injected directly rather than `@Provides`d, so this may need no explicit DI module
change beyond `AsegurarUsuarioUseCase` already being providable from Task 4 Step 5 above) — read
`PlanesViewModel.kt`'s full constructor before editing to confirm whether it's `@HiltViewModel` +
`@Inject constructor` (most likely, matching every other ViewModel seen in this codebase) and add
the new parameter there directly.

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesViewModelTest"`
Expected: PASS (all existing tests in this file too — `crearViewModel()`'s signature change
affects every test that calls it).

- [ ] **Step 10: Write the failing CuentaViewModel test**

Add to `CuentaViewModelTest.kt`:

```kotlin
    @Test
    fun `registrar exitoso asegura el Usuario con nombre y email`() = runTest {
        val usuarioRepo = FakeUsuarioRepository(existentes = listOf(Usuario(id = "user-1", nombreUsuario = "GatoAzul1")))
        val viewModel = crearViewModel(
            asegurarUsuario = AsegurarUsuarioUseCase(usuarioRepo, GenerarNombreUsuarioUnicoUseCase(usuarioRepo)),
        )

        viewModel.registrar(nombre = "Ana", email = "ana@example.com", password = "secret123")
        advanceUntilIdle()

        val actualizado = usuarioRepo.actualizados.single()
        assertEquals("Ana", actualizado.nombre)
        assertEquals("ana@example.com", actualizado.email)
    }
```

- [ ] **Step 11: Run test to verify it fails**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaViewModelTest"`
Expected: FAIL — `CuentaViewModel` doesn't take an `AsegurarUsuarioUseCase` yet.

- [ ] **Step 12: Wire into `CuentaViewModel.registrar()`**

Add `private val asegurarUsuario: AsegurarUsuarioUseCase` to the constructor, and update
`registrar()` (`feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt:35-44`):

```kotlin
    fun registrar(nombre: String, email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching {
                vincularCredencial(email = email, password = password, nombre = nombre)
                val uid = sesion.value.uid ?: error("sin uid tras vincular")
                asegurarUsuario.paraVincularEmail(usuarioId = uid, nombre = nombre, email = email)
            }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(error = error.message)
                }
        }
    }
```

- [ ] **Step 13: Run tests to verify they pass**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaViewModelTest"`
Expected: PASS.

- [ ] **Step 14: Full build check**

Run: `./gradlew :app:assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 15: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCase.kt \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCaseTest.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt \
  feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt \
  feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt \
  feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt \
  feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt
git commit -m "feat: ensure a live Usuario doc on anonymous bootstrap and on registration"
```

---

### Task 5: Edit nombreUsuario from the profile screen

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/RenombrarUsuarioUseCase.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt`
- Modify: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt`
- Modify: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaScreen.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/RenombrarUsuarioUseCaseTest.kt`
- Test: `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt`
- Test: `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt`

- [ ] **Step 1: Write the failing use case test**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenombrarUsuarioUseCaseTest {

    @Test
    fun `renombra exitosamente cuando el repositorio confirma la reserva`() = runTest {
        val repo = FakeUsuarioRepository(resultadoRenombrar = true)
        val useCase = RenombrarUsuarioUseCase(repo)

        val resultado = useCase(usuarioId = "u1", nombreUsuarioAnterior = "GatoAzul1", nombreUsuarioNuevo = "ZorroVeloz9")

        assertTrue(resultado)
    }

    @Test
    fun `devuelve false cuando el nuevo nombreUsuario ya esta tomado`() = runTest {
        val repo = FakeUsuarioRepository(resultadoRenombrar = false)
        val useCase = RenombrarUsuarioUseCase(repo)

        val resultado = useCase(usuarioId = "u1", nombreUsuarioAnterior = "GatoAzul1", nombreUsuarioNuevo = "ZorroVeloz9")

        assertFalse(resultado)
    }
}

private class FakeUsuarioRepository(private val resultadoRenombrar: Boolean) : UsuarioRepository {
    override suspend fun obtener(usuarioId: String): Usuario? = null
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = emptyList()
    override suspend fun intentarReservarNombreUsuario(n: String, u: String): Boolean = true
    override suspend fun crear(usuario: Usuario) = Unit
    override suspend fun actualizar(usuario: Usuario) = Unit
    override suspend fun renombrar(usuarioId: String, anterior: String, nuevo: String) = resultadoRenombrar
    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(n: String): String? = null
    override suspend fun resolverPorEmail(e: String): String? = null
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.RenombrarUsuarioUseCaseTest"`
Expected: FAIL.

- [ ] **Step 3: Write the use case**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

class RenombrarUsuarioUseCase(
    private val usuarioRepository: UsuarioRepository,
) {
    suspend operator fun invoke(usuarioId: String, nombreUsuarioAnterior: String, nombreUsuarioNuevo: String): Boolean =
        usuarioRepository.renombrar(
            usuarioId = usuarioId,
            nombreUsuarioAnterior = normalizarNombreUsuario(nombreUsuarioAnterior),
            nombreUsuarioNuevo = normalizarNombreUsuario(nombreUsuarioNuevo),
        )
}
```

- [ ] **Step 4: Run test, add `@Provides`, verify pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.RenombrarUsuarioUseCaseTest"` → PASS.

Add to `UsuarioUseCaseModule`:

```kotlin
    @Provides
    fun provideRenombrarUsuario(
        usuarioRepository: UsuarioRepository,
    ): RenombrarUsuarioUseCase = RenombrarUsuarioUseCase(usuarioRepository)
```

- [ ] **Step 5: Write the failing CuentaViewModel test for editing**

```kotlin
    @Test
    fun `editarNombreUsuario exitoso actualiza el estado con el nuevo valor`() = runTest {
        val viewModel = crearViewModel(renombrarUsuario = { _, _, _ -> true })

        viewModel.editarNombreUsuario("ZorroVeloz9")
        advanceUntilIdle()

        assertEquals("ZorroVeloz9", viewModel.perfilEstado.value.nombreUsuario)
        assertNull(viewModel.perfilEstado.value.nombreUsuarioError)
    }

    @Test
    fun `editarNombreUsuario fallido expone un error sin tocar el estado previo`() = runTest {
        val viewModel = crearViewModel(renombrarUsuario = { _, _, _ -> false })

        viewModel.editarNombreUsuario("ZorroVeloz9")
        advanceUntilIdle()

        assertNotNull(viewModel.perfilEstado.value.nombreUsuarioError)
    }
```

(`renombrarUsuario` here is a constructor-injected lambda/fake matching `RenombrarUsuarioUseCase`'s
call shape for the test — adjust to this file's actual established fake-injection style once
`CuentaViewModelTest.kt` is open, following whatever pattern `subirAvatarUseCase`'s existing tests
already use in that same file.)

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaViewModelTest"`
Expected: FAIL.

- [ ] **Step 7: Wire `editarNombreUsuario` into `CuentaViewModel`**

Add `private val renombrarUsuario: RenombrarUsuarioUseCase` to the constructor, extend
`PerfilEstado` with `nombreUsuario: String? = null, nombreUsuarioError: String? = null`, and add:

```kotlin
    fun editarNombreUsuario(nuevo: String) {
        val uid = sesion.value.uid ?: return
        val anterior = _perfilEstado.value.nombreUsuario ?: return
        viewModelScope.launch {
            val exito = renombrarUsuario(usuarioId = uid, nombreUsuarioAnterior = anterior, nombreUsuarioNuevo = nuevo)
            _perfilEstado.update {
                if (exito) it.copy(nombreUsuario = nuevo, nombreUsuarioError = null)
                else it.copy(nombreUsuarioError = "Ese nombre de usuario ya está en uso")
            }
        }
    }
```

`perfilEstado.value.nombreUsuario` needs to be seeded from the current `Usuario` when the
`CuentaScreen` opens — add a `UsuarioRepository` (or a thin `ObservarUsuarioUseCase`) call in an
`init`/`LaunchedEffect`-driven load, consistent with how `sesion` is already sourced from
`AuthRepository.observeSesion()`. Confirm the simplest wiring against the actual current file
before adding a new use case just for this one read.

- [ ] **Step 8: Add the edit UI to `PerfilContenido`**

In `CuentaScreen.kt`'s `PerfilContenido` (`:133-185`), add an editable field below the name
`Text`, using the same `OutlinedTextField` + inline-error pattern already used in `RegistroForm`:

```kotlin
        Spacer(Modifier.height(8.dp))
        var nombreUsuarioEditado by rememberSaveable(perfilEstado.nombreUsuario) {
            mutableStateOf(perfilEstado.nombreUsuario.orEmpty())
        }
        OutlinedTextField(
            value = nombreUsuarioEditado,
            onValueChange = { nombreUsuarioEditado = it },
            label = { Text(stringResource(R.string.account_profile_nombre_usuario)) },
            isError = perfilEstado.nombreUsuarioError != null,
            supportingText = perfilEstado.nombreUsuarioError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { onEditarNombreUsuario(nombreUsuarioEditado) },
            enabled = nombreUsuarioEditado != perfilEstado.nombreUsuario,
        ) {
            Text(stringResource(R.string.account_profile_guardar_nombre_usuario))
        }
```

Add `perfilEstado: PerfilEstado` and `onEditarNombreUsuario: (String) -> Unit` to
`PerfilContenido`'s parameters, threaded from `CuentaScreen`'s existing `perfilEstado`
collection and `viewModel::editarNombreUsuario`. Add the two new strings to both
`feature/cuenta/src/main/res/values/strings.xml` and `values-en/strings.xml` (i18n gate).

- [ ] **Step 9: Run tests to verify they pass**

Run: `./gradlew :feature:cuenta:testDebugUnitTest lintDebug`
Expected: PASS, no `HardcodedText`/`MissingTranslation`.

- [ ] **Step 10: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/RenombrarUsuarioUseCase.kt \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/RenombrarUsuarioUseCaseTest.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt \
  feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt \
  feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaScreen.kt \
  feature/cuenta/src/main/res/values/strings.xml \
  feature/cuenta/src/main/res/values-en/strings.xml \
  feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt \
  feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt
git commit -m "feat(cuenta): edit nombreUsuario from the profile screen"
```

---

### Task 6: Miembros shows nombreUsuario instead of UID

**Files:**
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosViewModel.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt` or a new `PlanModule` `@Provides` for whichever use case is added
- Test: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosViewModelTest.kt` (create if it doesn't exist yet — confirm first)

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `resuelve el nombreUsuario de cada miembro del plan`() = runTest {
        val accesosRepo = FakeAccesoPlanRepository(
            accesos = listOf(AccesoPlan(usuarioId = "u1", planId = "p1", rol = Rol.EDITOR)),
        )
        val usuarioRepo = FakeUsuarioRepository(
            existentes = listOf(Usuario(id = "u1", nombreUsuario = "GatoAzul1")),
        )
        val viewModel = crearViewModel(accesosRepo = accesosRepo, usuarioRepo = usuarioRepo)

        val job = launch { viewModel.miembrosResueltos("p1").collect {} }
        advanceUntilIdle()

        assertEquals("GatoAzul1", viewModel.miembrosResueltos("p1").value.single().nombreUsuario)
        job.cancel()
    }
```

(Exact shape depends on `MiembrosViewModel`'s current signature — confirm whether
`observarMiembros` is being replaced or a new `miembrosResueltos` combining flow is added
alongside it once the file is open, and follow this codebase's `combine`/`flatMapLatest`
established pattern, e.g. `CategoriasViewModel`'s or `PlanesViewModel.resumenesPorPlan`'s shape.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.MiembrosViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Add a small domain model pairing `AccesoPlan` with its resolved `Usuario`**

```kotlin
package com.agoitdev.spenvo.domain.model

data class MiembroResuelto(
    val acceso: AccesoPlan,
    val usuario: Usuario?,
)
```

(`usuario: Usuario?` stays nullable for the cold-cache case the design calls out — `MiembroCard`
falls back to a loading placeholder rather than ever showing the raw UID.)

- [ ] **Step 4: Wire `UsuarioRepository` into `MiembrosViewModel`**

Add `private val usuarioRepository: UsuarioRepository` to the constructor and a resolving flow:

```kotlin
    fun miembrosResueltos(planId: String): StateFlow<List<MiembroResuelto>> =
        observarMiembros(planId)
            .map { accesos ->
                val usuarios = usuarioRepository.obtenerVarios(accesos.map { it.usuarioId })
                    .associateBy { it.id }
                accesos.map { acceso -> MiembroResuelto(acceso, usuarios[acceso.usuarioId]) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.MiembrosViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Update `MiembrosScreen`'s `MiembroCard`**

Switch `MiembrosScreen`'s collection from `observarMiembros` to `miembrosResueltos`, and update
`MiembroCard` (`MiembrosScreen.kt:134-146`):

```kotlin
@Composable
private fun MiembroCard(miembro: MiembroResuelto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = miembro.usuario?.nombreUsuario ?: stringResource(R.string.members_cargando),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = miembro.acceso.rol.name.lowercase() + " · " +
                    miembro.acceso.invitacionEstado.name.lowercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Add `members_cargando` ("Cargando…"/"Loading…") to both locale files (i18n gate).

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :feature:planes:testDebugUnitTest lintDebug`
Expected: PASS, no i18n errors.

- [ ] **Step 8: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/MiembroResuelto.kt \
  feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosViewModel.kt \
  feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt \
  feature/planes/src/main/res/values/strings.xml \
  feature/planes/src/main/res/values-en/strings.xml \
  feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosViewModelTest.kt
git commit -m "feat(planes): show nombreUsuario instead of raw UID in Miembros"
```

---

### Task 7: Invite by nombreUsuario or email — pending invitations, generic confirmation

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/ResolverIdentificadorUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/InvitacionPendienteRepository.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseInvitacionPendienteRepository.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/remote/dto/InvitacionPendienteDto.kt`
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCase.kt`
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCase.kt` (resolve pending invites on `paraVincularEmail`)
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosViewModel.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/PlanModule.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCaseTest.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCaseTest.kt`

- [ ] **Step 1: Write the failing test for identifier resolution + generic outcome**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitarMiembroUseCaseTest {

    @Test
    fun `invitar por nombreUsuario resuelto crea el AccesoPlan directo`() = runTest {
        val usuarioRepo = FakeUsuarioRepository(resolucionesNombreUsuario = mapOf("gatoazul1" to "u1"))
        val accesosRepo = FakeAccesoPlanRepository()
        val pendientesRepo = FakePendientesRepository()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "GatoAzul1", rol = Rol.EDITOR)

        assertEquals(listOf("u1"), accesosRepo.invitados.map { it.usuarioId })
        assertTrue(pendientesRepo.creadas.isEmpty())
    }

    @Test
    fun `invitar por email no resuelto crea una invitacion pendiente`() = runTest {
        val usuarioRepo = FakeUsuarioRepository()
        val accesosRepo = FakeAccesoPlanRepository()
        val pendientesRepo = FakePendientesRepository()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "familia@example.com", rol = Rol.VIEWER)

        assertTrue(accesosRepo.invitados.isEmpty())
        assertEquals(listOf("familia@example.com"), pendientesRepo.creadas.map { it.email })
    }

    @Test
    fun `invitar por nombreUsuario no resuelto no crea nada, sin lanzar`() = runTest {
        val usuarioRepo = FakeUsuarioRepository()
        val accesosRepo = FakeAccesoPlanRepository()
        val pendientesRepo = FakePendientesRepository()
        val useCase = InvitarMiembroUseCase(accesosRepo, usuarioRepo, pendientesRepo)

        useCase(planId = "p1", identificador = "NoExiste99", rol = Rol.VIEWER)

        assertTrue(accesosRepo.invitados.isEmpty())
        assertTrue(pendientesRepo.creadas.isEmpty())
    }
}
```

(`FakeUsuarioRepository(resolucionesNombreUsuario = ...)`, `FakeAccesoPlanRepository`,
`FakePendientesRepository` are small local fakes matching this test file's needs — write them
inline as `private class`es in the same file, following the established pattern.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.InvitarMiembroUseCaseTest"`
Expected: FAIL.

- [ ] **Step 3: Write `InvitacionPendienteRepository` + domain model**

```kotlin
package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.InvitacionPendiente

interface InvitacionPendienteRepository {
    suspend fun crear(invitacion: InvitacionPendiente)
    suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente>
    suspend fun eliminar(emailNormalizado: String, planId: String)
}
```

Add to `Entities.kt`:

```kotlin
data class InvitacionPendiente(
    val email: String,
    val planId: String,
    val rol: Rol,
    val invitadoPor: String,
    val createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 4: Rewrite `InvitarMiembroUseCase`**

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionEstado
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Rol
import com.agoitdev.spenvo.domain.model.normalizarEmail
import com.agoitdev.spenvo.domain.model.normalizarNombreUsuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository

class InvitarMiembroUseCase(
    private val accesosRepository: AccesoPlanRepository,
    private val usuarioRepository: UsuarioRepository,
    private val pendientesRepository: InvitacionPendienteRepository,
) {
    /** El resultado siempre es "invitación enviada" desde la UI, exista o no la cuenta. */
    suspend operator fun invoke(planId: String, identificador: String, rol: Rol) {
        val esEmail = identificador.contains('@')
        val usuarioId = if (esEmail) {
            usuarioRepository.resolverPorEmail(normalizarEmail(identificador))
        } else {
            usuarioRepository.resolverPorNombreUsuario(normalizarNombreUsuario(identificador))
        }

        if (usuarioId != null) {
            accesosRepository.invitarMiembro(
                AccesoPlan(usuarioId = usuarioId, planId = planId, rol = rol, invitacionEstado = InvitacionEstado.PENDIENTE),
            )
            return
        }

        if (esEmail) {
            pendientesRepository.crear(
                InvitacionPendiente(
                    email = normalizarEmail(identificador),
                    planId = planId,
                    rol = rol,
                    invitadoPor = "",
                ),
            )
        }
        // nombreUsuario no resuelto: se descarta silenciosamente, no hay cuenta "futura" que esperar.
    }
}
```

(`invitadoPor` needs the caller's own uid — thread it through from `MiembrosViewModel.invitar()`,
which already has access to the current session via `AuthRepository`, similar to how
`CategoriasViewModel.actualizar()` reads `authRepository.observeSesion().first().uid`.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.InvitarMiembroUseCaseTest"`
Expected: PASS.

- [ ] **Step 6: Write the failing pending-resolution test on `AsegurarUsuarioUseCase`**

Add to `AsegurarUsuarioUseCaseTest.kt`:

```kotlin
    @Test
    fun `al vincular email resuelve invitaciones pendientes para ese email`() = runTest {
        val usuarioRepo = FakeUsuarioRepository(existentes = listOf(Usuario(id = "u1", nombreUsuario = "GatoAzul1")))
        val accesosRepo = FakeAccesoPlanRepository()
        val pendientesRepo = FakePendientesRepository(
            existentes = listOf(InvitacionPendiente(email = "ana@example.com", planId = "p1", rol = Rol.EDITOR, invitadoPor = "u2")),
        )
        val useCase = AsegurarUsuarioUseCase(
            usuarioRepo, GenerarNombreUsuarioUnicoUseCase(usuarioRepo), accesosRepo, pendientesRepo,
        )

        useCase.paraVincularEmail(usuarioId = "u1", nombre = "Ana", email = "ana@example.com")

        assertEquals(listOf("u1"), accesosRepo.invitados.map { it.usuarioId })
        assertTrue(pendientesRepo.eliminadas.contains("ana@example.com" to "p1"))
    }
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.AsegurarUsuarioUseCaseTest"`
Expected: FAIL.

- [ ] **Step 8: Wire pending-resolution into `AsegurarUsuarioUseCase.paraVincularEmail`**

Add `accesosRepository: AccesoPlanRepository` and `pendientesRepository: InvitacionPendienteRepository`
to the constructor, and extend `paraVincularEmail`:

```kotlin
    suspend fun paraVincularEmail(usuarioId: String, nombre: String, email: String) {
        val existente = usuarioRepository.obtener(usuarioId)
            ?: Usuario(id = usuarioId, nombreUsuario = generarNombreUsuarioUnico(usuarioId))
        usuarioRepository.actualizar(existente.copy(nombre = nombre, email = email))
        val emailNormalizado = normalizarEmail(email)
        usuarioRepository.registrarIndiceEmail(usuarioId, emailNormalizado)

        pendientesRepository.obtenerPorEmail(emailNormalizado).forEach { pendiente ->
            accesosRepository.invitarMiembro(
                AccesoPlan(usuarioId = usuarioId, planId = pendiente.planId, rol = pendiente.rol, invitacionEstado = InvitacionEstado.PENDIENTE),
            )
            pendientesRepository.eliminar(emailNormalizado, pendiente.planId)
        }
    }
```

- [ ] **Step 9: Run test to verify it passes; update `UsuarioModule`'s `@Provides` for the new constructor params**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.AsegurarUsuarioUseCaseTest"` → PASS.

- [ ] **Step 10: Write `FirebaseInvitacionPendienteRepository` + `InvitacionPendienteDto`**

Follow the exact `UsuarioDto`/`FirebaseUsuarioRepository` shape from Task 3: `toMap`/`fromData`/
`fromDomain`, doc ID `"${emailNormalizado}_$planId"`, collection `invitaciones_pendientes_por_email`.
`obtenerPorEmail` needs a `whereEqualTo("email", emailNormalizado)` query (this is the one
intentional exception to "no queries" in this design — restricted by rules to the caller's own
verified email, per the design doc).

- [ ] **Step 11: Wire DI, update `MiembrosViewModel.invitar()` to pass the raw identifier field**

`MiembrosViewModel.invitar(planId, identificador, rol)` replaces the current `usuarioId` param
name/validation (`"El UID del usuario es obligatorio"` error text also needs updating to
something like "El nombre de usuario o email es obligatorio"). `InvitarDialog`'s text field label
(`members_invite_uid` string) needs renaming/rewording to reflect nombreUsuario-or-email input
(new string key, both locale files).

Regardless of outcome, `MiembrosViewModel.invitar()` always sets
`_estadoInvitar.value = InvitarEstado(invitado = true)` on success of the use case call itself
(not on whether a real account was found) — this is the generic-confirmation guarantee; write a
test asserting this explicitly (`invitar` with an identifier that resolves to nothing still marks
`invitado = true`).

- [ ] **Step 12: Full build + test check**

Run: `./gradlew :app:assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL, all green, no i18n errors.

- [ ] **Step 13: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/model/Entities.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/InvitacionPendienteRepository.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCase.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCase.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/remote/dto/InvitacionPendienteDto.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/remote/repository/FirebaseInvitacionPendienteRepository.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/PlanModule.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt \
  feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosViewModel.kt \
  feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt \
  feature/planes/src/main/res/values/strings.xml \
  feature/planes/src/main/res/values-en/strings.xml \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCaseTest.kt \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/AsegurarUsuarioUseCaseTest.kt
git commit -m "feat(planes): invite by nombreUsuario/email with generic confirmation and pending-by-email"
```

---

### Task 8: Anonymous Analytics signal for unresolved invites

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (or `core/data/build.gradle.kts`, whichever module ends up
  owning the Firebase Analytics dependency — put it in `:core:data`, matching where every other
  Firebase SDK usage already lives)
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AnalyticsRepository.kt`
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/analytics/FirebaseAnalyticsRepository.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt` (or a new
  `AnalyticsModule.kt`)
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCase.kt`
- Test: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCaseTest.kt`

- [ ] **Step 1: Add the dependency**

In `gradle/libs.versions.toml`, add under the existing Firebase entries (match the BOM's own
version — confirm the exact `firebase-analytics-ktx` artifact name against the current
`androidx.compose.bom`-style alias convention already used for `firebase-auth`/`firebase-firestore`
in this file before writing the exact toml key):

```toml
firebase-analytics = { module = "com.google.firebase:firebase-analytics-ktx" }
```

Add `libs.firebase.analytics` to `core/data/build.gradle.kts`'s `dependencies { }` block.

Run: `./gradlew :core:data:dependencies --write-locks`

- [ ] **Step 2: Write the failing test**

```kotlin
    @Test
    fun `invitar por nombreUsuario no resuelto dispara el evento anonimo`() = runTest {
        val usuarioRepo = FakeUsuarioRepository()
        val analytics = FakeAnalyticsRepository()
        val useCase = InvitarMiembroUseCase(FakeAccesoPlanRepository(), usuarioRepo, FakePendientesRepository(), analytics)

        useCase(planId = "p1", identificador = "NoExiste99", rol = Rol.VIEWER)

        assertEquals(listOf("invitacion_no_resuelta"), analytics.eventosRegistrados)
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.InvitarMiembroUseCaseTest"`
Expected: FAIL — `InvitarMiembroUseCase` doesn't take an `AnalyticsRepository` yet.

- [ ] **Step 4: Write `AnalyticsRepository` (domain) + Firebase impl (data)**

```kotlin
package com.agoitdev.spenvo.domain.repository

interface AnalyticsRepository {
    fun registrarEvento(nombre: String)
}
```

```kotlin
package com.agoitdev.spenvo.data.analytics

import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsRepository {
    override fun registrarEvento(nombre: String) {
        analytics.logEvent(nombre, null)
    }
}
```

- [ ] **Step 5: Wire DI**

```kotlin
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
```

(needs `@ApplicationContext context: Context` — confirm Hilt's context-injection convention
already used elsewhere in this codebase's `@Provides` methods, e.g. any existing
`@ApplicationContext`-consuming provider, before writing this exact signature.) Bind
`AnalyticsRepository` to `FirebaseAnalyticsRepository` via `@Binds`.

- [ ] **Step 6: Fire the event from `InvitarMiembroUseCase`**

Add `private val analyticsRepository: AnalyticsRepository` to the constructor, call
`analyticsRepository.registrarEvento("invitacion_no_resuelta")` in the nombreUsuario-not-found
branch and right before the pending-invite `crear()` call in the email branch (both "not
resolved" paths fire it — only the "resolved to a real account" path stays silent).

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.InvitarMiembroUseCaseTest"`
Expected: PASS.

- [ ] **Step 8: Full build check**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml gradle/lockfiles \
  core/data/build.gradle.kts core/data/gradle.lockfile \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AnalyticsRepository.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/analytics/FirebaseAnalyticsRepository.kt \
  core/data/src/main/java/com/agoitdev/spenvo/data/di/UsuarioModule.kt \
  core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCase.kt \
  core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/InvitarMiembroUseCaseTest.kt
git commit -m "feat(data): anonymous Analytics signal for unresolved invite attempts"
```

---

### Task 9: Firestore rules + rules-tests

**Files:**
- Modify: `firestore.rules`
- Modify: `rules-tests/rules.test.mjs`

- [ ] **Step 1: Write the failing rules tests**

Add to `rules-tests/rules.test.mjs`, following that file's existing setup/teardown and
`assertSucceeds`/`assertFails` helpers (confirm the exact test-app/auth-context helper names
against the file's current top before writing these, since this plan's reconnaissance didn't
capture its full content verbatim):

```javascript
describe("usuarios", () => {
  it("permite get de un doc conocido a cualquier autenticado", async () => {
    // seed usuarios/u1 as an unauthenticated admin context, then:
    await assertSucceeds(getDoc(doc(authedDb("u2"), "usuarios/u1")));
  });

  it("deniega list sobre la coleccion completa", async () => {
    await assertFails(getDocs(collection(authedDb("u2"), "usuarios")));
  });

  it("el dueno puede crear su propio doc con uid propio", async () => {
    await assertSucceeds(
      setDoc(doc(authedDb("u1"), "usuarios/u1"), { uid: "u1", nombreUsuario: "gatoazul1", createdAt: new Date(), updatedAt: new Date() })
    );
  });

  it("no se puede crear un doc de usuarios apuntando a otro uid", async () => {
    await assertFails(
      setDoc(doc(authedDb("u1"), "usuarios/u2"), { uid: "u2", nombreUsuario: "gatoazul1", createdAt: new Date(), updatedAt: new Date() })
    );
  });
});

describe("nombres_usuario", () => {
  it("permite get de un doc conocido a cualquier autenticado", async () => {
    await assertSucceeds(getDoc(doc(authedDb("u2"), "nombres_usuario/gatoazul1")));
  });

  it("deniega list sobre la coleccion completa", async () => {
    await assertFails(getDocs(collection(authedDb("u2"), "nombres_usuario")));
  });

  it("el dueno puede reservar un nombreUsuario para si mismo", async () => {
    await assertSucceeds(
      setDoc(doc(authedDb("u1"), "nombres_usuario/gatoazul1"), { usuarioId: "u1" })
    );
  });

  it("no se puede reservar un nombreUsuario apuntando a otro uid", async () => {
    await assertFails(
      setDoc(doc(authedDb("u1"), "nombres_usuario/gatoazul1"), { usuarioId: "u2" })
    );
  });

  it("no se puede sobrescribir una reserva existente", async () => {
    // seed nombres_usuario/gatoazul1 -> { usuarioId: "u1" } as admin, then:
    await assertFails(
      setDoc(doc(authedDb("u1"), "nombres_usuario/gatoazul1"), { usuarioId: "u1" })
    );
  });

  it("solo el dueno puede borrar su propia reserva", async () => {
    // seed nombres_usuario/gatoazul1 -> { usuarioId: "u1" } as admin, then:
    await assertFails(deleteDoc(doc(authedDb("u2"), "nombres_usuario/gatoazul1")));
    await assertSucceeds(deleteDoc(doc(authedDb("u1"), "nombres_usuario/gatoazul1")));
  });
});

describe("emails_usuario", () => {
  it("mismo comportamiento que nombres_usuario: get abierto, list denegado, create/delete solo dueno", async () => {
    await assertSucceeds(getDoc(doc(authedDb("u2"), "emails_usuario/ana@example.com")));
    await assertFails(getDocs(collection(authedDb("u2"), "emails_usuario")));
    await assertSucceeds(setDoc(doc(authedDb("u1"), "emails_usuario/ana@example.com"), { usuarioId: "u1" }));
    await assertFails(setDoc(doc(authedDb("u2"), "emails_usuario/otro@example.com"), { usuarioId: "u1" }));
  });
});

describe("invitaciones_pendientes_por_email", () => {
  it("cualquier autenticado puede crear una invitacion pendiente", async () => {
    await assertSucceeds(
      setDoc(doc(authedDb("u1"), "invitaciones_pendientes_por_email/ana@example.com_p1"), {
        email: "ana@example.com", planId: "p1", rol: "editor", invitadoPor: "u1", createdAt: new Date(),
      })
    );
  });

  it("list solo funciona filtrando por el propio email verificado", async () => {
    // seed the doc above as admin, then query as an auth context whose token.email is ana@example.com:
    await assertSucceeds(
      getDocs(query(collection(authedDbWithEmail("u2", "ana@example.com"), "invitaciones_pendientes_por_email"), where("email", "==", "ana@example.com")))
    );
    await assertFails(
      getDocs(query(collection(authedDb("u3"), "invitaciones_pendientes_por_email"), where("email", "==", "ana@example.com")))
    );
  });
});

describe("acceso_plan_financiero create — auto-resolucion de invitacion pendiente", () => {
  it("el propio invitado puede auto-otorgarse el rol exacto de su invitacion pendiente", async () => {
    // seed as admin: planes_financieros/p1, invitaciones_pendientes_por_email/ana@example.com_p1
    // -> { email: "ana@example.com", planId: "p1", rol: "editor", invitadoPor: "u2", createdAt }
    await assertSucceeds(
      setDoc(doc(authedDbWithEmail("u1", "ana@example.com"), "acceso_plan_financiero/u1_p1"), {
        usuarioId: "u1", planId: "p1", rol: "editor", invitacionEstado: "pendiente",
        createdAt: new Date(), updatedAt: new Date(),
      })
    );
  });

  it("no puede auto-otorgarse un rol distinto al de la invitacion pendiente", async () => {
    // same seed as above (rol: "editor" pending)
    await assertFails(
      setDoc(doc(authedDbWithEmail("u1", "ana@example.com"), "acceso_plan_financiero/u1_p1"), {
        usuarioId: "u1", planId: "p1", rol: "owner", invitacionEstado: "pendiente",
        createdAt: new Date(), updatedAt: new Date(),
      })
    );
  });

  it("no puede auto-otorgarse acceso sin una invitacion pendiente para su email verificado", async () => {
    await assertFails(
      setDoc(doc(authedDbWithEmail("u1", "otro@example.com"), "acceso_plan_financiero/u1_p1"), {
        usuarioId: "u1", planId: "p1", rol: "editor", invitacionEstado: "pendiente",
        createdAt: new Date(), updatedAt: new Date(),
      })
    );
  });
});
```

(`authedDbWithEmail` — a helper providing a Firestore instance whose auth context carries a
specific `token.email` — likely doesn't exist yet in this file; add it alongside the existing
`authedDb`-style helper, mirroring how the Firebase emulator test SDK's `initializeTestEnvironment`
auth-context options already used elsewhere in this file are constructed.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd rules-tests && npm test`
Expected: FAIL — none of the new rules exist yet.

- [ ] **Step 3: Update `firestore.rules`**

Replace the existing `usuarios/{usuarioId}` block and add the three new collections:

```
    // ---------- usuarios ----------
    match /usuarios/{usuarioId} {
      allow get: if isSignedIn();
      allow list: if false;
      allow create: if isSignedIn() && request.auth.uid == usuarioId
        && request.resource.data.uid == usuarioId;
      allow update: if isSignedIn() && request.auth.uid == usuarioId;
      allow delete: if false;
    }

    // ---------- nombres_usuario (lookup index, unicidad de nombreUsuario) ----------
    match /nombres_usuario/{nombreUsuarioNormalizado} {
      allow get: if isSignedIn();
      allow list: if false;
      allow create: if isSignedIn() && request.resource.data.usuarioId == request.auth.uid;
      allow update: if false;
      allow delete: if isSignedIn() && resource.data.usuarioId == request.auth.uid;
    }

    // ---------- emails_usuario (lookup index, resolucion de invitaciones) ----------
    match /emails_usuario/{emailNormalizado} {
      allow get: if isSignedIn();
      allow list: if false;
      allow create: if isSignedIn() && request.resource.data.usuarioId == request.auth.uid;
      allow update: if false;
      allow delete: if isSignedIn() && resource.data.usuarioId == request.auth.uid;
    }

    // ---------- invitaciones_pendientes_por_email ----------
    match /invitaciones_pendientes_por_email/{invitacionId} {
      allow get: if false;
      allow list: if isSignedIn()
        && request.query.where.size() == 1
        && request.query.where[0][0] == "email"
        && request.query.where[0][2] == request.auth.token.email;
      allow create: if isSignedIn();
      allow update: if false;
      allow delete: if isSignedIn()
        && resource.data.email == request.auth.token.email;
    }
```

(The `request.query.where` shape for restricting `list` to a single, caller-matching equality
filter is version-sensitive across Firestore rules releases — confirm the exact accessor syntax
against the Firebase docs/current SDK version pinned in this project before trusting this
verbatim; if the emulator rejects this exact form, the documented alternative is
`request.query.limit <= N && ...` combined with a custom claim, but the equality-filter form
above is the standard documented pattern as of this plan's writing.)

- [ ] **Step 3b: Fix `acceso_plan_financiero.create` — required for pending-invite auto-resolution
  to work at all**

Found during Task 7's review, not in the original plan: `AsegurarUsuarioUseCase.paraVincularEmail`
(Task 7) resolves a pending email invite by writing the new `AccesoPlan` **as the newly-registered
invitee themselves**, client-side, from their own session. The `acceso_plan_financiero.create`
rule that existed before this plan only allows a `create` when the caller is the plan's `owner`
(on plan creation) or already has `admin`+ access to that plan — neither is ever true for a
brand-new invitee. Without this fix, every pending-invite resolution write fails with
`PERMISSION_DENIED` once rules are enforced, silently breaking the entire "invite by email before
they've registered" feature this plan built in Task 7 — the write throws inside
`paraVincularEmail`'s `forEach`, which per Task 7's own known gap (see design doc's "orphaned
pending invites" note) leaves that invite permanently stuck pending with no retry.

Add a third disjunct to the existing `acceso_plan_financiero` `create` rule in `firestore.rules`
(read the current full rule first — it has two disjuncts today, `owner`-on-plan-creation and
`admin`+-invites-a-member; this adds a third, it does not replace either):

```
      allow create: if isSignedIn() && (
          (docId == request.auth.uid + '_' + request.resource.data.planId
            && request.resource.data.usuarioId == request.auth.uid
            && request.resource.data.rol == 'owner'
            && get(/databases/$(database)/documents/planes_financieros/$(request.resource.data.planId)).data.createdBy == request.auth.uid)
          || tieneRolMinimo(
              get(/databases/$(database)/documents/acceso_plan_financiero/$(request.auth.uid + '_' + request.resource.data.planId)),
              2
            )
          // Self-grant from a pending invite: only the exact role that was invited, only when a
          // matching invitaciones_pendientes_por_email doc exists for the caller's OWN verified
          // (Firebase Auth token) email — never a client-supplied one, so this can't be spoofed.
          || (docId == request.auth.uid + '_' + request.resource.data.planId
              && request.resource.data.usuarioId == request.auth.uid
              && request.auth.token.email != null
              && get(/databases/$(database)/documents/invitaciones_pendientes_por_email/$(request.auth.token.email.lower() + '_' + request.resource.data.planId)).data.rol == request.resource.data.rol)
        );
```

This intentionally does NOT also grant `delete` on the now-consumed
`invitaciones_pendientes_por_email` doc — `AsegurarUsuarioUseCase.paraVincularEmail`'s own
`pendientesRepository.eliminar(...)` call already needs (and per Step 3 above, already has) a
`delete` rule scoped to `resource.data.email == request.auth.token.email`, which the newly-granted
invitee satisfies once they're signed in with that email. No separate change needed there.

Run `cd rules-tests && npm test` after this step alone (before Step 4's broader pass) to confirm
the three new `acceso_plan_financiero` cases pass without breaking the two pre-existing
`acceso_plan_financiero` describe blocks already in `rules-tests/rules.test.mjs` from before this
plan (owner-creates-on-plan-creation, admin-invites-a-member) — this is a shared rule, regression
risk here is real.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd rules-tests && npm test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add firestore.rules rules-tests/rules.test.mjs
git commit -m "feat(rules): usuarios get/list split, nombres_usuario/emails_usuario indexes, pending invites"
```

---

### Task 10: Full verification + CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: All unit tests**

Run: `./gradlew testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all green project-wide.

- [ ] **Step 3: Instrumented tests (migration + any Compose UI tests touched)**

Run: `./gradlew :core:data:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL (requires a connected device/emulator).

- [ ] **Step 4: Lint**

Run: `./gradlew lintDebug --rerun-tasks`
Expected: BUILD SUCCESSFUL, no `HardcodedText`/`MissingTranslation` errors.

- [ ] **Step 5: detekt**

Run: `./gradlew detekt --rerun-tasks`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 6: Firestore rules tests**

Run: `cd rules-tests && npm test`
Expected: all green.

- [ ] **Step 7: Update `doc/database/schema.mdd`**

`usuarios` table changed (new `nombreUsuario` column, `nombre`/`email` now nullable) — update the
diagram/definition per AGENTS.md's schema-versioning gate.

- [ ] **Step 8: Update CHANGELOG.md**

Add under `## [Unreleased]` → `### Added`:

```markdown
- `Usuario` is now a live, synced entity instead of dead schema: a unique `nombreUsuario`
  (`Adjetivo+Sustantivo+número`, e.g. `GatoAzul42`) is generated on first anonymous sign-in and
  carried over on registration, editable from the profile screen. Miembros shows `nombreUsuario`
  instead of the raw Firebase UID, and inviting a member now accepts a `nombreUsuario` or email
  instead of a UID — an email that doesn't match an existing account becomes a pending invitation
  that resolves automatically once that person registers. The app never confirms or denies
  whether a given email/nombreUsuario belongs to a real account: Firestore rules split `get` from
  `list` on every identity-lookup collection (`usuarios`, `nombres_usuario`, `emails_usuario`),
  and an anonymous Firebase Analytics event (no email/nombreUsuario attached) gives visibility
  into unresolved invite attempts without ever logging what was searched.
```

- [ ] **Step 9: Commit**

```bash
git add CHANGELOG.md doc/database/schema.mdd
git commit -m "docs: changelog and schema entry for Usuario/nombreUsuario"
```

---

## Self-review notes (for whoever executes this plan)

- **Task 1** is fully precise for the domain/entity/mapper changes. The migration's
  rename→add→copy→drop dance for making `nombre`/`email` nullable is SQLite-standard but
  hasn't been run against this project's actual schema — verify column order/defaults survive
  by reading the migration test's assertions carefully once implemented, not just trusting it
  compiles.
- **Task 3, Step 5** flags an unconfirmed import path for the `await()` extension — this plan's
  reconnaissance saw it used (`import com.agoitdev.spenvo.data.remote.await`) but never opened
  that file directly; confirm before wiring.
- **Task 4, Step 8** is the plan's least certain step: whether `PlanesViewModel` needs a DI
  module change at all (most `@HiltViewModel`s in this codebase are pure constructor-injected,
  no `@Provides` needed) is flagged explicitly rather than guessed — read the file first.
- **Task 6** assumes `MiembrosViewModelTest.kt` may not exist yet (not found in this plan's
  reconnaissance, unlike every other feature module's ViewModel test) — if so, Step 1 creates it
  fresh rather than extending it; confirm before starting.
- **Task 7** is the largest, most interdependent task (`InvitarMiembroUseCase`,
  `AsegurarUsuarioUseCase`, and the new pending-invitation repository all change together) — do
  not split it across two agents/sessions without re-reading the current state of all three
  first, since a partial application would leave `InvitarMiembroUseCase`'s constructor and its
  only caller (`MiembrosViewModel`) out of sync mid-task.
- **Task 8's Step 5** flags an unconfirmed `@ApplicationContext` injection pattern — this
  codebase's exact convention for providing Android `Context` into a Hilt module wasn't
  captured during reconnaissance; confirm against any existing `@Provides` that already needs
  `Context` (if none exists, `FirebaseAnalytics.getInstance()` with no context argument is also a
  valid overload and may be simpler).
- **Task 9's Step 3** flags the `request.query.where` rules syntax as version-sensitive — this is
  the one piece of this entire plan resting on an assumption about the exact Firestore rules
  language accessor shape rather than a confirmed-in-this-codebase pattern (every other rule in
  `firestore.rules` uses simpler `resource.data`/`request.resource.data`/`get()` primitives, none
  use `request.query`). Verify against the Firebase Rules documentation for the SDK version this
  project's `firebase.json`/emulator config pins, and adjust if the exact accessor differs.
- Every task after Task 1 depends on the previous task's commit landing first — Task 1
  (domain+Room) blocks everything; Task 2 (generator+interface) blocks Task 3 (repository impl);
  Task 4 (AsegurarUsuarioUseCase) blocks Tasks 5–7 (all use the live `Usuario` doc it creates);
  Task 7 depends on Task 6's `MiembrosViewModel` changes already having landed cleanly, since
  both touch the same file's constructor.
