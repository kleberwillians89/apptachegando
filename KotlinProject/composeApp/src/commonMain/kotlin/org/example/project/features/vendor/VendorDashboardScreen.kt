package org.example.project.features.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.example.project.core.data.AuthRepository
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.backendResult
import org.example.project.core.utils.UiState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDashboardScreen(
    onStartRoute: () -> Unit
) {
    var isOnline by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        when (val result = backendResult("Não foi possível carregar seu perfil.") {
            val user = AuthRepository.currentUser() ?: error("Entre na sua conta.")
            VendorRepository.getByUserId(user.id) ?: error("Perfil não encontrado.")
        }) {
            is UiState.Success -> isOnline = result.data.isOnline
            is UiState.Error -> errorMessage = result.message
            else -> Unit
        }
        isBusy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Meu trabalho") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isOnline) "Você aparece no mapa" else "Você não aparece no mapa",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isOnline,
                    enabled = !isBusy,
                    onCheckedChange = { online ->
                        scope.launch {
                            isBusy = true
                            when (val result = backendResult("Não foi possível atualizar sua disponibilidade.") { VendorRepository.setOnline(online) }) {
                                is UiState.Success -> { isOnline = online; errorMessage = null }
                                is UiState.Error -> errorMessage = result.message
                                else -> Unit
                            }
                            isBusy = false
                        }
                    }
                )
            }

            Text(
                "A disponibilidade permite entrar no catálogo. Para aparecer no mapa, publique uma localização durante uma rota ativa; posições antigas expiram.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(title = "Visitas hoje", value = "—", modifier = Modifier.weight(1f))
                StatCard(title = "Clientes perto", value = "—", modifier = Modifier.weight(1f))
            }

            Button(
                onClick = onStartRoute,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                enabled = isOnline
            ) {
                Text("Começar minha rota")
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.primary))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
