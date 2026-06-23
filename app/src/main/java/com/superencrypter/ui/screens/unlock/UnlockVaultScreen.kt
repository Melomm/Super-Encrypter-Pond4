package com.superencrypter.ui.screens.unlock

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superencrypter.ui.components.IconBadge
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.components.VaultButtonShape
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultDanger
import com.superencrypter.ui.theme.VaultMuted
import com.superencrypter.ui.theme.VaultOutline
import com.superencrypter.ui.theme.VaultPrimary
import com.superencrypter.ui.theme.VaultSecondary
import com.superencrypter.ui.theme.VaultSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockVaultScreen(
    viewModel: UnlockVaultViewModel,
    onBack: () -> Unit,
    onUnlocked: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var pendingAutoUnlock by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.unlockWithCurrentLocation() else viewModel.locationPermissionDenied()
    }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingAutoUnlock = true
            viewModel.selectKey(uri)
        }
    }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    LaunchedEffect(state.keyUri, state.vault?.geoLockEnabled, pendingAutoUnlock) {
        if (pendingAutoUnlock && state.keyUri != null) {
            pendingAutoUnlock = false
            if (state.vault?.geoLockEnabled == true) {
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                viewModel.unlockWithoutLocation()
            }
        }
    }

    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Destrancar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(state.vault?.name ?: "Pasta segura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SecurityCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.VpnKey, VaultPrimary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Arquivo-chave", fontWeight = FontWeight.SemiBold)
                            Text(
                                state.keyFileName ?: "Ao selecionar o arquivo correto, a pasta será destrancada automaticamente.",
                                color = VaultMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { keyPicker.launch(arrayOf("*/*")) },
                        enabled = !state.isLoading,
                        shape = VaultButtonShape,
                        border = BorderStroke(1.dp, VaultOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selecionar arquivo-chave")
                    }
                    if (state.vault?.geoLockEnabled == true) {
                        InfoBanner(
                            text = "GeoLock ativo: o GPS será validado após selecionar o arquivo.",
                            color = VaultSecondary,
                            icon = Icons.Default.LocationOn
                        )
                    }
                }
            }

            state.error?.let { InfoBanner(it, VaultDanger, Icons.Default.Warning) }

            if (state.isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Validando arquivo-chave...", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
