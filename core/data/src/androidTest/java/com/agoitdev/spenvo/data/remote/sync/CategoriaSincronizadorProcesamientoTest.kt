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
    fun documento_sin_edicion_pendiente_se_aplica() = runTest {
        procesarSnapshotCategorias(db, db.categoriaDao(), registroEdiciones, registroConflictos, listOf(categoria()))

        assertEquals("Comida", db.categoriaDao().get("p1:comida")?.nombre)
    }

    @Test
    fun conflicto_genuino_no_se_aplica_y_se_registra_con_la_version_local_reconstruida_de_Room() = runTest {
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
    fun marcador_huerfano_sin_fila_local_se_limpia_y_se_aplica_el_remoto_sin_crash() = runTest {
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
