package com.superencrypter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaults")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keyHash: String,
    val keyFingerprint: String,
    val salt: String,
    val geoLockEnabled: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Double,
    val checkCipher: String? = null,
    val checkIv: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
