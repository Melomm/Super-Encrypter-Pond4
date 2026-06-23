package com.superencrypter.ui.screens.manualscan

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.remote.ScanHistoryItem
import com.superencrypter.data.remote.ScanResult
import com.superencrypter.data.repository.SuperEncrypterRepository
import com.superencrypter.data.scan.ManualScanProgressBus
import com.superencrypter.data.scan.ManualScanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualScanUiState(
    val isLoading: Boolean = false,
    val scanStatusText: String? = null,
    val result: ScanResult? = null,
    val history: List<ScanHistoryItem> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val isClearingHistory: Boolean = false,
    val isFullHistory: Boolean = false,
    val historyError: String? = null,
    val error: String? = null
)

class ManualScanViewModel(
    private val repository: SuperEncrypterRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ManualScanUiState())
    val state = _state.asStateFlow()
    private var lastHandledScanEventId = 0L

    init {
        loadRecentHistory()
        viewModelScope.launch {
            ManualScanProgressBus.state.collect { progress ->
                _state.update {
                    it.copy(
                        isLoading = progress.isRunning,
                        scanStatusText = progress.statusText,
                        result = progress.result,
                        error = progress.error
                    )
                }
                if (progress.completedEventId != 0L && progress.completedEventId != lastHandledScanEventId) {
                    lastHandledScanEventId = progress.completedEventId
                    loadRecentHistory()
                }
            }
        }
    }

    fun scan(context: Context, uri: Uri) {
        runCatching {
            ContextCompat.startForegroundService(
                context.applicationContext,
                ManualScanService.intent(context.applicationContext, uri)
            )
        }.onSuccess {
            _state.update {
                it.copy(
                    isLoading = true,
                    scanStatusText = "Iniciando scan em segundo plano",
                    result = null,
                    error = null
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isLoading = false,
                    scanStatusText = null,
                    error = error.message ?: "Erro ao iniciar scan em segundo plano."
                )
            }
        }
    }
    fun loadRecentHistory() {
        loadHistory(limit = 3, isFullHistory = false)
    }

    fun loadFullHistory() {
        loadHistory(limit = null, isFullHistory = true)
    }

    fun clearHistory() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingHistory = true, historyError = null) }
            runCatching { repository.clearScanHistory() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            history = emptyList(),
                            isClearingHistory = false,
                            isFullHistory = false,
                            historyError = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isClearingHistory = false,
                            historyError = error.message ?: "Erro ao limpar histórico de scans."
                        )
                    }
                }
        }
    }

    private fun loadHistory(limit: Int?, isFullHistory: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isHistoryLoading = true, historyError = null) }
            runCatching { repository.scanHistory(limit) }
                .onSuccess { history ->
                    _state.update {
                        it.copy(
                            history = history,
                            isHistoryLoading = false,
                            isFullHistory = isFullHistory,
                            historyError = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isHistoryLoading = false,
                            historyError = error.message ?: "Erro ao carregar histórico de scans."
                        )
                    }
                }
        }
    }
}
