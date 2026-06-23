package com.superencrypter.data.repository

import android.net.Uri
import com.superencrypter.data.crypto.CryptoManager
import com.superencrypter.data.crypto.SessionVaultManager
import com.superencrypter.data.crypto.fromBase64
import com.superencrypter.data.crypto.toBase64
import com.superencrypter.data.file.FileStorage
import com.superencrypter.data.file.PickedFileInfo
import com.superencrypter.data.file.PickedFileThumbnail
import com.superencrypter.data.file.PlainFileExport
import com.superencrypter.data.local.dao.HistoryDao
import com.superencrypter.data.local.dao.VaultDao
import com.superencrypter.data.local.dao.VaultFileDao
import com.superencrypter.data.local.entity.HistoryEntity
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultFileEntity
import com.superencrypter.data.local.entity.VaultSummaryRow
import com.superencrypter.data.location.GeoPoint
import com.superencrypter.data.location.LocationService
import com.superencrypter.data.notification.NotificationHelper
import com.superencrypter.data.remote.ScanResult
import com.superencrypter.data.remote.ScanHistoryItem
import com.superencrypter.data.remote.ScanJobStatus
import com.superencrypter.data.remote.VirusTotalClient
import com.superencrypter.model.VirusTotalStatus
import com.superencrypter.util.AppConstants
import com.superencrypter.util.AppVisibilityTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class PreviewContent {
    data class Image(val file: File) : PreviewContent()
    data class Text(val value: String) : PreviewContent()
    data class Unsupported(val message: String) : PreviewContent()
}

data class DecryptedFileShare(
    val files: List<File>,
    val mimeType: String
)

data class FileThumbnail(
    val fileId: Long,
    val file: File,
    val isVideo: Boolean
)

data class ImportFilesResult(
    val importedCount: Int,
    val removedOriginalCount: Int,
    val failedDeleteNames: List<String>
)

class SuperEncrypterRepository(
    private val vaultDao: VaultDao,
    private val fileDao: VaultFileDao,
    private val historyDao: HistoryDao,
    private val crypto: CryptoManager,
    private val fileStorage: FileStorage,
    private val locationService: LocationService,
    private val notificationHelper: NotificationHelper,
    private val virusTotalClient: VirusTotalClient,
    private val sessionVaultManager: SessionVaultManager
) {
    val unlockedVaults: StateFlow<Set<Long>> = sessionVaultManager.unlockedVaults

    fun observeVaultSummaries(): Flow<List<VaultSummaryRow>> = vaultDao.observeVaultSummaries()

    fun observeVault(vaultId: Long): Flow<VaultEntity?> = vaultDao.observeVault(vaultId)

    fun observeFiles(vaultId: Long): Flow<List<VaultFileEntity>> = fileDao.observeFiles(vaultId)

    fun isUnlocked(vaultId: Long): Boolean = sessionVaultManager.isUnlocked(vaultId)

    suspend fun readKeyInfo(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val picked = fileStorage.readPickedFile(uri)
        val hash = crypto.sha256Hex(picked.bytes)
        picked.name to crypto.fingerprintFromHash(hash)
    }

    suspend fun createVault(
        name: String,
        keyUri: Uri,
        geoLockEnabled: Boolean,
        location: GeoPoint?
    ): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Informe um nome para a pasta segura." }

        val keyFile = fileStorage.readPickedFile(keyUri)
        val keyHash = crypto.sha256Hex(keyFile.bytes)
        try {
            virusTotalClient.scanSha256(keyHash)
        } catch (_: Exception) {
            // A criação da pasta não deve depender da disponibilidade do backend local.
        }
        notificationHelper.notify(
            "Hash criado com segurança!",
            "O arquivo-chave foi identificado por SHA-256."
        )
        val salt = crypto.randomSalt()

        if (geoLockEnabled && location == null) {
            error("Não foi possível capturar a localização para ativar o GeoLock.")
        }

        val vault = VaultEntity(
            name = cleanName,
            keyHash = keyHash,
            keyFingerprint = crypto.fingerprintFromHash(keyHash),
            salt = salt.toBase64(),
            geoLockEnabled = geoLockEnabled,
            latitude = location?.latitude,
            longitude = location?.longitude,
            radiusMeters = AppConstants.GEOLOCK_RADIUS_METERS
        )
        val id = vaultDao.insert(vault)
        val key = crypto.deriveKey(keyFile.bytes, salt)
        sessionVaultManager.unlock(id, key)
        historyDao.insert(HistoryEntity(type = "vault_created", message = "Pasta segura criada: $cleanName"))
        id
    }

    suspend fun unlockVault(vaultId: Long, keyUri: Uri, currentLocation: GeoPoint?): Unit =
        withContext(Dispatchers.IO) {
            val vault = vaultDao.getVault(vaultId) ?: error("Pasta segura não encontrada.")
            val keyFile = fileStorage.readPickedFile(keyUri)
            val selectedHash = crypto.sha256Hex(keyFile.bytes)

            if (selectedHash != vault.keyHash) {
                error("Arquivo-chave incorreto. Selecione o mesmo arquivo usado na criação da pasta.")
            }

            if (vault.geoLockEnabled) {
                val savedPoint = GeoPoint(
                    latitude = vault.latitude ?: error("Localização original da pasta não foi salva."),
                    longitude = vault.longitude ?: error("Localização original da pasta não foi salva.")
                )
                val livePoint = currentLocation ?: error("Não foi possível obter sua localização atual.")
                val distance = locationService.distanceMeters(livePoint, savedPoint)
                if (distance > vault.radiusMeters) {
                    notificationHelper.notify(
                        "Tentativa bloqueada pelo GeoLock",
                        "Você está fora da área permitida para abrir esta pasta."
                    )
                    error("Você está fora da área permitida pelo GeoLock. Distância aproximada: ${distance.toInt()} m.")
                }
            }

            val key = crypto.deriveKey(keyFile.bytes, vault.salt.fromBase64())
            sessionVaultManager.unlock(vaultId, key)
            historyDao.insert(HistoryEntity(type = "vault_unlocked", message = "Pasta destrancada: ${vault.name}"))
        }

    suspend fun lockVault(vaultId: Long) {
        sessionVaultManager.lock(vaultId)
        historyDao.insert(HistoryEntity(type = "vault_locked", message = "Pasta trancada."))
    }

    suspend fun lockVaults(vaultIds: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        val ids = vaultIds.distinct()
        ids.forEach { sessionVaultManager.lock(it) }
        historyDao.insert(
            HistoryEntity(
                type = "vaults_locked",
                message = "${ids.size} pasta(s) trancada(s)."
            )
        )
    }

    suspend fun deleteVault(vaultId: Long): Unit = withContext(Dispatchers.IO) {
        val vault = vaultDao.getVault(vaultId)
        sessionVaultManager.lock(vaultId)
        fileStorage.deleteVaultDirectory(vaultId)
        vaultDao.deleteVault(vaultId)
        historyDao.insert(
            HistoryEntity(
                type = "vault_deleted",
                message = "Pasta removida: ${vault?.name ?: vaultId}"
            )
        )
    }

    suspend fun deleteVaults(vaultIds: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        val ids = vaultIds.distinct()
        if (ids.isEmpty()) return@withContext
        ids.forEach { vaultId ->
            sessionVaultManager.lock(vaultId)
            fileStorage.deleteVaultDirectory(vaultId)
        }
        vaultDao.deleteVaults(ids)
        historyDao.insert(
            HistoryEntity(
                type = "vaults_deleted",
                message = "${ids.size} pasta(s) removida(s)."
            )
        )
    }

    suspend fun importFile(vaultId: Long, fileUri: Uri): Unit = withContext(Dispatchers.IO) {
        val key = sessionVaultManager.keyFor(vaultId)
            ?: error("Destranque a pasta antes de importar arquivos.")
        val picked = importPickedFile(vaultId, fileUri, key)

        notificationHelper.notify("Arquivo protegido com sucesso", "${picked.name} foi criptografado localmente.")
        historyDao.insert(HistoryEntity(type = "file_imported", message = "Arquivo importado: ${picked.name}"))
    }

    suspend fun pickedFileInfo(fileUris: Collection<Uri>): List<PickedFileInfo> = withContext(Dispatchers.IO) {
        fileUris.distinctBy { it.toString() }.map { fileStorage.pickedFileInfo(it) }
    }

    suspend fun pickedFileThumbnail(fileUri: Uri): PickedFileThumbnail? = withContext(Dispatchers.IO) {
        fileStorage.pickedFileThumbnail(fileUri)
    }

    suspend fun importFiles(
        vaultId: Long,
        fileUris: Collection<Uri>,
        removeOriginals: Boolean
    ): ImportFilesResult = withContext(Dispatchers.IO) {
        val key = sessionVaultManager.keyFor(vaultId)
            ?: error("Destranque a pasta antes de importar arquivos.")
        val uris = fileUris.distinctBy { it.toString() }
        if (uris.isEmpty()) error("Selecione ao menos um arquivo.")

        val importedNames = mutableListOf<String>()
        val failedDeleteNames = mutableListOf<String>()
        uris.forEach { uri ->
            val picked = importPickedFile(vaultId, uri, key)
            importedNames += picked.name
            if (removeOriginals) {
                val deleted = fileStorage.deletePickedFile(uri)
                if (!deleted) failedDeleteNames += picked.name
            }
        }

        val action = if (removeOriginals) "movido(s)" else "importado(s)"
        notificationHelper.notify(
            "Arquivo(s) protegido(s)",
            "${importedNames.size} arquivo(s) foram $action para a pasta."
        )
        historyDao.insert(
            HistoryEntity(
                type = if (removeOriginals) "files_moved_from_device" else "files_imported",
                message = "${importedNames.size} arquivo(s) $action."
            )
        )

        ImportFilesResult(
            importedCount = importedNames.size,
            removedOriginalCount = if (removeOriginals) importedNames.size - failedDeleteNames.size else 0,
            failedDeleteNames = failedDeleteNames
        )
    }

    private suspend fun importPickedFile(vaultId: Long, fileUri: Uri, key: javax.crypto.SecretKey): com.superencrypter.data.file.PickedFile {
        val picked = fileStorage.readPickedFile(fileUri)
        val encrypted = try {
            crypto.encrypt(picked.bytes, key)
        } catch (_: Exception) {
            error("Erro ao criptografar o arquivo.")
        }
        val path = fileStorage.saveEncryptedFile(vaultId, picked.name, encrypted.cipherBytes)

        fileDao.insert(
            VaultFileEntity(
                vaultId = vaultId,
                originalName = picked.name,
                mimeType = picked.mimeType,
                encryptedPath = path,
                iv = encrypted.ivBase64,
                sha256 = "",
                virusTotalStatus = VirusTotalStatus.NOT_CHECKED.name.lowercase(),
                sizeBytes = picked.sizeBytes
            )
        )
        return picked
    }

    suspend fun deleteVaultFiles(vaultId: Long, fileIds: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        val ids = fileIds.distinct()
        if (ids.isEmpty()) return@withContext
        val files = filesForVault(vaultId, ids)
        fileDao.deleteFiles(ids)
        files.forEach { fileStorage.deleteEncryptedFile(it.encryptedPath) }
        historyDao.insert(
            HistoryEntity(
                type = "files_deleted",
                message = "${ids.size} arquivo(s) removido(s)."
            )
        )
    }

    suspend fun moveVaultFiles(
        sourceVaultId: Long,
        targetVaultId: Long,
        fileIds: Collection<Long>
    ): Unit = withContext(Dispatchers.IO) {
        require(sourceVaultId != targetVaultId) { "Selecione uma pasta de destino diferente." }
        val ids = fileIds.distinct()
        if (ids.isEmpty()) return@withContext

        val sourceKey = sessionVaultManager.keyFor(sourceVaultId)
            ?: error("Destranque a pasta atual antes de mover arquivos.")
        val targetKey = sessionVaultManager.keyFor(targetVaultId)
            ?: error("Destranque a pasta de destino antes de mover arquivos.")
        val targetVault = vaultDao.getVault(targetVaultId) ?: error("Pasta de destino não encontrada.")
        val files = filesForVault(sourceVaultId, ids)

        files.forEach { file ->
            val decrypted = try {
                crypto.decrypt(fileStorage.readEncryptedFile(file.encryptedPath), sourceKey, file.iv)
            } catch (_: Exception) {
                error("Erro ao descriptografar ${file.originalName} para mover.")
            }
            val encrypted = try {
                crypto.encrypt(decrypted, targetKey)
            } catch (_: Exception) {
                error("Erro ao recriptografar ${file.originalName}.")
            }
            val newPath = fileStorage.saveEncryptedFile(targetVaultId, file.originalName, encrypted.cipherBytes)
            fileDao.moveFile(file.id, targetVaultId, newPath, encrypted.ivBase64)
            fileStorage.deleteEncryptedFile(file.encryptedPath)
        }

        historyDao.insert(
            HistoryEntity(
                type = "files_moved",
                message = "${files.size} arquivo(s) movido(s) para ${targetVault.name}."
            )
        )
    }

    suspend fun moveVaultFilesToDevice(
        vaultId: Long,
        fileIds: Collection<Long>,
        destinationTreeUri: Uri
    ): Int = withContext(Dispatchers.IO) {
        val ids = fileIds.distinct()
        if (ids.isEmpty()) error("Selecione ao menos um arquivo.")
        val key = sessionVaultManager.keyFor(vaultId)
            ?: error("Destranque a pasta antes de mover arquivos.")
        val files = filesForVault(vaultId, ids)
        val exports = files.map { file ->
            val decrypted = try {
                crypto.decrypt(fileStorage.readEncryptedFile(file.encryptedPath), key, file.iv)
            } catch (_: Exception) {
                error("Erro ao descriptografar ${file.originalName} para mover.")
            }
            PlainFileExport(
                name = file.originalName,
                mimeType = file.mimeType,
                bytes = decrypted
            )
        }

        fileStorage.writePlainFilesToTree(destinationTreeUri, exports)
        fileDao.deleteFiles(ids)
        files.forEach { fileStorage.deleteEncryptedFile(it.encryptedPath) }
        historyDao.insert(
            HistoryEntity(
                type = "files_moved_to_device",
                message = "${files.size} arquivo(s) movido(s) para o Files."
            )
        )
        files.size
    }

    suspend fun prepareDecryptedFileShare(vaultId: Long, fileIds: Collection<Long>): DecryptedFileShare =
        withContext(Dispatchers.IO) {
            val ids = fileIds.distinct()
            if (ids.isEmpty()) error("Selecione ao menos um arquivo.")
            val key = sessionVaultManager.keyFor(vaultId)
                ?: error("Destranque a pasta antes de compartilhar arquivos.")
            val files = filesForVault(vaultId, ids)
            fileStorage.clearShareCache()
            val shareFiles = files.map { file ->
                val decrypted = try {
                    crypto.decrypt(fileStorage.readEncryptedFile(file.encryptedPath), key, file.iv)
                } catch (_: Exception) {
                    error("Erro ao descriptografar ${file.originalName} para compartilhamento.")
                }
                fileStorage.writeShareFile(file.originalName, decrypted)
            }
            historyDao.insert(
                HistoryEntity(
                    type = "files_shared_plain",
                    message = "${files.size} arquivo(s) preparado(s) para compartilhamento descriptografado."
                )
            )
            DecryptedFileShare(
                files = shareFiles,
                mimeType = if (files.size == 1) files.first().mimeType else "*/*"
            )
        }

    suspend fun thumbnailForFile(vaultId: Long, fileId: Long): FileThumbnail? = withContext(Dispatchers.IO) {
        val key = sessionVaultManager.keyFor(vaultId)
            ?: return@withContext null
        val file = fileDao.getFile(fileId) ?: return@withContext null
        if (file.vaultId != vaultId) return@withContext null

        val isImage = file.mimeType.startsWith("image/")
        val isVideo = file.mimeType.startsWith("video/")
        if (!isImage && !isVideo) return@withContext null

        val decrypted = try {
            crypto.decrypt(fileStorage.readEncryptedFile(file.encryptedPath), key, file.iv)
        } catch (_: Exception) {
            return@withContext null
        }

        val thumbnail = if (isImage) {
            fileStorage.writeImageThumbnail(file.originalName, decrypted)
        } else {
            fileStorage.writeVideoThumbnail(file.originalName, decrypted)
        } ?: return@withContext null

        FileThumbnail(fileId = file.id, file = thumbnail, isVideo = isVideo)
    }

    suspend fun scanPickedFile(
        fileUri: Uri,
        onStatus: (String) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {
        val picked = fileStorage.readPickedFile(fileUri)
        onStatus("Enviando arquivo")
        var job = virusTotalClient.startScanFileJob(picked.name, picked.mimeType, picked.bytes)
        onStatus(scanJobMessage(job))

        while (job.status != "completed" && job.status != "failed") {
            delay(2500)
            job = virusTotalClient.scanFileJob(job.id)
            onStatus(scanJobMessage(job))
        }

        if (job.status == "failed") {
            error(job.error ?: "Falha ao escanear arquivo no VirusTotal.")
        }

        val result = job.result ?: error("Backend concluiu o scan sem retornar resultado.")
        notifyScanFinished(picked.name, result.status)
        historyDao.insert(
            HistoryEntity(
                type = "manual_scan",
                message = "Arquivo escaneado manualmente: ${picked.name}"
            )
        )
        result
    }

    private fun scanJobMessage(job: ScanJobStatus): String {
        val base = when (job.status) {
            "hash_lookup" -> "Consultando hash"
            "uploaded" -> "Arquivo enviado"
            "in_progress" -> "Análise em andamento"
            "completed" -> "Análise concluída"
            "failed" -> "Falha na análise"
            else -> job.statusText.ifBlank { "Análise em andamento" }
        }
        return if (job.status == "in_progress" && job.queuePosition != null) {
            "$base (Posição na fila: ${job.queuePosition})"
        } else {
            base
        }
    }

    suspend fun scanHistory(limit: Int? = null): List<ScanHistoryItem> = withContext(Dispatchers.IO) {
        try {
            virusTotalClient.scanHistory(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun clearScanHistory(): Unit = withContext(Dispatchers.IO) {
        virusTotalClient.clearScanHistory()
    }

    suspend fun previewFile(vaultId: Long, fileId: Long): PreviewContent = withContext(Dispatchers.IO) {
        val key = sessionVaultManager.keyFor(vaultId)
            ?: error("Destranque a pasta antes de visualizar arquivos.")
        val file = fileDao.getFile(fileId) ?: error("Arquivo não encontrado.")

        val isImage = file.mimeType.startsWith("image/")
        val isText = file.mimeType == "text/plain" || file.originalName.endsWith(".txt", ignoreCase = true)
        if (!isImage && !isText) {
            return@withContext PreviewContent.Unsupported("Preview não disponível para este tipo de arquivo.")
        }

        val decrypted = try {
            crypto.decrypt(fileStorage.readEncryptedFile(file.encryptedPath), key, file.iv)
        } catch (_: Exception) {
            error("Erro ao descriptografar o arquivo.")
        }

        if (isImage) {
            PreviewContent.Image(fileStorage.writePreviewFile(file.originalName, decrypted))
        } else {
            PreviewContent.Text(decrypted.toString(Charsets.UTF_8))
        }
    }

    suspend fun clearPreviewCache() = withContext(Dispatchers.IO) {
        fileStorage.clearPreviewCache()
        fileStorage.clearThumbnailCache()
    }

    suspend fun exportVault(vaultId: Long): File = withContext(Dispatchers.IO) {
        val vault = vaultDao.getVault(vaultId) ?: error("Pasta segura não encontrada.")
        val files = fileDao.getFiles(vaultId)
        fileStorage.clearExportCache()
        val export = fileStorage.createSuperVaultExport(vault, files)
        notificationHelper.notify("Pasta exportada com sucesso", "${vault.name} foi gerada como .supervault.")
        historyDao.insert(HistoryEntity(type = "vault_exported", message = "Pasta exportada: ${vault.name}"))
        export
    }

    suspend fun exportVaults(vaultIds: Collection<Long>): File = withContext(Dispatchers.IO) {
        fileStorage.clearExportCache()
        val exports = vaultIds.distinct().mapNotNull { vaultId ->
            val vault = vaultDao.getVault(vaultId) ?: return@mapNotNull null
            fileStorage.createSuperVaultExport(vault, fileDao.getFiles(vaultId))
        }
        val bulkExport = fileStorage.createBulkExport(exports)
        notificationHelper.notify("Pastas exportadas com sucesso", "${exports.size} pasta(s) foram preparadas para compartilhamento.")
        historyDao.insert(HistoryEntity(type = "vaults_exported", message = "${exports.size} pasta(s) exportada(s)."))
        bulkExport
    }

    fun shareIntentFor(file: File) = fileStorage.shareIntentFor(file)

    fun sharePlainIntentFor(payload: DecryptedFileShare) =
        fileStorage.sharePlainIntentFor(payload.files, payload.mimeType)

    private fun notifyScanFinished(fileName: String, status: VirusTotalStatus) {
        if (!AppVisibilityTracker.isInForeground) {
            val resultText = when (status) {
                VirusTotalStatus.CLEAN -> "seguro"
                VirusTotalStatus.SUSPICIOUS -> "suspeito"
                VirusTotalStatus.MALICIOUS -> "malicioso"
                VirusTotalStatus.UNKNOWN -> "desconhecido"
                VirusTotalStatus.NOT_CHECKED -> "não verificado"
            }
            val title = if (status == VirusTotalStatus.SUSPICIOUS || status == VirusTotalStatus.MALICIOUS) {
                "Alerta do VirusTotal"
            } else {
                "Scan finalizado"
            }
            notificationHelper.notify(
                title,
                "A análise de $fileName foi concluída com resultado $resultText."
            )
            return
        }

        notifyIfSuspicious(fileName, status)
    }

    private fun notifyIfSuspicious(fileName: String, status: VirusTotalStatus) {
        if (status == VirusTotalStatus.SUSPICIOUS || status == VirusTotalStatus.MALICIOUS) {
            notificationHelper.notify(
                "Alerta do VirusTotal",
                "O arquivo $fileName recebeu status ${status.name.lowercase()}."
            )
        }
    }

    suspend fun currentLocation(): GeoPoint? = locationService.currentLocation()

    private suspend fun filesForVault(vaultId: Long, ids: List<Long>): List<VaultFileEntity> {
        val files = fileDao.getFilesByIds(ids)
        if (files.size != ids.size || files.any { it.vaultId != vaultId }) {
            error("Arquivo selecionado não pertence a esta pasta.")
        }
        return files
    }
}
