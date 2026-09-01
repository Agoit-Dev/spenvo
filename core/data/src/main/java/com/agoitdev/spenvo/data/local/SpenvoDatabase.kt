package com.agoitdev.spenvo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agoitdev.spenvo.data.local.converter.Converters
import com.agoitdev.spenvo.data.local.dao.AccesoPlanDao
import com.agoitdev.spenvo.data.local.dao.CategoriaDao
import com.agoitdev.spenvo.data.local.dao.ConflictoEdicionDao
import com.agoitdev.spenvo.data.local.dao.EdicionPendienteDao
import com.agoitdev.spenvo.data.local.dao.GastoDao
import com.agoitdev.spenvo.data.local.dao.IngresoDao
import com.agoitdev.spenvo.data.local.dao.PlanFinancieroDao
import com.agoitdev.spenvo.data.local.dao.UsuarioDao
import com.agoitdev.spenvo.data.local.entity.AccesoPlanEntity
import com.agoitdev.spenvo.data.local.entity.CategoriaEntity
import com.agoitdev.spenvo.data.local.entity.ConflictoEdicionEntity
import com.agoitdev.spenvo.data.local.entity.EdicionPendienteEntity
import com.agoitdev.spenvo.data.local.entity.GastoEntity
import com.agoitdev.spenvo.data.local.entity.IngresoEntity
import com.agoitdev.spenvo.data.local.entity.PlanFinancieroEntity
import com.agoitdev.spenvo.data.local.entity.UsuarioEntity
import com.agoitdev.spenvo.security.PassphraseProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usuarios` (" +
                        "`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                        "`avatarUrl` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `planes_financieros` (" +
                        "`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `descripcion` TEXT, `moneda` TEXT NOT NULL, " +
                        "`createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`editedBy` TEXT, `editedAt` INTEGER, `deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `acceso_plan_financiero` (" +
                        "`usuarioId` TEXT NOT NULL, `planId` TEXT NOT NULL, " +
                        "`rol` TEXT NOT NULL, `invitacionEstado` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`usuarioId`, `planId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categorias` (" +
                        "`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `nombre` TEXT NOT NULL, " +
                        "`icono` TEXT NOT NULL, `iconoUrl` TEXT, `tipo` TEXT NOT NULL, " +
                        "`editedBy` TEXT, `editedAt` INTEGER, `deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_categorias_planId_tipo` " +
                        "ON `categorias` (`planId`, `tipo`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gastos` (" +
                        "`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `categoriaId` TEXT NOT NULL, " +
                        "`montoUnidadesMenores` INTEGER NOT NULL, `fecha` TEXT NOT NULL, `descripcion` TEXT, " +
                        "`creadoPor` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`editedBy` TEXT, `editedAt` INTEGER, `deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gastos_planId_fecha` ON `gastos` (`planId`, `fecha`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ingresos` (" +
                        "`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `categoriaId` TEXT NOT NULL, " +
                        "`montoUnidadesMenores` INTEGER NOT NULL, `fecha` TEXT NOT NULL, `descripcion` TEXT, " +
                        "`creadoPor` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`editedBy` TEXT, `editedAt` INTEGER, `deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingresos_planId_fecha` ON `ingresos` (`planId`, `fecha`)")
            }
        }

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
            // SQLCipher 4.x no carga la lib nativa automáticamente; el consumidor
            // debe cargarla explícitamente antes de abrir la DB.
            System.loadLibrary("sqlcipher")
            val passphrase = passphraseProvider.getOrCreate()
            return Room.databaseBuilder(context, SpenvoDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(String(passphrase).toByteArray(Charsets.UTF_8)))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
