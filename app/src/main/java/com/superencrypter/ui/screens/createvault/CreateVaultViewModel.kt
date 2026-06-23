package com.superencrypter.ui.screens.createvault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.location.GeoPoint
import com.superencrypter.data.repository.SuperEncrypterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateVaultUiState(
    val name: String = "",
    val keyUri: Uri? = null,
    val keyFileName: String? = null,
    val keyFingerprint: String? = null,
    val geoLockEnabled: Boolean = false,
    val capturedLocation: GeoPoint? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdVaultId: Long? = null
)

class CreateVaultViewModel(
    private val repository: SuperEncrypterRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CreateVaultUiState())
    val state = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value, error = null) }
    }

    fun onGeoLockChange(enabled: Boolean) {
        _state.update {
            it.copy(
                geoLockEnabled = enabled,
                capturedLocation = if (enabled) it.capturedLocation else null,
                error = null
            )
        }
    }

    fun selectKey(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.readKeyInfo(uri) }
                .onSuccess { (name, fingerprint) ->
                    _state.update {
                        it.copy(
                            keyUri = uri,
                            keyFileName = name,
                            keyFingerprint = fingerprint,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Erro ao ler o arquivo-chave.") }
                }
        }
    }

    fun captureLocation() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val location = repository.currentLocation()
            _state.update {
                it.copy(
                    isLoading = false,
                    capturedLocation = location,
                    error = if (location == null) "Não foi possível capturar a localização atual." else null
                )
            }
        }
    }

    fun locationPermissionDenied() {
        _state.update {
            it.copy(
                geoLockEnabled = false,
                capturedLocation = null,
                error = "Permissão de localização negada. O GeoLock precisa do GPS para salvar o local da pasta."
            )
        }
    }

    fun createVault() {
        val current = state.value
        val keyUri = current.keyUri
        if (current.name.isBlank()) {
            _state.update { it.copy(error = "Informe um nome para a pasta segura.") }
            return
        }
        if (keyUri == null) {
            _state.update { it.copy(error = "Selecione um arquivo-chave.") }
            return
        }
        if (current.geoLockEnabled && current.capturedLocation == null) {
            _state.update { it.copy(error = "Ative o GeoLock somente depois de capturar a localização.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.createVault(
                    name = current.name,
                    keyUri = keyUri,
                    geoLockEnabled = current.geoLockEnabled,
                    location = current.capturedLocation
                )
            }.onSuccess { id ->
                _state.update { it.copy(isLoading = false, createdVaultId = id) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Erro ao criar pasta segura.") }
            }
        }
    }
}
