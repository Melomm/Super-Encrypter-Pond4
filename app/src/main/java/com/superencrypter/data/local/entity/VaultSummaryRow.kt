package com.superencrypter.data.local.entity

data class VaultSummaryRow(
    val id: Long,
    val name: String,
    val keyHash: String,
    val keyFingerprint: String,
    val salt: String,
    val geoLockEnabled: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Double,
    val createdAt: Long,
    val fileCount: Int
)
