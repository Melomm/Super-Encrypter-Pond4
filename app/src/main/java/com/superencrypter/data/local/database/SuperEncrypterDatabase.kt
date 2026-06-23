package com.superencrypter.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.superencrypter.data.local.dao.HistoryDao
import com.superencrypter.data.local.dao.VaultDao
import com.superencrypter.data.local.dao.VaultFileDao
import com.superencrypter.data.local.entity.HistoryEntity
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultFileEntity

@Database(
    entities = [VaultEntity::class, VaultFileEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SuperEncrypterDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun vaultFileDao(): VaultFileDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): SuperEncrypterDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SuperEncrypterDatabase::class.java,
                "super_encrypter.db"
            ).build()
    }
}
