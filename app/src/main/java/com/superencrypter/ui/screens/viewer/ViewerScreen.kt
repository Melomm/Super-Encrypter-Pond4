package com.superencrypter.ui.screens.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superencrypter.data.repository.PreviewContent
import com.superencrypter.ui.components.EmptySecurityState
import com.superencrypter.ui.components.InfoBanner
import com.superencrypter.ui.components.SecurityCard
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultDanger
import com.superencrypter.ui.theme.VaultMuted
import com.superencrypter.ui.theme.VaultSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = VaultBackground,
        topBar = {
            TopAppBar(
                title = { Text("Visualização") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultSurface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> InfoBanner(state.error.orEmpty(), VaultDanger, Icons.Default.Warning)
                state.content is PreviewContent.Image -> {
                    val file = (state.content as PreviewContent.Image).file
                    val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        InfoBanner("Erro ao carregar imagem.", VaultDanger, Icons.Default.Warning)
                    }
                }
                state.content is PreviewContent.Text -> {
                    val text = (state.content as PreviewContent.Text).value
                    SecurityCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Conteúdo descriptografado", fontWeight = FontWeight.SemiBold)
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                state.content is PreviewContent.Unsupported -> {
                    EmptySecurityState(
                        icon = Icons.Default.VisibilityOff,
                        title = "Preview indisponível",
                        body = (state.content as PreviewContent.Unsupported).message
                    )
                }
                else -> {
                    EmptySecurityState(
                        icon = Icons.Default.Description,
                        title = "Nenhum conteúdo",
                        body = "Selecione um arquivo destrancado para visualizar."
                    )
                }
            }
        }
    }
}
