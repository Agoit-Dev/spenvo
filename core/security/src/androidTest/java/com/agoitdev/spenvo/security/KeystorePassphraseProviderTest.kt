package com.agoitdev.spenvo.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystorePassphraseProviderTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun loadSqlCipherLibrary() {
        System.loadLibrary("sqlcipher")
    }

    @Test
    fun genera_persiste_y_retorna_la_misma_passphrase() {
        clearPrefs()

        val first = provider().getOrCreate()
        val second = provider().getOrCreate()

        assertTrue(first.isNotEmpty())
        assertEquals(first.size, 44)
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun crear_insertar_cerrar_reabrir_leer_con_misma_passphrase() {
        clearPrefs()
        val dbFile = File(context.filesDir, "keystore-test.db")
        dbFile.delete()

        val passphrase = provider().getOrCreate()

        val db = SQLiteDatabase.openOrCreateDatabase(
            dbFile.absolutePath,
            String(passphrase).toByteArray(Charsets.UTF_8),
            null,
            null,
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS t (id INTEGER PRIMARY KEY, v TEXT)")
        db.execSQL("INSERT INTO t (v) VALUES (?)", arrayOf<Any>("hola"))
        db.close()

        val reopened = SQLiteDatabase.openOrCreateDatabase(
            dbFile.absolutePath,
            String(provider().getOrCreate()).toByteArray(Charsets.UTF_8),
            null,
            null,
        )
        val cursor = reopened.rawQuery("SELECT v FROM t", null)
        cursor.moveToFirst()
        assertEquals("hola", cursor.getString(0))
        cursor.close()
        reopened.close()
    }

    private fun provider(): PassphraseProvider = AndroidKeystorePassphraseProvider(context)

    private fun clearPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "spenvo_security"
    }
}