package com.superencrypter.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.superencrypter.data.local.dao.HistoryDao
import com.superencrypter.data.local.dao.VaultDao
import com.superencrypter.data.local.dao.VaultFileDao
import com.superencrypter.data.local.entity.HistoryEntity
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultFileEntity

@Database(
    entities = [VaultEntity::class, VaultFileEntity::class, HistoryEntity::class],
    version = 2,
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
            )
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vaults ADD COLUMN checkCipher TEXT")
                db.execSQL("ALTER TABLE vaults ADD COLUMN checkIv TEXT")
            }
        }
    }
}
