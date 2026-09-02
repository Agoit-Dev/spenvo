package com.agoitdev.spenvo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpenvoDatabaseMigrationTest {

    private val passphrase = "test-passphrase".toByteArray(Charsets.UTF_8)

    @Before
    fun loadSqlCipherLibrary() {
        System.loadLibrary("sqlcipher")
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SpenvoDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(passphrase),
    )

    @Test
    fun migra_de_v1_a_v2_creando_tablas_nuevas() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO sync_state (id, lastSyncedAtMillis) VALUES ('default', 123456)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, SpenvoDatabase.MIGRATION_1_2)

        db.query("SELECT lastSyncedAtMillis FROM sync_state WHERE id = 'default'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(123456, cursor.getLong(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('usuarios', 'planes_financieros', 'acceso_plan_financiero', 'categorias', 'gastos', 'ingresos')")
            .use { cursor ->
                val tables = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(
                    setOf("usuarios", "planes_financieros", "acceso_plan_financiero", "categorias", "gastos", "ingresos"),
                    tables,
                )
            }
        db.close()
    }

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

    @Test
    fun migra_de_v3_a_v4_creando_tablas_de_conflictos() {
        helper.createDatabase(TEST_DB_V3_V4, 3).use { db ->
            db.execSQL(
                "INSERT INTO usuarios (id, nombreUsuario, nombre, email, avatarUrl, createdAt, updatedAt) " +
                    "VALUES ('u1', 'GatoAzul42', 'Ana', 'ana@example.com', NULL, 123456, 123456)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_V3_V4, 4, true, SpenvoDatabase.MIGRATION_3_4)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('ediciones_pendientes', 'conflictos_pendientes')")
            .use { cursor ->
                val tables = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(setOf("ediciones_pendientes", "conflictos_pendientes"), tables)
            }
        db.query("SELECT COUNT(*) FROM ediciones_pendientes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM conflictos_pendientes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT nombre, email, nombreUsuario FROM usuarios WHERE id = 'u1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Ana", cursor.getString(0))
            assertEquals("ana@example.com", cursor.getString(1))
            assertEquals("GatoAzul42", cursor.getString(2))
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test-v1-to-v2"
        private const val TEST_DB_V2_V3 = "migration-test-v2-to-v3"
        private const val TEST_DB_V3_V4 = "migration-test-v3-to-v4"
    }
}
