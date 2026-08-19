package com.agoitdev.spenvo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SpenvoDatabase : RoomDatabase() {

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val DATABASE_NAME = "spenvo.db"

        fun build(context: Context, passphrase: CharArray): SpenvoDatabase {
            return Room.databaseBuilder(context, SpenvoDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(String(passphrase).toByteArray(Charsets.UTF_8)))
                .build()
        }
    }
}
