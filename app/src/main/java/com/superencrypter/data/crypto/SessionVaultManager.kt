package com.superencrypter.data.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey

class SessionVaultManager {
    private val keys = mutableMapOf<Long, SecretKey>()
    private val _unlockedVaults = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedVaults: StateFlow<Set<Long>> = _unlockedVaults.asStateFlow()

    fun unlock(vaultId: Long, key: SecretKey) {
        keys[vaultId] = key
        _unlockedVaults.value = keys.keys.toSet()
    }

    fun lock(vaultId: Long) {
        keys.remove(vaultId)
        _unlockedVaults.value = keys.keys.toSet()
    }

    fun keyFor(vaultId: Long): SecretKey? = keys[vaultId]

    fun isUnlocked(vaultId: Long): Boolean = keys.containsKey(vaultId)
}
