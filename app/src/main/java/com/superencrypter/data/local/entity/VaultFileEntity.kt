package com.superencrypter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_files",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaultId")]
)
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultId: Long,
    val originalName: String,
    val mimeType: String,
    val encryptedPath: String,
    val iv: String,
    val sha256: String,
    val virusTotalStatus: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)
