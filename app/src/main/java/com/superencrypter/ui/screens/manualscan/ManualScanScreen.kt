package com.superencrypter.ui.screens.manualscan

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superencrypter.data.remote.ScanHistoryItem
import com.superencrypter.ui.components.EmptySecurityState
import com.superencrypter.ui.components.IconBadge
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.components.VaultButtonShape
import com.superencrypter.ui.components.VirusTotalChip
import com.superencrypter.ui.components.formatBytes
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultDanger
import com.superencrypter.ui.theme.VaultMuted
import com.superencrypter.ui.theme.VaultPrimary
import com.superencrypter.ui.theme.VaultSurface
import com.superencrypter.ui.theme.VaultWarning

@Composable
fun ManualScanScreen(viewModel: ManualScanViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.scan(context, uri)
        }
    }

    Scaffold(containerColor = VaultBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanHeader()
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Manual") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadRecentHistory()
                    },
                    text = { Text("Histórico") }
                )
            }

            if (selectedTab == 0) {
                ManualScanContent(
                    state = state,
                    onPickFile = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                ScanHistoryContent(
                    state = state,
                    onSeeMore = viewModel::loadFullHistory,
                    onClearHistory = viewModel::clearHistory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScanHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Default.Security, VaultPrimary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Scan manual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Verifique arquivos no VirusTotal.",
                color = VaultMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ManualScanContent(
    state: ManualScanUiState,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SecurityCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoBanner(
                        text = "O arquivo completo será enviado para análise externa.",
                        color = VaultWarning,
                        icon = Icons.Default.Warning
                    )
                    Button(
                        onClick = onPickFile,
                        enabled = !state.isLoading,
                        shape = VaultButtonShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Selecionar e escanear")
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        state.scanStatusText ?: "Enviando arquivo",
                        color = VaultMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        state.result?.let { result ->
            item {
                SecurityCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Resultado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            VirusTotalChip(result.status.name.lowercase())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ScanMetric("Malicioso", result.malicious, Modifier.weight(1f))
                            ScanMetric("Suspeito", result.suspicious, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ScanMetric("Limpo", result.harmless, Modifier.weight(1f))
                            ScanMetric("Sem detecção", result.undetected, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        state.error?.let {
            item { InfoBanner(it, VaultDanger, Icons.Default.Warning) }
        }
    }
}

@Composable
private fun ScanHistoryContent(
    state: ManualScanUiState,
    onSeeMore: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Limpar Histórico?") },
            text = { Text("Essa ação remove todo o histórico de scans manuais") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmation = false
                        onClearHistory()
                    },
                    shape = VaultButtonShape
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (state.isFullHistory) "Histórico completo" else "Scans recentes",
                        color = VaultMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = !state.isClearingHistory && !state.isHistoryLoading
                    ) {
                        if (state.isClearingHistory) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Limpar")
                        }
                    }
                }
            }
        }

        if (state.isHistoryLoading) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Carregando histórico...", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        state.historyError?.let {
            item { InfoBanner(it, VaultDanger, Icons.Default.Warning) }
        }

        if (!state.isHistoryLoading && state.history.isEmpty()) {
            item {
                EmptySecurityState(
                    icon = Icons.Default.History,
                    title = "Nenhum scan registrado",
                    body = "O historico de scans manuais aparecem aqui."
                )
            }
        }

        items(state.history, key = { it.id }) { scan ->
            ScanHistoryCard(scan)
        }

        if (!state.isFullHistory && state.history.isNotEmpty()) {
            item {
                TextButton(
                    onClick = onSeeMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ver mais")
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(scan: ScanHistoryItem) {
    SecurityCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.History, VaultPrimary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(scan.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${scan.mimeType} - ${formatBytes(scan.sizeBytes)} - ${formatCheckedAt(scan.checkedAt)}",
                        color = VaultMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                VirusTotalChip(scan.result.status.name.lowercase())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ScanMetric("Malicioso", scan.result.malicious, Modifier.weight(1f))
                ScanMetric("Suspeito", scan.result.suspicious, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ScanMetric("Limpo", scan.result.harmless, Modifier.weight(1f))
                ScanMetric("Sem detecção", scan.result.undetected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScanMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(VaultSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = VaultMuted, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatCheckedAt(value: String): String =
    value.take(16).replace("T", " ").ifBlank { "sem data" }
