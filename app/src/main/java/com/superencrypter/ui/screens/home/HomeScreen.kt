package com.superencrypter.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.superencrypter.ui.components.EmptySecurityState
import com.superencrypter.ui.components.IconBadge
import com.superencrypter.ui.components.IconStatusChip
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.components.VaultButtonShape
import com.superencrypter.ui.components.VaultCardShape
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultDanger
import com.superencrypter.ui.theme.VaultMuted
import com.superencrypter.ui.theme.VaultOutline
import com.superencrypter.ui.theme.VaultPrimary
import com.superencrypter.ui.theme.VaultSecondary
import com.superencrypter.ui.theme.VaultSurfaceHigh

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateVault: () -> Unit,
    onOpenVault: (Long) -> Unit,
    onUnlockVault: (Long) -> Unit
) {
    val vaults by viewModel.vaults.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val bulkExportedFile by viewModel.bulkExportedFile.collectAsState()
    val context = LocalContext.current
    var lockConfirmation by remember { mutableStateOf<HomeVaultItem?>(null) }
    var bulkConfirmation by remember { mutableStateOf<HomeBulkConfirmation?>(null) }

    LaunchedEffect(bulkExportedFile) {
        bulkExportedFile?.let { file ->
            context.startActivity(viewModel.shareIntentFor(file))
            viewModel.bulkExportConsumed()
        }
    }

    lockConfirmation?.let { vault ->
        AlertDialog(
            onDismissRequest = { lockConfirmation = null },
            title = { Text("Trancar pasta?") },
            text = { Text("Deseja trancar a pasta ${vault.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.lockVault(vault.id)
                        lockConfirmation = null
                    },
                    shape = VaultButtonShape
                ) {
                    Text("Trancar")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockConfirmation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    bulkConfirmation?.let { confirmation ->
        val selectedCount = actionState.selectedVaultIds.size
        AlertDialog(
            onDismissRequest = { bulkConfirmation = null },
            title = {
                Text(
                    when (confirmation) {
                        HomeBulkConfirmation.LOCK -> "Trancar pastas?"
                        HomeBulkConfirmation.DELETE -> "Excluir pastas?"
                    }
                )
            },
            text = {
                Text(
                    when (confirmation) {
                        HomeBulkConfirmation.LOCK ->
                            "Deseja trancar $selectedCount pasta(s) selecionada(s)? Será necessário usar o arquivo-chave para acessar novamente."
                        HomeBulkConfirmation.DELETE ->
                            "Essa ação remove $selectedCount pasta(s) e todos os arquivos criptografados internos."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (confirmation) {
                            HomeBulkConfirmation.LOCK -> viewModel.lockSelectedVaults()
                            HomeBulkConfirmation.DELETE -> viewModel.deleteSelectedVaults()
                        }
                        bulkConfirmation = null
                    },
                    shape = VaultButtonShape,
                    colors = if (confirmation == HomeBulkConfirmation.DELETE) {
                        ButtonDefaults.buttonColors(containerColor = VaultDanger)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (confirmation == HomeBulkConfirmation.DELETE) "Excluir" else "Trancar")
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkConfirmation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        containerColor = VaultBackground,
        floatingActionButton = {
            if (!actionState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onCreateVault,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Criar pasta") },
                    shape = VaultButtonShape
                )
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
                HomeHeader(
                    vaultCount = vaults.size,
                    selectionMode = actionState.isSelectionMode,
                    allSelected = vaults.isNotEmpty() && actionState.selectedVaultIds.size == vaults.size,
                    onToggleSelectAll = viewModel::toggleSelectAll
                )
            }

            if (actionState.isSelectionMode && actionState.selectedVaultIds.isNotEmpty()) {
                val hasUnlockedSelectedVault = vaults.any { vault ->
                    vault.id in actionState.selectedVaultIds && vault.isUnlocked
                }
                item {
                    BulkActionBar(
                        selectedCount = actionState.selectedVaultIds.size,
                        busy = actionState.isDeleting,
                        onDelete = { bulkConfirmation = HomeBulkConfirmation.DELETE },
                        onLock = { bulkConfirmation = HomeBulkConfirmation.LOCK },
                        lockEnabled = hasUnlockedSelectedVault,
                        onShare = viewModel::shareSelectedVaults,
                        onCancel = viewModel::clearSelection
                    )
                }
            }

            actionState.message?.let { message ->
                item { InfoBanner(message, VaultPrimary, Icons.Default.CheckCircle) }
            }
            actionState.error?.let { error ->
                item { InfoBanner(error, VaultDanger, Icons.Default.Warning) }
            }

            if (vaults.isEmpty()) {
                item { EmptyState() }
            } else {
                items(vaults, key = { it.id }) { vault ->
                    VaultCard(
                        vault = vault,
                        selectionMode = actionState.isSelectionMode,
                        isSelected = actionState.selectedVaultIds.contains(vault.id),
                        onClick = {
                            if (actionState.isSelectionMode) viewModel.toggleSelection(vault.id)
                            else onOpenVault(vault.id)
                        },
                        onLongClick = { viewModel.startSelection(vault.id) },
                        onToggleSelection = { viewModel.toggleSelection(vault.id) },
                        onToggleLock = {
                            if (vault.isUnlocked) lockConfirmation = vault
                            else onUnlockVault(vault.id)
                        }
                    )
                }
            }
        }
    }
}

private enum class HomeBulkConfirmation {
    LOCK,
    DELETE
}

@Composable
private fun HomeHeader(
    vaultCount: Int,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Default.Security, VaultPrimary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Super Encrypter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$vaultCount pasta(s) protegida(s) por arquivo-chave",
                color = VaultMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (selectionMode && vaultCount > 0) {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
        }
    }
}

@Composable
private fun EmptyState() {
    EmptySecurityState(
        icon = Icons.Default.Folder,
        title = "Nenhuma pasta segura criada",
        body = "Use Criar pasta para iniciar um cofre local."
    )
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    busy: Boolean,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    lockEnabled: Boolean,
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
                    "$selectedCount selecionada(s)",
                    color = VaultMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.defaultMinSize(minHeight = 34.dp)) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactActionButton(
                    icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    text = "Trancar",
                    enabled = !busy && lockEnabled,
                    onClick = onLock,
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    text = "Enviar",
                    enabled = !busy,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
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
private fun CompactActionButton(
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
        modifier = modifier.defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
    ) {
        icon()
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultCard(
    vault: HomeVaultItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleLock: () -> Unit
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LockButton(
                isUnlocked = vault.isUnlocked,
                onClick = onToggleLock
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vault.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vault.fileCount} arquivo(s)", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                    if (vault.geoLockEnabled) {
                        IconStatusChip("GeoLock", Icons.Default.LocationOn, VaultSecondary)
                    }
                }
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
            }
        }
    }
}

@Composable
private fun LockButton(
    isUnlocked: Boolean,
    onClick: () -> Unit,
    size: Dp = 42.dp
) {
    val color = if (isUnlocked) VaultPrimary else VaultDanger
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.7f)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = if (isUnlocked) "Trancar" else "Destrancar",
            tint = color,
            modifier = Modifier.size(21.dp)
        )
    }
}
