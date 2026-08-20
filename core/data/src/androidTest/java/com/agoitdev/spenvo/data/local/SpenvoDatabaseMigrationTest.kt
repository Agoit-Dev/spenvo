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

    companion object {
        private const val TEST_DB = "migration-test-v1-to-v2"
    }
}