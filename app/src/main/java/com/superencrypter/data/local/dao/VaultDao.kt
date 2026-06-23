package com.superencrypter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vaults ORDER BY createdAt DESC")
    fun observeVaults(): Flow<List<VaultEntity>>

    @Query(
        """
        SELECT vaults.*, COUNT(vault_files.id) AS fileCount
        FROM vaults
        LEFT JOIN vault_files ON vault_files.vaultId = vaults.id
        GROUP BY vaults.id
        ORDER BY vaults.createdAt DESC
        """
    )
    fun observeVaultSummaries(): Flow<List<VaultSummaryRow>>

    @Query("SELECT * FROM vaults WHERE id = :id")
    fun observeVault(id: Long): Flow<VaultEntity?>

    @Query("SELECT * FROM vaults WHERE id = :id")
    suspend fun getVault(id: Long): VaultEntity?

    @Insert
    suspend fun insert(vault: VaultEntity): Long

    @Query("DELETE FROM vaults WHERE id = :id")
    suspend fun deleteVault(id: Long)

    @Query("DELETE FROM vaults WHERE id IN (:ids)")
    suspend fun deleteVaults(ids: List<Long>)
}
