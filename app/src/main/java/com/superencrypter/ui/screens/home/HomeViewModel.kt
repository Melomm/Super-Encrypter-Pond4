package com.superencrypter.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.repository.SuperEncrypterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HomeVaultItem(
    val id: Long,
    val name: String,
    val fileCount: Int,
    val isUnlocked: Boolean,
    val geoLockEnabled: Boolean
)

data class HomeActionState(
    val selectedVaultIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class HomeViewModel(private val repository: SuperEncrypterRepository) : ViewModel() {
    private val _actionState = MutableStateFlow(HomeActionState())
    val actionState: StateFlow<HomeActionState> = _actionState.asStateFlow()

    private val _bulkExportedFile = MutableStateFlow<File?>(null)
    val bulkExportedFile: StateFlow<File?> = _bulkExportedFile.asStateFlow()

    val vaults = combine(
        repository.observeVaultSummaries(),
        repository.unlockedVaults
    ) { rows, unlocked ->
        rows.map {
            HomeVaultItem(
                id = it.id,
                name = it.name,
                fileCount = it.fileCount,
                isUnlocked = unlocked.contains(it.id),
                geoLockEnabled = it.geoLockEnabled
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startSelection(vaultId: Long) {
        _actionState.update {
            it.copy(isSelectionMode = true, selectedVaultIds = setOf(vaultId), message = null, error = null)
        }
    }

    fun enterSelectionMode() {
        _actionState.update { it.copy(isSelectionMode = true, message = null, error = null) }
    }

    fun toggleSelectAll() {
        val allIds = vaults.value.map { it.id }.toSet()
        _actionState.update { current ->
            val selected = if (current.selectedVaultIds.containsAll(allIds) && allIds.isNotEmpty()) {
                emptySet()
            } else {
                allIds
            }
            current.copy(
                isSelectionMode = selected.isNotEmpty(),
                selectedVaultIds = selected,
                message = null,
                error = null
            )
        }
    }

    fun toggleSelection(vaultId: Long) {
        _actionState.update { current ->
            val next = if (current.selectedVaultIds.contains(vaultId)) {
                current.selectedVaultIds - vaultId
            } else {
                current.selectedVaultIds + vaultId
            }
            current.copy(
                isSelectionMode = next.isNotEmpty(),
                selectedVaultIds = next,
                message = null,
                error = null
            )
        }
    }

    fun clearSelection() {
        _actionState.update {
            it.copy(isSelectionMode = false, selectedVaultIds = emptySet(), message = null, error = null)
        }
    }

    fun lockVault(vaultId: Long) {
        viewModelScope.launch {
            repository.lockVault(vaultId)
        }
    }

    fun lockSelectedVaults() {
        val selected = actionState.value.selectedVaultIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.lockVaults(selected) }
                .onSuccess {
                    _actionState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedVaultIds = emptySet(),
                            message = "${selected.size} pasta(s) trancada(s).",
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _actionState.update { it.copy(error = error.message ?: "Erro ao trancar pastas.") }
                }
        }
    }

    fun shareSelectedVaults() {
        val selected = actionState.value.selectedVaultIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _actionState.update { it.copy(isDeleting = true, error = null, message = null) }
            runCatching { repository.exportVaults(selected) }
                .onSuccess { file ->
                    _bulkExportedFile.value = file
                    _actionState.update {
                        it.copy(
                            isSelectionMode = false,
                            selectedVaultIds = emptySet(),
                            isDeleting = false,
                            message = "${selected.size} pasta(s) pronta(s) para compartilhar."
                        )
                    }
                }
                .onFailure { error ->
                    _actionState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Erro ao compartilhar pastas."
                        )
                    }
                }
        }
    }

    fun shareIntentFor(file: File) = repository.shareIntentFor(file)

    fun bulkExportConsumed() {
        _bulkExportedFile.value = null
    }

    fun deleteSelectedVaults() {
        val selected = actionState.value.selectedVaultIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _actionState.update { it.copy(isDeleting = true, error = null, message = null) }
            runCatching { repository.deleteVaults(selected) }
                .onSuccess {
                    _actionState.update {
                        it.copy(
                            selectedVaultIds = emptySet(),
                            isSelectionMode = false,
                            isDeleting = false,
                            message = "${selected.size} pasta(s) removida(s)."
                        )
                    }
                }
                .onFailure { error ->
                    _actionState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Erro ao remover pastas."
                        )
                    }
                }
        }
    }
}
