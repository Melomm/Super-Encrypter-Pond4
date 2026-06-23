package com.superencrypter.ui.screens.unlock

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.repository.SuperEncrypterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockUiState(
    val vault: VaultEntity? = null,
    val keyUri: Uri? = null,
    val keyFileName: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val unlocked: Boolean = false
)

class UnlockVaultViewModel(
    private val vaultId: Long,
    private val repository: SuperEncrypterRepository
) : ViewModel() {
    private val transient = MutableStateFlow(UnlockUiState())

    val state: StateFlow<UnlockUiState> = combine(
        repository.observeVault(vaultId),
        transient
    ) { vault, current -> current.copy(vault = vault) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnlockUiState())

    fun selectKey(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.readKeyInfo(uri) }
                .onSuccess { (name, _) ->
                    transient.update { it.copy(keyUri = uri, keyFileName = name, error = null) }
                }
                .onFailure { error ->
                    transient.update { it.copy(error = error.message ?: "Erro ao ler arquivo-chave.") }
                }
        }
    }

    fun unlockWithCurrentLocation() {
        unlock(fetchLocation = true)
    }

    fun unlockWithoutLocation() {
        unlock(fetchLocation = false)
    }

    private fun unlock(fetchLocation: Boolean) {
        val keyUri = state.value.keyUri
        if (keyUri == null) {
            transient.update { it.copy(error = "Selecione o arquivo-chave.") }
            return
        }
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null) }
            val location = if (fetchLocation) repository.currentLocation() else null
            runCatching { repository.unlockVault(vaultId, keyUri, location) }
                .onSuccess {
                    transient.update { it.copy(isLoading = false, unlocked = true) }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Não foi possível destrancar.") }
                }
        }
    }

    fun locationPermissionDenied() {
        transient.update {
            it.copy(error = "Permissão de localização negada. Esta pasta usa GeoLock e precisa do GPS.")
        }
    }
}
