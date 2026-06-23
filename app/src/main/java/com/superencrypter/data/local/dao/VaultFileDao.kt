package com.superencrypter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.superencrypter.data.local.entity.VaultFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    fun observeFiles(vaultId: Long): Flow<List<VaultFileEntity>>

    @Query("SELECT COUNT(*) FROM vault_files WHERE vaultId = :vaultId")
    fun observeFileCount(vaultId: Long): Flow<Int>

    @Query("SELECT * FROM vault_files WHERE id = :id")
    suspend fun getFile(id: Long): VaultFileEntity?

    @Query("SELECT * FROM vault_files WHERE id IN (:ids)")
    suspend fun getFilesByIds(ids: List<Long>): List<VaultFileEntity>

    @Query("SELECT * FROM vault_files WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    suspend fun getFiles(vaultId: Long): List<VaultFileEntity>

    @Insert
    suspend fun insert(file: VaultFileEntity): Long

    @Query("UPDATE vault_files SET vaultId = :targetVaultId, encryptedPath = :encryptedPath, iv = :iv WHERE id = :fileId")
    suspend fun moveFile(fileId: Long, targetVaultId: Long, encryptedPath: String, iv: String)

    @Query("DELETE FROM vault_files WHERE id IN (:ids)")
    suspend fun deleteFiles(ids: List<Long>)
}
