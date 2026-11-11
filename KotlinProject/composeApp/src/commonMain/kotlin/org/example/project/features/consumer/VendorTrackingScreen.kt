package org.example.project.features.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.core.data.backendResult
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.Vendor
import org.example.project.core.utils.UiState
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.PersonIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorTrackingScreen(
    vendorId: String,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val uiState by produceState<UiState<Vendor?>>(initialValue = UiState.Loading, vendorId) {
        value = backendResult("Não foi possível carregar os dados.") { VendorRepository.getById(vendorId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acompanhar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = ArrowBackIcon, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Erro ao carregar os dados.")
                }
            }
            is UiState.Success -> {
                val vendor = (uiState as UiState.Success<Vendor?>).data
                val vendorsFlow = androidx.compose.runtime.remember(vendorId) { VendorRepository.listenToVendorStates(vendorId = vendorId) }
                val vendorState by vendorsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
                val vendors = (vendorState as? UiState.Success)?.data.orEmpty()
                val currentVendor = if (vendorState is UiState.Success) vendors.singleOrNull() else null

                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (vendorState is UiState.Error) Text("Não foi possível atualizar a posição. Verifique a conexão.")
                    if (vendorState is UiState.Success && currentVendor == null) Text("Profissional indisponível.")
                    else if (vendorState is UiState.Success && trackingLocation(vendorState) == null) Text("Localização recente indisponível. Aguarde uma atualização do profissional.")
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = PersonIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            currentVendor?.name ?: vendor?.name ?: "Profissional",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val vendorLocation = trackingLocation(vendorState)

                org.example.project.core.ui.maps.NativeMap(
                    modifier = Modifier.fillMaxSize(),
                    userLocation = null,
                    vendorLocations = vendorLocation?.let { listOf(it) } ?: emptyList(),
                    onMapClick = {}
                )
                // Overlay button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    OutlinedButton(
                        onClick = { onProfileClick(vendorId) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text("Ver perfil")
                    }
                }
            }

            Text(
                text = "Previsão de chegada indisponível. A posição depende do GPS e da conexão do profissional.",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Anuncie aqui", style = MaterialTheme.typography.titleMedium)
            }
        }
        }
        }
    }
}
