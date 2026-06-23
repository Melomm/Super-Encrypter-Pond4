package com.superencrypter.data.file

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.superencrypter.data.local.entity.VaultEntity
import com.superencrypter.data.local.entity.VaultFileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayList
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PickedFile(
    val bytes: ByteArray,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class PickedFileInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class PickedFileThumbnail(
    val uriString: String,
    val file: File,
    val isVideo: Boolean
)

data class PlainFileExport(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class SuperVaultImport(
    val name: String,
    val salt: String,
    val geoLockEnabled: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Double,
    val checkCipher: String?,
    val checkIv: String?,
    val files: List<SuperVaultFileImport>
)

data class SuperVaultFileImport(
    val originalName: String,
    val mimeType: String,
    val iv: String,
    val sha256: String,
    val virusTotalStatus: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val encryptedBytes: ByteArray
)

class FileStorage(private val context: Context) {
    fun readPickedFile(uri: Uri): PickedFile {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Não foi possível ler o arquivo selecionado.")
        val metadata = queryMetadata(uri)
        return PickedFile(
            bytes = bytes,
            name = metadata.name ?: "arquivo-${System.currentTimeMillis()}",
            mimeType = resolver.getType(uri) ?: metadata.mimeType ?: "application/octet-stream",
            sizeBytes = metadata.sizeBytes ?: bytes.size.toLong()
        )
    }

    fun pickedFileInfo(uri: Uri): PickedFileInfo {
        val metadata = queryMetadata(uri)
        return PickedFileInfo(
            uri = uri,
            name = metadata.name ?: "arquivo-${System.currentTimeMillis()}",
            mimeType = context.contentResolver.getType(uri) ?: metadata.mimeType ?: "application/octet-stream",
            sizeBytes = metadata.sizeBytes ?: 0L
        )
    }

    fun pickedFileThumbnail(uri: Uri): PickedFileThumbnail? {
        val info = pickedFileInfo(uri)
        val isImage = info.mimeType.startsWith("image/")
        val isVideo = info.mimeType.startsWith("video/")
        if (!isImage && !isVideo) return null

        val thumbnail = if (isImage) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            writeImageThumbnail(info.name, bytes)
        } else {
            writeVideoThumbnail(uri, info.name)
        } ?: return null

        return PickedFileThumbnail(
            uriString = uri.toString(),
            file = thumbnail,
            isVideo = isVideo
        )
    }

    fun deletePickedFile(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (_: Exception) {
        false
    }

    fun saveEncryptedFile(vaultId: Long, originalName: String, encryptedBytes: ByteArray): String {
        val vaultDir = File(context.filesDir, "vaults/$vaultId").apply { mkdirs() }
        val file = File(vaultDir, "${UUID.randomUUID()}-${sanitize(originalName)}.enc")
        file.writeBytes(encryptedBytes)
        return file.absolutePath
    }

    fun readEncryptedFile(path: String): ByteArray = File(path).readBytes()

    fun deleteEncryptedFile(path: String) {
        File(path).delete()
    }

    fun writeFileToUri(file: File, destination: Uri) {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Nao foi possivel salvar ${file.name} no Files.")
    }

    fun readSuperVaultImports(uri: Uri): List<SuperVaultImport> {
        val picked = readPickedFile(uri)
        val entries = readZipEntries(picked.bytes)

        if (entries.containsKey("metadata.json")) {
            return listOf(parseSuperVaultEntries(entries, picked.name))
        }

        val imports = entries
            .filterKeys { it.endsWith(".supervault", ignoreCase = true) }
            .map { (name, bytes) -> parseSuperVaultEntries(readZipEntries(bytes), name) }

        if (imports.isEmpty()) {
            error("Selecione um arquivo .supervault ou um .zip exportado em bulk.")
        }

        return imports
    }

    fun deleteVaultDirectory(vaultId: Long) {
        File(context.filesDir, "vaults/$vaultId").deleteRecursively()
    }

    fun writePreviewFile(fileName: String, bytes: ByteArray): File {
        val previewDir = File(context.cacheDir, "previews").apply { mkdirs() }
        val file = File(previewDir, "${UUID.randomUUID()}-${sanitize(fileName)}")
        file.writeBytes(bytes)
        return file
    }

    fun clearPreviewCache() {
        File(context.cacheDir, "previews").deleteRecursively()
    }

    fun writeImageThumbnail(fileName: String, bytes: ByteArray): File {
        val thumbnailDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val file = File(thumbnailDir, "${UUID.randomUUID()}-${sanitize(fileName)}.jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > 512 || (bounds.outHeight / sampleSize) > 512) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )

        if (bitmap == null) {
            file.writeBytes(bytes)
            return file
        }

        val largestSide = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = 240f / largestSide
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        file.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 86, output)
        }
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        return file
    }

    fun writeVideoThumbnail(fileName: String, bytes: ByteArray): File? {
        val thumbnailDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val source = File(thumbnailDir, "${UUID.randomUUID()}-${sanitize(fileName)}")
        val thumbnail = File(thumbnailDir, "${UUID.randomUUID()}-${sanitize(fileName)}.jpg")
        source.writeBytes(bytes)

        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    thumbnail.outputStream().use { output ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 82, output)
                    }
                    frame.recycle()
                    thumbnail
                } else {
                    null
                }
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            null
        } finally {
            source.delete()
        }
    }

    private fun writeVideoThumbnail(uri: Uri, fileName: String): File? {
        val thumbnailDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val thumbnail = File(thumbnailDir, "${UUID.randomUUID()}-${sanitize(fileName)}.jpg")

        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    thumbnail.outputStream().use { output ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 82, output)
                    }
                    frame.recycle()
                    thumbnail
                } else {
                    null
                }
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearThumbnailCache() {
        File(context.cacheDir, "thumbnails").deleteRecursively()
    }

    fun writeShareFile(fileName: String, bytes: ByteArray): File {
        val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(shareDir, "${UUID.randomUUID()}-${sanitize(fileName)}")
        file.writeBytes(bytes)
        return file
    }

    fun clearShareCache() {
        File(context.cacheDir, "shares").deleteRecursively()
    }

    fun writePlainFilesToTree(treeUri: Uri, files: List<PlainFileExport>) {
        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        files.forEach { file ->
            val destination = DocumentsContract.createDocument(
                context.contentResolver,
                treeDocumentUri,
                file.mimeType.ifBlank { "application/octet-stream" },
                file.name
            ) ?: error("Não foi possível criar ${file.name} no Files.")

            context.contentResolver.openOutputStream(destination)?.use { output ->
                output.write(file.bytes)
            } ?: error("Não foi possível escrever ${file.name} no Files.")
        }
    }

    fun clearExportCache() {
        File(context.cacheDir, "exports").deleteRecursively()
    }

    fun createSuperVaultExport(vault: VaultEntity, files: List<VaultFileEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "${sanitize(vault.name)}-${vault.id}.supervault")
        val metadata = JSONObject()
            .put("format", "supervault")
            .put("version", 1)
            .put("vault", JSONObject()
                .put("id", vault.id)
                .put("name", vault.name)
                .put("salt", vault.salt)
                .put("geoLockEnabled", vault.geoLockEnabled)
                .put("latitude", vault.latitude)
                .put("longitude", vault.longitude)
                .put("radiusMeters", vault.radiusMeters)
                .put("checkCipher", vault.checkCipher)
                .put("checkIv", vault.checkIv)
                .put("createdAt", vault.createdAt)
            )
            .put("files", JSONArray(files.map { file ->
                JSONObject()
                    .put("id", file.id)
                    .put("originalName", file.originalName)
                    .put("mimeType", file.mimeType)
                    .put("iv", file.iv)
                    .put("sha256", file.sha256)
                    .put("virusTotalStatus", file.virusTotalStatus)
                    .put("sizeBytes", file.sizeBytes)
                    .put("encryptedName", File(file.encryptedPath).name)
                    .put("createdAt", file.createdAt)
            }))

        ZipOutputStream(exportFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(metadata.toString(2).toByteArray())
            zip.closeEntry()

            files.forEach { vaultFile ->
                val encrypted = File(vaultFile.encryptedPath)
                if (encrypted.exists()) {
                    zip.putNextEntry(ZipEntry("files/${encrypted.name}"))
                    encrypted.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return exportFile
    }

    fun createBulkExport(vaultExports: List<File>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "supervault-bulk-${System.currentTimeMillis()}.zip")
        ZipOutputStream(exportFile.outputStream()).use { zip ->
            vaultExports.forEach { file ->
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return exportFile
    }

    fun shareIntentFor(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Compartilhar pasta .supervault"
        )
    }

    fun shareIntentFor(files: List<File>): Intent {
        val uris = ArrayList(files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        })
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/zip"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Compartilhar pastas .supervault"
        )
    }

    fun sharePlainIntentFor(files: List<File>, mimeType: String): Intent {
        val safeType = mimeType.ifBlank { "application/octet-stream" }
        return if (files.size == 1) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                files.first()
            )
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = safeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Compartilhar arquivo"
            )
        } else {
            val uris = ArrayList(files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            })
            Intent.createChooser(
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Compartilhar arquivos"
            )
        }
    }

    private fun queryMetadata(uri: Uri): Metadata {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                return Metadata(
                    name = if (nameIndex >= 0) cursor.getString(nameIndex) else null,
                    sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null,
                    mimeType = context.contentResolver.getType(uri)
                )
            }
        }
        return Metadata(mimeType = context.contentResolver.getType(uri))
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "vault" }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    entries[entry.name.replace('\\', '/')] = output.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (entries.isEmpty()) error("Arquivo compactado invalido.")
        return entries
    }

    private fun parseSuperVaultEntries(entries: Map<String, ByteArray>, sourceName: String): SuperVaultImport {
        val metadataBytes = entries["metadata.json"]
            ?: error("$sourceName nao e uma pasta .supervault valida.")
        val metadata = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        if (metadata.optString("format") != "supervault") {
            error("$sourceName nao usa o formato .supervault.")
        }

        val vault = metadata.getJSONObject("vault")
        val files = metadata.optJSONArray("files") ?: JSONArray()

        return SuperVaultImport(
            name = vault.getString("name"),
            salt = vault.getString("salt"),
            geoLockEnabled = vault.optBoolean("geoLockEnabled", false),
            latitude = vault.optNullableDouble("latitude"),
            longitude = vault.optNullableDouble("longitude"),
            radiusMeters = vault.optDouble("radiusMeters", 0.0),
            checkCipher = vault.optString("checkCipher").takeIf { it.isNotBlank() },
            checkIv = vault.optString("checkIv").takeIf { it.isNotBlank() },
            files = (0 until files.length()).map { index ->
                val file = files.getJSONObject(index)
                val encryptedName = file.getString("encryptedName")
                val encryptedBytes = entries["files/$encryptedName"]
                    ?: error("$sourceName esta incompleto: $encryptedName nao foi encontrado.")
                SuperVaultFileImport(
                    originalName = file.getString("originalName"),
                    mimeType = file.optString("mimeType", "application/octet-stream"),
                    iv = file.getString("iv"),
                    sha256 = file.optString("sha256", ""),
                    virusTotalStatus = file.optString("virusTotalStatus", "not_checked"),
                    sizeBytes = file.optLong("sizeBytes", encryptedBytes.size.toLong()),
                    createdAt = file.optLong("createdAt", System.currentTimeMillis()),
                    encryptedBytes = encryptedBytes
                )
            }
        )
    }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name)

    private data class Metadata(
        val name: String? = null,
        val sizeBytes: Long? = null,
        val mimeType: String? = null
    )
}
