package com.superencrypter.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.repository.PreviewContent
import com.superencrypter.data.repository.SuperEncrypterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val isLoading: Boolean = true,
    val content: PreviewContent? = null,
    val error: String? = null
)

class ViewerViewModel(
    private val vaultId: Long,
    private val fileId: Long,
    private val repository: SuperEncrypterRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ViewerUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.previewFile(vaultId, fileId) }
                .onSuccess { content ->
                    _state.update { it.copy(isLoading = false, content = content) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Erro ao abrir preview.") }
                }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { repository.clearPreviewCache() }
        super.onCleared()
    }
}
