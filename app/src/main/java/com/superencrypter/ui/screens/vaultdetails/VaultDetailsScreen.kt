package com.superencrypter.ui.screens.vaultdetails

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superencrypter.data.file.PickedFileInfo
import com.superencrypter.data.file.PickedFileThumbnail
import com.superencrypter.data.local.entity.VaultFileEntity
import com.superencrypter.data.repository.FileThumbnail
import com.superencrypter.ui.components.EmptySecurityState
import com.superencrypter.ui.components.IconBadge
import com.superencrypter.ui.components.IconStatusChip
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.components.StatusChip
import com.superencrypter.ui.components.VaultButtonShape
import com.superencrypter.ui.components.VaultCardShape
import com.superencrypter.ui.components.formatBytes
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultDanger
import com.superencrypter.ui.theme.VaultMuted
import com.superencrypter.ui.theme.VaultOutline
import com.superencrypter.ui.theme.VaultPrimary
import com.superencrypter.ui.theme.VaultSecondary
import com.superencrypter.ui.theme.VaultSurface
import com.superencrypter.ui.theme.VaultSurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDetailsScreen(
    viewModel: VaultDetailsViewModel,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onOpenFile: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val decryptedShare by viewModel.decryptedShare.collectAsState()
    val context = LocalContext.current
    var moveDialogOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    var lockVaultConfirmationOpen by remember { mutableStateOf(false) }
    var deleteVaultConfirmationOpen by remember { mutableStateOf(false) }
    var deleteFilesConfirmationOpen by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(OpenMultipleDocumentsWithWrite()) { uris ->
        persistPickedUriPermissions(context, uris)
        viewModel.prepareImportFiles(uris)
    }
    val moveToFilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistTreeUriPermission(context, uri)
            viewModel.moveSelectedFilesToFiles(uri)
        }
    }

    LaunchedEffect(exportedFile) {
        exportedFile?.let { file ->
            context.startActivity(viewModel.shareIntentFor(file))
            viewModel.exportedFileConsumed()
        }
    }

    LaunchedEffect(decryptedShare) {
        decryptedShare?.let { payload ->
            context.startActivity(viewModel.sharePlainIntentFor(payload))
            viewModel.decryptedShareConsumed()
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    LaunchedEffect(state.isUnlocked, state.files.map { it.id to it.encryptedPath }) {
        viewModel.loadVisibleThumbnails()
    }

    if (moveDialogOpen) {
        MoveFilesDialog(
            targets = state.moveTargets,
            onDismiss = { moveDialogOpen = false },
            onMoveToFiles = {
                moveDialogOpen = false
                moveToFilesPicker.launch(null)
            },
            onMove = { targetId ->
                moveDialogOpen = false
                viewModel.moveSelectedFiles(targetId)
            }
        )
    }

    if (importDialogOpen) {
        ImportFileDialog(
            onDismiss = { importDialogOpen = false },
            onChooseFile = {
                importDialogOpen = false
                importPicker.launch(arrayOf("*/*"))
            }
        )
    }

    if (lockVaultConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { lockVaultConfirmationOpen = false },
            title = { Text("Trancar pasta?") },
            text = { Text("Você precisará selecionar o arquivo-chave para acessar esta pasta novamente.") },
            confirmButton = {
                Button(
                    onClick = {
                        lockVaultConfirmationOpen = false
                        viewModel.lockVault()
                    },
                    shape = VaultButtonShape
                ) {
                    Text("Trancar")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockVaultConfirmationOpen = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (deleteVaultConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { deleteVaultConfirmationOpen = false },
            title = { Text("Excluir pasta?") },
            text = { Text("Essa ação remove a pasta e todos os arquivos criptografados internos.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteVaultConfirmationOpen = false
                        viewModel.deleteVault()
                    },
                    shape = VaultButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultDanger)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteVaultConfirmationOpen = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (deleteFilesConfirmationOpen) {
        val selectedCount = state.selectedFileIds.size
        AlertDialog(
            onDismissRequest = { deleteFilesConfirmationOpen = false },
            title = { Text("Excluir arquivos?") },
            text = { Text("Essa ação remove $selectedCount arquivo(s) selecionado(s) desta pasta.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteFilesConfirmationOpen = false
                        viewModel.deleteSelectedFiles()
                    },
                    shape = VaultButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultDanger)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFilesConfirmationOpen = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (state.pendingImportFiles.isNotEmpty()) {
        ImportConfirmationScreen(
            files = state.pendingImportFiles,
            selectedUris = state.selectedImportUris,
            thumbnails = state.pendingImportThumbnails,
            isLoading = state.isLoading,
            error = state.error,
            onBack = viewModel::cancelPendingImport,
            onToggleFile = viewModel::toggleImportSelection,
            onToggleSelectAll = viewModel::toggleSelectAllImportFiles,
            onImport = viewModel::importPendingFiles
        )
        return
    }

    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text(state.vault?.name ?: "Pasta segura") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                VaultSummaryCard(
                    state = state,
                    onUnlock = onUnlock,
                    onImport = { importDialogOpen = true },
                    onExport = viewModel::exportVault,
                    onLock = { lockVaultConfirmationOpen = true },
                    onDelete = { deleteVaultConfirmationOpen = true }
                )
            }

            state.message?.let { message ->
                item { InfoBanner(message, VaultPrimary, Icons.Default.CheckCircle) }
            }
            state.error?.let { error ->
                item { InfoBanner(error, VaultDanger, Icons.Default.Warning) }
            }

            if (state.isUnlocked) {
                item {
                    FileSectionHeader(
                        fileCount = state.files.size,
                        selectionMode = state.isFileSelectionMode,
                        allSelected = state.files.isNotEmpty() && state.selectedFileIds.size == state.files.size,
                        onToggleSelectAll = viewModel::toggleSelectAllFiles
                    )
                }
                if (state.isFileSelectionMode && state.selectedFileIds.isNotEmpty()) {
                    item {
                        FileBulkActionBar(
                            selectedCount = state.selectedFileIds.size,
                            busy = state.isLoading,
                            onDelete = { deleteFilesConfirmationOpen = true },
                            onMove = { moveDialogOpen = true },
                            onShare = viewModel::shareSelectedFiles,
                            onCancel = viewModel::clearFileSelection
                        )
                    }
                }
                if (state.files.isEmpty()) {
                    item {
                        EmptySecurityState(
                            icon = Icons.Default.Folder,
                            title = "Nenhum arquivo importado",
                            body = "Use Importar para criar uma cópia criptografada nesta pasta."
                        )
                    }
                } else {
                    items(state.files, key = { it.id }) { file ->
                        VaultFileCard(
                            file = file,
                            thumbnail = state.thumbnails[file.id],
                            selectionMode = state.isFileSelectionMode,
                            isSelected = state.selectedFileIds.contains(file.id),
                            onClick = {
                                if (state.isFileSelectionMode) viewModel.toggleFileSelection(file.id)
                                else onOpenFile(file.id)
                            },
                            onLongClick = { viewModel.startFileSelection(file.id) },
                            onToggleSelection = { viewModel.toggleFileSelection(file.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFileDialog(
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar arquivos?") },
        text = { Text("Escolha os arquivos antes de confirmar o que deve entrar nesta pasta.") },
        confirmButton = {
            Button(
                onClick = onChooseFile,
                shape = VaultButtonShape
            ) {
                Text("Escolher arquivos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportConfirmationScreen(
    files: List<PickedFileInfo>,
    selectedUris: Set<String>,
    thumbnails: Map<String, PickedFileThumbnail>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onToggleFile: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onImport: (Boolean) -> Unit
) {
    var importModeDialogOpen by remember { mutableStateOf(false) }
    val selectedCount = selectedUris.size
    val allSelected = files.isNotEmpty() && selectedCount == files.size

    if (importModeDialogOpen) {
        AlertDialog(
            onDismissRequest = { importModeDialogOpen = false },
            title = { Text("Como importar?") },
            text = { Text("Copiar mantém o arquivo original. Mover importa e tenta remover o original do Files.") },
            confirmButton = {
                Button(
                    onClick = {
                        importModeDialogOpen = false
                        onImport(false)
                    },
                    enabled = !isLoading,
                    shape = VaultButtonShape
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            importModeDialogOpen = false
                            onImport(true)
                        },
                        enabled = !isLoading
                    ) {
                        Text("Mover")
                    }
                    TextButton(onClick = { importModeDialogOpen = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Confirmar arquivos") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultSurface)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VaultSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$selectedCount selecionado(s)",
                    color = VaultMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { importModeDialogOpen = true },
                    enabled = selectedCount > 0 && !isLoading,
                    shape = VaultButtonShape
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Importar")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Arquivos selecionados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${files.size} arquivo(s) escolhidos no Files", color = VaultMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
                }
            }

            error?.let {
                item { InfoBanner(it, VaultDanger, Icons.Default.Warning) }
            }

            if (isLoading) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Preparando arquivos...", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(files, key = { it.uri.toString() }) { file ->
                PendingImportFileCard(
                    file = file,
                    thumbnail = thumbnails[file.uri.toString()],
                    isSelected = selectedUris.contains(file.uri.toString()),
                    enabled = !isLoading,
                    onToggle = { onToggleFile(file.uri.toString()) }
                )
            }
        }
    }
}

@Composable
private fun PendingImportFileCard(
    file: PickedFileInfo,
    thumbnail: PickedFileThumbnail?,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = VaultCardShape,
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceHigh),
        border = BorderStroke(1.dp, if (isSelected) VaultPrimary.copy(alpha = 0.85f) else VaultOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PendingImportPreviewThumb(thumbnail = thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("${file.mimeType} - ${formatBytes(file.sizeBytes)}", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    }
}

@Composable
private fun PendingImportPreviewThumb(thumbnail: PickedFileThumbnail?) {
    if (thumbnail == null) {
        IconBadge(Icons.AutoMirrored.Filled.InsertDriveFile, VaultPrimary)
        return
    }

    val bitmap = remember(thumbnail.file.absolutePath) {
        BitmapFactory.decodeFile(thumbnail.file.absolutePath)
    }

    if (bitmap == null) {
        IconBadge(Icons.AutoMirrored.Filled.InsertDriveFile, VaultPrimary)
        return
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VaultSurface),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (thumbnail.isVideo) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(VaultBackground.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VaultPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FileSectionHeader(
    fileCount: Int,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Arquivos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("$fileCount protegido(s)", color = VaultMuted, style = MaterialTheme.typography.labelMedium)
        }
        if (selectionMode && fileCount > 0) {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
        }
    }
}

@Composable
private fun FileBulkActionBar(
    selectedCount: Int,
    busy: Boolean,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit
) {
    SecurityCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$selectedCount selecionado(s)",
                    color = VaultMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileActionButton(
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    text = "Enviar",
                    enabled = !busy,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
                FileActionButton(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    text = "Mover",
                    enabled = !busy,
                    onClick = onMove,
                    modifier = Modifier.weight(1f)
                )
                FileActionButton(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    text = "Excluir",
                    enabled = !busy,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    danger = true
                )
            }
        }
    }
}

@Composable
private fun FileActionButton(
    icon: @Composable () -> Unit,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = VaultButtonShape,
        border = BorderStroke(1.dp, if (danger) VaultDanger.copy(alpha = 0.6f) else VaultOutline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) VaultDanger else VaultPrimary),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier
    ) {
        icon()
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MoveFilesDialog(
    targets: List<FileMoveTarget>,
    onDismiss: () -> Unit,
    onMoveToFiles: () -> Unit,
    onMove: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover arquivos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (targets.isEmpty()) {
                    Text("Crie ou destranque outra pasta para mover arquivos.", color = VaultMuted)
                } else {
                    targets.forEach { target ->
                        OutlinedButton(
                            onClick = { onMove(target.id) },
                            enabled = target.isUnlocked,
                            shape = VaultButtonShape,
                            border = BorderStroke(1.dp, VaultOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(target.name)
                                if (!target.isUnlocked) {
                                    Text("Destranque para mover", color = VaultMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onMoveToFiles) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Files")
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun VaultSummaryCard(
    state: VaultDetailsUiState,
    onUnlock: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit
) {
    val stateColor = if (state.isUnlocked) VaultPrimary else VaultDanger
    val stateIcon = if (state.isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock
    val stateText = if (state.isUnlocked) "Destrancada" else "Trancada"

    SecurityCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(stateIcon, stateColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stateText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.isUnlocked) "Conteúdo disponível para importação e preview." else "Selecione o arquivo-chave para acessar.",
                        color = VaultMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            state.vault?.let { vault ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("Key ID: ${vault.keyFingerprint.take(12)}", VaultPrimary)
                    if (vault.geoLockEnabled) IconStatusChip("GeoLock", Icons.Default.LocationOn, VaultSecondary)
                }
            }

            if (!state.isUnlocked) {
                Button(
                    onClick = onUnlock,
                    shape = VaultButtonShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Destrancar pasta")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onImport,
                        enabled = !state.isLoading,
                        shape = VaultButtonShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Importar")
                    }
                    OutlinedButton(
                        onClick = onExport,
                        enabled = !state.isLoading,
                        shape = VaultButtonShape,
                        border = BorderStroke(1.dp, VaultOutline),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Enviar")
                    }
                }
                OutlinedButton(
                    onClick = onLock,
                    shape = VaultButtonShape,
                    border = BorderStroke(1.dp, VaultOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Trancar")
                }
            }

            OutlinedButton(
                onClick = onDelete,
                enabled = !state.isLoading,
                shape = VaultButtonShape,
                border = BorderStroke(1.dp, VaultDanger.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultDanger),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Excluir pasta")
            }

            if (state.isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Processando operação...", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultFileCard(
    file: VaultFileEntity,
    thumbnail: FileThumbnail?,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Card(
        shape = VaultCardShape,
        colors = CardDefaults.cardColors(containerColor = VaultSurfaceHigh),
        border = BorderStroke(1.dp, if (isSelected) VaultPrimary.copy(alpha = 0.85f) else VaultOutline),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilePreviewThumb(thumbnail = thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(file.originalName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("${file.mimeType} - ${formatBytes(file.sizeBytes)}", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            }
        }
    }
}

@Composable
private fun FilePreviewThumb(thumbnail: FileThumbnail?) {
    if (thumbnail == null) {
        IconBadge(Icons.AutoMirrored.Filled.InsertDriveFile, VaultPrimary)
        return
    }

    val bitmap = remember(thumbnail.file.absolutePath) {
        BitmapFactory.decodeFile(thumbnail.file.absolutePath)
    }

    if (bitmap == null) {
        IconBadge(Icons.AutoMirrored.Filled.InsertDriveFile, VaultPrimary)
        return
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VaultSurface),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (thumbnail.isVideo) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(VaultBackground.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VaultPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private class OpenMultipleDocumentsWithWrite : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val mimeTypes = input.ifEmpty { arrayOf("*/*") }
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val uris = mutableListOf<Uri>()
        intent.data?.let { uris += it }
        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                uris += clipData.getItemAt(index).uri
            }
        }
        return uris.distinctBy { it.toString() }
    }
}

private fun persistPickedUriPermissions(context: Context, uris: List<Uri>) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    uris.forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

private fun persistTreeUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}
