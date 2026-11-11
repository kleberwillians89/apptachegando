package org.example.project.features.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.core.data.backendResult
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.Vendor
import org.example.project.core.utils.UiState
import androidx.compose.material3.CircularProgressIndicator
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.PersonIcon
import org.example.project.core.navigation.StarIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorListScreen(
    category: String,
    onBack: () -> Unit,
    onVendorClick: (String) -> Unit
) {
    var page by remember(category) { mutableStateOf(0) }
    var retry by remember { mutableStateOf(0) }
    val tracker = org.example.project.core.utils.rememberLocationTracker()
    val uiState by produceState<UiState<List<Vendor>>>(initialValue = UiState.Loading, category, page, retry) {
        value = UiState.Loading
        val location = if (tracker.requestLocationPermission()) tracker.getCurrentLocation() else null
        if (location == null) value = UiState.Error("Ative a localização para buscar esta categoria no seu percurso.")
        else VendorRepository.listenToVendorStates(page = page, location = location, category = category).collect { value = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category) },
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
                    Column {
                        Text("Não foi possível buscar a categoria. Confira GPS, conexão e a liberação do contrato de descoberta.")
                        TextButton(onClick = { retry++ }) { Text("Tentar novamente") }
                    }
                }
            }
            is UiState.Success -> {
                val vendors = (uiState as UiState.Success<List<Vendor>>).data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Text(
                        "${vendors.size} vendedores nesta página",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Row {
                        TextButton(enabled = page > 0, onClick = { page-- }) { Text("Anterior") }
                        Text("Página ${page + 1}", modifier = Modifier.weight(1f))
                        TextButton(enabled = vendors.size == VendorRepository.PAGE_SIZE, onClick = { page++ }) { Text("Próxima") }
                    }
                    if (vendors.isEmpty()) Text(
                        "Nenhum profissional online nesta categoria e página. Volte mais tarde ou escolha outra categoria.",
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        items(vendors, key = { it.id }) { vendor ->
                            VendorListItemCard(
                                name = vendor.name,
                                description = vendor.description,
                                distance = vendor.distance,
                                rating = vendor.rating,
                                isOnline = vendor.isOnline,
                                onClick = { onVendorClick(vendor.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorListItemCard(
    name: String,
    description: String,
    distance: String,
    rating: String,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PersonIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(org.example.project.core.theme.AppColors.Success)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Online", style = MaterialTheme.typography.labelSmall, color = org.example.project.core.theme.AppColors.Success)
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                if (rating.isNotBlank() && rating != "0.0") Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = StarIcon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(rating, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(imageVector = LocationOnIcon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(distance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
