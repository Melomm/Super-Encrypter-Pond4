package com.superencrypter.ui.screens.vaultdetails

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultFileEntity
import com.superencrypter.data.file.PickedFileInfo
import com.superencrypter.data.file.PickedFileThumbnail
import com.superencrypter.data.repository.DecryptedFileShare
import com.superencrypter.data.repository.FileThumbnail
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

data class FileMoveTarget(
    val id: Long,
    val name: String,
    val isUnlocked: Boolean
)

data class VaultDetailsUiState(
    val vault: VaultEntity? = null,
    val files: List<VaultFileEntity> = emptyList(),
    val moveTargets: List<FileMoveTarget> = emptyList(),
    val thumbnails: Map<Long, FileThumbnail> = emptyMap(),
    val pendingImportFiles: List<PickedFileInfo> = emptyList(),
    val selectedImportUris: Set<String> = emptySet(),
    val pendingImportThumbnails: Map<String, PickedFileThumbnail> = emptyMap(),
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = false,
    val selectedFileIds: Set<Long> = emptySet(),
    val isFileSelectionMode: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false
)

class VaultDetailsViewModel(
    private val vaultId: Long,
    private val repository: SuperEncrypterRepository
) : ViewModel() {
    private val transient = MutableStateFlow(VaultDetailsUiState())
    private val loadingThumbnails = mutableSetOf<Long>()

    val state: StateFlow<VaultDetailsUiState> = combine(
        repository.observeVault(vaultId),
        repository.observeFiles(vaultId),
        repository.unlockedVaults,
        repository.observeVaultSummaries(),
        transient
    ) { vault, files, unlocked, summaries, current ->
        val fileIds = files.map { it.id }.toSet()
        val unlockedCurrentVault = unlocked.contains(vaultId)
        current.copy(
            vault = vault,
            files = files,
            thumbnails = if (unlockedCurrentVault) current.thumbnails.filterKeys { it in fileIds } else emptyMap(),
            isUnlocked = unlockedCurrentVault,
            moveTargets = summaries
                .filter { it.id != vaultId }
                .map { FileMoveTarget(id = it.id, name = it.name, isUnlocked = unlocked.contains(it.id)) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultDetailsUiState())

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile = _exportedFile.asStateFlow()

    private val _filesExportedFile = MutableStateFlow<File?>(null)
    val filesExportedFile = _filesExportedFile.asStateFlow()

    private val _decryptedShare = MutableStateFlow<DecryptedFileShare?>(null)
    val decryptedShare = _decryptedShare.asStateFlow()

    fun loadVisibleThumbnails() {
        val current = state.value
        if (!current.isUnlocked) return
        current.files
            .filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
            .filter { current.thumbnails[it.id] == null }
            .forEach { file ->
                if (!loadingThumbnails.add(file.id)) return@forEach
                viewModelScope.launch {
                    val thumbnail = repository.thumbnailForFile(vaultId, file.id)
                    if (thumbnail != null) {
                        transient.update { currentState ->
                            currentState.copy(thumbnails = currentState.thumbnails + (file.id to thumbnail))
                        }
                    }
                    loadingThumbnails.remove(file.id)
                }
            }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.importFile(vaultId, uri) }
                .onSuccess {
                    transient.update { it.copy(isLoading = false, message = "Arquivo protegido com sucesso.") }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao importar arquivo.") }
                }
        }
    }

    fun prepareImportFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            transient.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    message = null,
                    pendingImportFiles = emptyList(),
                    selectedImportUris = emptySet(),
                    pendingImportThumbnails = emptyMap()
                )
            }
            runCatching { repository.pickedFileInfo(uris) }
                .onSuccess { files ->
                    transient.update {
                        it.copy(
                            isLoading = false,
                            pendingImportFiles = files,
                            selectedImportUris = files.map { file -> file.uri.toString() }.toSet()
                        )
                    }
                    loadPendingImportThumbnails(files)
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao preparar arquivos.") }
                }
        }
    }

    fun loadPendingImportThumbnails(filesOverride: List<PickedFileInfo>? = null) {
        val current = state.value
        val files = filesOverride ?: current.pendingImportFiles
        files
            .filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
            .filter { current.pendingImportThumbnails[it.uri.toString()] == null }
            .forEach { file ->
                viewModelScope.launch {
                    val thumbnail = repository.pickedFileThumbnail(file.uri)
                    if (thumbnail != null) {
                        transient.update { currentState ->
                            currentState.copy(
                                pendingImportThumbnails = currentState.pendingImportThumbnails + (thumbnail.uriString to thumbnail)
                            )
                        }
                    }
                }
            }
    }

    fun toggleImportSelection(uriString: String) {
        transient.update { current ->
            val next = if (current.selectedImportUris.contains(uriString)) {
                current.selectedImportUris - uriString
            } else {
                current.selectedImportUris + uriString
            }
            current.copy(selectedImportUris = next, error = null, message = null)
        }
    }

    fun toggleSelectAllImportFiles() {
        transient.update { current ->
            val all = current.pendingImportFiles.map { it.uri.toString() }.toSet()
            val next = if (all.isNotEmpty() && current.selectedImportUris.containsAll(all)) emptySet() else all
            current.copy(selectedImportUris = next, error = null, message = null)
        }
    }

    fun cancelPendingImport() {
        transient.update {
            it.copy(
                pendingImportFiles = emptyList(),
                selectedImportUris = emptySet(),
                pendingImportThumbnails = emptyMap(),
                error = null,
                message = null
            )
        }
    }

    fun importPendingFiles(moveOriginals: Boolean) {
        val current = state.value
        val selected = current.pendingImportFiles
            .filter { it.uri.toString() in current.selectedImportUris }
            .map { it.uri }
        if (selected.isEmpty()) {
            transient.update { it.copy(error = "Selecione ao menos um arquivo para importar.") }
            return
        }

        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.importFiles(vaultId, selected, removeOriginals = moveOriginals) }
                .onSuccess { result ->
                    val message = when {
                        moveOriginals && result.failedDeleteNames.isNotEmpty() ->
                            "${result.importedCount} arquivo(s) importado(s), mas alguns originais não puderam ser removidos."
                        moveOriginals ->
                            "${result.importedCount} arquivo(s) movido(s) para a pasta."
                        else ->
                            "${result.importedCount} arquivo(s) copiado(s) para a pasta."
                    }
                    transient.update {
                        it.copy(
                            isLoading = false,
                            pendingImportFiles = emptyList(),
                            selectedImportUris = emptySet(),
                            pendingImportThumbnails = emptyMap(),
                            message = message
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao importar arquivos.") }
                }
        }
    }

    fun startFileSelection(fileId: Long) {
        transient.update {
            it.copy(
                isFileSelectionMode = true,
                selectedFileIds = setOf(fileId),
                message = null,
                error = null
            )
        }
    }

    fun toggleFileSelection(fileId: Long) {
        transient.update { current ->
            val next = if (current.selectedFileIds.contains(fileId)) {
                current.selectedFileIds - fileId
            } else {
                current.selectedFileIds + fileId
            }
            current.copy(
                isFileSelectionMode = next.isNotEmpty(),
                selectedFileIds = next,
                message = null,
                error = null
            )
        }
    }

    fun toggleSelectAllFiles() {
        transient.update { current ->
            val allIds = state.value.files.map { it.id }.toSet()
            val selected = if (current.selectedFileIds.containsAll(allIds) && allIds.isNotEmpty()) {
                emptySet()
            } else {
                allIds
            }
            current.copy(
                isFileSelectionMode = selected.isNotEmpty(),
                selectedFileIds = selected,
                message = null,
                error = null
            )
        }
    }

    fun clearFileSelection() {
        transient.update { it.copy(isFileSelectionMode = false, selectedFileIds = emptySet(), message = null, error = null) }
    }

    fun deleteSelectedFiles() {
        val selected = state.value.selectedFileIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.deleteVaultFiles(vaultId, selected) }
                .onSuccess {
                    transient.update {
                        it.copy(
                            isLoading = false,
                            isFileSelectionMode = false,
                            selectedFileIds = emptySet(),
                            message = "${selected.size} arquivo(s) removido(s)."
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao remover arquivos.") }
                }
        }
    }

    fun moveSelectedFiles(targetVaultId: Long) {
        val selected = state.value.selectedFileIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.moveVaultFiles(vaultId, targetVaultId, selected) }
                .onSuccess {
                    transient.update {
                        it.copy(
                            isLoading = false,
                            isFileSelectionMode = false,
                            selectedFileIds = emptySet(),
                            message = "${selected.size} arquivo(s) movido(s)."
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao mover arquivos.") }
                }
        }
    }

    fun moveSelectedFilesToFiles(destinationUri: Uri) {
        val selected = state.value.selectedFileIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.moveVaultFilesToDevice(vaultId, selected, destinationUri) }
                .onSuccess { movedCount ->
                    transient.update {
                        it.copy(
                            isLoading = false,
                            isFileSelectionMode = false,
                            selectedFileIds = emptySet(),
                            message = "$movedCount arquivo(s) movido(s) para o Files."
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao mover arquivos para o Files.") }
                }
        }
    }

    fun shareSelectedFiles() {
        val selected = state.value.selectedFileIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.prepareDecryptedFileShare(vaultId, selected) }
                .onSuccess { payload ->
                    _decryptedShare.value = payload
                    transient.update {
                        it.copy(
                            isLoading = false,
                            isFileSelectionMode = false,
                            selectedFileIds = emptySet(),
                            message = "${selected.size} arquivo(s) pronto(s) para enviar."
                        )
                    }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao preparar compartilhamento.") }
                }
        }
    }

    fun decryptedShareConsumed() {
        _decryptedShare.value = null
    }

    fun exportVault() {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.exportVault(vaultId) }
                .onSuccess { file ->
                    _exportedFile.value = file
                    transient.update { it.copy(isLoading = false, message = "Pasta exportada com sucesso.") }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao exportar pasta.") }
                }
        }
    }

    fun exportedFileConsumed() {
        _exportedFile.value = null
    }

    fun exportVaultToFiles() {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.exportVault(vaultId) }
                .onSuccess { file ->
                    _filesExportedFile.value = file
                    transient.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao preparar pasta para o Files.") }
                }
        }
    }

    fun saveExportedVaultToFiles(destination: Uri?) {
        val file = _filesExportedFile.value
        if (destination == null || file == null) {
            _filesExportedFile.value = null
            return
        }

        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.writeExportToUri(file, destination) }
                .onSuccess {
                    _filesExportedFile.value = null
                    transient.update { it.copy(isLoading = false, message = "${file.name} salvo no Files.") }
                }
                .onFailure { error ->
                    _filesExportedFile.value = null
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao salvar no Files.") }
                }
        }
    }

    fun shareIntentFor(file: File) = repository.shareIntentFor(file)

    fun sharePlainIntentFor(payload: DecryptedFileShare) = repository.sharePlainIntentFor(payload)

    fun lockVault() {
        viewModelScope.launch {
            repository.lockVault(vaultId)
        }
    }

    fun deleteVault() {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.deleteVault(vaultId) }
                .onSuccess {
                    transient.update { it.copy(isLoading = false, deleted = true) }
                }
                .onFailure { error ->
                    transient.update { it.copy(isLoading = false, error = error.message ?: "Erro ao excluir pasta.") }
                }
        }
    }
}
