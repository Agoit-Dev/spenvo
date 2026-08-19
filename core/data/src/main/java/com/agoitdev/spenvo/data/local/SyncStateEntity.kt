package com.agoitdev.spenvo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val lastSyncedAtMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}
