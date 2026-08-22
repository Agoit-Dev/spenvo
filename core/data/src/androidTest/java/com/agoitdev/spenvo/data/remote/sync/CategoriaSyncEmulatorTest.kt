package com.agoitdev.spenvo.data.remote.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agoitdev.spenvo.data.local.SpenvoDatabase
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.remote.await
import com.agoitdev.spenvo.data.remote.dto.CategoriaDto
import com.agoitdev.spenvo.data.remote.repository.FirebaseCategoriaRepository
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the category repository (optimistic Room-first) and the sync listener
 * against the local Firestore Emulator (see `firebase.json`, port 8081, rules
 * `firestore.emulator.rules` allow-all). Start the emulator before running:
 * `firebase emulators:start --only firestore --project spenvo-dev --rules core/data/src/androidTest/firestore.emulator.rules`
 */
@RunWith(AndroidJUnit4::class)
class CategoriaSyncEmulatorTest {

    private lateinit var db: SpenvoDatabase
    private lateinit var dao: CategoriaDao
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repo: FirebaseCategoriaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = FirebaseOptions.Builder()
            .setApplicationId("com.agoitdev.spenvo")
            .setApiKey("test-api-key")
            .setProjectId("spenvo-dev")
            .build()
        val app = FirebaseApp.initializeApp(
            context,
            options,
            "emulator-${System.currentTimeMillis()}",
        )!!
        firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("10.0.2.2", 8081)
        db = Room.inMemoryDatabaseBuilder(context, SpenvoDatabase::class.java).build()
        dao = db.categoriaDao()
        repo = FirebaseCategoriaRepository(firestore, dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun crearCategoria_escribe_firestore_y_room() = runBlocking {
        val categoria = Categoria(
            id = "p1:gasto_comida",
            planId = "p1",
            nombre = "Comida",
            icono = "comida",
            tipo = TipoCategoria.GASTO,
        )

        repo.crearCategoria(categoria)

        val doc = firestore.collection("categorias").document(categoria.id).get().await()
        assertEquals("Comida", doc.data?.get("nombre"))
        assertEquals(listOf("p1:gasto_comida"), dao.observeByPlan("p1").first().map { it.id })
    }

    @Test
    fun crearCategoria_errores_permanentes_rollback_room() = runBlocking {
        val categoria = Categoria(
            id = "",
            planId = "p1",
            nombre = "Invalida",
            icono = "default",
            tipo = TipoCategoria.GASTO,
        )

        var lanzado = false
        try {
            repo.crearCategoria(categoria)
        } catch (e: Exception) {
            lanzado = true
        }

        assertTrue(lanzado)
        assertEquals(0, dao.observeByPlan("p1").first().size)
    }

    @Test
    fun sincronizador_upserta_categorias_de_firestore() = runBlocking {
        val sincronizador = CategoriaSincronizador(firestore, dao)
        val job = launch { sincronizador.sincronizar("p1").collect { } }

        val categoria = Categoria(
            id = "p1:ingreso_salario",
            planId = "p1",
            nombre = "Salario",
            icono = "sueldo",
            tipo = TipoCategoria.INGRESO,
        )
        firestore.collection("categorias").document(categoria.id)
            .set(CategoriaDto.fromDomain(categoria).toMap())
            .await()

        kotlinx.coroutines.delay(2_000)

        job.cancel()
        val ids = dao.observeByPlan("p1").first().map { it.id }
        assertEquals(listOf("p1:ingreso_salario"), ids)
    }

    @Test
    fun sincronizador_respeta_soft_delete() = runBlocking {
        val sincronizador = CategoriaSincronizador(firestore, dao)
        val job = launch { sincronizador.sincronizar("p1").collect { } }

        val categoria = Categoria(
            id = "p1:gasto_ropa",
            planId = "p1",
            nombre = "Ropa",
            icono = "ropa",
            tipo = TipoCategoria.GASTO,
            deletedAt = java.time.Instant.now(),
        )
        firestore.collection("categorias").document(categoria.id)
            .set(CategoriaDto.fromDomain(categoria).toMap())
            .await()

        kotlinx.coroutines.delay(2_000)

        job.cancel()
        assertEquals(0, dao.observeByPlan("p1").first().size)
    }
}
