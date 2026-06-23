package com.superencrypter.ui.screens.createvault

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superencrypter.ui.components.IconBadge
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.components.StatusChip
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
fun CreateVaultScreen(
    viewModel: CreateVaultViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectKey(uri)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.onGeoLockChange(true)
            viewModel.captureLocation()
        } else {
            viewModel.locationPermissionDenied()
        }
    }

    LaunchedEffect(state.createdVaultId) {
        state.createdVaultId?.let(onCreated)
    }

    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Criar pasta") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nome da pasta") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                singleLine = true,
                shape = VaultButtonShape,
                modifier = Modifier.fillMaxWidth()
            )

            SecurityCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.VpnKey, VaultPrimary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Arquivo-chave", fontWeight = FontWeight.SemiBold)
                            Text(
                                state.keyFileName ?: "Selecione o arquivo usado para derivar a chave AES.",
                                color = VaultMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    state.keyFingerprint?.let {
                        StatusChip("Key ID: ${it.take(16)}", VaultPrimary)
                    }
                    OutlinedButton(
                        onClick = { keyPicker.launch(arrayOf("*/*")) },
                        shape = VaultButtonShape,
                        border = BorderStroke(1.dp, VaultOutline)
                    ) {
                        Text("Selecionar arquivo-chave")
                    }
                }
            }

            SecurityCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Default.LocationOn, VaultSecondary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("GeoLock", fontWeight = FontWeight.SemiBold)
                            Text("Exige GPS dentro de 200 metros para destrancar.", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = state.geoLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                else viewModel.onGeoLockChange(false)
                            }
                        )
                    }
                    if (state.geoLockEnabled) {
                        if (state.capturedLocation == null && state.isLoading) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text("Capturando localização...", color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (state.capturedLocation != null) {
                            InfoBanner("Localização salva para esta pasta.", VaultPrimary, Icons.Default.CheckCircle)
                        }
                    }
                }
            }

            state.error?.let { InfoBanner(it, VaultDanger, Icons.Default.Warning) }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = viewModel::createVault,
                enabled = !state.isLoading,
                shape = VaultButtonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Criar pasta")
                }
            }
        }
    }
}
