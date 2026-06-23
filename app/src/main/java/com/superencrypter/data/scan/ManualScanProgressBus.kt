package com.superencrypter.data.scan

import com.superencrypter.data.remote.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ManualScanProgress(
    val isRunning: Boolean = false,
    val statusText: String? = null,
    val result: ScanResult? = null,
    val error: String? = null,
    val completedEventId: Long = 0L
)

object ManualScanProgressBus {
    private val _state = MutableStateFlow(ManualScanProgress())
    val state = _state.asStateFlow()

    fun started() {
        _state.value = ManualScanProgress(
            isRunning = true,
            statusText = "Enviando arquivo"
        )
    }

    fun status(statusText: String) {
        _state.value = _state.value.copy(
            isRunning = true,
            statusText = statusText,
            error = null
        )
    }

    fun completed(result: ScanResult) {
        _state.value = ManualScanProgress(
            isRunning = false,
            statusText = "Analise concluida",
            result = result,
            completedEventId = System.currentTimeMillis()
        )
    }

    fun failed(message: String) {
        _state.value = ManualScanProgress(
            isRunning = false,
            error = message,
            completedEventId = System.currentTimeMillis()
        )
    }
}
